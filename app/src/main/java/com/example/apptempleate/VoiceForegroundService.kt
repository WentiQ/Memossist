package com.example.apptempleate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class VoiceForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createVoiceCallNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_VOICE_SERVICE) {
            stopSelf()
            sendBroadcast(Intent(ACTION_VOICE_CALL_STOPPED_EVENT))
            return START_NOT_STICKY
        }

        // Acquire Partial WakeLock to keep CPU awake during phone sleep mode
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Memossist:VoiceCallWakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // Max 3 hours safety timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Build Intent to return to VoiceConversationActivity
        val returnIntent = Intent(this, VoiceConversationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val returnPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build Intent to End Call from status bar notification
        val stopIntent = Intent(this, VoiceForegroundService::class.java).apply {
            setAction(ACTION_STOP_VOICE_SERVICE)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_voice)
            .setContentTitle("Memossist Live Voice Call")
            .setContentText("Microphone active. Voice call running in background.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(returnPendingIntent)
            .addAction(R.drawable.ic_call_end, "End Call", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(true)
        super.onDestroy()
    }

    private fun createVoiceCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Live Voice Calls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ongoing foreground service for Memossist live voice call continuous microphone recording"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "memossist_voice_call_channel"
        const val NOTIFICATION_ID = 8881
        const val ACTION_STOP_VOICE_SERVICE = "com.example.apptempleate.ACTION_STOP_VOICE_SERVICE"
        const val ACTION_VOICE_CALL_STOPPED_EVENT = "com.example.apptempleate.ACTION_VOICE_CALL_STOPPED_EVENT"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, VoiceForegroundService::class.java)
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
            try {
                val intent = Intent(context, VoiceForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
