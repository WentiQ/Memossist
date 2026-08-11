package com.example.apptempleate

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationItem(
    val id: String,
    val reminderId: String? = null,
    val conversationId: String? = null,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "SYSTEM", // CHAT_ANSWER, ONE_DAY_BEFORE, MORNING_OF_DAY, ONE_HOUR_BEFORE, TEN_MIN_BEFORE, POST_EVENT_CHECK, SYSTEM
    var isRead: Boolean = false
) {
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val sdfToday = SimpleDateFormat("h:mm a", Locale.getDefault())
        val sdfDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        return when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${(diff / 60_000L)}m ago"
            diff < 86400_000L -> "Today ${sdfToday.format(Date(timestamp))}"
            diff < 172800_000L -> "Yesterday ${sdfToday.format(Date(timestamp))}"
            else -> sdfDate.format(Date(timestamp))
        }
    }

    fun getTypeIconText(): String {
        return when (type) {
            "ONE_DAY_BEFORE" -> "📅"
            "MORNING_OF_DAY" -> "🌅"
            "ONE_HOUR_BEFORE" -> "⏰"
            "TEN_MIN_BEFORE" -> "🚨"
            "POST_EVENT_CHECK" -> "💬"
            else -> "🔔"
        }
    }
}
