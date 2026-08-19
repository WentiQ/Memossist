package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class CaseLatencyStat(
    val messageType: MessageType,
    val displayName: String,
    val shortName: String,
    val icon: String,
    val avgSeconds: Float,
    val lastSeconds: Float,
    val totalCount: Int,
    val baselineSeconds: Float,
    val effectiveEstimatedSeconds: Float
)

object ResponseStatsRepository {

    private const val PREF_NAME = "memossist_response_stats"
    private const val KEY_RUNNING_AVG = "running_avg_seconds"
    private const val KEY_LAST_DURATION = "last_response_duration_seconds"
    private const val KEY_TOTAL_COUNT = "total_response_count"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getBaselineSeconds(type: MessageType): Float = when (type) {
        MessageType.REMINDER_ONLY -> 2.0f
        MessageType.TELLING -> 3.0f
        MessageType.ASKING -> 4.5f
        MessageType.MIXED -> 5.0f
        MessageType.REMINDER_AND_TELLING -> 3.8f
        MessageType.REMINDER_AND_ASKING -> 5.2f
        MessageType.REMINDER_AND_MIXED -> 6.5f
    }

    fun getStats(context: Context): Pair<Float, Int> {
        val prefs = getPrefs(context)
        val avg = prefs.getFloat(KEY_RUNNING_AVG, 0.0f)
        val count = prefs.getInt(KEY_TOTAL_COUNT, 0)
        return Pair(avg, count)
    }

    fun getLastDuration(context: Context): Float {
        return getPrefs(context).getFloat(KEY_LAST_DURATION, 0.0f)
    }

    fun getCaseStats(context: Context, type: MessageType): Pair<Float, Int> {
        val prefs = getPrefs(context)
        val avg = prefs.getFloat("${KEY_RUNNING_AVG}_${type.name}", 0.0f)
        val count = prefs.getInt("${KEY_TOTAL_COUNT}_${type.name}", 0)
        return Pair(avg, count)
    }

    fun getLastCaseDuration(context: Context, type: MessageType): Float {
        return getPrefs(context).getFloat("${KEY_LAST_DURATION}_${type.name}", 0.0f)
    }

    fun recordCaseResponseTime(context: Context, type: MessageType, durationSeconds: Float): Pair<Float, Int> {
        if (durationSeconds <= 0f) return getCaseStats(context, type)
        // Also update global aggregate stats
        recordNewResponseTime(context, durationSeconds)

        val prefs = getPrefs(context)
        val prevAvg = prefs.getFloat("${KEY_RUNNING_AVG}_${type.name}", 0.0f)
        val prevCount = prefs.getInt("${KEY_TOTAL_COUNT}_${type.name}", 0)
        val newCount = prevCount + 1

        val newAvg = if (prevCount == 0 || prevAvg <= 0.15f) {
            durationSeconds
        } else if (newCount <= 4) {
            prevAvg + (durationSeconds - prevAvg) / newCount.toFloat()
        } else {
            (0.60f * prevAvg) + (0.40f * durationSeconds)
        }

        prefs.edit()
            .putFloat("${KEY_RUNNING_AVG}_${type.name}", newAvg)
            .putFloat("${KEY_LAST_DURATION}_${type.name}", durationSeconds)
            .putInt("${KEY_TOTAL_COUNT}_${type.name}", newCount)
            .apply()

        android.util.Log.i("ResponseStatsRepository", "Recorded ${type.name}: duration=${durationSeconds}s, newAvg=${newAvg}s, count=${newCount}")
        return Pair(newAvg, newCount)
    }

