package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences

object ResponseStatsRepository {

    private const val PREF_NAME = "memossist_response_stats"
    private const val KEY_RUNNING_AVG = "running_avg_seconds"
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

    /**
     * Incrementally updates running average using:
     * newCount = prevCount + 1
     * newAvg = prevAvg + (duration - prevAvg) / newCount
     */
    fun recordNewResponseTime(context: Context, durationSeconds: Float): Pair<Float, Int> {
        if (durationSeconds <= 0f) return getStats(context)

        val prefs = getPrefs(context)
        val prevAvg = prefs.getFloat(KEY_RUNNING_AVG, 0.0f)
        val prevCount = prefs.getInt(KEY_TOTAL_COUNT, 0)

        val newCount = prevCount + 1
        val newAvg = prevAvg + (durationSeconds - prevAvg) / newCount.toFloat()

        prefs.edit()
            .putFloat(KEY_RUNNING_AVG, newAvg)
            .putInt(KEY_TOTAL_COUNT, newCount)
            .apply()

        return Pair(newAvg, newCount)
    }

    fun formatTimerString(elapsedSeconds: Long, avgSeconds: Float, totalCount: Int): String {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        val elapsedFormatted = String.format("%02d:%02d", min, sec)

        return if (totalCount == 0) {
            "⏱️ $elapsedFormatted"
        } else {
            val avgInt = avgSeconds.toInt()
            val avgMin = avgInt / 60
            val avgSec = avgInt % 60
            val avgFormatted = String.format("%02d:%02d", avgMin, avgSec)
            "⏱️ $elapsedFormatted / Est. $avgFormatted (#$totalCount)"
        }
    }
}
