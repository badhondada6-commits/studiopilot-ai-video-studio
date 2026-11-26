package com.Lia.assistant.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EchoGuardTest {
    @Test
    fun countsConcurrentPlaybackAndAppliesEchoTail() = runTest {
        EchoGuard.reset()
        EchoGuard.useScopeForTests(this)
        try {
            EchoGuard.notifyPlaybackStarted()
            EchoGuard.notifyPlaybackStarted()
            assertTrue(EchoGuard.isMicMuted())

            EchoGuard.notifyPlaybackStopped()
            advanceTimeBy(500)
            runCurrent()
            assertTrue(EchoGuard.isMicMuted())

            EchoGuard.notifyPlaybackStopped()
            advanceTimeBy(449)
            runCurrent()
            assertTrue(EchoGuard.isMicMuted())
            advanceTimeBy(1)
            runCurrent()
            assertFalse(EchoGuard.isMicMuted())
        } finally {
            EchoGuard.reset()
        }
    }

    @Test
    fun newPlaybackCancelsPendingTailAndWatchdogEventuallyUnmutes() = runTest {
        EchoGuard.reset()
        EchoGuard.useScopeForTests(this)
        try {
            EchoGuard.notifyPlaybackStarted()
            EchoGuard.notifyPlaybackStopped()
            advanceTimeBy(300)
            runCurrent()

            EchoGuard.notifyPlaybackStarted()
            advanceTimeBy(200)
            runCurrent()
            assertTrue(EchoGuard.isMicMuted())

            advanceTimeBy(59_800)
            runCurrent()
            assertFalse(EchoGuard.isMicMuted())
        } finally {
            EchoGuard.reset()
        }
    }
}
