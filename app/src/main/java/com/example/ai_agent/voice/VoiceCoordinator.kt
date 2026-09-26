package com.example.ai_agent.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCoordinator @Inject constructor(
    private val voiceModelManager: VoiceModelManager,
    private val speechToTextEngine: SpeechToTextEngine,
    private val textToSpeechEngine: TextToSpeechEngine,
    private val wakeWordManager: WakeWordManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioRecorder = AudioRecorder()

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _language = MutableStateFlow(VoiceLanguage.ENGLISH)
    val language: StateFlow<VoiceLanguage> = _language.asStateFlow()

    private var wakeWordEnabled = false
    private var ttsEnabled = true
    private var micPermissionGranted = false
    private var pausedForBackground = false
    private var llmGenerating = false
    private var speakJob: Job? = null
    private val prepareMutex = Mutex()

    var onTranscription: ((String) -> Unit)? = null
    var onWakeWordUnavailable: (() -> Unit)? = null

    init {
        scope.launch {
            wakeWordManager.detections.collect {
                if (wakeWordEnabled && _state.value == VoiceState.ListeningForWakeWord) {
                    startRecording(afterWakeWord = true)
                }
            }
        }
        scope.launch {
            voiceModelManager.downloadState.collect { downloadState ->
                when (downloadState) {
                    is VoiceState.Downloading,
                    is VoiceState.DownloadingModels,
                    is VoiceState.Installing -> _state.value = downloadState
                    is VoiceState.Error -> _state.value = downloadState
                    VoiceState.Idle -> {
                        if (_state.value is VoiceState.Downloading ||
                            _state.value is VoiceState.DownloadingModels ||
                            _state.value is VoiceState.Installing
                        ) {
                            _state.value = VoiceState.Idle
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        micPermissionGranted = granted
        if (!granted && wakeWordEnabled) {
            wakeWordManager.stop()
            if (_state.value == VoiceState.ListeningForWakeWord) {
                _state.value = VoiceState.Idle
            }
        } else if (granted && wakeWordEnabled && _state.value == VoiceState.Idle && !llmGenerating) {
            scope.launch { enableWakeWord() }
        }
    }

    fun setLlmGenerating(generating: Boolean) {
        llmGenerating = generating
        if (generating) {
            pauseWakeWordListening()
        }
    }

    fun finishLlmResponse(text: String, speakAloud: Boolean = true) {
        llmGenerating = false
        if (speakAloud && ttsEnabled && text.isNotBlank()) {
            speak(text)
        } else {
            scope.launch { resumeAfterVoiceAction() }
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        _language.value = VoiceLanguage.normalize(language)
        speechToTextEngine.release()
        textToSpeechEngine.release()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        wakeWordEnabled = enabled
        if (enabled) {
            if (!micPermissionGranted) {
                _state.value = VoiceState.Error("Microphone permission is required for wake word.")
                return
            }
            if (!llmGenerating) {
                scope.launch { enableWakeWord() }
            }
        } else {
            wakeWordManager.stop()
            if (_state.value == VoiceState.ListeningForWakeWord) {
                _state.value = VoiceState.Idle
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        ttsEnabled = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    suspend fun prefetchModels() = prepareEngines()

    fun onAppBackgrounded() {
        pausedForBackground = wakeWordEnabled
        wakeWordManager.stop()
        if (_state.value == VoiceState.Speaking) {
            stopSpeaking()
        }
        if (_state.value == VoiceState.Recording) {
            audioRecorder.stop()
            _state.value = VoiceState.Idle
        }
    }

    fun onAppForegrounded() {
        if (pausedForBackground && wakeWordEnabled && micPermissionGranted && !llmGenerating) {
            scope.launch { enableWakeWord() }
        }
        pausedForBackground = false
    }

    fun stopSpeaking() {
        val job = speakJob
        speakJob = null
        job?.cancel()
        textToSpeechEngine.stop()
        if (job == null) {
            scope.launch { resumeAfterVoiceAction() }
        }
    }

    fun toggleRecording() {
        scope.launch {
            when (_state.value) {
                VoiceState.Recording -> stopRecordingAndTranscribe()
                VoiceState.Speaking -> stopSpeaking()
                VoiceState.Idle, VoiceState.ListeningForWakeWord -> {
                    try {
                        prepareEngines()
                        startRecording(afterWakeWord = false)
                    } catch (error: Exception) {
                        _state.value = VoiceState.Error(
                            error.message ?: "Failed to prepare voice models"
                        )
                    }
                }
                is VoiceState.Downloading,
                VoiceState.DownloadingModels,
                is VoiceState.Installing -> Unit
                else -> Unit
            }
        }
    }

    fun speak(text: String) {
        if (!ttsEnabled || text.isBlank()) {
            scope.launch { resumeAfterVoiceAction() }
            return
        }
        speakJob?.cancel()
        speakJob = scope.launch {
            try {
                prepareEngines()
                _state.value = VoiceState.Speaking
                wakeWordManager.stop()
                textToSpeechEngine.speak(text)
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    _state.value = VoiceState.Error(error.message ?: "TTS failed")
                }
            } finally {
                speakJob = null
                resumeAfterVoiceAction()
            }
        }
    }

    private fun pauseWakeWordListening() {
        wakeWordManager.stop()
        if (_state.value == VoiceState.ListeningForWakeWord) {
            _state.value = VoiceState.Idle
        }
    }

    private suspend fun enableWakeWord() {
        if (llmGenerating) return
        if (!micPermissionGranted) {
            _state.value = VoiceState.Error("Microphone permission is required for wake word.")
            return
        }
        try {
            if (!wakeWordManager.hasRequiredAssets()) {
                onWakeWordUnavailable?.invoke()
                _state.value = VoiceState.Error(
                    "Wake word is unavailable in this build. Reinstall the app or contact support."
                )
                return
            }
            prepareEngines()
            wakeWordManager.start()
            _state.value = VoiceState.ListeningForWakeWord
        } catch (error: Exception) {
            _state.value = VoiceState.Error(error.message ?: "Wake word failed to start")
        }
    }

    private fun startRecording(afterWakeWord: Boolean) {
        if (_state.value == VoiceState.Recording) return
        wakeWordManager.stop()
        _state.value = VoiceState.Recording
        audioRecorder.start(scope)
        if (afterWakeWord) {
            scope.launch {
                delay(5_000)
                if (_state.value == VoiceState.Recording) {
                    stopRecordingAndTranscribe()
                }
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        if (_state.value != VoiceState.Recording) return
        scope.launch {
            try {
                _state.value = VoiceState.Transcribing
                // Suspend for the capture coroutine to fully exit before reading samples,
                // avoiding the ArrayList race that used to drop the tail of the recording.
                val samples = audioRecorder.stopAndDrain()
                prepareEngines()
                if (samples.isEmpty()) {
                    _state.value = VoiceState.Error("No audio captured")
                    resumeAfterVoiceAction()
                    return@launch
                }
                val text = withContext(Dispatchers.IO) {
                    speechToTextEngine.transcribe(samples)
                }
                if (text.isBlank()) {
                    _state.value = VoiceState.Error("Could not understand speech")
                } else {
                    onTranscription?.invoke(text)
                }
            } catch (error: Exception) {
                _state.value = VoiceState.Error(error.message ?: "Transcription failed")
            } finally {
                if (!llmGenerating) {
                    resumeAfterVoiceAction()
                }
            }
        }
    }

    private suspend fun prepareEngines() = prepareMutex.withLock {
        val language = _language.value
        if (!voiceModelManager.areVoiceModelsReady(language)) {
            withContext(Dispatchers.IO) {
                voiceModelManager.ensureModels(language)
            }
        }
        withContext(Dispatchers.IO) {
            speechToTextEngine.prepare(language)
            textToSpeechEngine.prepare(language)
        }
    }

    private suspend fun resumeAfterVoiceAction() {
        if (llmGenerating) {
            _state.value = VoiceState.Idle
            return
        }
        if (wakeWordEnabled && micPermissionGranted) {
            enableWakeWord()
        } else {
            _state.value = VoiceState.Idle
        }
    }

    fun release() {
        speakJob?.cancel()
        audioRecorder.release()
        wakeWordManager.release()
        speechToTextEngine.release()
        textToSpeechEngine.release()
    }
}
