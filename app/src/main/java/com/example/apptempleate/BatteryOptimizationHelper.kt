package com.example.apptempleate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Requests the OS exemption required for long-running local LLM inference. */
object BatteryOptimizationHelper {
    fun requestExemptionIfNeeded(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
        try {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        } catch (_: Exception) {
            // The foreground service and wake lock remain the fallback.
        }
    }
}
