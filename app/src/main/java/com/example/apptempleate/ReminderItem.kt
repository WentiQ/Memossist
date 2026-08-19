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

    fun getCalendarDayDifference(): Int {
        val calNow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEvent = Calendar.getInstance().apply {
            timeInMillis = eventTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = calEvent.timeInMillis - calNow.timeInMillis
        return (diffMillis / (1000L * 60 * 60 * 24)).toInt()
    }

    fun getTimeBasedGreeting(userName: String = "Dinesh"): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning $userName"
            in 12..16 -> "Good afternoon $userName"
            in 17..21 -> "Good evening $userName"
            else -> "Hey $userName"
        }
    }

    fun getTimeStatusLabel(): String {
        val now = System.currentTimeMillis()
        val diff = eventTimeMillis - now

        if (isCompleted) return "Completed"
        if (diff < 0) {
            val pastMinutes = (-diff / 60_000L).coerceAtLeast(1)
            return when {
                pastMinutes < 60 -> "${pastMinutes}m ago"
                pastMinutes < 1440 -> "${pastMinutes / 60}h ago"
                else -> "${pastMinutes / 1440}d ago"
            }
        }

        return when {
            diff < 60_000L -> "Starting now"
            diff < 3600_000L -> "In ${(diff / 60_000L).coerceAtLeast(1)}m"
            diff < 86400_000L -> {
                val hours = diff / 3600_000L
                val remainderMins = (diff % 3600_000L) / 60_000L
                if (remainderMins > 0 && hours < 6) "In ${hours}h ${remainderMins}m" else "In ${hours}h"
            }
            else -> {
                val days = (diff / 86400_000L).coerceAtLeast(1)
                if (days == 1L) "In 1 day" else "In $days days"
            }
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
        val now = System.currentTimeMillis()
        val diffMillis = eventTimeMillis - now
        val diffDays = getCalendarDayDifference()
        val timeOnly = getFormattedEventTimeOnly()
        val greeting = getTimeBasedGreeting(userName)

        if (isCompleted) {
            return "$greeting, '${title}' was marked as completed."
        }

        if (diffMillis < 0) {
            return when (diffDays) {
                0 -> "$greeting, your '${title}' was scheduled for $timeOnly earlier today."
                -1 -> "$greeting, your '${title}' was scheduled yesterday at $timeOnly."
                else -> "$greeting, your '${title}' was scheduled for ${getFormattedEventDateTime()}."
            }
        }

        return when {
            // Event in less than 15 minutes
            diffMillis <= 15 * 60_000L -> {
                val mins = (diffMillis / 60_000L).coerceAtLeast(1)
                "$greeting! '${title}' starts in $mins minute${if (mins > 1) "s" else ""} ($timeOnly)!"
            }
            // Event in less than 1 hour
            diffMillis <= 60 * 60_000L -> {
                val mins = (diffMillis / 60_000L).coerceAtLeast(1)
                "$greeting, your '${title}' is coming up in $mins minutes ($timeOnly)!"
            }
            // Event later today
            diffDays == 0 -> {
                val hours = (diffMillis / 3600_000L).coerceAtLeast(1)
                "$greeting! Do you remember today you have '${title}' at $timeOnly (in ~$hours hour${if (hours > 1) "s" else ""})?"
            }
            // Event tomorrow
            diffDays == 1 -> {
                "$greeting! Gentle reminder for tomorrow: You have '${title}' scheduled at $timeOnly."
            }
            // Event within next few days of this week
            diffDays in 2..6 -> {
                val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(eventTimeMillis))
                "$greeting! Reminder for this $dayOfWeek: You have '${title}' scheduled at $timeOnly."
            }
            // Event on later specific date
            else -> {
                val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(eventTimeMillis))
                "$greeting! Reminder for $dateStr: You have '${title}' scheduled at $timeOnly."
            }
        }
    }

    fun getDayLabel(): String {
        val diffDays = getCalendarDayDifference()
        val timeOnly = getFormattedEventTimeOnly()

        return when (diffDays) {
            0 -> "TODAY $timeOnly"
            1 -> "TOMORROW $timeOnly"
            -1 -> "YESTERDAY $timeOnly"
            in 2..6 -> "${SimpleDateFormat("EEE", Locale.getDefault()).format(Date(eventTimeMillis)).uppercase()} $timeOnly"
            else -> "${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(eventTimeMillis)).uppercase()} $timeOnly"
        }
    }
}
