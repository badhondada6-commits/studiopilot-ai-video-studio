package com.Lia.assistant.voice

import android.content.Context
import com.Lia.assistant.ActionExecutor
import com.Lia.assistant.InstalledAppLabelCache
import com.Lia.assistant.LiaAction
import com.Lia.assistant.LiaToolCatalog
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality
import com.Lia.assistant.data.PersonalityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class NovaOrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    CONNECTING,
    ERROR,
}

object VoiceSessionManager {
    private const val RECONNECT_DELAY_MS = 1_200L
    private const val MAX_RECONNECT_ATTEMPTS = 5
    private const val PLAYBACK_SILENCE_TIMEOUT_MS = 300L
    private const val VAD_THRESHOLD = 0.02f
    private const val CONNECTION_LOST_MESSAGE =
        "Connection lost - tap Stop and reopen Voice Mode"

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val lifecycleLock = Any()
    private val playbackLock = Any()
    private val sessionEpoch = AtomicLong(0)
    private val lifecycleGeneration = AtomicLong(0)
    private val micStarted = AtomicBoolean(false)

    private val _state = MutableStateFlow(NovaOrbState.IDLE)
    val state: StateFlow<NovaOrbState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _statusText = MutableStateFlow("Ready to talk")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPersonality = MutableStateFlow(Personality.NORMAL)
    val currentPersonality: StateFlow<Personality> = _currentPersonality.asStateFlow()

    private val _currentLanguage = MutableStateFlow(LanguagePreference.AUTO)
    val currentLanguage: StateFlow<LanguagePreference> = _currentLanguage.asStateFlow()

    private val _detectedLanguage = MutableStateFlow(DetectedLanguage.UNKNOWN)
    val detectedLanguage: StateFlow<DetectedLanguage> = _detectedLanguage.asStateFlow()

    private val _vadActive = MutableStateFlow(false)
    val vadActive: StateFlow<Boolean> = _vadActive.asStateFlow()

    private val _conversationTurns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val conversationTurns: StateFlow<List<ConversationTurn>> = _conversationTurns.asStateFlow()

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var geminiClient: GeminiLiveClient? = null

    @Volatile
    private var liveAudioIO: LiveAudioIO? = null

    @Volatile
    private var activeScope: CoroutineScope? = null
    private var reconnectAttempts = 0
    private var reconnectRequestId = 0L
    private var reconnectJob: Job? = null
    private var playbackSilenceWatchdog: Job? = null
    private var playbackTurnActive = false

    fun start(context: Context) {
        val appContext = context.applicationContext
        val generation: Long
        val scope: CoroutineScope
        synchronized(lifecycleLock) {
            if (_isActive.value) return

            generation = lifecycleGeneration.incrementAndGet()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            activeScope = scope
            applicationContext = appContext
            _isActive.value = true
            _state.value = NovaOrbState.CONNECTING
            _statusText.value = "Connecting to ${AssistantBrand.NAME}..."
            _amplitude.value = 0f
            _muted.value = false
            _lastError.value = null
            _currentPersonality.value = Personality.NORMAL
            _currentLanguage.value = LanguagePreference.AUTO
            _detectedLanguage.value = DetectedLanguage.UNKNOWN
            _vadActive.value = false
            reconnectAttempts = 0
            reconnectRequestId++
            reconnectJob?.cancel()
            reconnectJob = null
            playbackSilenceWatchdog?.cancel()
            playbackSilenceWatchdog = null
            playbackTurnActive = false
            micStarted.set(false)
            liveAudioIO = LiveAudioIO(
                onMicChunk = { bytes -> forwardMicChunk(generation, bytes) },
                onMicAmplitude = { value -> handleMicAmplitude(generation, value) },
            )
        }

        ConversationMemory.clear()
        _conversationTurns.value = emptyList()
        EchoGuard.reset()
        VoiceLatencyTracker.reset()

        scope.launch {
            try {
                InstalledAppLabelCache.prewarm(appContext)
            } catch (_: RuntimeException) {
                _lastError.value = "Installed app labels could not be prepared"
            }
            if (isCurrentLifecycle(generation)) {
                connectionMutex.withLock {
                    if (isCurrentLifecycle(generation)) connectSession(isResuming = false)
                }
            }
        }
    }

