package com.Lia.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceFallbackTest {
    @Test
    fun missingDataStoreValuesUseLegacyPreferences() {
        assertEquals(
            Personality.NORMAL,
            PreferenceFallback.personality(dataStoreValue = null, legacyValue = "NORMAL"),
        )
        assertEquals(
            LanguagePreference.HINGLISH,
            PreferenceFallback.language(dataStoreValue = null, legacyValue = "HINGLISH"),
        )
    }

    @Test
    fun missingLegacyValuesUseDefaults() {
        assertEquals(
            Personality.NORMAL,
            PreferenceFallback.personality(dataStoreValue = null, legacyValue = null),
        )
        assertEquals(
            LanguagePreference.AUTO,
            PreferenceFallback.language(dataStoreValue = null, legacyValue = null),
        )
    }

    @Test
    fun dataStoreValuesTakePrecedenceOverLegacyPreferences() {
        assertEquals(
            Personality.NORMAL,
            PreferenceFallback.personality(dataStoreValue = "NORMAL", legacyValue = "MISSING"),
        )
        assertEquals(
            LanguagePreference.ENGLISH,
            PreferenceFallback.language(dataStoreValue = "ENGLISH", legacyValue = "HINDI"),
        )
    }

    @Test
    fun invalidSavedValuesFallBackToSafeDefaults() {
        assertEquals(Personality.NORMAL, PreferenceFallback.personality("UNKNOWN"))
        assertEquals(LanguagePreference.AUTO, PreferenceFallback.language("UNKNOWN"))
    }

    @Test
    fun stringsPreferDataStoreThenLegacyThenDefault() {
        assertEquals("new", PreferenceFallback.string("new", "old", "default"))
        assertEquals("old", PreferenceFallback.string(null, "old", "default"))
        assertEquals("default", PreferenceFallback.string(null, null, "default"))
    }
}
