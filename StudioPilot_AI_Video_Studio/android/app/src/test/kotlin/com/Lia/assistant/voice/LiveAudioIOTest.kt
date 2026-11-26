package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveAudioIOTest {
    @Test
    fun rmsUsesLittleEndianPcm16Samples() {
        val pcm = byteArrayOf(
            0x00,
            0x40,
            0x00,
            0xC0.toByte(),
        )

        assertEquals(0.5f, LiveAudioIO.rms(pcm), 0.0001f)
    }

    @Test
    fun rmsReturnsZeroForNoCompleteSamples() {
        assertEquals(0f, LiveAudioIO.rms(byteArrayOf(0x7F)), 0f)
    }
}
