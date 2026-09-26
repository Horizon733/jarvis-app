package com.example.llama

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext context: Context
) {

    private var nativePtr: Long = 0

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /** `nativeInit` is a one-shot per-process JNI setup call. Guard so multiple `load()`
     * invocations don't repeat it (each repeat leaked/re-initialised global native state). */
    private val nativeInitialised = AtomicBoolean(false)

    /** Set true whenever a generation is running; consulted by `awaitClose` so idempotent
     * flow completions don't call `nativeCancel` on an already-finished session (undefined
     * behaviour in some backends). */
    @Volatile private var generationActive = false

    companion object {
        private const val DEFAULT_CONTEXT_SIZE = 4096

        init {
            System.loadLibrary("llama_bridge")
        }
    }

    private val nativeLibraryDir = context.applicationInfo.nativeLibraryDir

    suspend fun load(modelPath: String, ctxSize: Int = DEFAULT_CONTEXT_SIZE, threads: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = java.io.File(modelPath)
            require(modelFile.exists()) { "Model file not found: $modelPath" }
            require(modelFile.isFile) { "Model path is not a file: $modelPath" }
            require(modelFile.canRead()) { "Cannot read model file: $modelPath" }
            require(modelFile.length() > 0L) { "Model file is empty: $modelPath" }

            if (nativeInitialised.compareAndSet(false, true)) {
                nativeInit(nativeLibraryDir)
            }
            nativePtr = nativeLoad(modelPath, ctxSize, threads)
            if (nativePtr != 0L && nativePtr != -1L && nativePtr != -2L) {
                _isLoaded.value = true
                loadedContextSize = nativeGetLoadedContextSize().coerceAtLeast(1)
                lastGenerationError = null
                lastGenerationWarning = null
                Result.success(Unit)
            } else {
                val detail = nativeGetLastError()
                val message = when (nativePtr) {
                    -1L -> detail ?: "Unsupported or corrupt GGUF file"
                    -2L -> detail ?: "Out of memory while creating model context"
                    else -> detail ?: "Failed to load model"
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generate(prompt: String, params: SamplingParams): Flow<String> =
        generateChat(arrayOf("user"), arrayOf(prompt), params)

    fun generateChat(
        roles: Array<String>,
        contents: Array<String>,
        params: SamplingParams,
    ): Flow<String> = callbackFlow {
        require(roles.isNotEmpty() && roles.size == contents.size) {
            "Conversation roles and contents must be non-empty and have equal lengths"
        }
        val callback = object : LlamaCallback {
            override fun onToken(token: String) {
                trySend(token)
            }

            override fun onFinished(error: String?) {
                if (!error.isNullOrBlank()) {
                    lastGenerationError = error
                    lastGenerationWarning = null
                } else {
                    lastGenerationWarning = nativeGetLastError()?.takeIf { it.isNotBlank() }
                }
            }
        }

        generationActive = true
        val generation = launch(Dispatchers.IO) {
            try {
                nativeGenerateChat(
                    roles,
                    contents,
                    params.temp,
                    params.topK,
                    params.topP,
                    params.repeatPenalty,
                    params.nPredict,
                    callback,
                )
            } finally {
                generationActive = false
                close()
            }
        }

        awaitClose {
            // Only signal the native layer if there's actually a live session — spurious
            // cancels on an already-finished session are undefined behaviour on some backends.
            if (generationActive) {
                nativeCancel()
            }
            generation.cancel()
        }
    }

    suspend fun generateChatResult(
        roles: Array<String>,
        contents: Array<String>,
        params: SamplingParams,
    ): GenerationResult = withContext(Dispatchers.IO) {
        lastGenerationError = null
        lastGenerationWarning = null
        val text = StringBuilder()
        val callback = object : LlamaCallback {
            override fun onToken(token: String) {
                text.append(token)
            }

            override fun onFinished(error: String?) {
                if (!error.isNullOrBlank()) {
                    lastGenerationError = error
                    lastGenerationWarning = null
                } else {
                    lastGenerationWarning = nativeGetLastError()?.takeIf { it.isNotBlank() }
                }
            }
        }
        generationActive = true
        try {
            nativeGenerateChat(
                roles,
                contents,
                params.temp,
                params.topK,
                params.topP,
                params.repeatPenalty,
                params.nPredict,
                callback,
            )
        } finally {
            generationActive = false
        }
        val output = text.toString()
        val error = lastGenerationError ?: nativeGetLastError()
        GenerationResult(
            text = output,
            error = if (output.isBlank()) error else null,
            warning = if (output.isNotBlank()) {
                lastGenerationWarning ?: nativeGetLastError()?.takeIf { it.isNotBlank() }
            } else {
                null
            },
        )
    }

    @Volatile
    var lastGenerationError: String? = null

    @Volatile
    var lastGenerationWarning: String? = null

    @Volatile
    var loadedContextSize: Int = DEFAULT_CONTEXT_SIZE

    interface LlamaCallback {
        fun onToken(token: String)
        fun onFinished(error: String?) {}
    }

    fun cancel() {
        if (generationActive) {
            nativeCancel()
        }
    }

    fun unload() {
        if (_isLoaded.value) {
            nativeUnload()
            _isLoaded.value = false
            nativePtr = 0
            // Reset transient state so a subsequent load starts clean.
            loadedContextSize = DEFAULT_CONTEXT_SIZE
            lastGenerationError = null
            lastGenerationWarning = null
            generationActive = false
        }
    }

    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeGetLoadedContextSize(): Int
    private external fun nativeGetLastError(): String?
    private external fun nativeInit(nativeLibraryDir: String)
    private external fun nativeUnload()
    private external fun nativeCancel()
    private external fun nativeGenerateChat(
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        nPredict: Int,
        callback: LlamaCallback
    )
}
