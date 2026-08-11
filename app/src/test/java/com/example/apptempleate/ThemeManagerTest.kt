package com.example.apptempleate

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeManagerTest {
    @Test
    fun themeModeMapping_isCorrect() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeManager.modeFromTheme(ThemeManager.THEME_LIGHT))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeManager.modeFromTheme(ThemeManager.THEME_DARK))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeManager.modeFromTheme(ThemeManager.THEME_SYSTEM))
    }

    @Test
    fun displayName_isFriendly() {
        assertEquals("Light", ThemeManager.displayName(ThemeManager.THEME_LIGHT))
        assertEquals("Dark", ThemeManager.displayName(ThemeManager.THEME_DARK))
        assertEquals("System default", ThemeManager.displayName(ThemeManager.THEME_SYSTEM))
    }
}