    fun toggleMute() {
        val nowMuted = !_muted.value
        _muted.value = nowMuted
        if (nowMuted) {
            _amplitude.value = 0f
            _vadActive.value = false
        }
    }

    fun stop() {
        val client: GeminiLiveClient?
        val audioIO: LiveAudioIO?
        val scope: CoroutineScope?
        synchronized(lifecycleLock) {
            _isActive.value = false
            lifecycleGeneration.incrementAndGet()
            sessionEpoch.incrementAndGet()
            reconnectRequestId++
            reconnectJob?.cancel()
            reconnectJob = null
            client = geminiClient
            geminiClient = null
            audioIO = liveAudioIO
            liveAudioIO = null
            scope = activeScope
            activeScope = null
            applicationContext = null
            micStarted.set(false)
        }
        scope?.coroutineContext?.get(Job)?.cancel()
        synchronized(playbackLock) {
            playbackSilenceWatchdog?.cancel()
            playbackSilenceWatchdog = null
            playbackTurnActive = false
        }
        client?.disconnect()
        if (audioIO != null) {
            processScope.launch { audioIO.release() }
        }
        EchoGuard.reset()
        ConversationMemory.clear()
        _conversationTurns.value = emptyList()
        _state.value = NovaOrbState.IDLE
        _amplitude.value = 0f
        _muted.value = false
        _statusText.value = "Ready to talk"
        _lastError.value = null
        _detectedLanguage.value = DetectedLanguage.UNKNOWN
        _vadActive.value = false
    }

    fun refreshInstructions(context: Context) {
        if (!_isActive.value) return
        val appContext = context.applicationContext
        val scope: CoroutineScope
        synchronized(lifecycleLock) {
            scope = activeScope ?: return
            applicationContext = appContext
            reconnectRequestId++
            reconnectJob?.cancel()
            reconnectJob = null
        }
        scope.launch {
            connectionMutex.withLock {
                if (_isActive.value) connectSession(isResuming = true)
            }
        }
    }

    private suspend fun connectSession(isResuming: Boolean) {
        if (!_isActive.value) return
        val epoch = sessionEpoch.incrementAndGet()
        val previousClient = synchronized(lifecycleLock) {
            geminiClient.also { geminiClient = null }
        }
        previousClient?.disconnect()
        if (!isCurrentEpoch(epoch)) return

        _state.value = NovaOrbState.CONNECTING
        _statusText.value = "Connecting to ${AssistantBrand.NAME}..."
        val context = applicationContext ?: return

        val personality: Personality
        val language: LanguagePreference
        val assistantName: String
        val savedVoiceName: String
        try {
            personality = PersonalityRepository.getPersonality(context)
            language = PersonalityRepository.getLanguage(context)
            assistantName = PersonalityRepository.getAssistantName(context)
            savedVoiceName = PersonalityRepository.getVoiceName(context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrentEpoch(epoch)) {
                _lastError.value = "Voice settings could not be loaded"
                scheduleReconnect(epoch)
            }
            return
        }
        if (!isCurrentEpoch(epoch)) return

        _currentPersonality.value = personality
        _currentLanguage.value = language
        val prompt = PersonalityPromptBuilder.build(
            personality = personality,
            language = language,
            detectedLanguage = _detectedLanguage.value,
            recap = if (isResuming) ConversationMemory.recap() else null,
            assistantName = assistantName,
        )
        val client = GeminiLiveClient(context, createListener(epoch))
        synchronized(lifecycleLock) {
            if (!_isActive.value || sessionEpoch.get() != epoch) return
            geminiClient = client
            try {
                client.connect(prompt, mapVoiceName(savedVoiceName))
            } catch (error: RuntimeException) {
                _lastError.value = "Gemini Live could not be started"
                scheduleReconnect(epoch)
            }
        }
    }

