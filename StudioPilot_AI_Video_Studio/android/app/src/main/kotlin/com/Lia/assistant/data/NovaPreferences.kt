package com.Lia.assistant.data

import android.content.Context
import android.content.SharedPreferences

object NovaPreferences {
    private const val PREFERENCES_NAME = "nova_prefs"

    object Keys {
        const val THEME_MODE = "theme_mode"
        const val REDUCED_MOTION = "reduced_motion"
        const val ORB_STYLE = "orb_style"
        const val PERSONALITY = "personality"
        const val USER_NAME = "user_name"
        const val VOICE_INPUT_ENABLED = "voice_input_enabled"
        const val WAKE_WORD_ENABLED = "wake_word_enabled"
        const val LANGUAGE = "language"
        const val HAS_ONBOARDED = "has_onboarded"
        const val VOICE_NAME = "voice_name"
        const val RESPONSE_STYLE = "response_style"
        const val SPEECH_SPEED = "speech_speed"
        const val VOICE_VOLUME = "voice_volume"
        const val ASSISTANT_NAME = "assistant_name"
        const val CONVERSATIONS = "conversations"
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? =
        preferences(context).getString(key, defaultValue)

    fun putString(context: Context, key: String, value: String) {
        preferences(context).edit().putString(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean =
        preferences(context).getBoolean(key, defaultValue)

    fun putBoolean(context: Context, key: String, value: Boolean) {
        preferences(context).edit().putBoolean(key, value).apply()
    }

    fun getFloat(context: Context, key: String, defaultValue: Float = 0f): Float =
        preferences(context).getFloat(key, defaultValue)

    fun putFloat(context: Context, key: String, value: Float) {
        preferences(context).edit().putFloat(key, value).apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
