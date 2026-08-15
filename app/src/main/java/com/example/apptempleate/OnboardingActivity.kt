package com.example.apptempleate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class OnboardingActivity : AppCompatActivity() {

    // Step Progress Pills
    private lateinit var pillStep1: LinearLayout
    private lateinit var tvStep1Num: TextView
    private lateinit var pillStep2: LinearLayout
    private lateinit var tvStep2Num: TextView
    private lateinit var tvStep2Text: TextView
    private lateinit var pillStep3: LinearLayout
    private lateinit var tvStep3Num: TextView
    private lateinit var tvStep3Text: TextView
    private lateinit var pillStep4: LinearLayout
    private lateinit var tvStep4Num: TextView
    private lateinit var tvStep4Text: TextView

    // Step 1 Views
    private lateinit var step1Container: LinearLayout
    private lateinit var rlAvatarContainer: RelativeLayout
    private lateinit var tvAvatarInitials: TextView
    private lateinit var ivProfilePic: ImageView
    private lateinit var btnChangeAvatar: ImageView
    private lateinit var etUserName: EditText
    private lateinit var tvNameError: TextView
    private lateinit var btnStep1Next: AppCompatButton

    // Step 2 Views (Background LLM & OEM Battery Setup)
    private lateinit var step2Container: LinearLayout
    private lateinit var btnOnboardingNavBattery: AppCompatButton
    private lateinit var btnOnboardingNavSleep: AppCompatButton
    private lateinit var btnOnboardingNavAutoLaunch: AppCompatButton
    private lateinit var btnStep2Next: AppCompatButton

    // Step 3 Views (Security)
    private lateinit var step3Container: LinearLayout
    private lateinit var switchEnableAppLock: SwitchCompat
    private lateinit var btnStep3Next: AppCompatButton

    // Step 4 Views (Restore & Finish)
    private lateinit var step4Container: LinearLayout
    private lateinit var btnImportDataNow: LinearLayout
    private lateinit var btnFinishOnboarding: AppCompatButton

    private lateinit var prefs: SharedPreferences
    private var selectedAvatarUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Non-persistable URI fallback
            }
            selectedAvatarUri = it
            displayAvatar(it)
        }
    }



    private val openImportFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val jsonStr = contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                if (!jsonStr.isNullOrBlank()) {
                    val result = DataBackupRepository.importAllData(this, jsonStr)

                    AlertDialog.Builder(this)
                        .setTitle("Data Imported Successfully 🎉")
                        .setMessage("Imported items added to your app:\n\n" +
                                "• ${result.conversationCount} Conversations\n" +
                                "• ${result.memoryCount} Memory Experiences (prefixed with 'I_')\n" +
                                "• ${result.edgeCount} DAG Connections\n" +
                                "• ${result.reminderCount} Reminders\n" +
                                "• ${result.notificationCount} Notifications")
                        .setPositiveButton("Awesome! Go to Workspace") { dialog, _ ->
                            dialog.dismiss()
                            completeOnboardingAndLaunchMain()
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Selected backup file was empty.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_onboarding)

        // Theme Adaptive App Logo at Top Header
        findViewById<ImageView>(R.id.ivHeaderLogo)?.setImageResource(ThemeManager.getLogoDrawable(this))

        prefs = getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)

        // Progress Pills Bindings
        pillStep1 = findViewById(R.id.pillStep1)
        tvStep1Num = findViewById(R.id.tvStep1Num)
        pillStep2 = findViewById(R.id.pillStep2)
        tvStep2Num = findViewById(R.id.tvStep2Num)
        tvStep2Text = findViewById(R.id.tvStep2Text)
        pillStep3 = findViewById(R.id.pillStep3)
        tvStep3Num = findViewById(R.id.tvStep3Num)
        tvStep3Text = findViewById(R.id.tvStep3Text)
        pillStep4 = findViewById(R.id.pillStep4)
        tvStep4Num = findViewById(R.id.tvStep4Num)
        tvStep4Text = findViewById(R.id.tvStep4Text)

        // Step 1 Bindings
        step1Container = findViewById(R.id.step1Container)
        rlAvatarContainer = findViewById(R.id.rlAvatarContainer)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        ivProfilePic = findViewById(R.id.ivProfilePic)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        etUserName = findViewById(R.id.etUserName)
        tvNameError = findViewById(R.id.tvNameError)
        btnStep1Next = findViewById(R.id.btnStep1Next)

        // Step 2 Bindings (Battery & Background Setup)
        step2Container = findViewById(R.id.step2Container)
        btnOnboardingNavBattery = findViewById(R.id.btnOnboardingNavBattery)
        btnOnboardingNavSleep = findViewById(R.id.btnOnboardingNavSleep)
        btnOnboardingNavAutoLaunch = findViewById(R.id.btnOnboardingNavAutoLaunch)
        btnStep2Next = findViewById(R.id.btnStep2Next)

        // Step 3 Bindings (Security)
        step3Container = findViewById(R.id.step3Container)
        switchEnableAppLock = findViewById(R.id.switchEnableAppLock)
        btnStep3Next = findViewById(R.id.btnStep3Next)

        // Step 4 Bindings (Restore)
        step4Container = findViewById(R.id.step4Container)
        btnImportDataNow = findViewById(R.id.btnImportDataNow)
        btnFinishOnboarding = findViewById(R.id.btnFinishOnboarding)

        // Pre-fill existing name if present
        val existingName = prefs.getString("user_name", "")
        if (!existingName.isNullOrEmpty()) {
            etUserName.setText(existingName)
            tvAvatarInitials.text = existingName.take(1).uppercase()
        }

        // Live text watcher to update initials avatar dynamically
        etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                if (input.isNotEmpty()) {
                    tvNameError.visibility = View.GONE
                    if (selectedAvatarUri == null) {
                        tvAvatarInitials.text = input.take(1).uppercase()
                    }
                } else {
                    tvAvatarInitials.text = "U"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Avatar Click Listeners
        val pickAvatarListener = View.OnClickListener {
            selectImageLauncher.launch("image/*")
        }
        rlAvatarContainer.setOnClickListener(pickAvatarListener)
        btnChangeAvatar.setOnClickListener(pickAvatarListener)

        // Step 1 -> Step 2 Navigation
        btnStep1Next.setOnClickListener {
            val nameInput = etUserName.text.toString().trim()
            if (nameInput.isEmpty()) {
                tvNameError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            tvNameError.visibility = View.GONE

            prefs.edit().putString("user_name", nameInput).apply()
            selectedAvatarUri?.let { uri ->
                prefs.edit().putString("user_avatar_uri", uri.toString()).apply()
            }

            showStep2()
        }

        // Step 2 Direct Navigation Buttons
        btnOnboardingNavBattery.setOnClickListener {
            BatteryOptimizationHelper.openAppBatterySettings(this)
        }

        btnOnboardingNavSleep.setOnClickListener {
            BatteryOptimizationHelper.openSleepStandbySettings(this)
        }

        btnOnboardingNavAutoLaunch.setOnClickListener {
            BatteryOptimizationHelper.openAutoLaunchSettings(this)
        }

        // Step 2 -> Step 3 Navigation
        btnStep2Next.setOnClickListener {
            showStep3()
        }

        // Step 3 App Lock Listener
        switchEnableAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!AppLockManager.isDeviceSecure(this)) {
                    switchEnableAppLock.isChecked = false
                    AlertDialog.Builder(this)
                        .setTitle("Phone Screen Lock Required 🔒")
                        .setMessage("Please set up a PIN, pattern, or fingerprint lock in your phone's system settings first.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnCheckedChangeListener
                }

                AppLockManager.showBiometricPrompt(
                    activity = this,
                    title = "Enable App Lock",
                    subtitle = "Authenticate to enable App Lock for Memossist",
                    onSuccess = {
                        AppLockManager.setAppLockEnabled(this, true)
                        AppLockManager.isSessionAuthenticated = true
                        AppLockManager.applySecureFlag(this)
                        switchEnableAppLock.isChecked = true
                        Toast.makeText(this, "App Lock enabled", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        switchEnableAppLock.isChecked = false
                        AppLockManager.setAppLockEnabled(this, false)
                    }
                )
            } else {
                AppLockManager.setAppLockEnabled(this, false)
                AppLockManager.applySecureFlag(this)
            }
        }

        // Step 3 -> Step 4 Navigation
        btnStep3Next.setOnClickListener {
            showStep4()
        }

        // Step 4 Actions
        btnImportDataNow.setOnClickListener {
            openImportFileLauncher.launch(arrayOf("application/json", "*/*"))
        }

        btnFinishOnboarding.setOnClickListener {
            completeOnboardingAndLaunchMain()
        }
    }

    private fun showStep2() {
        animateViewTransition(step1Container, step2Container)

        // Pill 1 -> Checked
        pillStep1.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep1.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep1Num.text = "✓"
        tvStep1Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep1Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        findViewById<TextView>(R.id.tvStep1Text).setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        // Pill 2 -> Active
        pillStep2.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep2.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep2Num.setBackgroundResource(R.drawable.bg_circle_icon_button)
        tvStep2Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep2Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvStep2Text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }

    private fun showStep3() {
        animateViewTransition(step2Container, step3Container)

        // Pill 2 -> Checked
        pillStep2.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep2.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep2Num.text = "✓"
        tvStep2Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep2Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvStep2Text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        // Pill 3 -> Active
        pillStep3.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep3.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep3Num.setBackgroundResource(R.drawable.bg_circle_icon_button)
        tvStep3Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep3Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvStep3Text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }

    private fun showStep4() {
        animateViewTransition(step3Container, step4Container)

        // Pill 3 -> Checked
        pillStep3.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep3.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep3Num.text = "✓"
        tvStep3Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep3Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvStep3Text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        // Pill 4 -> Active
        pillStep4.setBackgroundResource(R.drawable.bg_tag_rounded)
        pillStep4.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_card_border)
        tvStep4Num.setBackgroundResource(R.drawable.bg_circle_icon_button)
        tvStep4Num.backgroundTintList = ContextCompat.getColorStateList(this, R.color.app_tag_background)
        tvStep4Num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvStep4Text.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }

    private fun animateViewTransition(fromView: View, toView: View) {
        val fadeOut = AlphaAnimation(1.0f, 0.0f).apply { duration = 150 }
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply { duration = 200 }

        fromView.startAnimation(fadeOut)
        fromView.visibility = View.GONE

        toView.visibility = View.VISIBLE
        toView.startAnimation(fadeIn)
    }

    private fun displayAvatar(uri: Uri) {
        try {
            ivProfilePic.setImageURI(uri)
            ivProfilePic.visibility = View.VISIBLE
            tvAvatarInitials.visibility = View.GONE
        } catch (e: Exception) {
            ivProfilePic.visibility = View.GONE
            tvAvatarInitials.visibility = View.VISIBLE
        }
    }

    private fun completeOnboardingAndLaunchMain() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
        AppLockManager.isSessionAuthenticated = true
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
