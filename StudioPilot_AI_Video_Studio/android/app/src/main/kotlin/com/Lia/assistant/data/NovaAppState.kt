package com.Lia.assistant.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class NovaAppState private constructor(
    private val context: Context,
    themeMode: String,
    reducedMotion: Boolean,
    orbStyle: String,
    personality: Personality,
    assistantName: String,
    userName: String,
    language: LanguagePreference,
    voiceName: String,
    speechSpeed: Float,
    voiceVolume: Float,
    conversations: List<String>,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var themeMode by mutableStateOf(themeMode)
        private set
    var reducedMotion by mutableStateOf(reducedMotion)
        private set
    var orbStyle by mutableStateOf(orbStyle)
        private set
    var personality by mutableStateOf(personality)
        private set
    var assistantName by mutableStateOf(assistantName)
        private set
    var userName by mutableStateOf(userName)
        private set
    var language by mutableStateOf(language)
        private set
    var voiceName by mutableStateOf(voiceName)
        private set
    var speechSpeed by mutableStateOf(speechSpeed)
        private set
    var voiceVolume by mutableStateOf(voiceVolume)
        private set
    var conversations by mutableStateOf(conversations.toMutableStateList())
        private set

    fun applyThemeMode(value: String) {
        themeMode = value
        NovaPreferences.putString(context, NovaPreferences.Keys.THEME_MODE, value)
    }

    fun applyReducedMotion(value: Boolean) {
        reducedMotion = value
        NovaPreferences.putBoolean(context, NovaPreferences.Keys.REDUCED_MOTION, value)
    }

    fun applyOrbStyle(value: String) {
        orbStyle = value
        NovaPreferences.putString(context, NovaPreferences.Keys.ORB_STYLE, value)
    }

    fun applyPersonality(value: Personality) {
        personality = value
        NovaPreferences.putString(context, NovaPreferences.Keys.PERSONALITY, value.name)
        repositoryScope.launch { PersonalityRepository.setPersonality(context, value) }
    }

    fun applyAssistantName(value: String) {
        assistantName = value
        NovaPreferences.putString(context, NovaPreferences.Keys.ASSISTANT_NAME, value)
        repositoryScope.launch { PersonalityRepository.setAssistantName(context, value) }
    }

    fun applyUserName(value: String) {
        userName = value
        NovaPreferences.putString(context, NovaPreferences.Keys.USER_NAME, value)
    }

    fun applyLanguage(value: LanguagePreference) {
        language = value
        NovaPreferences.putString(context, NovaPreferences.Keys.LANGUAGE, value.name)
        repositoryScope.launch { PersonalityRepository.setLanguage(context, value) }
    }

    fun applyVoiceName(value: String) {
        voiceName = value
        NovaPreferences.putString(context, NovaPreferences.Keys.VOICE_NAME, value)
        repositoryScope.launch { PersonalityRepository.setVoiceName(context, value) }
    }

    fun applySpeechSpeed(value: Float) {
        speechSpeed = value
        NovaPreferences.putFloat(context, NovaPreferences.Keys.SPEECH_SPEED, value)
    }

    fun applyVoiceVolume(value: Float) {
        voiceVolume = value
        NovaPreferences.putFloat(context, NovaPreferences.Keys.VOICE_VOLUME, value)
    }

    fun applyConversations(value: List<String>) {
        conversations = value.toMutableStateList()
        NovaPreferences.putString(
            context,
            NovaPreferences.Keys.CONVERSATIONS,
            JSONArray(value).toString(),
        )
    }

    fun close() {
        repositoryScope.cancel()
    }

    companion object {
        suspend fun load(context: Context): NovaAppState = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val savedPersonality = NovaPreferences.getString(
                appContext,
                NovaPreferences.Keys.PERSONALITY,
                Personality.NORMAL.name,
            )
            val savedLanguage = NovaPreferences.getString(
                appContext,
                NovaPreferences.Keys.LANGUAGE,
                LanguagePreference.AUTO.name,
            )
            val savedConversations = NovaPreferences.getString(
                appContext,
                NovaPreferences.Keys.CONVERSATIONS,
                "[]",
            ) ?: "[]"

            NovaAppState(
                context = appContext,
                themeMode = NovaPreferences.getString(
                    appContext,
                    NovaPreferences.Keys.THEME_MODE,
                    DEFAULT_THEME_MODE,
                ) ?: DEFAULT_THEME_MODE,
                reducedMotion = NovaPreferences.getBoolean(
                    appContext,
                    NovaPreferences.Keys.REDUCED_MOTION,
                ),
                orbStyle = NovaPreferences.getString(
                    appContext,
                    NovaPreferences.Keys.ORB_STYLE,
                    DEFAULT_ORB_STYLE,
                ) ?: DEFAULT_ORB_STYLE,
                personality = PreferenceFallback.personality(savedPersonality.orEmpty()),
                assistantName = NovaPreferences.getString(
                    appContext,
                    NovaPreferences.Keys.ASSISTANT_NAME,
                    AssistantBrand.NAME,
                ) ?: AssistantBrand.NAME,
                userName = NovaPreferences.getString(
                    appContext,
                    NovaPreferences.Keys.USER_NAME,
                    "",
                ) ?: "",
                language = PreferenceFallback.language(savedLanguage.orEmpty()),
                voiceName = NovaPreferences.getString(
                    appContext,
                    NovaPreferences.Keys.VOICE_NAME,
                    DEFAULT_VOICE_NAME,
                ) ?: DEFAULT_VOICE_NAME,
                speechSpeed = NovaPreferences.getFloat(
                    appContext,
                    NovaPreferences.Keys.SPEECH_SPEED,
                    DEFAULT_SPEECH_SPEED,
                ),
                voiceVolume = NovaPreferences.getFloat(
                    appContext,
                    NovaPreferences.Keys.VOICE_VOLUME,
                    DEFAULT_VOICE_VOLUME,
                ),
                conversations = parseConversations(savedConversations),
            )
        }

        private fun parseConversations(encoded: String): List<String> {
            val json = JSONArray(encoded)
            return List(json.length()) { index -> json.getString(index) }
        }

        private const val DEFAULT_THEME_MODE = "DARK"
        private const val DEFAULT_ORB_STYLE = "NOVA"
        private const val DEFAULT_VOICE_NAME = "Aoede"
        private const val DEFAULT_SPEECH_SPEED = 1f
        private const val DEFAULT_VOICE_VOLUME = 1f
    }
}
