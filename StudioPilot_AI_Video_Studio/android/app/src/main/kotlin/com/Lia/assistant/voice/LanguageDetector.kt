package com.Lia.assistant.voice

import java.util.Locale

object LanguageDetector {
    private val wordPattern = Regex("[^a-zA-Z']+")
    private val hinglishMarkers = setOf(
        "hai",
        "hain",
        "ho",
        "kya",
        "kyun",
        "kaise",
        "nahi",
        "haan",
        "mujhe",
        "tumhe",
        "aap",
        "mera",
        "yaar",
        "bhai",
        "accha",
        "theek",
        "matlab",
        "karna",
        "karo",
        "raha",
        "chahiye",
        "batao",
        "suno",
        "dekho",
        "abhi",
        "phir",
        "lekin",
        "bahut",
        "thoda",
        "kuch",
        "sab",
        "wala",
        "wali",
        "wale",
    )

    fun detect(text: String): DetectedLanguage {
        if (text.isBlank()) return DetectedLanguage.UNKNOWN
        if (text.any { it in '\u0900'..'\u097F' }) return DetectedLanguage.HINDI

        val words = text.lowercase(Locale.ROOT)
            .split(wordPattern)
            .filter(String::isNotEmpty)
        if (words.isEmpty()) return DetectedLanguage.UNKNOWN

        val markerCount = words.count(hinglishMarkers::contains)
        val isShortUtterance = words.size <= 4
        val isHinglish = if (isShortUtterance) {
            markerCount >= 1
        } else {
            markerCount >= 2 || markerCount.toDouble() / words.size >= 0.15
        }

        return if (isHinglish) DetectedLanguage.HINGLISH else DetectedLanguage.ENGLISH
    }
}
