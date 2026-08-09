package com.example.apptempleate

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager

object AppLockManager {

    private const val PREFS_NAME = "MemossistPrefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

    var isSessionAuthenticated: Boolean = false

    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    /**
     * Applies FLAG_SECURE if App Lock is enabled to hide window contents from Recent Tasks switcher
     * (PhonePe-style view protection and screen capture prevention).
     */
    fun applySecureFlag(activity: Activity) {
        try {
            if (isAppLockEnabled(activity)) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    fun createDeviceCredentialIntent(context: Context, title: String, description: String): Intent? {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return if (keyguardManager?.isDeviceSecure == true) {
            keyguardManager.createConfirmDeviceCredentialIntent(title, description)
        } else {
            null
        }
    }
}
