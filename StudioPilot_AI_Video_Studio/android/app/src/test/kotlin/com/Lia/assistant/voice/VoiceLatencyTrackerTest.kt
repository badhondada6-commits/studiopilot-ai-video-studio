package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLatencyTrackerTest {
    @Test
    fun inOrderMarksCalculateTurnDeltas() {
        VoiceLatencyTracker.reset()
        VoiceLatencyTracker.beginTurn()

        assertTrue(VoiceLatencyTracker.mark(VoiceLatencyMark.INPUT_STARTED, 1_000))
        assertTrue(VoiceLatencyTracker.mark(VoiceLatencyMark.FIRST_AUDIO, 1_275))
        assertTrue(VoiceLatencyTracker.mark(VoiceLatencyMark.TURN_COMPLETED, 1_650))

        val turn = VoiceLatencyTracker.currentTurn.value
        assertEquals(275L, turn.firstAudioLatencyMs)
        assertEquals(650L, turn.totalTurnDurationMs)
    }

    @Test
    fun outOfOrderMarksAreIgnored() {
        VoiceLatencyTracker.reset()
        VoiceLatencyTracker.beginTurn()

        assertTrue(VoiceLatencyTracker.mark(VoiceLatencyMark.INPUT_STARTED, 1_000))
        assertTrue(VoiceLatencyTracker.mark(VoiceLatencyMark.FIRST_AUDIO, 1_300))
        assertFalse(VoiceLatencyTracker.mark(VoiceLatencyMark.INPUT_STARTED, 1_100))
        assertFalse(VoiceLatencyTracker.mark(VoiceLatencyMark.TURN_COMPLETED, 1_200))

        val turn = VoiceLatencyTracker.currentTurn.value
        assertEquals(1_000L, turn.inputStartedAtMs)
        assertEquals(1_300L, turn.firstAudioAtMs)
        assertEquals(null, turn.turnCompletedAtMs)
    }

    @Test
    fun reconnectCountIsObservableAndResettable() {
        VoiceLatencyTracker.reset()

        VoiceLatencyTracker.incrementReconnectCount()
        VoiceLatencyTracker.incrementReconnectCount()
        assertEquals(2, VoiceLatencyTracker.reconnectCount.value)

        VoiceLatencyTracker.reset()
        assertEquals(0, VoiceLatencyTracker.reconnectCount.value)
    }
}
