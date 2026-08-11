package com.example.apptempleate

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReminderTrigger(
    val triggerId: String,
    val reminderId: String,
    val triggerTimeMillis: Long,
    val type: String, // ONE_DAY_BEFORE, MORNING_OF_DAY, ONE_HOUR_BEFORE, TEN_MIN_BEFORE, POST_EVENT_CHECK, CUSTOM
    val deliveryStyle: String, // NOTIFICATION, FULLSCREEN_ALARM, CALL_SIMULATION
    val humanoidMessage: String,
    var isTriggered: Boolean = false
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(triggerTimeMillis))
    }

    fun getTypeBadge(): String {
        return when (type) {
            "ONE_DAY_BEFORE" -> "1 Day Before"
            "MORNING_OF_DAY" -> "Morning Briefing"
            "ONE_HOUR_BEFORE" -> "1 Hr Before"
            "TEN_MIN_BEFORE" -> "10 Min Before"
            "POST_EVENT_CHECK" -> "Post-Event Check"
            else -> "Custom"
        }
    }
}

data class ReminderItem(
    val id: String,
    var title: String,
    var description: String,
    var eventTimeMillis: Long,
    var importance: String = "MEDIUM", // HIGH, MEDIUM, LOW
    var category: String = "PERSONAL", // CLASS, MEETING, DOCTOR, TASK, PERSONAL
    var isActive: Boolean = true,
    var isCompleted: Boolean = false,
    var consecutiveUnansweredFullscreenAlerts: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val triggers: MutableList<ReminderTrigger> = mutableListOf()
) {
    fun getFormattedEventDateTime(): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(eventTimeMillis))
    }

    fun getFormattedEventTimeOnly(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(eventTimeMillis))
    }

    fun getFormattedEventDateOnly(): String {
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return sdf.format(Date(eventTimeMillis))
    }

    fun getTimeStatusLabel(): String {
        val now = System.currentTimeMillis()
        val diff = eventTimeMillis - now
        return when {
            isCompleted -> "Completed"
            diff < 0 -> "Passed"
            diff < 3600_000L -> "In ${(diff / 60_000L).coerceAtLeast(1)} mins"
            diff < 86400_000L -> "In ${diff / 3600_000L} hours"
            else -> "In ${diff / 86400_000L} days"
        }
    }

    fun getImportanceColor(): String {
        return when (importance.uppercase()) {
            "HIGH" -> "#DC2626" // Crimson
            "MEDIUM" -> "#D97706" // Amber
            else -> "#2563EB" // Blue
        }
    }

    fun getCategoryIconText(): String {
        return when (category.uppercase()) {
            "CLASS" -> "📚"
            "MEETING" -> "🤝"
            "DOCTOR" -> "🩺"
            "TASK" -> "📝"
            else -> "⏰"
        }
    }

    fun getHumanoidWorkspaceStatement(userName: String = "Dinesh"): String {
        val triggerMsg = triggers.firstOrNull { it.humanoidMessage.isNotEmpty() }?.humanoidMessage
        if (!triggerMsg.isNullOrEmpty()) {
            return triggerMsg
        }

        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calEvent = Calendar.getInstance().apply { timeInMillis = eventTimeMillis }

        val isToday = calNow.get(Calendar.DAY_OF_YEAR) == calEvent.get(Calendar.DAY_OF_YEAR) &&
                      calNow.get(Calendar.YEAR) == calEvent.get(Calendar.YEAR)

        val timeOnly = getFormattedEventTimeOnly()

        return if (isToday) {
            "Hey $userName, do you remember today you have $title at $timeOnly?"
        } else {
            "Hey $userName, tomorrow you have $title at $timeOnly."
        }
    }

    fun getDayLabel(): String {
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calEvent = Calendar.getInstance().apply { timeInMillis = eventTimeMillis }

        val isToday = calNow.get(Calendar.DAY_OF_YEAR) == calEvent.get(Calendar.DAY_OF_YEAR) &&
                      calNow.get(Calendar.YEAR) == calEvent.get(Calendar.YEAR)

        return if (isToday) "TODAY ${getFormattedEventTimeOnly()}" else "TOMORROW ${getFormattedEventTimeOnly()}"
    }
}
