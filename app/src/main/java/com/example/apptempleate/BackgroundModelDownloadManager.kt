package com.example.apptempleate

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ModelDownloadProgress(
    val modelId: String,
    val isDownloading: Boolean,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Double = 0.0,
    val percentage: Int = 0,
    val statusMessage: String = "",
    val isCompleted: Boolean = false,
    val error: String? = null
)

object BackgroundModelDownloadManager {

    private val progressMap = ConcurrentHashMap<String, ModelDownloadProgress>()
    private val listeners = mutableListOf<(ModelDownloadProgress) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun registerListener(listener: (ModelDownloadProgress) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: (ModelDownloadProgress) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyProgress(progress: ModelDownloadProgress) {
        progressMap[progress.modelId] = progress
        mainHandler.post {
            synchronized(listeners) {
                for (listener in listeners) {
                    listener(progress)
                }
            }
        }
    }

    fun getDownloadProgress(modelId: String): ModelDownloadProgress? {
        return progressMap[modelId]
    }

    fun isModelDownloading(modelId: String): Boolean {
        val progress = progressMap[modelId]
        return progress != null && progress.isDownloading
    }

    fun startDownload(context: Context, model: AiModel) {
        if (isModelDownloading(model.id)) return

        // Always delete any existing partial, temp, or broken model file before starting download again
        deleteDownloadedModel(context, model)

        val initialProgress = ModelDownloadProgress(
            modelId = model.id,
            isDownloading = true,
            statusMessage = "Starting background download..."
        )
        notifyProgress(initialProgress)

        RealModelDownloader.startDownload(context, model, object : RealModelDownloader.DownloadCallback {
            override fun onProgress(
                bytesDownloaded: Long,
                totalBytes: Long,
                speedBytesPerSec: Double,
                percentage: Int,
                statusMessage: String
            ) {
                val progress = ModelDownloadProgress(
                    modelId = model.id,
                    isDownloading = true,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speedBytesPerSec,
                    percentage = percentage,
                    statusMessage = statusMessage,
                    isCompleted = false
                )
                notifyProgress(progress)
            }

            override fun onSuccess(file: File) {
                NoeonAiEngine.markModelAsDownloaded(context, model.id)
                val progress = ModelDownloadProgress(
                    modelId = model.id,
                    isDownloading = false,
                    percentage = 100,
                    statusMessage = "Downloaded & Ready",
                    isCompleted = true
                )
                notifyProgress(progress)

                mainHandler.post {
                    Toast.makeText(context, "Completed download of ${model.name} (${file.name})", Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(errorMessage: String) {
                val progress = ModelDownloadProgress(
                    modelId = model.id,
                    isDownloading = false,
                    statusMessage = "Download failed: $errorMessage",
                    isCompleted = false,
                    error = errorMessage
                )
                notifyProgress(progress)

                mainHandler.post {
                    Toast.makeText(context, "Download error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun deleteDownloadedModel(context: Context, model: AiModel): Boolean {
        val targetFile = RealModelDownloader.getModelFile(context, model)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var deleted = false

        if (targetFile.exists()) {
            deleted = targetFile.delete() || deleted
        }
        if (tempFile.exists()) {
            deleted = tempFile.delete() || deleted
        }

        NoeonAiEngine.clearModelDownloaded(context, model.id)
        progressMap.remove(model.id)

        // If the deleted model was active, revert to default recommended model
        if (NoeonAiEngine.getSelectedModel(context).id == model.id) {
            NoeonAiEngine.setSelectedModel(context, NoeonAiEngine.DEFAULT_MODEL_ID)
        }

        return deleted
    }
}
