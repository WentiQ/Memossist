package com.example.apptempleate

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AlarmAlertActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_SNOOZE_DELAY_MILLIS = 30_000L
    }

    private lateinit var tvAlarmTitle: TextView
    private lateinit var tvAlarmMessage: TextView
    private lateinit var tvAlarmEventTime: TextView
    private lateinit var btnAlarmGotIt: LinearLayout
    private lateinit var btnAlarmSnooze: LinearLayout
    private lateinit var btnAlarmDismiss: TextView

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var reminderId: String? = null
    private var hasUserActed = false
    private val autoSnoozeHandler = Handler(Looper.getMainLooper())
    private val autoSnoozeRunnable = Runnable { handleUnansweredAlert() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        // Show over lockscreen and turn screen on
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_alarm_alert)

        tvAlarmTitle = findViewById(R.id.tvAlarmTitle)
        tvAlarmMessage = findViewById(R.id.tvAlarmMessage)
        tvAlarmEventTime = findViewById(R.id.tvAlarmEventTime)
        btnAlarmGotIt = findViewById(R.id.btnAlarmGotIt)
        btnAlarmSnooze = findViewById(R.id.btnAlarmSnooze)
        btnAlarmDismiss = findViewById(R.id.btnAlarmDismiss)

        findViewById<ImageView>(R.id.ivAlarmLogo).setImageResource(ThemeManager.getLogoDrawable(this))

        val tvLiveTimeBadge: TextView = findViewById(R.id.tvLiveTimeBadge)
        val tvAlarmBadge: TextView = findViewById(R.id.tvAlarmBadge)
        val vLogoPulseRing: View = findViewById(R.id.vLogoPulseRing)

        // Live Header Time
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
        tvLiveTimeBadge.text = sdfTime.format(Date())

        // Pulsing Logo Ring Animation
        val animX = android.animation.ObjectAnimator.ofFloat(vLogoPulseRing, "scaleX", 1.0f, 1.45f).apply {
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            duration = 900L
        }
        val animY = android.animation.ObjectAnimator.ofFloat(vLogoPulseRing, "scaleY", 1.0f, 1.45f).apply {
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            duration = 900L
        }
        val animAlpha = android.animation.ObjectAnimator.ofFloat(vLogoPulseRing, "alpha", 0.6f, 0.1f).apply {
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            duration = 900L
        }
        android.animation.AnimatorSet().apply {
            playTogether(animX, animY, animAlpha)
            start()
        }

        reminderId = intent.getStringExtra("EXTRA_REMINDER_ID")
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Smart Reminder"
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Hey! You have an upcoming event."
        val eventTime = intent.getLongExtra("EXTRA_EVENT_TIME", System.currentTimeMillis())

        tvAlarmTitle.text = title
        tvAlarmMessage.text = message

        val sdfEvent = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        tvAlarmEventTime.text = "Scheduled for ${sdfEvent.format(Date(eventTime))}"

        val lowerTitle = title.lowercase()
        tvAlarmBadge.text = when {
            lowerTitle.contains("class") || lowerTitle.contains("lecture") || lowerTitle.contains("exam") -> "⚡ CLASS REMINDER"
            lowerTitle.contains("doctor") || lowerTitle.contains("hospital") || lowerTitle.contains("medicine") -> "🏥 HEALTH & DOCTOR"
            lowerTitle.contains("meet") || lowerTitle.contains("interview") || lowerTitle.contains("call") -> "💼 MEETING REMINDER"
            else -> "🚨 SMART REMINDER"
        }

        startAlarmSoundAndVibration()
        autoSnoozeHandler.postDelayed(autoSnoozeRunnable, AUTO_SNOOZE_DELAY_MILLIS)

        btnAlarmGotIt.setOnClickListener {
            registerUserInteraction()
            stopAlarmSoundAndVibration()
            reminderId?.let { id ->
                ReminderRepository.toggleReminderCompleted(this, id)
                Toast.makeText(this, "Marked as completed!", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        btnAlarmSnooze.setOnClickListener {
            registerUserInteraction()
            stopAlarmSoundAndVibration()
            snoozeReminder(10)
            Toast.makeText(this, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnAlarmDismiss.setOnClickListener {
            registerUserInteraction()
            stopAlarmSoundAndVibration()
            finish()
        }
    }

    private fun startAlarmSoundAndVibration() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(longArrayOf(0, 600, 400, 600, 400), 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSoundAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snoozeReminder(minutes: Int) {
        val rId = reminderId ?: return
        val list = ReminderRepository.loadAllReminders(this)
        val item = list.find { it.id == rId } ?: return

        val snoozeTime = System.currentTimeMillis() + minutes * 60_000L
        val snoozeTrigger = ReminderTrigger(
            triggerId = "TRG_SNZ_${UUID.randomUUID().toString().take(6)}",
            reminderId = rId,
            triggerTimeMillis = snoozeTime,
            type = "CUSTOM",
            deliveryStyle = "FULLSCREEN_ALARM",
            humanoidMessage = "⏰ Snoozed Reminder: '${item.title}' is starting soon!"
        )
        item.triggers.add(snoozeTrigger)
        ReminderRepository.addOrUpdateReminder(this, item)
    }

    private fun registerUserInteraction() {
        hasUserActed = true
        autoSnoozeHandler.removeCallbacks(autoSnoozeRunnable)
        reminderId?.let { ReminderRepository.resetUnansweredFullscreenAlertCount(this, it) }
    }

    private fun handleUnansweredAlert() {
        if (hasUserActed || isFinishing) return

        stopAlarmSoundAndVibration()
        val rId = reminderId
        if (rId != null && !ReminderRepository.handleUnansweredFullscreenAlert(this, rId)) {
            sendUnansweredReminderNotification(rId)
        }
        finish()
    }

    private fun sendUnansweredReminderNotification(rId: String) {
        sendBroadcast(Intent(this, ReminderReceiver::class.java).apply {
            action = "com.example.apptempleate.ACTION_TRIGGER_REMINDER"
            putExtra("EXTRA_REMINDER_ID", rId)
            putExtra("EXTRA_TITLE", "Reminder still needs attention")
            putExtra(
                "EXTRA_MESSAGE",
                "${tvAlarmTitle.text}: full-screen alerts were paused after 3 unanswered reminders."
            )
            putExtra("EXTRA_DELIVERY_STYLE", "NOTIFICATION")
            putExtra("EXTRA_IMPORTANCE", "MEDIUM")
            putExtra("EXTRA_EVENT_TIME", intent.getLongExtra("EXTRA_EVENT_TIME", System.currentTimeMillis()))
        })
    }

    override fun onBackPressed() {
        registerUserInteraction()
        super.onBackPressed()
    }

    override fun onDestroy() {
        autoSnoozeHandler.removeCallbacks(autoSnoozeRunnable)
        super.onDestroy()
        stopAlarmSoundAndVibration()
    }
}
