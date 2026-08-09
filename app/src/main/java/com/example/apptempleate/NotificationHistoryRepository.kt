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
                val reminderId = obj.optString("reminderId", null)
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
        val list = loadLast30DaysNotifications(context)
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

    fun getUnreadCount(context: Context): Int {
        return loadLast30DaysNotifications(context).count { !it.isRead }
    }

    fun clearAll(context: Context) {
        saveAllNotifications(context, emptyList())
    }
}
