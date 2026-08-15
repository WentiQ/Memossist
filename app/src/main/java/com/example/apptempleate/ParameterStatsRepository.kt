package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Tracks performance and latency statistics for the 2nd LLM
 * parameter evaluation engine (importance, confidence, stability scoring).
 */
object ParameterStatsRepository {

    private const val PREF_NAME = "memossist_param_stats"
    private const val KEY_RUNNING_AVG_MS = "param_running_avg_ms"
    private const val KEY_LAST_DURATION_MS = "param_last_duration_ms"
    private const val KEY_TOTAL_EVALUATIONS = "param_total_evaluations_count"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun recordEvaluation(context: Context, durationMs: Long) {
        if (durationMs <= 0) return

        val prefs = getPrefs(context)
        val prevAvgMs = prefs.getFloat(KEY_RUNNING_AVG_MS, 0.0f)
        val prevCount = prefs.getInt(KEY_TOTAL_EVALUATIONS, 0)
        val newCount = prevCount + 1

        val newAvgMs = if (prevCount == 0 || prevAvgMs <= 0.0f) {
            durationMs.toFloat()
        } else if (newCount <= 5) {
            prevAvgMs + (durationMs.toFloat() - prevAvgMs) / newCount.toFloat()
        } else {
            (0.70f * prevAvgMs) + (0.30f * durationMs.toFloat())
        }

        prefs.edit()
            .putFloat(KEY_RUNNING_AVG_MS, newAvgMs)
            .putFloat(KEY_LAST_DURATION_MS, durationMs.toFloat())
            .putInt(KEY_TOTAL_EVALUATIONS, newCount)
            .apply()
    }

    fun getAvgDurationSeconds(context: Context): Float {
        val avgMs = getPrefs(context).getFloat(KEY_RUNNING_AVG_MS, 0.0f)
        return avgMs / 1000.0f
    }

    fun getLastDurationSeconds(context: Context): Float {
        val lastMs = getPrefs(context).getFloat(KEY_LAST_DURATION_MS, 0.0f)
        return lastMs / 1000.0f
    }

    fun getTotalEvaluationsCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_TOTAL_EVALUATIONS, 0)
    }

    fun formatDuration(durationSeconds: Float): String {
        return when {
            durationSeconds <= 0f -> "--"
            durationSeconds < 1.0f -> String.format(Locale.US, "%.0f ms", durationSeconds * 1000f)
            else -> String.format(Locale.US, "%.2f s", durationSeconds)
        }
    }
}
