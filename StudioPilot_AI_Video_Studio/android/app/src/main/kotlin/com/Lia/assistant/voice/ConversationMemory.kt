package com.Lia.assistant.voice

enum class ConversationRole {
    USER,
    ASSISTANT,
}

data class ConversationTurn(
    val role: ConversationRole,
    val text: String,
)

object ConversationMemory {
    private const val MAX_TURNS = 12
    private const val MAX_RECAP_CHARACTERS = 1600
    private val turns = ArrayDeque<ConversationTurn>()

    @Synchronized
    fun add(role: ConversationRole, text: String) {
        if (text.isBlank()) return
        turns.addLast(ConversationTurn(role, text))
        while (turns.size > MAX_TURNS) {
            turns.removeFirst()
        }
    }

    @Synchronized
    fun addUser(text: String) = add(ConversationRole.USER, text)

    @Synchronized
    fun addAssistant(text: String) = add(ConversationRole.ASSISTANT, text)

    @Synchronized
    fun turns(): List<ConversationTurn> = turns.toList()

    @Synchronized
    fun recap(): String? {
        if (turns.isEmpty()) return null
        return turns.joinToString(separator = "\n") { turn ->
            val speaker = if (turn.role == ConversationRole.USER) "User" else "You"
            "$speaker: ${turn.text}"
        }.takeLast(MAX_RECAP_CHARACTERS)
    }

    @Synchronized
    fun clear() {
        turns.clear()
    }
}