    /**
     * Updates running average and stores last response duration.
     * Uses adaptive Exponential Moving Average (EMA) so that the average
     * dynamically updates and never gets stuck at old static values like 0.1s.
     */
    fun recordNewResponseTime(context: Context, durationSeconds: Float): Pair<Float, Int> {
        if (durationSeconds <= 0f) return getStats(context)

        val prefs = getPrefs(context)
        val prevAvg = prefs.getFloat(KEY_RUNNING_AVG, 0.0f)
        val prevCount = prefs.getInt(KEY_TOTAL_COUNT, 0)

        val newCount = prevCount + 1

        val newAvg = if (prevCount == 0 || prevAvg <= 0.15f) {
            durationSeconds
        } else if (newCount <= 5) {
            prevAvg + (durationSeconds - prevAvg) / newCount.toFloat()
        } else {
            (0.65f * prevAvg) + (0.35f * durationSeconds)
        }

        prefs.edit()
            .putFloat(KEY_RUNNING_AVG, newAvg)
            .putFloat(KEY_LAST_DURATION, durationSeconds)
            .putInt(KEY_TOTAL_COUNT, newCount)
            .apply()

        android.util.Log.i("ResponseStatsRepository", "Recorded global: duration=${durationSeconds}s, newAvg=${newAvg}s, count=${newCount}")
        return Pair(newAvg, newCount)
    }

    /**
     * Formats the live chat timer string tailored to the specific MessageType case.
     */
    fun formatTimerStringForCase(context: Context, elapsedSeconds: Long, type: MessageType?): String {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        val elapsedFormatted = String.format(Locale.US, "%02d:%02d", min, sec)

        val targetType = type ?: MessageType.TELLING
        val (avgSec, count) = getCaseStats(context, targetType)
        val baseline = getBaselineSeconds(targetType)

        val estFloat = if (count > 0 && avgSec > 0f) {
            avgSec
        } else {
            val lastDur = getLastCaseDuration(context, targetType)
            if (lastDur > 0f) lastDur else baseline
        }

        val estInt = estFloat.toInt().coerceAtLeast(1)
        val estMin = estInt / 60
        val estSec = estInt % 60
        val estFormatted = String.format(Locale.US, "%02d:%02d", estMin, estSec)

        val caseTag = when (targetType) {
            MessageType.REMINDER_ONLY -> "Rem"
            MessageType.TELLING -> "Tell"
            MessageType.ASKING -> "Ask"
            MessageType.MIXED -> "Mix"
            MessageType.REMINDER_AND_TELLING -> "Rem+Tell"
            MessageType.REMINDER_AND_ASKING -> "Rem+Ask"
            MessageType.REMINDER_AND_MIXED -> "Rem+Mix"
        }

        val countTag = if (count > 0) " (#$count)" else ""
        return "$elapsedFormatted / Est. $estFormatted [$caseTag$countTag]"
    }

    fun formatTimerString(context: Context, elapsedSeconds: Long, avgSeconds: Float = 0f, totalCount: Int = 0): String {
        val (currentAvg, count) = getStats(context)
        val estFloat = if (count > 0 && currentAvg > 0f) currentAvg else getLastDuration(context).takeIf { it > 0f } ?: 3.5f
        val estInt = estFloat.toInt().coerceAtLeast(1)
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        val estMin = estInt / 60
        val estSec = estInt % 60
        return String.format(Locale.US, "%02d:%02d / Est. %02d:%02d", min, sec, estMin, estSec)
    }

    fun getAllCaseStats(context: Context): List<CaseLatencyStat> {
        val cases = listOf(
            Triple(MessageType.REMINDER_ONLY, "Reminder Only", ""),
            Triple(MessageType.TELLING, "Telling (Facts)", ""),
            Triple(MessageType.ASKING, "Asking (Q&A Recall)", ""),
            Triple(MessageType.MIXED, "Mixed (Fact + Q&A)", ""),
            Triple(MessageType.REMINDER_AND_TELLING, "Reminder + Telling", ""),
            Triple(MessageType.REMINDER_AND_ASKING, "Reminder + Asking", ""),
            Triple(MessageType.REMINDER_AND_MIXED, "Reminder + Mixed", "")
        )

        return cases.map { (type, name, icon) ->
            val (avg, count) = getCaseStats(context, type)
            val last = getLastCaseDuration(context, type)
            val baseline = getBaselineSeconds(type)
            val effective = if (count > 0 && avg > 0f) avg else (if (last > 0f) last else baseline)
            CaseLatencyStat(
                messageType = type,
                displayName = name,
                shortName = type.name.replace("_", " "),
                icon = icon,
                avgSeconds = avg,
                lastSeconds = last,
                totalCount = count,
                baselineSeconds = baseline,
                effectiveEstimatedSeconds = effective
            )
        }
    }
}
