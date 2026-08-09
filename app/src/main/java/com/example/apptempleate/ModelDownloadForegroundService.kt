package com.example.apptempleate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ModelDownloadForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 8801

        const val EXTRA_MODEL_ID = "extra_model_id"

        fun startService(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val downloadListener: (ModelDownloadProgress) -> Unit = { progress ->
        if (progress.isDownloading) {
            updateNotification(
                title = "Downloading AI Model",
                message = progress.statusMessage,
                progressPercent = progress.percentage
            )
        } else {
            if (progress.isCompleted) {
                updateNotification(
                    title = "Model Download Complete 🎉",
                    message = "AI model is downloaded and ready for local inference",
                    progressPercent = 100
                )
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        BackgroundModelDownloadManager.registerListener(downloadListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val model = modelId?.let { ModelCatalog.getModelById(it) }
        val modelName = model?.name ?: "AI Model"

        val notification = createNotification("Downloading $modelName", "Starting background download...", 0)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundModelDownloadManager.unregisterListener(downloadListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for AI Model downloads running in background"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, message: String, progressPercent: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ModelMarketplaceActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_import)
            .setContentIntent(pendingIntent)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, message: String, progressPercent: Int) {
        val notification = createNotification(title, message, progressPercent)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
