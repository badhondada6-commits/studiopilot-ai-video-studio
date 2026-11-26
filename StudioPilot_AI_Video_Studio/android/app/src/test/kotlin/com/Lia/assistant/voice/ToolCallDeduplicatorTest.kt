package com.Lia.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallDeduplicatorTest {
    @Test
    fun acceptsEachCallIdOnlyOnce() {
        val deduplicator = ToolCallDeduplicator()

        assertTrue(deduplicator.shouldProcess("call-1"))
        assertFalse(deduplicator.shouldProcess("call-1"))
        assertTrue(deduplicator.shouldProcess("call-2"))
    }

    @Test
    fun evictsOldestIdsWhenCapacityIsReached() {
        val deduplicator = ToolCallDeduplicator(maxRememberedIds = 2)

        assertTrue(deduplicator.shouldProcess("call-1"))
        assertTrue(deduplicator.shouldProcess("call-2"))
        assertTrue(deduplicator.shouldProcess("call-3"))
        assertTrue(deduplicator.shouldProcess("call-1"))
    }

    @Test
    fun clearAllowsIdsToBeProcessedAgain() {
        val deduplicator = ToolCallDeduplicator()

        assertTrue(deduplicator.shouldProcess("call-1"))
        deduplicator.clear()

        assertTrue(deduplicator.shouldProcess("call-1"))
    }
}
