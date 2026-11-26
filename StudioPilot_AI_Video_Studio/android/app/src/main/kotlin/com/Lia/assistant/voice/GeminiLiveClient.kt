package com.Lia.assistant.voice

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.Lia.assistant.LiaToolCatalog
import com.Lia.assistant.data.ApiKeyStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onSetupComplete()
        fun onAudioChunk(bytes: ByteArray)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onTurnComplete()
        fun onInterrupted()
        fun onError(message: String)
        fun onClosed()
    }

    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val toolCallDeduplicator = ToolCallDeduplicator()

    @Volatile
    private var webSocket: WebSocket? = null

    fun connect(systemInstruction: String, voiceName: String = "Aoede") {
        val apiKey = ApiKeyStore.getKey(appContext)
        if (apiKey == null) {
            listener.onError("Gemini API key is not configured")
            return
        }

        toolCallDeduplicator.clear()
        webSocket?.close(CLOSE_CODE_NORMAL, "Reconnecting")

        val request = Request.Builder()
            .url(WS_URL.replace(API_KEY_PLACEHOLDER, Uri.encode(apiKey)))
            .build()
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!webSocket.send(setupMessage(systemInstruction, voiceName).toString())) {
                        listener.onError("Could not send the Gemini Live setup message")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleServerMessage(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@GeminiLiveClient.webSocket === webSocket) {
                        this@GeminiLiveClient.webSocket = null
                    }
                    listener.onClosed()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (this@GeminiLiveClient.webSocket === webSocket) {
                        this@GeminiLiveClient.webSocket = null
                    }
                    listener.onError(
                        if (response == null) {
                            "Gemini Live connection failed"
                        } else {
                            "Gemini Live connection failed (HTTP ${response.code})"
                        },
                    )
                    listener.onClosed()
                }
            },
        )
    }

    fun sendAudio(pcmBytes: ByteArray) {
        val audio = JSONObject()
            .put("mimeType", INPUT_AUDIO_MIME_TYPE)
            .put("data", Base64.encodeToString(pcmBytes, Base64.NO_WRAP))
        sendMessage(
            JSONObject()
                .put("realtimeInput", JSONObject().put("audio", audio)),
        )
    }

    fun sendToolResponse(id: String, name: String, resultJson: String) {
        val functionResponse = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("response", JSONObject(resultJson))
        sendMessage(
            JSONObject()
                .put(
                    "toolResponse",
                    JSONObject().put(
                        "functionResponses",
                        JSONArray().put(functionResponse),
                    ),
                ),
        )
    }

    fun disconnect() {
        val socket = webSocket ?: return
        webSocket = null
        socket.close(CLOSE_CODE_NORMAL, "Client disconnected")
    }

    private fun setupMessage(systemInstruction: String, voiceName: String): JSONObject {
        val speechConfig = JSONObject()
            .put(
                "voiceConfig",
                JSONObject().put(
                    "prebuiltVoiceConfig",
                    JSONObject().put("voiceName", voiceName),
                ),
            )
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", speechConfig)
        val config = JSONObject()
            .put("model", MODEL)
            .put("generationConfig", generationConfig)
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction)),
                ),
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        val declarations = LiaToolCatalog.declarations()
        if (declarations.length() > 0) {
            config.put(
                "tools",
                JSONArray().put(
                    JSONObject().put("functionDeclarations", declarations),
                ),
            )
        }
        return JSONObject().put("setup", config)
    }

    private fun sendMessage(message: JSONObject) {
        val socket = webSocket
        if (socket == null) {
            listener.onError("Gemini Live is not connected")
        } else if (!socket.send(message.toString())) {
            listener.onError("Could not send the Gemini Live message")
        }
    }

    private fun handleServerMessage(message: String) {
        val root = try {
            JSONObject(message)
        } catch (_: JSONException) {
            listener.onError("Received an invalid Gemini Live response")
            return
        }

        if (root.has("sessionResumptionUpdate") || root.has("goAway")) return
        if (root.has("error")) {
            listener.onError("Gemini Live API returned an error")
            return
        }
        if (root.has("setupComplete")) listener.onSetupComplete()
        handleToolCalls(root.optJSONObject("toolCall"))

        val serverContent = root.optJSONObject("serverContent") ?: return
        val modelTurn = serverContent.optJSONObject("modelTurn")
        val parts = modelTurn?.optJSONArray("parts")
        if (parts != null) {
            for (index in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(index)?.optJSONObject("inlineData") ?: continue
                val mimeType = inlineData.optString("mimeType")
                if (!mimeType.startsWith(OUTPUT_AUDIO_MIME_TYPE, ignoreCase = true)) continue
                val encodedAudio = inlineData.optString("data")
                if (encodedAudio.isEmpty()) continue
                val audio = try {
                    Base64.decode(encodedAudio, Base64.DEFAULT)
                } catch (_: IllegalArgumentException) {
                    listener.onError("Received invalid audio from Gemini Live")
                    continue
                }
                listener.onAudioChunk(audio)
            }
        }

        serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf(String::isNotEmpty)
            ?.let(listener::onInputTranscript)
        serverContent.optJSONObject("outputTranscription")
            ?.optString("text")
            ?.takeIf(String::isNotEmpty)
            ?.let(listener::onOutputTranscript)
        if (serverContent.optBoolean("interrupted")) listener.onInterrupted()
        if (serverContent.optBoolean("turnComplete")) listener.onTurnComplete()
    }

    private fun handleToolCalls(toolCall: JSONObject?) {
        val functionCalls = toolCall?.optJSONArray("functionCalls") ?: return
        for (index in 0 until functionCalls.length()) {
            val functionCall = functionCalls.optJSONObject(index) ?: continue
            val id = functionCall.optString("id", "")
            val name = functionCall.optString("name", "")
            if (id.isEmpty() || name.isEmpty() || !toolCallDeduplicator.shouldProcess(id)) continue
            listener.onToolCall(
                id,
                name,
                functionCall.optJSONObject("args") ?: JSONObject(),
            )
        }
    }

    companion object {
        const val MODEL = "models/gemini-3.8-live"
        const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=<API_KEY>"

        private const val API_KEY_PLACEHOLDER = "<API_KEY>"
        private const val INPUT_AUDIO_MIME_TYPE = "audio/pcm;rate=16000"
        private const val OUTPUT_AUDIO_MIME_TYPE = "audio/pcm"
        private const val CLOSE_CODE_NORMAL = 1000
    }
}
