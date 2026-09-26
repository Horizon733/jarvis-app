package com.example.ai_agent.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_agent.data.local.PreferencesManager
import com.example.ai_agent.data.repository.ChatRepository
import com.example.ai_agent.domain.model.LlmState
import com.example.ai_agent.ui.common.UserMessage
import com.example.ai_agent.util.LlmHelper
import com.example.ai_agent.util.ModelImportPhase
import com.example.ai_agent.util.ModelManager
import com.example.ai_agent.util.summary
import com.example.ai_agent.voice.VoiceCoordinator
import com.example.ai_agent.voice.VoiceLanguage
import com.example.ai_agent.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val llmHelper: LlmHelper,
    private val modelManager: ModelManager,
    private val voiceCoordinator: VoiceCoordinator,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    val chats = repository.getAllChats().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val messages = _currentChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getMessagesForChat(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val llmState = llmHelper.state

    val activeModelName = repository.getAllModels()
        .map { models -> models.firstOrNull { it.isActive }?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _userMessage = MutableStateFlow<UserMessage?>(null)
    val userMessage = _userMessage.asStateFlow()

    private val _currentStreamingResponse = MutableStateFlow("")
    val currentStreamingResponse = _currentStreamingResponse.asStateFlow()

    private val _modelMetadata = MutableStateFlow<String?>(null)
    val modelMetadata = _modelMetadata.asStateFlow()

    private val _importPhase = MutableStateFlow<ModelImportPhase?>(null)
    val importPhase = _importPhase.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    val voiceState = voiceCoordinator.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle)

    val voiceLanguage = voiceCoordinator.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceLanguage.ENGLISH)

    val wakeWordEnabled = preferencesManager.wakeWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val ttsEnabled = preferencesManager.ttsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val onboardingComplete = preferencesManager.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceDownloadConsented = preferencesManager.voiceDownloadConsented
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val jarvisHudEnabled = preferencesManager.jarvisHudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var micPermissionGranted = false
    private var voiceDownloadConsentedLocal = false

    init {
        voiceCoordinator.onTranscription = { text -> sendMessage(text) }
        voiceCoordinator.onWakeWordUnavailable = {
            viewModelScope.launch { preferencesManager.setWakeWordEnabled(false) }
        }

        viewModelScope.launch {
            preferencesManager.voiceLanguage.collect { voiceCoordinator.setLanguage(it) }
        }
        viewModelScope.launch {
            preferencesManager.ttsEnabled.collect { voiceCoordinator.setTtsEnabled(it) }
        }
        viewModelScope.launch {
            preferencesManager.wakeWordEnabled.collect { enabled ->
                if (enabled && !micPermissionGranted) {
                    preferencesManager.setWakeWordEnabled(false)
                } else {
                    voiceCoordinator.setWakeWordEnabled(enabled)
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.voiceDownloadConsented.collect { consented ->
                voiceDownloadConsentedLocal = consented
                if (consented && llmHelper.state.value is LlmState.Ready) {
                    prefetchVoiceModels()
                }
            }
        }
        viewModelScope.launch {
            llmHelper.state.collect { state ->
                when (state) {
                    is LlmState.Ready -> {
                        if (_importPhase.value is ModelImportPhase.Failed ||
                            _importPhase.value is ModelImportPhase.Loading
                        ) {
                            _importPhase.value = null
                        }
                        if (voiceDownloadConsentedLocal) {
                            prefetchVoiceModels()
                        }
                    }
                    is LlmState.Error -> {
                        _userMessage.value = UserMessage(state.message, "Retry") {
                            viewModelScope.launch { restoreActiveModel() }
                        }
                        if (_isGenerating.value) {
                            _isGenerating.value = false
                            voiceCoordinator.finishLlmResponse("")
                        }
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            llmHelper.partialResults.collect { (text, done) ->
                if (!done) {
                    _currentStreamingResponse.value += text
                    return@collect
                }

                // If stopGeneration already saved a "(stopped)" marker to the DB, swallow the
                // native done-signal so we don't write an empty follow-up bubble.
                if (llmHelper.suppressNextDone) {
                    llmHelper.suppressNextDone = false
                    _currentStreamingResponse.value = ""
                    _isGenerating.value = false
                    return@collect
                }

                val chatId = _currentChatId.value
                val fullResponse = _currentStreamingResponse.value.trim()
                _currentStreamingResponse.value = ""
                _isGenerating.value = false

                if (chatId == null) return@collect

                val isGenerationFailure = fullResponse.contains("**Generation failed")

                if (fullResponse.isNotBlank()) {
                    repository.saveMessage(chatId, fullResponse, false)
                }

                if (isGenerationFailure) {
                    voiceCoordinator.finishLlmResponse("")
                    return@collect
                }

                llmHelper.lastGenerationDiagnostic.value?.let { warning ->
                    _userMessage.value = UserMessage(warning)
                }

                voiceCoordinator.finishLlmResponse(fullResponse)
            }
        }
        viewModelScope.launch {
            voiceCoordinator.state.collect { state ->
                if (state is VoiceState.Error) {
                    _userMessage.value = UserMessage(state.message)
                }
            }
        }
        viewModelScope.launch {
            restoreActiveModel()
        }
    }

    fun updateMicPermission(granted: Boolean) {
        micPermissionGranted = granted
        voiceCoordinator.setMicPermissionGranted(granted)
    }

    fun completeOnboarding(consentVoiceDownload: Boolean) {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete()
            if (consentVoiceDownload) {
                preferencesManager.setVoiceDownloadConsented()
            }
        }
    }

    fun consentVoiceDownload() {
        viewModelScope.launch {
            preferencesManager.setVoiceDownloadConsented()
        }
    }

    private suspend fun restoreActiveModel() {
        _importPhase.value = ModelImportPhase.Loading
        val result = modelManager.loadActiveModel()
        result.onSuccess { active ->
            if (active != null) {
                _modelMetadata.value = modelManager.metadataSummaryForPath(active.path)
                _importPhase.value = null
            } else {
                _importPhase.value = null
            }
        }.onFailure { error ->
            _importPhase.value = ModelImportPhase.Failed(
                error.message ?: "Failed to load saved model"
            )
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importPhase.value = null
            try {
                _importPhase.value = ModelImportPhase.Parsing
                _importPhase.value = ModelImportPhase.Copying
                val imported = modelManager.importFromUri(uri)
                val sizeMb = imported.modelFile.length() / (1024 * 1024)
                _modelMetadata.value = imported.metadata.summary() + "\nFile size: ${sizeMb} MB"
                _importPhase.value = ModelImportPhase.Loading
                val result = modelManager.loadModel(
                    imported.modelFile.absolutePath,
                    imported.modelFile.name,
                )
                if (result.isSuccess) {
                    _importPhase.value = ModelImportPhase.Copied(imported.metadata, imported.modelFile)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Failed to load model"
                    _importPhase.value = ModelImportPhase.Failed(message)
                    _userMessage.value = UserMessage(message)
                }
            } catch (error: Exception) {
                _importPhase.value = ModelImportPhase.Failed(
                    error.message ?: "Failed to import model"
                )
                _userMessage.value = UserMessage(error.message ?: "Failed to import model")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun selectChat(chatId: Long) {
        _currentChatId.value = chatId
    }

    fun startNewChat() {
        viewModelScope.launch {
            val id = repository.createChat(DEFAULT_CHAT_TITLE)
            _currentChatId.value = id
        }
    }

    fun renameChat(chatId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.renameChat(chatId, title.trim())
        }
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        if (llmHelper.state.value !is LlmState.Ready) {
            _userMessage.value = UserMessage("Load a model before sending messages.")
            return
        }
        voiceCoordinator.setLlmGenerating(true)
        viewModelScope.launch {
            val existing = _currentChatId.value
            val chatId: Long
            val justCreated: Boolean
            if (existing == null) {
                chatId = repository.createChat(DEFAULT_CHAT_TITLE)
                _currentChatId.value = chatId
                justCreated = true
            } else {
                chatId = existing
                justCreated = false
            }
            repository.saveMessage(chatId, trimmed, true)
            // Give brand-new chats a title derived from the first user message — much more
            // useful in the nav drawer than "New Conversation".
            if (justCreated) {
                repository.renameChat(chatId, deriveChatTitle(trimmed))
            }
            _isGenerating.value = true
            _currentStreamingResponse.value = ""
            val history = repository.getRecentMessages(chatId)
            llmHelper.generateResponse(history)
        }
    }

    private fun deriveChatTitle(firstMessage: String): String {
        val single = firstMessage
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        return if (single.length <= MAX_TITLE_LENGTH) {
            single.ifBlank { DEFAULT_CHAT_TITLE }
        } else {
            single.take(MAX_TITLE_LENGTH).trimEnd() + "…"
        }
    }

    fun stopGeneration() {
        // Snapshot BEFORE cancelling native so we don't race with the JNI onFinished callback
        // clearing `_currentStreamingResponse` via the collector.
        val current = _currentStreamingResponse.value
        // Tell LlmHelper to swallow the trailing done-signal — we commit the final message here.
        llmHelper.suppressNextDone = true
        llmHelper.stopGeneration()
        _isGenerating.value = false
        voiceCoordinator.setLlmGenerating(false)
        if (current.isNotBlank()) {
            viewModelScope.launch {
                val text = "$current\n\n*(stopped)*"
                _currentChatId.value?.let { repository.saveMessage(it, text, false) }
                _currentStreamingResponse.value = ""
                voiceCoordinator.finishLlmResponse("")
            }
        } else {
            _currentStreamingResponse.value = ""
            voiceCoordinator.finishLlmResponse("")
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
            if (_currentChatId.value == chatId) {
                _currentChatId.value = null
            }
        }
    }

    fun toggleVoiceRecording() {
        voiceCoordinator.toggleRecording()
    }

    fun stopSpeaking() {
        voiceCoordinator.stopSpeaking()
    }

    fun setVoiceLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            preferencesManager.setVoiceLanguage(VoiceLanguage.normalize(language))
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !micPermissionGranted) {
                _userMessage.value = UserMessage("Microphone permission is required for wake word.")
                return@launch
            }
            preferencesManager.setWakeWordEnabled(enabled)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTtsEnabled(enabled)
        }
    }

    fun onAppBackgrounded() {
        voiceCoordinator.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        voiceCoordinator.onAppForegrounded()
    }

    private fun prefetchVoiceModels() {
        viewModelScope.launch {
            try {
                voiceCoordinator.prefetchModels()
            } catch (error: Exception) {
                _userMessage.value = UserMessage(
                    error.message ?: "Voice download failed",
                    "Retry",
                ) { prefetchVoiceModels() }
            }
        }
    }

    companion object {
        private const val DEFAULT_CHAT_TITLE = "New Conversation"
        private const val MAX_TITLE_LENGTH = 40
    }
}
