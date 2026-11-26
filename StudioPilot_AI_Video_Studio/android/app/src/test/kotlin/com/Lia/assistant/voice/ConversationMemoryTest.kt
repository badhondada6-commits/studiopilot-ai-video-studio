package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryTest {
    @Test
    fun recapKeepsOnlyTheLatestTwelveTurns() {
        ConversationMemory.clear()
        repeat(13) { index -> ConversationMemory.addUser("message $index") }

        val recap = ConversationMemory.recap().orEmpty()
        assertEquals(12, ConversationMemory.turns().size)
        assertTrue(recap.startsWith("User: message 1"))
        assertTrue(recap.endsWith("User: message 12"))
        ConversationMemory.clear()
    }

    @Test
    fun recapIsCappedToTheLastSixteenHundredCharacters() {
        ConversationMemory.clear()
        ConversationMemory.addUser("x".repeat(1700))

        assertEquals(1600, ConversationMemory.recap()?.length)
        ConversationMemory.clear()
    }
}
