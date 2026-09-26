package com.example.ai_agent.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.content.Context
import android.os.StatFs
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ai_agent.data.local.HuggingFaceTokenStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import kotlinx.coroutines.CancellationException

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val client: OkHttpClient,
    private val tokenStore: HuggingFaceTokenStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "model.gguf"
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: fileName
        val uri = runCatching { URI(url) }.getOrElse {
            return Result.failure(workDataOf(KEY_ERROR to "Invalid model URL"))
        }
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "Model downloads must use HTTPS"))
        }
        if (!isSafeFileName(fileName) || !fileName.endsWith(".gguf", ignoreCase = true)) {
            return Result.failure(workDataOf(KEY_ERROR to "Invalid model filename"))
        }
        val modelsDir = File(applicationContext.filesDir, ModelImporter.MODELS_DIR).apply { mkdirs() }
        val partial = File(modelsDir, "$fileName.partial")
        val destination = File(modelsDir, fileName)

        // Enforce that destination stays inside modelsDir even if fileName sneaks through
        val modelsRoot = modelsDir.canonicalPath + File.separator
        if (!destination.canonicalPath.startsWith(modelsRoot) ||
            !partial.canonicalPath.startsWith(modelsRoot)
        ) {
            return Result.failure(workDataOf(KEY_ERROR to "Invalid destination path"))
        }
        val partialBytes = partial.takeIf { it.isFile }?.length() ?: 0L

        setProgress(workDataOf(KEY_PROGRESS to 0, KEY_DISPLAY_NAME to displayName))
        setForeground(createForegroundInfo(displayName, 0))

        return try {
            val request = Request.Builder().url(url).apply {
                if (partialBytes > 0L) {
                    header("Range", "bytes=$partialBytes-")
                }
                if (uri.host == "huggingface.co") {
                    tokenStore.token.value
                        ?.takeIf { it.isNotBlank() }
                        ?.let { header("Authorization", "Bearer $it") }
                }
            }.build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        "This model provider requires accepting its license or adding an optional access token."
                    } else {
                        "HTTP ${response.code}"
                    }
                }
                val body = requireNotNull(response.body)
                val resume = partialBytes > 0L && response.code == 206
                if (!resume) partial.delete()
                val startingBytes = if (resume) partialBytes else 0L
                val total = body.contentLength().takeIf { it >= 0L }?.plus(startingBytes) ?: -1L
                requireSufficientStorage(modelsDir, body.contentLength())

                FileOutputStream(partial, resume).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = startingBytes
                        var lastReportedBytes = downloaded
                        var lastReportedAt = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = SystemClock.elapsedRealtime()
                            if (downloaded - lastReportedBytes >= PROGRESS_BYTES_INTERVAL ||
                                now - lastReportedAt >= PROGRESS_TIME_INTERVAL_MS
                            ) {
                                reportProgress(displayName, downloaded, total)
                                lastReportedBytes = downloaded
                                lastReportedAt = now
                            }
                        }
                        output.fd.sync()
                        reportProgress(displayName, downloaded, total)
                    }
                }
            }
            // Verify the downloaded file actually looks like a GGUF before we hand it to the
            // native loader (which would otherwise SIGSEGV on a bad response body).
            if (!isValidGgufFile(partial)) {
                partial.delete()
                return Result.failure(
                    workDataOf(
                        KEY_ERROR to "Downloaded file is not a valid GGUF model. " +
                            "Check the URL (login walls / redirects will save HTML by mistake).",
                    ),
                )
            }
            check(partial.renameTo(destination)) { "Could not finalize download" }
            Result.success(workDataOf(KEY_OUTPUT_PATH to destination.absolutePath))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            partial.delete()
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Download failed")))
        }
    }

    private suspend fun reportProgress(modelName: String, downloaded: Long, total: Long) {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        setProgress(
            workDataOf(
                KEY_PROGRESS to progress,
                KEY_DISPLAY_NAME to modelName,
            ),
        )
        setForeground(createForegroundInfo(modelName, progress))
    }

    private fun requireSufficientStorage(directory: File, remainingDownloadBytes: Long) {
        if (remainingDownloadBytes < 0L) return
        val availableBytes = StatFs(directory.absolutePath).availableBytes
        require(availableBytes >= remainingDownloadBytes + MIN_FREE_SPACE_BYTES) {
            "Not enough device storage. Free at least ${MIN_FREE_SPACE_BYTES / (1024 * 1024)} MB and retry."
        }
    }

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val channelId = CHANNEL_ID
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("AI Agent")
            .setContentText(
                if (progress > 0) "Downloading $modelName · $progress%"
                else "Preparing $modelName",
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress.coerceAtLeast(0), progress <= 0)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() &&
            File(name).name == name &&
            !name.contains('\u0000') &&
            !name.contains("..") &&
            name.length <= 200

    private fun isValidGgufFile(file: File): Boolean {
        if (!file.isFile || file.length() < GGUF_HEADER_MIN_BYTES) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4) return@runCatching false
                magic.contentEquals(GGUF_MAGIC)
            }
        }.getOrDefault(false)
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR = "error"
        const val WORK_NAME = "model_download"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_BYTES_INTERVAL = 1L * 1024 * 1024
        private const val PROGRESS_TIME_INTERVAL_MS = 750L
        private const val MIN_FREE_SPACE_BYTES = 100L * 1024 * 1024
        private const val GGUF_HEADER_MIN_BYTES = 8L
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"
    }
}