    private fun createListener(epoch: Long) = object : GeminiLiveClient.Listener {
        override fun onSetupComplete() {
            synchronized(lifecycleLock) {
                if (!_isActive.value || sessionEpoch.get() != epoch) return
                reconnectAttempts = 0
                _lastError.value = null
                _state.value = NovaOrbState.LISTENING
                _statusText.value = "${AssistantBrand.NAME} is listening..."
                if (micStarted.compareAndSet(false, true)) {
                    try {
                        liveAudioIO?.startRecording()
                    } catch (_: RuntimeException) {
                        micStarted.set(false)
                        _lastError.value = "Microphone could not be started"
                        scheduleReconnect(epoch)
                    }
                }
            }
        }

        override fun onAudioChunk(bytes: ByteArray) {
            if (!isCurrentEpoch(epoch)) return
            val audioIO = liveAudioIO ?: return
            synchronized(playbackLock) {
                if (!playbackTurnActive) {
                    playbackTurnActive = true
                    EchoGuard.notifyPlaybackStarted()
                    VoiceLatencyTracker.mark(VoiceLatencyMark.FIRST_AUDIO)
                }
            }
            try {
                val value = audioIO.playChunk(bytes)
                if (!isCurrentEpoch(epoch)) return
                _amplitude.value = value
                _state.value = NovaOrbState.SPEAKING
                _statusText.value = "${AssistantBrand.NAME} is speaking..."
                rearmPlaybackSilenceWatchdog(epoch)
            } catch (_: RuntimeException) {
                _lastError.value = "Audio playback failed"
                finishPlayback(epoch, flush = true)
                scheduleReconnect(epoch)
            }
        }

        override fun onInputTranscript(text: String) {
            if (text.isBlank()) return
            synchronized(lifecycleLock) {
                if (!_isActive.value || sessionEpoch.get() != epoch) return
                ConversationMemory.addUser(text)
                _conversationTurns.value = ConversationMemory.turns()
                _detectedLanguage.value = LanguageDetector.detect(text)
                _state.value = NovaOrbState.THINKING
                _statusText.value = "${AssistantBrand.NAME} is thinking..."
            }
        }

        override fun onOutputTranscript(text: String) {
            if (text.isBlank()) return
            synchronized(lifecycleLock) {
                if (!_isActive.value || sessionEpoch.get() != epoch) return
                ConversationMemory.addAssistant(text)
                _conversationTurns.value = ConversationMemory.turns()
            }
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            if (!isCurrentEpoch(epoch)) return
            if (!LiaToolCatalog.isDeclared(name)) {
                sendToolResponse(
                    epoch,
                    id,
                    name,
                    toolUnavailableResponse("That action is not available in this version."),
                )
                return
            }
            if (name == BUILD_WEBSITE_TOOL) {
                sendToolResponse(
                    epoch,
                    id,
                    name,
                    toolUnavailableResponse("Website building is not available in this version yet."),
                )
                return
            }

            val scope = activeScope ?: return
            scope.launch {
                val response = try {
                    val result = withContext(Dispatchers.IO) {
                        ActionExecutor.execute(LiaAction(name = name, args = args))
                    }
                    JSONObject()
                        .put("result", if (result.available) "success" else "unavailable")
                        .put("message", result.message)
                        .toString()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _lastError.value = "The requested action failed"
                    JSONObject()
                        .put("result", "error")
                        .put("message", "The requested action failed")
                        .toString()
                }
                sendToolResponse(epoch, id, name, response)
            }
        }

        override fun onTurnComplete() {
            if (!isCurrentEpoch(epoch)) return
            VoiceLatencyTracker.mark(VoiceLatencyMark.TURN_COMPLETED)
            finishPlayback(epoch, flush = true)
        }

        override fun onInterrupted() {
            if (!isCurrentEpoch(epoch)) return
            VoiceLatencyTracker.mark(VoiceLatencyMark.TURN_COMPLETED)
            finishPlayback(epoch, flush = true)
        }

        override fun onError(message: String) {
            if (!isCurrentEpoch(epoch)) return
            _lastError.value = message
            scheduleReconnect(epoch)
        }

        override fun onClosed() {
            if (!isCurrentEpoch(epoch)) return
            _lastError.value = "Gemini Live connection closed"
            scheduleReconnect(epoch)
        }
    }

    private fun forwardMicChunk(generation: Long, bytes: ByteArray) {
        if (!isCurrentLifecycle(generation) || _muted.value || EchoGuard.isMicMuted()) return
        if (_state.value != NovaOrbState.LISTENING && _state.value != NovaOrbState.SPEAKING) return
        geminiClient?.sendAudio(bytes)
    }

