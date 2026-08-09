package com.example.apptempleate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "memossist_reminders_channel"
        const val CHANNEL_NAME = "Memossist Smart Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("EXTRA_REMINDER_ID") ?: return
        val triggerId = intent.getStringExtra("EXTRA_TRIGGER_ID") ?: ""
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Smart Reminder"
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Hey! You have an upcoming reminder event."
        val deliveryStyle = intent.getStringExtra("EXTRA_DELIVERY_STYLE") ?: "NOTIFICATION"
        val importance = intent.getStringExtra("EXTRA_IMPORTANCE") ?: "MEDIUM"
        val eventTime = intent.getLongExtra("EXTRA_EVENT_TIME", System.currentTimeMillis())

        if (triggerId.isNotBlank()) {
            ReminderRepository.markTriggerAsFired(context, triggerId)
        }

        // Log Notification into 30-Day Notification History (Works even when app is closed)
        val notifItem = NotificationItem(
            id = "NOTIF_${System.currentTimeMillis()}_${(100..999).random()}",
            reminderId = reminderId,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            type = if (deliveryStyle == "FULLSCREEN_ALARM") "TEN_MIN_BEFORE" else "SYSTEM",
            isRead = false
        )
        NotificationHistoryRepository.addNotification(context, notifItem)

        // Create System Notification Channel
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open RemindersActivity when notification is tapped
        val openIntent = Intent(context, RemindersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("HIGHLIGHT_REMINDER_ID", reminderId)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open Full-Screen Alarm Alert
        val alarmAlertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_REMINDER_ID", reminderId)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_MESSAGE", message)
            putExtra("EXTRA_EVENT_TIME", eventTime)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (reminderId + "_alarm").hashCode(),
            alarmAlertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)

        if (deliveryStyle == "FULLSCREEN_ALARM" || deliveryStyle == "CALL_SIMULATION" || importance == "HIGH") {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            // Also launch full screen activity directly for maximum visibility
            try {
                context.startActivity(alarmAlertIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        notificationManager.notify((reminderId + triggerId).hashCode(), builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Smart AI reminder alerts, briefings, and full screen alarm notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
