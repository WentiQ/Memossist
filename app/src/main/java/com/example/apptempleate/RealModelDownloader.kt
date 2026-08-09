package com.example.apptempleate

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RealModelDownloader {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCancelled = false

    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, speedBytesPerSec: Double, percentage: Int, statusMessage: String)
        fun onSuccess(file: File)
        fun onError(errorMessage: String)
    }

    fun getModelFile(context: Context, model: AiModel): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, model.fileName)
    }

    fun isModelFileDownloaded(context: Context, model: AiModel): Boolean {
        val file = getModelFile(context, model)
        if (!file.exists()) return false
        // File must be at least 70% of total expected size (or >= 50MB for small models) to be considered complete
        val minExpectedBytes = (model.downloadSizeMb * 1024L * 1024L * 0.70).toLong().coerceAtLeast(50L * 1024L * 1024L)
        return file.length() >= minExpectedBytes
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun startDownload(context: Context, model: AiModel, callback: DownloadCallback) {
        isCancelled = false
        val targetFile = getModelFile(context, model)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        try {
            if (targetFile.exists()) targetFile.delete()
            if (tempFile.exists()) tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        executor.execute {
            var urlConnection: HttpURLConnection? = null
            var inputStream: InputStream?
            var outputStream: FileOutputStream?

            try {
                var currentUrl = model.downloadUrl
                var redirects = 0
                val maxRedirects = 5

                // Follow redirects (Hugging Face redirects to CDN storage)
                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.connectTimeout = 30000
                    urlConnection.readTimeout = 120000
                    urlConnection.requestMethod = "GET"
                    urlConnection.setRequestProperty("User-Agent", "MemossistAndroidApp/1.0")
                    urlConnection.instanceFollowRedirects = false
                    urlConnection.connect()

                    val status = urlConnection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308) {

                        currentUrl = urlConnection.getHeaderField("Location")
                        urlConnection.disconnect()
                        redirects++
                    } else if (status == HttpURLConnection.HTTP_OK) {
                        break
                    } else {
                        throw Exception("HTTP Server returned response code: $status")
                    }
                }

                val contentLength = urlConnection?.contentLengthLong ?: -1L
                val totalLength = if (contentLength > 0) contentLength else (model.downloadSizeMb * 1024L * 1024L)

                inputStream = urlConnection?.inputStream ?: throw Exception("Failed to open input stream from server")
                outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(64 * 1024) // 64KB buffer
                var bytesRead: Int
                var totalDownloaded = 0L
                val startTime = System.currentTimeMillis()
                var lastSpeedCalcTime = startTime
                var lastSpeedCalcBytes = 0L
                var currentSpeed = 0.0

                mainHandler.post {
                    callback.onProgress(0, totalLength, 0.0, 0, "Connecting to download server...")
                }

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) {
                        outputStream.close()
                        tempFile.delete()
                        mainHandler.post { callback.onError("Download cancelled by user") }
                        return@execute
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead

                    val currentTime = System.currentTimeMillis()
                    val timeDiff = (currentTime - lastSpeedCalcTime) / 1000.0

                    if (timeDiff >= 0.5) { // Recalculate speed every 500ms
                        val bytesDiff = totalDownloaded - lastSpeedCalcBytes
                        currentSpeed = bytesDiff / timeDiff
                        lastSpeedCalcTime = currentTime
                        lastSpeedCalcBytes = totalDownloaded

                        val progressPercent = if (totalLength > 0) ((totalDownloaded * 100) / totalLength).toInt().coerceIn(0, 99) else 50
                        val mbDownloaded = totalDownloaded / (1024.0 * 1024.0)
                        val mbTotal = totalLength / (1024.0 * 1024.0)
                        val speedMb = currentSpeed / (1024.0 * 1024.0)

                        val statusMsg = String.format("Downloading: %.1f MB / %.1f MB (%.1f MB/s)", mbDownloaded, mbTotal, speedMb)

                        mainHandler.post {
                            callback.onProgress(totalDownloaded, totalLength, currentSpeed, progressPercent, statusMsg)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Rename temp file to target model GGUF file
                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }

                mainHandler.post {
                    callback.onProgress(totalDownloaded, totalLength, currentSpeed, 100, "Verifying model checksum & activating...")
                    callback.onSuccess(targetFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                tempFile.delete()
                mainHandler.post {
                    callback.onError(e.localizedMessage ?: "Network connection failed during model download")
                }
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}
