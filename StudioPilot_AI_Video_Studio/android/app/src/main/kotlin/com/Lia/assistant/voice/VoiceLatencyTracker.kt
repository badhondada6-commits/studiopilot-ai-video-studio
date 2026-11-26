package com.Lia.assistant.voice

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceLatencyMark {
    INPUT_STARTED,
    FIRST_AUDIO,
    TURN_COMPLETED,
}

data class VoiceTurnLatency(
    val inputStartedAtMs: Long? = null,
    val firstAudioAtMs: Long? = null,
    val turnCompletedAtMs: Long? = null,
) {
    val firstAudioLatencyMs: Long?
        get() = if (inputStartedAtMs != null && firstAudioAtMs != null) {
            firstAudioAtMs - inputStartedAtMs
        } else {
            null
        }

    val totalTurnDurationMs: Long?
        get() = if (inputStartedAtMs != null && turnCompletedAtMs != null) {
            turnCompletedAtMs - inputStartedAtMs
        } else {
            null
        }
}

object VoiceLatencyTracker {
    private val _currentTurn = MutableStateFlow(VoiceTurnLatency())
    val currentTurn: StateFlow<VoiceTurnLatency> = _currentTurn.asStateFlow()

    private val _reconnectCount = MutableStateFlow(0)
    val reconnectCount: StateFlow<Int> = _reconnectCount.asStateFlow()

    private var lastMarkOrder = -1
    private var lastTimestampMs = Long.MIN_VALUE

    @Synchronized
    fun beginTurn() {
        _currentTurn.value = VoiceTurnLatency()
        lastMarkOrder = -1
        lastTimestampMs = Long.MIN_VALUE
    }

    @Synchronized
    fun mark(
        mark: VoiceLatencyMark,
        timestampMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        if (mark.ordinal <= lastMarkOrder || timestampMs < lastTimestampMs) return false
        val current = _currentTurn.value
        _currentTurn.value = when (mark) {
            VoiceLatencyMark.INPUT_STARTED -> current.copy(inputStartedAtMs = timestampMs)
            VoiceLatencyMark.FIRST_AUDIO -> current.copy(firstAudioAtMs = timestampMs)
            VoiceLatencyMark.TURN_COMPLETED -> current.copy(turnCompletedAtMs = timestampMs)
        }
        lastMarkOrder = mark.ordinal
        lastTimestampMs = timestampMs
        return true
    }

    @Synchronized
    fun incrementReconnectCount() {
        _reconnectCount.value += 1
    }

    @Synchronized
    fun reset() {
        beginTurn()
        _reconnectCount.value = 0
    }
}
