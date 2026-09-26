package com.example.ai_agent.voice

/** Loads ONNX Runtime before sherpa-onnx JNI to avoid native link errors. */
object VoiceNativeLibs {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("onnxruntime")
            System.loadLibrary("sherpa-onnx-jni")
            loaded = true
        }
    }
}
