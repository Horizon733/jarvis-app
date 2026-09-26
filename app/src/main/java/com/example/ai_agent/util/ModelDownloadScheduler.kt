package com.example.ai_agent.util

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface DownloadStatus {
    data class Downloading(val progress: Int, val modelName: String = "Model") : DownloadStatus
    data class Success(val path: String) : DownloadStatus
    data class Error(val message: String) : DownloadStatus
}

@Singleton
class ModelDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(url: String, fileName: String, displayName: String = fileName) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_URL to url,
                    ModelDownloadWorker.KEY_FILE_NAME to fileName,
                    ModelDownloadWorker.KEY_DISPLAY_NAME to displayName,
                )
            )
            .build()
        // KEEP rather than REPLACE: if a download is already in progress, the user must
        // explicitly cancel before starting a new one (mirrors the UI Button.enabled guard
        // in SettingsScreen). REPLACE silently drops the in-flight download, which is a
        // frustrating UX for multi-GB model files on mobile networks.
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueue(model: CuratedModel) =
        enqueue(model.downloadUrl, model.fileName, model.displayName)

    fun cancel() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    }

    fun observeDownload(): Flow<DownloadStatus?> =
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME).map { infos ->
            val info = infos.firstOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.RUNNING -> {
                    val progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                    val name = info.progress.getString(ModelDownloadWorker.KEY_DISPLAY_NAME)
                        ?: "Model"
                    DownloadStatus.Downloading(progress.coerceAtLeast(0), name)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val path = info.outputData.getString(ModelDownloadWorker.KEY_OUTPUT_PATH)
                    if (path != null) DownloadStatus.Success(path) else null
                }
                WorkInfo.State.FAILED -> {
                    val message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                    DownloadStatus.Error(message ?: "Download failed")
                }
                WorkInfo.State.CANCELLED -> DownloadStatus.Error("Download cancelled")
                else -> DownloadStatus.Downloading(0)
            }
        }
}
