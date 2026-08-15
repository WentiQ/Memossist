package com.example.apptempleate

import android.app.Application

class MemossistApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply user-configured theme at the earliest possible stage of app lifecycle
        ThemeManager.applySavedTheme(this)
        AppLifecycleTracker.init(this)

        // Initialize Memory Decay & Forgetting System (Periodic WorkManager worker & Startup check)
        MemoryDecayManager.schedulePeriodicDecay(this)
        MemoryDecayManager.runImmediateDecayAsync(this)

        // Pre-warm offline ML classification models in memory
        Thread {
            try {
                ReminderSentenceClassifier.init(this)
                NonReminderIntentClassifier.init(this)
            } catch (e: Exception) {
                android.util.Log.e("MemossistApp", "Error pre-warming local ML models", e)
            }
        }.start()
    }
}
