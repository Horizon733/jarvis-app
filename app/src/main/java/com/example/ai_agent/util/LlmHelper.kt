package com.example.ai_agent.util

import com.example.ai_agent.data.local.MessageEntity
import com.example.ai_agent.data.local.PreferencesManager
import com.example.ai_agent.domain.model.LlmState
import com.example.llama.LlamaEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LlmHelper @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val preferencesManager: PreferencesManager,
) {
    private val _state = MutableStateFlow<LlmState>(LlmState.ModelMissing)
    val state: StateFlow<LlmState> = _state.asStateFlow()

    // Buffered SharedFlow: with the default (no buffer, no replay), `emit` from the singleton
    // scope suspends whenever there is momentarily no collector — e.g. during a Compose
    // configuration change — and the whole generation stalls holding `modelMutex`. Drop the
    // oldest token if the UI cannot keep up rather than deadlocking the engine.
    private val _partialResults = MutableSharedFlow<Pair<String, Boolean>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val partialResults: SharedFlow<Pair<String, Boolean>> = _partialResults.asSharedFlow()

    private val _lastGenerationDiagnostic = MutableStateFlow<String?>(null)
    val lastGenerationDiagnostic = _lastGenerationDiagnostic.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val modelMutex = Mutex()

    private var loadedContextSize = DEFAULT_CONTEXT_SIZE

    /** Suppress the next `done=true` broadcast because `stopGeneration` already committed a
     * user-visible "(stopped)" marker. Prevents the collector in `ChatViewModel` from writing
     * an empty follow-up message. */
    @Volatile
    var suppressNextDone: Boolean = false

    suspend fun loadModel(modelPath: String): Result<Unit> = modelMutex.withLock {
        _state.value = LlmState.Loading
        val result = llamaEngine.load(modelPath, ctxSize = DEFAULT_CONTEXT_SIZE)
        if (result.isSuccess) {
            loadedContextSize = llamaEngine.loadedContextSize
            _state.value = LlmState.Ready
            Result.success(Unit)
        } else {
            val message = result.exceptionOrNull()?.message ?: "Unknown error"
            _state.value = LlmState.Error(message)
            Result.failure(Exception(message))
        }
    }

    fun generateResponse(messages: List<MessageEntity>) {
        scope.launch {
            modelMutex.withLock {
                _lastGenerationDiagnostic.value = null
                if (_state.value !is LlmState.Ready) {
                    emitGenerationFailure("Model is not loaded.")
                    return@withLock
                }
                if (messages.isEmpty()) {
                    emitGenerationFailure("No messages to send.")
                    return@withLock
                }

                val params = preferencesManager.inferenceSettings.first().toSamplingParams()
                val (roles, contents) = JarvisPersona.buildPrompt(messages)

                try {
                    var tokenCount = 0
                    llamaEngine.generateChat(roles, contents, params).collect { token ->
                        if (token.isNotEmpty()) tokenCount++
                        _partialResults.emit(token to false)
                    }
                    val error = llamaEngine.lastGenerationError
                    if (error != null) {
                        emitGenerationFailure(error)
                    } else if (tokenCount == 0) {
                        emitGenerationFailure("Unknown inference failure.")
                    } else {
                        _lastGenerationDiagnostic.value = llamaEngine.lastGenerationWarning
                        _partialResults.emit("" to true)
                    }
                } catch (error: Exception) {
                    emitGenerationFailure(error.message ?: "Generation failed")
                }
            }
        }
    }

    fun stopGeneration() {
        // native cancel is an atomic signal — no coroutine coordination needed; the collector
        // in generateResponse observes flow completion via the JNI onFinished callback.
        suppressNextDone = true
        llamaEngine.cancel()
    }

    suspend fun close() {
        modelMutex.withLock {
            llamaEngine.unload()
            _state.value = LlmState.Idle
        }
    }

    private suspend fun emitGenerationFailure(detail: String) {
        _lastGenerationDiagnostic.value = detail
        _partialResults.emit(
            GenerationDiagnostics.emptyResponseMessage(detail, loadedContextSize) to false,
        )
        _partialResults.emit("" to true)
    }

    companion object {
        private const val DEFAULT_CONTEXT_SIZE = 4096
    }
}
