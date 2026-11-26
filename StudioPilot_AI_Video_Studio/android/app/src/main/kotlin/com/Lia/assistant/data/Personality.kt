package com.Lia.assistant.data

enum class Personality(
    val displayName: String,
    val description: String,
    val icon: String,
    val systemInstruction: String,
) {
    NORMAL(
        displayName = "Normal",
        description = "A helpful, balanced assistant",
        icon = "sparkles",
        systemInstruction = "Personality: Normal. Be helpful, clear, and conversational.",
    ),
}
