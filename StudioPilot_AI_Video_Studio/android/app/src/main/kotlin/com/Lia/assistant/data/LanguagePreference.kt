package com.Lia.assistant.data

enum class LanguagePreference(
    val displayName: String,
    val description: String,
) {
    AUTO("Auto", "Follows whatever you speak"),
    HINDI("Hindi", "Respond in Hindi"),
    HINGLISH("Hinglish", "Respond in Hinglish"),
    ENGLISH("English", "Respond in English"),
}
