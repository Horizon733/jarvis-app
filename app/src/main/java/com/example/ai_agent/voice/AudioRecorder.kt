package com.example.ai_agent.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    // Concurrent queue: previously a plain ArrayList was written by the IO capture coroutine
    // and drained on `stop()` after only a cooperative `cancel()`, which raced with any
    // in-flight `chunks.add(...)`. On some devices this produced dropped samples or
    // `ArrayIndexOutOfBoundsException`.
    private val chunks = ConcurrentLinkedQueue<ShortArray>()
    private val sampleRate = 16_000

    private val minBufferSize: Int = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )

    fun start(scope: CoroutineScope) {
        if (audioRecord != null) return
        chunks.clear()
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }
        record.startRecording()
        audioRecord = record
        val maxSamples = sampleRate * MAX_DURATION_SECONDS
        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(minBufferSize)
            var samplesCaptured = 0
            while (isActive && audioRecord != null) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    chunks.add(buffer.copyOf(read))
                    samplesCaptured += read
                    // Hard cap so a forgotten mic press can't OOM (32 KB/s at 16 kHz/16-bit,
                    // multi-minute recordings on low-memory phones).
                    if (samplesCaptured >= maxSamples) break
                }
            }
        }
    }

    /**
     * Best-effort synchronous stop — cancels the capture coroutine and releases the
     * AudioRecord. Callers that don't need the samples (e.g. app backgrounding path) should
     * use this. Any in-flight [chunks.add] may race with a subsequent `start()`; that's why
     * [start] clears the queue up front.
     */
    fun stop() {
        captureJob?.cancel()
        captureJob = null
        val record = audioRecord
        audioRecord = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
    }

    /**
     * Suspending stop that waits for the capture coroutine to actually exit before draining
     * the collected samples. Use this when you need the audio buffer (e.g. transcription).
     */
    suspend fun stopAndDrain(): FloatArray {
        val job = captureJob
        captureJob = null
        job?.let {
            it.cancel()
            it.join()
        }
        val record = audioRecord
        audioRecord = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        val snapshot = chunks.toList()
        chunks.clear()
        return snapshot.toFloatArray()
    }

    fun release() {
        stop()
    }

    companion object {
        private const val MAX_DURATION_SECONDS = 60
    }
}

private fun List<ShortArray>.toFloatArray(): FloatArray {
    val total = sumOf { it.size }
    if (total == 0) return FloatArray(0)
    val output = FloatArray(total)
    var offset = 0
    for (chunk in this) {
        for (index in chunk.indices) {
            output[offset + index] = chunk[index] / 32768.0f
        }
        offset += chunk.size
    }
    return output
}
