package com.Lia.assistant.data

internal object PreferenceFallback {
    fun string(dataStoreValue: String?, legacyValue: String?, defaultValue: String): String =
        dataStoreValue ?: legacyValue ?: defaultValue

    fun personality(dataStoreValue: String?, legacyValue: String?): Personality =
        personality(string(dataStoreValue, legacyValue, Personality.NORMAL.name))

    fun personality(value: String): Personality =
        Personality.entries.firstOrNull { it.name == value } ?: Personality.NORMAL

    fun language(dataStoreValue: String?, legacyValue: String?): LanguagePreference =
        language(string(dataStoreValue, legacyValue, LanguagePreference.AUTO.name))

    fun language(value: String): LanguagePreference =
        LanguagePreference.entries.firstOrNull { it.name == value } ?: LanguagePreference.AUTO
}
