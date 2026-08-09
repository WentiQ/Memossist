package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object ReminderExtractor {

    data class ExtractedReminderData(
        val title: String,
        val description: String,
        val targetTimeMillis: Long,
        val category: String,
        val importance: String
    )

    /**
     * Extracts reminder details from LLM response or user text, and creates a full ReminderItem
     * with multi-timestamp triggers.
     */
    fun extractAndCreateReminder(
        context: Context,
        userMessage: String,
        llmExtractedTag: String? = null
    ): ReminderItem? {
        val data = parseReminderData(userMessage, llmExtractedTag) ?: return null
        val userName = getSavedUserName(context)
        val morningHour = getSavedMorningHour(context) // Default 7 (7 AM)

        val reminderId = "REM-${UUID.randomUUID().toString().take(6).uppercase()}"
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = sdfTime.format(Date(data.targetTimeMillis))

        val triggers = mutableListOf<ReminderTrigger>()
        val now = System.currentTimeMillis()
        val eventTime = data.targetTimeMillis

        // 1. One Day Before Trigger (T - 24 hours)
        val oneDayBefore = eventTime - 24 * 3600_000L
        if (oneDayBefore > now) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_1D_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = oneDayBefore,
                    type = "ONE_DAY_BEFORE",
                    deliveryStyle = "NOTIFICATION",
                    humanoidMessage = "Hey $userName! Gentle reminder for tomorrow: You have '${data.title}' scheduled at $timeStr. Don't forget to prepare!"
                )
            )
        }

        // 2. Start of Day Morning Briefing Trigger (Morning Hour on Event Day, e.g. 7:00 AM)
        val calMorning = Calendar.getInstance().apply {
            timeInMillis = eventTime
            set(Calendar.HOUR_OF_DAY, morningHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morningTime = calMorning.timeInMillis
        // Only add morning trigger if it occurs before the event and after current time
        if (morningTime > now && morningTime < eventTime - 15 * 60_000L) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_MRN_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = morningTime,
                    type = "MORNING_OF_DAY",
                    deliveryStyle = "NOTIFICATION",
                    humanoidMessage = "Good morning $userName! Do you remember today you have '${data.title}' at $timeStr right? Have a productive day!"
                )
            )
        }

        // 3. 1 Hour Before Trigger (T - 1 hour)
        val oneHourBefore = eventTime - 3600_000L
        if (oneHourBefore > now) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_1H_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = oneHourBefore,
                    type = "ONE_HOUR_BEFORE",
                    deliveryStyle = "NOTIFICATION",
                    humanoidMessage = "Hey $userName, your '${data.title}' is coming up in 1 hour ($timeStr)! Time to get ready."
                )
            )
        }

        // 4. 10 Minutes Before Trigger (T - 10 minutes) - Full Screen Alarm Alert
        val tenMinBefore = eventTime - 10 * 60_000L
        if (tenMinBefore > now) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_10M_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = tenMinBefore,
                    type = "TEN_MIN_BEFORE",
                    deliveryStyle = "FULLSCREEN_ALARM",
                    humanoidMessage = "🚨 Heading out? '${data.title}' starts in 10 minutes ($timeStr)!"
                )
            )
        }

        // 5. Post-Event Follow-up Check Trigger (T + 45 minutes)
        val postEventTime = eventTime + 45 * 60_000L
        if (postEventTime > now) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_POST_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = postEventTime,
                    type = "POST_EVENT_CHECK",
                    deliveryStyle = "NOTIFICATION",
                    humanoidMessage = "Hey $userName, did you go for '${data.title}' today? Hope it went really well!"
                )
            )
        }

        // Fallback: If no future trigger was calculated (event is very soon e.g. in 5 mins), add a 1-min alert
        if (triggers.isEmpty() && eventTime > now) {
            triggers.add(
                ReminderTrigger(
                    triggerId = "TRG_IMM_${UUID.randomUUID().toString().take(6)}",
                    reminderId = reminderId,
                    triggerTimeMillis = (now + 10_000L).coerceAtMost(eventTime),
                    type = "CUSTOM",
                    deliveryStyle = "FULLSCREEN_ALARM",
                    humanoidMessage = "Hey $userName, reminder alert for '${data.title}' at $timeStr!"
                )
            )
        }

        return ReminderItem(
            id = reminderId,
            title = data.title,
            description = data.description,
            eventTimeMillis = data.targetTimeMillis,
            importance = data.importance,
            category = data.category,
            isActive = true,
            isCompleted = false,
            createdTimestamp = now,
            triggers = triggers
        )
    }

    private fun parseReminderData(userMessage: String, llmTag: String?): ExtractedReminderData? {
        // 1. Try parsing JSON format from LLM tag if available
        if (!llmTag.isNullOrBlank() && llmTag != "NONE" && llmTag != "[]") {
            try {
                val cleanTag = llmTag.trim().removeSurrounding("[", "]").trim()
                val jsonObj = if (cleanTag.startsWith("{")) JSONObject(cleanTag) else JSONObject("{ $cleanTag }")
                val title = jsonObj.optString("title", "").ifBlank { jsonObj.optString("event", "") }
                val desc = jsonObj.optString("description", userMessage)
                val timeStr = jsonObj.optString("time", "").ifBlank { jsonObj.optString("date", "") }
                val imp = jsonObj.optString("importance", "MEDIUM").uppercase()
                val cat = jsonObj.optString("category", inferCategory(title + " " + desc)).uppercase()

                val timeMillis = parseNaturalLanguageDateTime(timeStr.ifBlank { userMessage })
                if (title.isNotBlank() && timeMillis > 0) {
                    return ExtractedReminderData(title, desc, timeMillis, cat, imp)
                }
            } catch (e: Exception) {
                // Fallback to text parsing
            }
        }

        // 2. Direct Heuristic Extraction from User Message
        val lower = userMessage.lowercase(Locale.getDefault())

        val reminderKeywords = listOf(
            "remind me", "reminder", "i have", "have to", "got to", "need to",
            "appointment", "class", "meeting", "doctor", "pickup", "pick up",
            "schedule", "flight", "exam", "test", "interview", "call someone", "visit"
        )

        val hasReminderIntent = reminderKeywords.any { lower.contains(it) } ||
                lower.contains("at ") || lower.contains("on ") || lower.contains("tomorrow") || lower.contains("next ")

        if (!hasReminderIntent) return null

        val targetTimeMillis = parseNaturalLanguageDateTime(userMessage)
        if (targetTimeMillis <= 0) return null

        val category = inferCategory(userMessage)
        val title = extractTitle(userMessage, category)
        val importance = if (lower.contains("urgent") || lower.contains("doctor") || lower.contains("exam") || lower.contains("flight")) "HIGH" else "MEDIUM"

        return ExtractedReminderData(
            title = title,
            description = userMessage,
            targetTimeMillis = targetTimeMillis,
            category = category,
            importance = importance
        )
    }

    private fun inferCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("class") || lower.contains("lecture") || lower.contains("exam") || lower.contains("study") -> "CLASS"
            lower.contains("doctor") || lower.contains("hospital") || lower.contains("clinic") || lower.contains("medicine") -> "DOCTOR"
            lower.contains("meet") || lower.contains("discussion") || lower.contains("interview") || lower.contains("call") -> "MEETING"
            lower.contains("pickup") || lower.contains("pick up") || lower.contains("station") || lower.contains("airport") || lower.contains("buy") -> "TASK"
            else -> "PERSONAL"
        }
    }

    private fun extractTitle(userMessage: String, category: String): String {
        var clean = userMessage
            .replace(Regex("(?i)remind me (that|to)?"), "")
            .replace(Regex("(?i)please remind me"), "")
            .replace(Regex("(?i)i have a?"), "")
            .replace(Regex("(?i)i have to"), "")
            .replace(Regex("(?i)i need to"), "")
            .trim()

        val sentenceEnd = clean.indexOfAny(charArrayOf('.', '!', '?'))
        if (sentenceEnd > 0) {
            clean = clean.substring(0, sentenceEnd)
        }

        if (clean.length > 40) {
            clean = clean.take(40) + "..."
        }

        if (clean.length < 3) {
            clean = when (category) {
                "CLASS" -> "Extra Class Session"
                "DOCTOR" -> "Doctor Appointment"
                "MEETING" -> "Important Meeting"
                "TASK" -> "Pickup / Task"
                else -> "Personal Event"
            }
        } else {
            clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        return clean
    }

    fun parseNaturalLanguageDateTime(text: String): Long {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val lower = text.lowercase(Locale.getDefault())

        // 1. Time Extraction (e.g. 2pm, 2:30pm, 14:00, 10 am, 6:30 pm)
        var hour = -1
        var minute = 0

        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val matches = timeRegex.findAll(lower).toList()

        for (m in matches) {
            val numStr = m.groupValues[1]
            val minStr = m.groupValues[2]
            val ampm = m.groupValues[3].lowercase()

            val num = numStr.toIntOrNull() ?: continue
            // Skip numbers that look like years or days of month (e.g. 2026 or 15th)
            if (num > 24) continue

            if (ampm.isNotBlank()) {
                hour = when {
                    ampm == "pm" && num < 12 -> num + 12
                    ampm == "am" && num == 12 -> 0
                    else -> num
                }
                if (minStr.isNotBlank()) minute = minStr.toIntOrNull() ?: 0
                break
            } else if (lower.contains("at $num") || lower.contains("by $num") || minStr.isNotBlank()) {
                hour = num
                if (minStr.isNotBlank()) minute = minStr.toIntOrNull() ?: 0
                if (hour in 1..7 && !lower.contains("am")) hour += 12 // Default 2 -> 14 (2 PM)
                break
            }
        }

        // If no explicit time found, check keywords like "evening", "morning", "afternoon"
        if (hour == -1) {
            hour = when {
                lower.contains("morning") -> 9
                lower.contains("afternoon") -> 14
                lower.contains("evening") -> 18
                lower.contains("night") -> 20
                else -> 14 // Default to 2 PM
            }
        }

        // 2. Day / Date Extraction
        var targetDayOffset = -1

        val daysOfWeek = mapOf(
            "sunday" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY
        )

        for ((dayName, dayConst) in daysOfWeek) {
            if (lower.contains(dayName)) {
                val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                var daysDiff = dayConst - currentDayOfWeek
                if (daysDiff <= 0 || lower.contains("next $dayName")) {
                    daysDiff += 7
                }
                cal.add(Calendar.DAY_OF_YEAR, daysDiff)
                targetDayOffset = daysDiff
                break
            }
        }

        if (targetDayOffset == -1) {
            if (lower.contains("tomorrow")) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } else if (lower.contains("next week")) {
                cal.add(Calendar.DAY_OF_YEAR, 7)
            } else if (lower.contains("today")) {
                // Keep current day
            } else {
                // Check if user specified a relative hour offset e.g. "in 2 hours"
                val relativeRegex = Regex("in (\\d+)\\s*(hour|hr|min|minute|day)", RegexOption.IGNORE_CASE)
                val relMatch = relativeRegex.find(lower)
                if (relMatch != null) {
                    val amount = relMatch.groupValues[1].toIntOrNull() ?: 1
                    val unit = relMatch.groupValues[2].lowercase()
                    val relCal = Calendar.getInstance()
                    if (unit.startsWith("min")) {
                        relCal.add(Calendar.MINUTE, amount)
                    } else if (unit.startsWith("day")) {
                        relCal.add(Calendar.DAY_OF_YEAR, amount)
                    } else {
                        relCal.add(Calendar.HOUR_OF_DAY, amount)
                    }
                    return relCal.timeInMillis
                }
                
                // If the hour today has already passed, assume user means tomorrow at that time
                val checkCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (checkCal.timeInMillis < now + 60_000L) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        var resultMillis = cal.timeInMillis
        if (resultMillis < now) {
            // Ensure target time is in the future
            cal.add(Calendar.DAY_OF_YEAR, 1)
            resultMillis = cal.timeInMillis
        }

        return resultMillis
    }

    private fun getSavedUserName(context: Context): String {
        val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_name", "Dinesh") ?: "Dinesh"
    }

    private fun getSavedMorningHour(context: Context): Int {
        val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("morning_briefing_hour", 7) // Default 7 AM
    }
}
