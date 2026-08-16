package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object NotificationHistoryRepository {

    private const val FILE_NAME = "memossist_notifications.json"
    private const val THIRTY_DAYS_MS = 30L * 24 * 3600_000L

    fun loadLast30DaysNotifications(context: Context): MutableList<NotificationItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return mutableListOf()
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NotificationItem>()
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - THIRTY_DAYS_MS

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val reminderId = obj.optString("reminderId", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                val conversationId = obj.optString("conversationId", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                val title = obj.getString("title")
                val message = obj.getString("message")
                val timestamp = obj.getLong("timestamp")
                val type = obj.optString("type", "SYSTEM")
                val isRead = obj.optBoolean("isRead", false)

                // STRICT FILTER: Keep ONLY last 30 days notifications!
                if (timestamp >= thirtyDaysAgo) {
                    list.add(
                        NotificationItem(
                            id = id,
                            reminderId = reminderId,
                            conversationId = conversationId,
                            title = title,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead
                        )
                    )
                }
            }

            list.sortByDescending { it.timestamp }

            // If any expired notifications were pruned, save updated list back
            if (list.size < array.length()) {
                saveAllNotifications(context, list)
            }

            list
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun saveAllNotifications(context: Context, list: List<NotificationItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("reminderId", item.reminderId)
                    put("conversationId", item.conversationId)
                    put("title", item.title)
                    put("message", item.message)
                    put("timestamp", item.timestamp)
                    put("type", item.type)
                    put("isRead", item.isRead)
                }
                array.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNotification(context: Context, notification: NotificationItem) {
        // STRICT RULE: Only record in notification centre if app is closed, in recent tasks (background),
        // or phone is slept / screen locked. Do not record if user is actively using the app in foreground.
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenInteractive = powerManager?.isInteractive ?: true
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isPhoneLocked = keyguardManager?.isKeyguardLocked ?: false
        val isPhoneSlept = !isScreenInteractive || isPhoneLocked

        val isAppClosedOrBackground = !AppLifecycleTracker.isAppInForeground

        if (!isAppClosedOrBackground && !isPhoneSlept) {
            // App is active in foreground with screen awake and unlocked -> Do not record in notification centre
            return
        }

        val list = loadLast30DaysNotifications(context)

        // De-duplication: Drop notification if an identical notification was logged within 5 seconds
        val now = System.currentTimeMillis()
        val isDuplicate = list.any { existing ->
            existing.type == notification.type &&
            existing.title == notification.title &&
            existing.message == notification.message &&
            (now - existing.timestamp) < 5_000L
        }

        if (isDuplicate) {
            return
        }

        list.add(0, notification)
        saveAllNotifications(context, list)
    }

    fun markAllAsRead(context: Context) {
        val list = loadLast30DaysNotifications(context)
        for (item in list) {
            item.isRead = true
        }
        saveAllNotifications(context, list)
    }

    fun markAsRead(context: Context, notificationId: String) {
        val list = loadLast30DaysNotifications(context)
        val item = list.find { it.id == notificationId }
        if (item != null) {
            item.isRead = true
            saveAllNotifications(context, list)
        }
    }

    fun deleteNotification(context: Context, notificationId: String) {
        val list = loadLast30DaysNotifications(context)
        val updated = list.filterNot { it.id == notificationId }
        saveAllNotifications(context, updated)
    }

    fun getUnreadCount(context: Context): Int {
        return loadLast30DaysNotifications(context).count { !it.isRead }
    }

    fun clearAll(context: Context) {
        saveAllNotifications(context, emptyList())
    }
}
