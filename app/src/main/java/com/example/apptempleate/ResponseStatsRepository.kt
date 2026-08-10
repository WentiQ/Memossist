package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object ResponseStatsRepository {

    private const val PREF_NAME = "memossist_response_stats"
    private const val KEY_RUNNING_AVG = "running_avg_seconds"
    private const val KEY_LAST_DURATION = "last_response_duration_seconds"
    private const val KEY_TOTAL_COUNT = "total_response_count"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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

        // Adaptive Exponential Moving Average (EMA):
        // If initial state (count <= 3 or prevAvg <= 0.15f), immediately seed with duration.
        // For established queries, use EMA with alpha = 0.35 for fast, real-time response adaptation.
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

        return Pair(newAvg, newCount)
    }

    /**
     * Formats the live chat timer string.
     * If estimated time is 00:00 (i.e. avgSeconds.toInt() <= 0), it shows the counted time
     * (lastDuration) of the PREVIOUS message as the estimated time.
     * From the next message onwards, it uses the normal estimated time formula.
     */
    fun formatTimerString(context: Context, elapsedSeconds: Long, avgSeconds: Float, totalCount: Int): String {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        val elapsedFormatted = String.format(Locale.US, "%02d:%02d", min, sec)

        if (totalCount == 0) {
            return "⏱️ $elapsedFormatted"
        }

        var estimatedSecFloat = avgSeconds
        // If estimated time is 00:00 (i.e. avgSeconds.toInt() <= 0), fallback to previous message's counted duration
        if (estimatedSecFloat.toInt() <= 0) {
            val lastDuration = getLastDuration(context)
            if (lastDuration > 0f) {
                estimatedSecFloat = lastDuration
            }
        }

        val estInt = estimatedSecFloat.toInt().coerceAtLeast(1)
        val estMin = estInt / 60
        val estSec = estInt % 60
        val estFormatted = String.format(Locale.US, "%02d:%02d", estMin, estSec)

        return "⏱️ $elapsedFormatted / Est. $estFormatted (#$totalCount)"
    }
}
