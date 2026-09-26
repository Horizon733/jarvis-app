package com.example.ai_agent.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_agent.data.local.HuggingFaceTokenStore
import com.example.ai_agent.data.local.ModelEntity
import com.example.ai_agent.data.local.PreferencesManager
import com.example.ai_agent.data.repository.ChatRepository
import com.example.ai_agent.util.DownloadStatus
import com.example.ai_agent.util.InferenceSettings
import com.example.ai_agent.util.CuratedModel
import com.example.ai_agent.util.ModelDownloadScheduler
import com.example.ai_agent.util.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
    private val modelDownloadScheduler: ModelDownloadScheduler,
) : ViewModel() {

    val models = repository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeModel = repository.getAllModels()
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inferenceSettings = preferencesManager.inferenceSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InferenceSettings())

    val hfToken = huggingFaceTokenStore.token

    val jarvisHudEnabled = preferencesManager.jarvisHudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _downloadStatus = MutableStateFlow<DownloadStatus?>(null)
    val downloadStatus = _downloadStatus.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private var handledDownloadPath: String? = null

    init {
        viewModelScope.launch {
            modelDownloadScheduler.observeDownload().collect { status ->
                _downloadStatus.value = status
                if (status is DownloadStatus.Success && status.path != handledDownloadPath) {
                    handledDownloadPath = status.path
                    val fileName = File(status.path).name
                    selectModel(ModelEntity(path = status.path, name = fileName))
                }
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val imported = modelManager.importFromUri(uri)
                val result = modelManager.loadModel(imported.modelFile.absolutePath, imported.modelFile.name)
                if (result.isFailure) {
                    _downloadStatus.value = DownloadStatus.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load model"
                    )
                }
            } catch (error: Exception) {
                _downloadStatus.value = DownloadStatus.Error(error.message ?: "Import failed")
            } finally {
                _importing.value = false
            }
        }
    }

    fun selectModel(model: ModelEntity) {
        viewModelScope.launch {
            val result = modelManager.selectModel(model)
            if (result.isFailure) {
                _downloadStatus.value = DownloadStatus.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load model"
                )
            }
        }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch {
            modelManager.deleteModel(model)
        }
    }

    fun startDownload(url: String, fileName: String) {
        modelDownloadScheduler.enqueue(url, fileName)
    }

    fun startDownload(model: CuratedModel) {
        modelDownloadScheduler.enqueue(model)
    }

    fun cancelDownload() {
        modelDownloadScheduler.cancel()
    }

    fun setJarvisHudEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setJarvisHudEnabled(enabled)
        }
    }

    fun updateInferenceSettings(settings: InferenceSettings) {
        viewModelScope.launch {
            preferencesManager.setInferenceSettings(settings)
        }
    }

    fun saveHfToken(token: String) {
        viewModelScope.launch {
            if (token.isBlank()) {
                huggingFaceTokenStore.clear()
            } else {
                huggingFaceTokenStore.save(token)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
