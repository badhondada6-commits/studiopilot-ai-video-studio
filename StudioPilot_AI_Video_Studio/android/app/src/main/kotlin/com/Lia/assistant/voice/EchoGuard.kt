package com.Lia.assistant.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object EchoGuard {
    private const val ECHO_TAIL_MS = 450L
    private const val WATCHDOG_MS = 60_000L

    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerScope: CoroutineScope = defaultScope
    private var activePlaybackCount = 0
    private var micMuted = false
    private var tailJob: Job? = null
    private var watchdogJob: Job? = null

    @Synchronized
    fun notifyPlaybackStarted() {
        tailJob?.cancel()
        tailJob = null
        if (activePlaybackCount == 0) {
            watchdogJob?.cancel()
            watchdogJob = timerScope.launch {
                delay(WATCHDOG_MS)
                synchronized(this@EchoGuard) {
                    if (activePlaybackCount > 0) {
                        activePlaybackCount = 0
                        micMuted = false
                        tailJob?.cancel()
                        tailJob = null
                    }
                    watchdogJob = null
                }
            }
        }
        activePlaybackCount++
        micMuted = true
    }

    @Synchronized
    fun notifyPlaybackStopped() {
        if (activePlaybackCount == 0) return
        activePlaybackCount--
        if (activePlaybackCount == 0) {
            watchdogJob?.cancel()
            watchdogJob = null
            tailJob?.cancel()
            tailJob = timerScope.launch {
                delay(ECHO_TAIL_MS)
                synchronized(this@EchoGuard) {
                    if (activePlaybackCount == 0) {
                        micMuted = false
                    }
                    tailJob = null
                }
            }
        }
    }

    @Synchronized
    fun isMicMuted(): Boolean = micMuted

    @Synchronized
    fun reset() {
        tailJob?.cancel()
        watchdogJob?.cancel()
        tailJob = null
        watchdogJob = null
        activePlaybackCount = 0
        micMuted = false
        timerScope = defaultScope
    }

    @Synchronized
    internal fun useScopeForTests(scope: CoroutineScope) {
        tailJob?.cancel()
        watchdogJob?.cancel()
        tailJob = null
        watchdogJob = null
        timerScope = scope
    }
}
