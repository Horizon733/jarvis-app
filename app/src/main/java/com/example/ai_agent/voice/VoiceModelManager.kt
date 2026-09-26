package com.example.ai_agent.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val voiceRoot = File(context.filesDir, "voice").apply { mkdirs() }
    private val downloadMutex = Mutex()

    private val _downloadState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val downloadState: StateFlow<VoiceState> = _downloadState.asStateFlow()

    fun isSttReady(language: VoiceLanguage): Boolean = when (language) {
        VoiceLanguage.HINDI -> {
            isValidFile(File(voiceRoot, "stt/tokens.txt"), MIN_TOKENS_BYTES) &&
                isValidFile(File(voiceRoot, "stt/hi/model.int8.onnx"), MIN_STT_BYTES)
        }
        VoiceLanguage.ENGLISH -> {
            isValidFile(File(voiceRoot, "stt/en/tokens.txt"), MIN_TOKENS_BYTES) &&
                isValidFile(File(voiceRoot, "stt/en/model.int8.onnx"), MIN_STT_BYTES)
        }
    }

    fun isTtsReady(language: VoiceLanguage): Boolean {
        val dir = ttsDir(language)
        val onnx = dir.listFiles()?.firstOrNull { file ->
            file.extension == "onnx" &&
                file.length() >= MIN_TTS_ONNX_BYTES &&
                matchesExpectedTtsOnnx(language, file.name)
        }
        val tokens = File(dir, "tokens.txt")
        val espeak = File(dir, "espeak-ng-data")
        return onnx != null &&
            isValidFile(tokens, MIN_TOKENS_BYTES) &&
            espeak.isDirectory &&
            espeak.list()?.isNotEmpty() == true
    }

    fun areVoiceModelsReady(language: VoiceLanguage): Boolean =
        isSttReady(language) && isTtsReady(language)

    fun sttModelPath(language: VoiceLanguage): SttModelPaths = when (language) {
        VoiceLanguage.HINDI -> SttModelPaths(
            model = File(voiceRoot, "stt/hi/model.int8.onnx").absolutePath,
            tokens = File(voiceRoot, "stt/tokens.txt").absolutePath,
        )
        VoiceLanguage.ENGLISH -> SttModelPaths(
            model = File(voiceRoot, "stt/en/model.int8.onnx").absolutePath,
            tokens = File(voiceRoot, "stt/en/tokens.txt").absolutePath,
        )
    }

    fun ttsModelPaths(language: VoiceLanguage): TtsModelPaths {
        val dir = ttsDir(language)
        val onnx = dir.listFiles()?.firstOrNull { it.extension == "onnx" && it.length() >= MIN_TTS_ONNX_BYTES }
            ?: error("TTS model not found for ${language.displayName}")
        return TtsModelPaths(
            modelDir = dir.absolutePath,
            modelName = onnx.name,
            dataDir = File(dir, "espeak-ng-data").absolutePath,
        )
    }

    suspend fun ensureModels(language: VoiceLanguage) = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            if (areVoiceModelsReady(language)) {
                _downloadState.value = VoiceState.Idle
                return@withContext
            }

            _downloadState.value = VoiceState.DownloadingModels
            try {
                if (!isSttReady(language)) {
                    downloadSttModels(language)
                }
                if (!isTtsReady(language)) {
                    downloadTtsModels(language)
                }

                if (!areVoiceModelsReady(language)) {
                    throw IllegalStateException(buildInstallError(language))
                }
                _downloadState.value = VoiceState.Idle
                Log.i(TAG, "Voice models ready for ${language.code}")
            } catch (error: Exception) {
                Log.e(TAG, "Voice model download failed", error)
                _downloadState.value = VoiceState.Error(
                    error.message ?: "Failed to download voice models"
                )
                throw error
            }
        }
    }

    private fun buildInstallError(language: VoiceLanguage): String {
        val stt = if (isSttReady(language)) "ok" else "missing"
        val tts = if (isTtsReady(language)) "ok" else "missing"
        return "Voice install incomplete (STT: $stt, TTS: $tts). " +
            "Clear app storage and retry on Wi‑Fi."
    }

    private fun downloadSttModels(language: VoiceLanguage) {
        when (language) {
            VoiceLanguage.HINDI -> {
                downloadFile(
                    "$HF_BASE/tokens.txt",
                    File(voiceRoot, "stt/tokens.txt"),
                    "Hindi STT vocabulary",
                    MIN_TOKENS_BYTES,
                )
                downloadFile(
                    "$HF_BASE/hi/model.int8.onnx",
                    File(voiceRoot, "stt/hi/model.int8.onnx"),
                    "Hindi STT model",
                    MIN_STT_BYTES,
                )
            }
            VoiceLanguage.ENGLISH -> {
                downloadFile(
                    "$HF_BASE/en/tokens.txt",
                    File(voiceRoot, "stt/en/tokens.txt"),
                    "English STT vocabulary",
                    MIN_TOKENS_BYTES,
                )
                downloadFile(
                    "$HF_BASE/en/model.int8.onnx",
                    File(voiceRoot, "stt/en/model.int8.onnx"),
                    "English STT model",
                    MIN_STT_BYTES,
                )
            }
        }
    }

    private fun downloadTtsModels(language: VoiceLanguage) {
        ensureSharedEspeakData()
        val targetDir = ttsDir(language)
        targetDir.mkdirs()

        if (isTtsReady(language)) return

        if (language == VoiceLanguage.ENGLISH) {
            removeStaleEnglishTts(targetDir)
        }

        val archiveName = if (language == VoiceLanguage.HINDI) {
            "vits-piper-hi_IN-priyamvada-medium.tar.bz2"
        } else {
            TTS_EN_ARCHIVE
        }
        val archive = File(voiceRoot, archiveName)
        val url = if (language == VoiceLanguage.HINDI) TTS_HI_URL else TTS_EN_URL
        downloadFile(url, archive, "${language.displayName} TTS model", MIN_ARCHIVE_BYTES)
        _downloadState.value = VoiceState.Installing("${language.displayName} TTS model")
        extractPiperArchive(archive, targetDir)
        archive.delete()

        linkEspeakData(targetDir)

        if (!isTtsReady(language)) {
            throw IllegalStateException(
                "Failed to install ${language.displayName} TTS into ${targetDir.absolutePath}"
            )
        }
    }

    private fun ensureSharedEspeakData() {
        val espeakDir = File(voiceRoot, "tts/espeak-ng-data")
        if (espeakDir.isDirectory && espeakDir.list()?.isNotEmpty() == true) return

        val archive = File(voiceRoot, "espeak-ng-data.tar.bz2")
        downloadFile(TTS_ESPEAK_URL, archive, "Speech phoneme data", MIN_ARCHIVE_BYTES)
        val tempDir = File(voiceRoot, "tmp-espeak").apply { deleteRecursively(); mkdirs() }
        _downloadState.value = VoiceState.Installing("Speech phoneme data")
        extractTarBz2(archive, tempDir)
        archive.delete()

        val extracted = findDirectoryNamed(tempDir, "espeak-ng-data")
            ?: error("espeak-ng-data not found in downloaded archive")
        extracted.copyRecursively(espeakDir, overwrite = true)
        tempDir.deleteRecursively()
    }

    private fun extractPiperArchive(archive: File, targetDir: File) {
        val tempDir = File(voiceRoot, "tmp-piper-${targetDir.name}").apply {
            deleteRecursively()
            mkdirs()
        }
        extractTarBz2(archive, tempDir)

        val onnxFile = tempDir.walkTopDown()
            .firstOrNull { it.isFile && it.extension == "onnx" && it.length() >= MIN_TTS_ONNX_BYTES }
            ?: error("No ONNX model found in ${archive.name}")

        val piperRoot = onnxFile.parentFile ?: tempDir
        piperRoot.listFiles()?.forEach { file ->
            file.copyRecursively(File(targetDir, file.name), overwrite = true)
        }
        tempDir.deleteRecursively()
        Log.i(TAG, "Installed TTS to ${targetDir.absolutePath}: ${targetDir.list()?.joinToString()}")
    }

    private fun linkEspeakData(modelDir: File) {
        val espeakSource = File(voiceRoot, "tts/espeak-ng-data")
        val espeakTarget = File(modelDir, "espeak-ng-data")
        if (!espeakTarget.exists() && espeakSource.exists()) {
            espeakSource.copyRecursively(espeakTarget, overwrite = true)
        }
    }

    private fun ttsDir(language: VoiceLanguage): File =
        File(voiceRoot, if (language == VoiceLanguage.HINDI) "tts/hi" else "tts/en")

    private fun matchesExpectedTtsOnnx(language: VoiceLanguage, fileName: String): Boolean =
        when (language) {
            VoiceLanguage.ENGLISH -> fileName.contains(TTS_EN_VOICE_ID)
            VoiceLanguage.HINDI -> fileName.contains("priyamvada")
        }

    private fun removeStaleEnglishTts(targetDir: File) {
        if (!targetDir.isDirectory) return
        val hasExpected = targetDir.listFiles()?.any { file ->
            file.extension == "onnx" && matchesExpectedTtsOnnx(VoiceLanguage.ENGLISH, file.name)
        } == true
        if (!hasExpected) {
            targetDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private fun downloadFile(
        url: String,
        destination: File,
        label: String,
        minBytes: Long,
    ) {
        destination.parentFile?.mkdirs()
        if (isValidFile(destination, minBytes)) {
            Log.i(TAG, "Skipping existing ${destination.name} (${destination.length()} bytes)")
            return
        }

        val partial = File(destination.parentFile, "${destination.name}.partial")
        for (attempt in 0..1) {
            val resumedBytes = if (attempt == 0 && partial.isFile) partial.length() else 0L
            if (resumedBytes == 0L) {
                destination.delete()
                partial.delete()
            }

            Log.i(TAG, "Downloading $label from $url (resume=$resumedBytes)")
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "ai-agent-android/1.0")
                .header("Accept", "*/*")
            if (resumedBytes > 0) {
                requestBuilder.header("Range", "bytes=$resumedBytes-")
            }

            var retryFresh = false
            client.newCall(requestBuilder.build()).execute().use { response ->
                val isResume = resumedBytes > 0 && response.code == 206
                val isFresh = resumedBytes == 0L && response.isSuccessful
                if (!isResume && !isFresh) {
                    if (resumedBytes > 0) {
                        partial.delete()
                        retryFresh = true
                        return@use
                    }
                    error("Download failed for $label: HTTP ${response.code}")
                }

                val body = requireNotNull(response.body) { "Empty response for $label" }
                FileOutputStream(partial, isResume).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = if (isResume) resumedBytes else 0L
                        val chunkTotal = body.contentLength()
                        val total = if (chunkTotal > 0) downloaded + chunkTotal else chunkTotal
                        var lastReportedBytes = downloaded
                        var lastReportedAt = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            // Throttle: previously wrote ~60k StateFlow values for a 500 MB
                            // download, causing needless recompositions all over voice UI.
                            val now = SystemClock.elapsedRealtime()
                            if (downloaded - lastReportedBytes >= PROGRESS_BYTES_INTERVAL ||
                                now - lastReportedAt >= PROGRESS_TIME_INTERVAL_MS
                            ) {
                                val progress = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                                _downloadState.value = VoiceState.Downloading(
                                    progress = progress.coerceAtLeast(0),
                                    label = label,
                                    downloadedMb = downloaded / (1024 * 1024),
                                    totalMb = if (total > 0) total / (1024 * 1024) else 0,
                                )
                                lastReportedBytes = downloaded
                                lastReportedAt = now
                            }
                        }
                        // Final flush so the UI reflects 100 % once the read loop exits.
                        val finalProgress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        _downloadState.value = VoiceState.Downloading(
                            progress = finalProgress.coerceAtLeast(0),
                            label = label,
                            downloadedMb = downloaded / (1024 * 1024),
                            totalMb = if (total > 0) total / (1024 * 1024) else 0,
                        )
                    }
                }
            }
            if (!retryFresh) break
        }

        check(partial.length() >= minBytes) {
            "Downloaded file for $label is too small (${partial.length()} bytes). " +
                "Check your connection and try again."
        }
        check(partial.renameTo(destination)) { "Could not finalize download for $label" }
        Log.i(TAG, "Saved $label (${destination.length()} bytes)")
    }

    private fun extractTarBz2(archive: File, destination: File) {
        destination.mkdirs()
        val destinationPath = destination.canonicalFile.path + File.separator
        TarArchiveInputStream(
            BufferedInputStream(BZip2CompressorInputStream(archive.inputStream()))
        ).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outputFile = File(destination, entry.name).canonicalFile
                    check(outputFile.path.startsWith(destinationPath)) {
                        "Archive entry escapes the destination: ${entry.name}"
                    }
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        tar.copyTo(output)
                    }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun findDirectoryNamed(root: File, name: String): File? =
        root.walkTopDown().firstOrNull { it.isDirectory && it.name == name }

    private fun isValidFile(file: File, minBytes: Long): Boolean =
        file.isFile && file.length() >= minBytes

    data class SttModelPaths(val model: String, val tokens: String)
    data class TtsModelPaths(val modelDir: String, val modelName: String, val dataDir: String)

    companion object {
        private const val TAG = "VoiceModelManager"
        private const val MIN_TOKENS_BYTES = 512L
        private const val MIN_STT_BYTES = 40L * 1_024 * 1_024
        private const val MIN_TTS_ONNX_BYTES = 512L * 1_024
        private const val MIN_ARCHIVE_BYTES = 512L * 1_024
        private const val PROGRESS_BYTES_INTERVAL = 1L * 1024 * 1024
        private const val PROGRESS_TIME_INTERVAL_MS = 500L

        private const val HF_BASE =
            "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
        private const val TTS_BASE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
        private const val TTS_ESPEAK_URL = "$TTS_BASE/espeak-ng-data.tar.bz2"
        // British male voice — common choice for a Jarvis-style assistant.
        private const val TTS_EN_ARCHIVE = "vits-piper-en_GB-alan-medium.tar.bz2"
        private const val TTS_EN_VOICE_ID = "alan"
        private const val TTS_EN_URL = "$TTS_BASE/$TTS_EN_ARCHIVE"
        private const val TTS_HI_URL = "$TTS_BASE/vits-piper-hi_IN-priyamvada-medium.tar.bz2"
    }
}
