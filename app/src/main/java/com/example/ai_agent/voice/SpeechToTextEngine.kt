package com.example.ai_agent.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechToTextEngine @Inject constructor(
    private val voiceModelManager: VoiceModelManager,
) {
    private var recognizer: OfflineRecognizer? = null
    private var activeLanguage: VoiceLanguage? = null

    fun prepare(language: VoiceLanguage) {
        if (activeLanguage == language && recognizer != null) return
        release()
        val paths = voiceModelManager.sttModelPath(language)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = paths.model),
                tokens = paths.tokens,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        recognizer = try {
            OfflineRecognizer(assetManager = null, config = config)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Failed to load STT model for ${language.displayName}: ${error.message}",
                error,
            )
        }
        activeLanguage = language
    }

    fun transcribe(samples: FloatArray, sampleRate: Int = 16_000): String {
        val engine = recognizer ?: error("STT engine not prepared")
        val stream = engine.createStream()
        stream.acceptWaveform(samples, sampleRate)
        engine.decode(stream)
        val result = engine.getResult(stream)
        stream.release()
        return result.text.trim()
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        activeLanguage = null
    }
}
