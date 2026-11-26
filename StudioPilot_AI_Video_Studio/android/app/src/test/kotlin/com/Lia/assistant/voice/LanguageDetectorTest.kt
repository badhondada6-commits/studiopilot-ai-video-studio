package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {
    @Test
    fun detectsHindiScript() {
        assertEquals(DetectedLanguage.HINDI, LanguageDetector.detect("नमस्ते, आप कैसे हैं?"))
    }

    @Test
    fun detectsHinglishMarkersInShortPhrase() {
        assertEquals(DetectedLanguage.HINGLISH, LanguageDetector.detect("kya haal hai yaar"))
    }

    @Test
    fun recognizesEnglishWithoutHinglishMarkers() {
        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detect("what's the weather"))
    }

    @Test
    fun blankInputIsUnknown() {
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("   "))
    }
}