    private fun handleMicAmplitude(generation: Long, value: Float) {
        if (!isCurrentLifecycle(generation)) return
        if (_muted.value || EchoGuard.isMicMuted()) {
            _vadActive.value = false
            if (_muted.value) _amplitude.value = 0f
            return
        }

        _amplitude.value = value
        val speaking = value >= VAD_THRESHOLD
        if (speaking && !_vadActive.value) {
            VoiceLatencyTracker.beginTurn()
            VoiceLatencyTracker.mark(VoiceLatencyMark.INPUT_STARTED)
        }
        _vadActive.value = speaking
    }

    private fun rearmPlaybackSilenceWatchdog(epoch: Long) {
        val scope = activeScope ?: return
        synchronized(playbackLock) {
            playbackSilenceWatchdog?.cancel()
            playbackSilenceWatchdog = scope.launch {
                delay(PLAYBACK_SILENCE_TIMEOUT_MS)
                finishPlayback(epoch, flush = true)
            }
        }
    }

    private fun finishPlayback(epoch: Long, flush: Boolean) {
        if (!isCurrentEpoch(epoch)) return
        val playbackWasActive = synchronized(playbackLock) {
            playbackSilenceWatchdog?.cancel()
            playbackSilenceWatchdog = null
            val wasActive = playbackTurnActive
            playbackTurnActive = false
            wasActive
        }
        try {
            if (flush) liveAudioIO?.flushPlayback()
        } catch (_: RuntimeException) {
            _lastError.value = "Audio playback could not be flushed"
        } finally {
            if (playbackWasActive) {
                VoiceLatencyTracker.mark(VoiceLatencyMark.TURN_COMPLETED)
                EchoGuard.notifyPlaybackStopped()
            }
        }
        if (!isCurrentEpoch(epoch)) return
        _amplitude.value = 0f
        _state.value = NovaOrbState.LISTENING
        _statusText.value = "${AssistantBrand.NAME} is listening..."
    }

    private fun sendToolResponse(epoch: Long, id: String, name: String, resultJson: String) {
        if (!isCurrentEpoch(epoch)) return
        geminiClient?.sendToolResponse(id, name, resultJson)
    }

    private fun toolUnavailableResponse(message: String): String =
        JSONObject()
            .put("result", "unavailable")
            .put("message", message)
            .toString()

    private fun scheduleReconnect(epoch: Long) {
        synchronized(lifecycleLock) {
            if (!_isActive.value || sessionEpoch.get() != epoch) return
            if (reconnectJob?.isActive == true) return
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                _state.value = NovaOrbState.ERROR
                _statusText.value = CONNECTION_LOST_MESSAGE
                _lastError.value = CONNECTION_LOST_MESSAGE
                return
            }

            reconnectAttempts++
            VoiceLatencyTracker.incrementReconnectCount()
            val requestId = ++reconnectRequestId
            val scope = activeScope ?: return
            lateinit var job: Job
            job = scope.launch {
                delay(RECONNECT_DELAY_MS)
                connectionMutex.withLock {
                    synchronized(lifecycleLock) {
                        if (reconnectRequestId == requestId && reconnectJob === job) {
                            reconnectJob = null
                        }
                    }
                    if (isCurrentEpoch(epoch)) connectSession(isResuming = true)
                }
            }
            reconnectJob = job
        }
    }

    private fun isCurrentEpoch(epoch: Long): Boolean =
        _isActive.value && sessionEpoch.get() == epoch

    private fun isCurrentLifecycle(generation: Long): Boolean =
        _isActive.value && lifecycleGeneration.get() == generation

    private fun mapVoiceName(savedName: String): String = when (savedName.trim().lowercase()) {
        "aoede" -> "Aoede"
        "charon" -> "Charon"
        "fenrir" -> "Fenrir"
        "kore" -> "Kore"
        "leda" -> "Leda"
        "orus" -> "Orus"
        "puck" -> "Puck"
        "zephyr" -> "Zephyr"
        else -> "Aoede"
    }

    private const val BUILD_WEBSITE_TOOL = "build_website"
}
