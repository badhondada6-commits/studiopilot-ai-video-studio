package com.Lia.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.Lia.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.assistantSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "assistant_settings")

object PersonalityRepository {
    private val personalityKey = stringPreferencesKey(NovaPreferences.Keys.PERSONALITY)
    private val languageKey = stringPreferencesKey(NovaPreferences.Keys.LANGUAGE)
    private val assistantNameKey = stringPreferencesKey(NovaPreferences.Keys.ASSISTANT_NAME)
    private val voiceNameKey = stringPreferencesKey(NovaPreferences.Keys.VOICE_NAME)

    fun personalityFlow(context: Context): Flow<Personality> =
        context.assistantSettingsDataStore.data.map { preferences ->
            withContext(Dispatchers.IO) {
                PreferenceFallback.personality(
                    dataStoreValue = preferences[personalityKey],
                    legacyValue = NovaPreferences.getString(
                        context,
                        NovaPreferences.Keys.PERSONALITY,
                    ),
                )
            }
        }

    fun languageFlow(context: Context): Flow<LanguagePreference> =
        context.assistantSettingsDataStore.data.map { preferences ->
            withContext(Dispatchers.IO) {
                PreferenceFallback.language(
                    dataStoreValue = preferences[languageKey],
                    legacyValue = NovaPreferences.getString(context, NovaPreferences.Keys.LANGUAGE),
                )
            }
        }

    fun assistantNameFlow(context: Context): Flow<String> =
        context.assistantSettingsDataStore.data.map { preferences ->
            withContext(Dispatchers.IO) {
                PreferenceFallback.string(
                    dataStoreValue = preferences[assistantNameKey],
                    legacyValue = NovaPreferences.getString(
                        context,
                        NovaPreferences.Keys.ASSISTANT_NAME,
                    ),
                    defaultValue = AssistantBrand.NAME,
                )
            }
        }

    fun voiceNameFlow(context: Context): Flow<String> =
        context.assistantSettingsDataStore.data.map { preferences ->
            withContext(Dispatchers.IO) {
                PreferenceFallback.string(
                    dataStoreValue = preferences[voiceNameKey],
                    legacyValue = NovaPreferences.getString(
                        context,
                        NovaPreferences.Keys.VOICE_NAME,
                    ),
                    defaultValue = DEFAULT_VOICE_NAME,
                )
            }
        }

    suspend fun getPersonality(context: Context): Personality = personalityFlow(context).first()

    suspend fun getLanguage(context: Context): LanguagePreference = languageFlow(context).first()

    suspend fun getAssistantName(context: Context): String = assistantNameFlow(context).first()

    suspend fun getVoiceName(context: Context): String = voiceNameFlow(context).first()

    suspend fun setPersonality(context: Context, personality: Personality) {
        context.assistantSettingsDataStore.edit { it[personalityKey] = personality.name }
        NovaPreferences.putString(context, NovaPreferences.Keys.PERSONALITY, personality.name)
        VoiceSessionManager.refreshInstructions(context)
    }

    suspend fun setLanguage(context: Context, language: LanguagePreference) {
        context.assistantSettingsDataStore.edit { it[languageKey] = language.name }
        NovaPreferences.putString(context, NovaPreferences.Keys.LANGUAGE, language.name)
        VoiceSessionManager.refreshInstructions(context)
    }

    suspend fun setAssistantName(context: Context, name: String) {
        context.assistantSettingsDataStore.edit { it[assistantNameKey] = name }
        NovaPreferences.putString(context, NovaPreferences.Keys.ASSISTANT_NAME, name)
        VoiceSessionManager.refreshInstructions(context)
    }

    suspend fun setVoiceName(context: Context, name: String) {
        context.assistantSettingsDataStore.edit { it[voiceNameKey] = name }
        NovaPreferences.putString(context, NovaPreferences.Keys.VOICE_NAME, name)
        VoiceSessionManager.refreshInstructions(context)
    }

    private const val DEFAULT_VOICE_NAME = "Aoede"
}
