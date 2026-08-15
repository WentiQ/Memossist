package com.example.apptempleate

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"
    private const val PREF_KEY = "app_theme"

    fun getSavedTheme(context: Context): String {
        val savedTheme = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
            .getString(PREF_KEY, THEME_SYSTEM)
        return when (savedTheme) {
            THEME_LIGHT, THEME_DARK, THEME_SYSTEM -> savedTheme
            else -> THEME_SYSTEM
        }
    }

    fun setTheme(context: Context, theme: String) {
        val normalizedTheme = when (theme) {
            THEME_LIGHT, THEME_DARK, THEME_SYSTEM -> theme
            else -> THEME_SYSTEM
        }
        context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, normalizedTheme)
            .apply()
        AppCompatDelegate.setDefaultNightMode(modeFromTheme(normalizedTheme))
        updateLauncherIcon(context)
    }

    fun isDarkTheme(context: Context): Boolean {
        return when (getSavedTheme(context)) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            THEME_SYSTEM -> {
                val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }

    fun getLogoDrawable(context: Context): Int {
        return if (isDarkTheme(context)) R.drawable.app_logo_dark else R.drawable.app_logo_light
    }

    fun updateLauncherIcon(context: Context) {
        try {
            val isDark = isDarkTheme(context)
            val pm = context.packageManager
            val lightAlias = android.content.ComponentName(context, "com.example.apptempleate.MainActivityLight")
            val darkAlias = android.content.ComponentName(context, "com.example.apptempleate.MainActivityDark")

            val enableAlias = if (isDark) darkAlias else lightAlias
            val disableAlias = if (isDark) lightAlias else darkAlias

            pm.setComponentEnabledSetting(
                enableAlias,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                disableAlias,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(modeFromTheme(getSavedTheme(context)))
        updateLauncherIcon(context)
    }

    fun modeFromTheme(theme: String): Int {
        return when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun displayName(theme: String): String {
        return when (theme) {
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            THEME_SYSTEM -> "System default"
            else -> "System default"
        }
    }
}
