package com.Lia.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

class LiveAudioIO(
    private val onMicChunk: (ByteArray) -> Unit,
    private val onMicAmplitude: (Float) -> Unit,
) {
    private val recording = AtomicBoolean(false)
    private val audioEffects = mutableListOf<android.media.audiofx.AudioEffect>()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var micThread: Thread? = null

    private var audioTrack: AudioTrack? = null
    private var playbackVolume = 1f

    @Synchronized
    fun startRecording() {
        if (recording.get()) return
        stopRecording()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_INPUT,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSize > 0) {
            "AudioRecord could not determine a valid buffer size ($minBufferSize)"
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_INPUT,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBufferSize, MIN_RECORD_BUFFER_SIZE),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "AudioRecord failed to initialize"
        }

        try {
            attachEffects(recorder.audioSessionId)
            recorder.startRecording()
        } catch (error: RuntimeException) {
            releaseEffects()
            recorder.release()
            throw error
        }

        audioRecord = recorder
        recording.set(true)
        micThread = Thread({ readMicrophone(recorder) }, MIC_THREAD_NAME).apply {
            start()
        }
    }

    @Synchronized
    fun stopRecording() {
        val recorder = audioRecord
        recording.set(false)
        if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // The recorder may have stopped between checking its state and stopping it.
            }
        }

        val thread = micThread
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(THREAD_JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        recorder?.release()
        audioRecord = null
        micThread = null
        releaseEffects()
    }

    @Synchronized
    fun playChunk(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return rms(bytes)
        val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }

        var offset = 0
        while (offset < bytes.size) {
            val written = track.write(
                bytes,
                offset,
                bytes.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            check(written > 0) { "AudioTrack failed to write playback audio ($written)" }
            offset += written
        }
        return rms(bytes)
    }

    @Synchronized
    fun flushPlayback() {
        val track = audioTrack ?: return
        track.pause()
        track.flush()
        track.play()
    }

    @Synchronized
    fun setVolume(volume: Float) {
        playbackVolume = if (volume.isNaN()) 1f else volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(playbackVolume)
    }

    @Synchronized
    fun release() {
        stopRecording()
        audioTrack?.release()
        audioTrack = null
    }

    private fun readMicrophone(recorder: AudioRecord) {
        val buffer = ByteArray(MIC_READ_CHUNK_SIZE)
        try {
            while (recording.get()) {
                val readCount = try {
                    recorder.read(buffer, 0, buffer.size)
                } catch (_: IllegalStateException) {
                    break
                }
                if (readCount < 0) break
                if (readCount == 0 || !recording.get()) continue

                val chunk = buffer.copyOf(readCount)
                onMicChunk(chunk)
                onMicAmplitude(rms(chunk))
            }
        } finally {
            recording.set(false)
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUTPUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSize > 0) {
            "AudioTrack could not determine a valid buffer size ($minBufferSize)"
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_OUTPUT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBufferSize * PLAYBACK_BUFFER_MULTIPLIER)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack failed to initialize")
        }
        track.setVolume(playbackVolume)
        return track
    }

    private fun attachEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioSessionId)?.also {
                    it.enabled = true
                    audioEffects += it
                }
            }
        } catch (_: RuntimeException) {
            // Audio effects are optional on devices that do not support them.
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.also {
                    it.enabled = true
                    audioEffects += it
                }
            }
        } catch (_: RuntimeException) {
            // Audio effects are optional on devices that do not support them.
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(audioSessionId)?.also {
                    it.enabled = true
                    audioEffects += it
                }
            }
        } catch (_: RuntimeException) {
            // Audio effects are optional on devices that do not support them.
        }
    }

    private fun releaseEffects() {
        audioEffects.forEach { effect ->
            try {
                effect.release()
            } catch (_: RuntimeException) {
                // Continue releasing remaining effects.
            }
        }
        audioEffects.clear()
    }

    companion object {
        private const val SAMPLE_RATE_INPUT = 16_000
        private const val SAMPLE_RATE_OUTPUT = 24_000
        private const val MIN_RECORD_BUFFER_SIZE = 4_096
        private const val MIC_READ_CHUNK_SIZE = 2_048
        private const val PLAYBACK_BUFFER_MULTIPLIER = 4
        private const val MIC_THREAD_NAME = "LiaAudioCapture"
        private const val THREAD_JOIN_TIMEOUT_MS = 1_000L

        fun rms(pcmBytes: ByteArray): Float {
            val sampleCount = pcmBytes.size / Short.SIZE_BYTES
            if (sampleCount == 0) return 0f

            var sumSquares = 0.0
            var index = 0
            while (index + 1 < pcmBytes.size) {
                val sample = (
                    (pcmBytes[index].toInt() and 0xFF) or
                        (pcmBytes[index + 1].toInt() shl 8)
                    ).toShort().toInt()
                sumSquares += sample.toDouble() * sample
                index += Short.SIZE_BYTES
            }
            return (sqrt(sumSquares / sampleCount) / 32768.0)
                .toFloat()
                .coerceIn(0f, 1f)
        }
    }
}
