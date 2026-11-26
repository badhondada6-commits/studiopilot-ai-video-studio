package com.Lia.assistant.data

import android.content.Context

object ApiKeyStore {
    private const val PREFERENCES_NAME = "nova_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    fun getKey(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_API_KEY, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun saveKey(context: Context, key: String) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GEMINI_API_KEY, key.trim())
            .apply()
    }

    fun hasKey(context: Context): Boolean = getKey(context) != null
}
