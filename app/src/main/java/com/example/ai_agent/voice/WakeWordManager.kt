package com.example.ai_agent.voice

import android.content.Context
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeWordManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: WakeWordEngine? = null

    private val _detections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    private var isRunning = false

    fun start() {
        if (isRunning) return
        require(hasRequiredAssets()) {
            "Wake word models missing. Run scripts/download_wakeword_assets.ps1 and rebuild."
        }
        val models = listOf(
            WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.5f,
            )
        )
        val wakeEngine = WakeWordEngine(
            context = context,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = 2_000L,
            scope = scope,
        )
        scope.launch {
            wakeEngine.detections.collect {
                _detections.emit(Unit)
            }
        }
        wakeEngine.start()
        engine = wakeEngine
        isRunning = true
    }

    fun stop() {
        engine?.stop()
        isRunning = false
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
    }

    fun hasRequiredAssets(): Boolean =
        assetExists("melspectrogram.onnx") &&
            assetExists("embedding_model.onnx") &&
            assetExists("hey_jarvis_v0.1.onnx")

    private fun assetExists(name: String): Boolean =
        try {
            context.assets.open(name).use { }
            true
        } catch (_: Exception) {
            false
        }
}
