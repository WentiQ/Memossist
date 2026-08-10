package com.example.apptempleate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** Requests OS battery exemption and provides direct navigation to OEM & Android battery settings. */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestExemptionIfNeeded(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            Toast.makeText(context, "Battery optimization is already disabled for Memossist.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open battery optimization settings directly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 1. Settings -> Apps -> App management -> Memossist -> Battery usage -> Allow background activity / Don't optimize */
    fun openAppBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /** 2. Settings -> Battery -> More settings -> Turn off Sleep standby optimization */
    fun openSleepStandbySettings(context: Context) {
        val intentsToTry = listOf(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent("android.intent.action.POWER_USAGE_SUMMARY"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intentsToTry) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    /** 3. Settings -> Apps -> Auto launch -> Enable Memossist */
    fun openAutoLaunchSettings(context: Context) {
        val oemComponents = listOf(
            // Realme / OPPO (ColorOS / Oplus)
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgauge.PowerUsageModelActivity"),
            // Xiaomi / MIUI / HyperOS
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Vivo / FuntouchOS
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Huawei / Honor
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // Samsung
            ComponentName("com.samsung.android.looper", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )

        for (comp in oemComponents) {
            try {
                val intent = Intent().apply {
                    component = comp
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }

        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

