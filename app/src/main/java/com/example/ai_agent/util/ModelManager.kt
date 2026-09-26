package com.example.ai_agent.util

import android.net.Uri
import com.example.ai_agent.data.local.ModelEntity
import com.example.ai_agent.data.repository.ChatRepository
import com.example.llama.gguf.GgufMetadataReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates model file management (import, activation, deletion) with the underlying native
 * engine. Serialization is delegated to [LlmHelper]'s internal mutex — layering another mutex
 * here just added confusion without adding correctness (the two mutexes could not co-lock).
 */
@Singleton
class ModelManager @Inject constructor(
    private val modelImporter: ModelImporter,
    private val repository: ChatRepository,
    private val llmHelper: LlmHelper,
) {
    private val metadataReader = GgufMetadataReader.create()

    suspend fun importFromUri(uri: Uri): ImportedModel = modelImporter.importFromUri(uri)

    suspend fun loadModel(path: String, name: String): Result<Unit> {
        val result = llmHelper.loadModel(path)
        if (result.isSuccess) {
            repository.addModel(ModelEntity(path = path, name = name))
        }
        return result
    }

    suspend fun loadActiveModel(): Result<ModelEntity?> {
        val active = repository.getActiveModel() ?: return Result.success(null)
        return llmHelper.loadModel(active.path).map { active }
    }

    suspend fun selectModel(model: ModelEntity): Result<Unit> {
        val result = llmHelper.loadModel(model.path)
        if (result.isSuccess) {
            repository.addModel(model)
        }
        return result
    }

    suspend fun metadataSummaryForPath(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        runCatching {
            file.inputStream().use { input ->
                val metadata = metadataReader.readStructuredMetadata(input)
                val sizeMb = file.length() / (1024 * 1024)
                metadata.summary() + "\nFile size: ${sizeMb} MB"
            }
        }.getOrNull()
    }

    suspend fun deleteModel(model: ModelEntity) {
        val wasActive = repository.getActiveModel()?.path == model.path
        repository.deleteModel(model)
        File(model.path).delete()
        File(model.path + ".part").delete()
        if (wasActive) {
            llmHelper.close()
        }
    }
}
