package com.example.ai_agent.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.llama.gguf.GgufMetadata
import com.example.llama.gguf.GgufMetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ModelImportPhase {
    data object Parsing : ModelImportPhase()
    data object Copying : ModelImportPhase()
    data object Loading : ModelImportPhase()
    data class Copied(val metadata: GgufMetadata, val modelFile: File) : ModelImportPhase()
    data class Failed(val message: String) : ModelImportPhase()
}

data class ImportedModel(
    val metadata: GgufMetadata,
    val modelFile: File,
)

@Singleton
class ModelImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val metadataReader = GgufMetadataReader.create()

    suspend fun importFromUri(uri: Uri): ImportedModel = withContext(Dispatchers.IO) {
        val sourceSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                -1L
            }
        } ?: -1L

        val metadata = context.contentResolver.openInputStream(uri)?.use { input ->
            metadataReader.readStructuredMetadata(input)
        } ?: throw IllegalStateException("Could not open selected file")

        val modelsDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val modelsRoot = modelsDir.canonicalPath + File.separator

        // GGUF metadata fields (general.basename, general.size_label, general.architecture, …)
        // are attacker-controlled. Sanitize aggressively and re-validate the canonical path
        // stays inside modelsDir before we ever open a stream on it.
        val safeBase = sanitizeFileName(metadata.suggestedFilename())
        val modelName = "$safeBase.gguf"
        val modelFile = File(modelsDir, modelName)
        val tempFile = File(modelsDir, "$modelName.part")
        check(modelFile.canonicalPath.startsWith(modelsRoot)) {
            "Refusing to write model outside app storage"
        }
        check(tempFile.canonicalPath.startsWith(modelsRoot)) {
            "Refusing to write partial file outside app storage"
        }

        val needsCopy = !modelFile.exists() ||
            modelFile.length() == 0L ||
            (sourceSize > 0L && modelFile.length() != sourceSize)

        if (needsCopy) {
            if (modelFile.exists()) modelFile.delete()
            if (tempFile.exists()) tempFile.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not copy model into app storage")

            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }

            if (sourceSize > 0L && modelFile.length() != sourceSize) {
                modelFile.delete()
                throw IllegalStateException(
                    "Downloaded file looks incomplete (${modelFile.length()} / $sourceSize bytes). Re-download the GGUF and try again."
                )
            }
        }

        val sizeMb = modelFile.length() / (1024 * 1024)
        if (sizeMb < 100) {
            modelFile.delete()
            throw IllegalStateException(
                "Model file is only ${sizeMb}MB — likely incomplete. Re-download the full GGUF and try again."
            )
        }

        ImportedModel(metadata, modelFile)
    }

    companion object {
        const val MODELS_DIR = "models"
        private const val MAX_NAME_LENGTH = 120

        internal fun sanitizeFileName(raw: String): String {
            val stripped = raw
                .filter { it.isLetterOrDigit() || it in "-_." }
                .replace(Regex("\\.+"), ".")
                .trim('.', '-', '_')
                .take(MAX_NAME_LENGTH)
            return stripped.ifBlank { "model-${System.currentTimeMillis()}" }
        }
    }
}

fun GgufMetadata.suggestedFilename(): String = when {
    basic.name != null -> {
        basic.sizeLabel?.let { size -> "${basic.name}-$size" } ?: basic.name!!
    }
    architecture?.architecture != null -> {
        val arch = architecture?.architecture
        basic.uuid?.let { uuid -> "$arch-$uuid" } ?: "$arch-${System.currentTimeMillis()}"
    }
    else -> "model-${System.currentTimeMillis()}"
}

fun GgufMetadata.summary(): String = buildString {
    appendLine("GGUF ${version.label}")
    basic.name?.let { appendLine("Model: $it") }
    basic.sizeLabel?.let { appendLine("Size: $it") }
    architecture?.architecture?.let { appendLine("Architecture: $it") }
    architecture?.fileType?.let { code ->
        appendLine("Quantization: ${com.example.llama.gguf.FileType.fromCode(code).label}")
    }
    dimensions?.contextLength?.let { appendLine("Context: $it tokens") }
    author?.organization?.let { appendLine("Organization: $it") }
    additional?.description?.let { appendLine(it) }
}.trim()
