package com.example.ai_agent.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceModelManager: VoiceModelManager,
) {
    private var tts: OfflineTts? = null
    private var activeLanguage: VoiceLanguage? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var cancelled = false

    fun prepare(language: VoiceLanguage) {
        if (activeLanguage == language && tts != null) return
        release()
        val paths = voiceModelManager.ttsModelPaths(language)
        val tokensFile = File(paths.modelDir, "tokens.txt")
        if (!tokensFile.isFile) {
            throw IllegalStateException("TTS tokens.txt missing for ${language.displayName}")
        }
        val config = getOfflineTtsConfig(
            modelDir = paths.modelDir,
            modelName = paths.modelName,
            acousticModelName = "",
            vocoder = "",
            voices = "",
            lexicon = "",
            dataDir = paths.dataDir,
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
        )
        tts = try {
            OfflineTts(assetManager = null, config = config)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Failed to load TTS model for ${language.displayName}: ${error.message}",
                error,
            )
        }
        activeLanguage = language
    }

    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        val plain = TextSpeechUtils.prepareForSpeech(text)
        if (plain.isBlank()) {
            throw IllegalStateException("No speakable text after removing markdown")
        }
        cancelled = false
        val engine = tts ?: error("TTS engine not prepared")
        val chunks = TextSpeechUtils.chunkForSpeech(plain)
        for (chunk in chunks) {
            if (cancelled) return@withContext
            // Slightly faster delivery reads more like an assistant.
            val audio = engine.generate(text = chunk, sid = 0, speed = 1.1f)
            if (audio.samples.isEmpty()) {
                Log.w(TAG, "TTS returned empty audio for chunk: ${chunk.take(40)}")
                continue
            }
            playSamples(audio.samples, audio.sampleRate)
        }
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty() || cancelled) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusGranted = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4)
        val bufferSize = maxOf(samples.size * 4, minBuffer)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track

        try {
            track.play()
            var offset = 0
            while (offset < samples.size && !cancelled) {
                val written = track.write(
                    samples,
                    offset,
                    samples.size - offset,
                    AudioTrack.WRITE_BLOCKING,
                )
                if (written <= 0) break
                offset += written
            }
            if (!cancelled && offset > 0) {
                val durationMs = (offset * 1000L / sampleRate) + 150L
                delay(durationMs)
            }
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            audioTrack = null
            if (focusGranted) {
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    fun stop() {
        cancelled = true
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        audioTrack = null
    }

    fun release() {
        stop()
        tts?.release()
        tts = null
        activeLanguage = null
    }

    companion object {
        private const val TAG = "TextToSpeechEngine"
    }
}
