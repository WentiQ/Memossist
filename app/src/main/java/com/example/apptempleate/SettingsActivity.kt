package com.example.apptempleate

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnClearCache: LinearLayout
    private lateinit var btnDeleteUserData: LinearLayout
    private lateinit var btnOpenModelMarketplace: LinearLayout
    private lateinit var tvSettingsModelIcon: TextView
    private lateinit var tvSettingsActiveModelName: TextView
    private lateinit var tvSettingsActiveModelBadge: TextView

    private lateinit var rlAvatarContainer: RelativeLayout
    private lateinit var tvAvatarInitials: TextView
    private lateinit var ivProfilePic: ImageView
    private lateinit var btnChangeAvatar: ImageView

    private lateinit var tvUserName: TextView
    private lateinit var btnEditName: ImageButton
    private lateinit var btnMorningTime: LinearLayout
    private lateinit var tvMorningTimeValue: TextView
    private lateinit var btnManageReminders: LinearLayout
    private lateinit var btnExportAllData: LinearLayout
    private lateinit var btnImportAllData: LinearLayout
    private lateinit var btnOpenDeviceHelp: LinearLayout
    private lateinit var switchAppLock: androidx.appcompat.widget.SwitchCompat
    private var pendingAppLockEnableState: Boolean = false

    private lateinit var prefs: SharedPreferences

    private val deviceCredentialAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            executeDeleteAllUserData()
        } else {
            Toast.makeText(this, "Authentication failed. Data preserved.", Toast.LENGTH_SHORT).show()
        }
    }

    private val appLockToggleAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AppLockManager.setAppLockEnabled(this, pendingAppLockEnableState)
            AppLockManager.isSessionAuthenticated = true
            AppLockManager.applySecureFlag(this)
            switchAppLock.isChecked = pendingAppLockEnableState
            val statusMsg = if (pendingAppLockEnableState) "App Lock enabled" else "App Lock disabled"
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show()
        } else {
            switchAppLock.isChecked = AppLockManager.isAppLockEnabled(this)
            Toast.makeText(this, "Authentication cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Non-persistable URI fallbacks fine
            }
            saveAvatarUri(it.toString())
            displayAvatar(it)
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        }
    }

    private val createExportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val jsonStr = DataBackupRepository.exportAllData(this)
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                AlertDialog.Builder(this)
                    .setTitle("Data Export Complete 📦")
                    .setMessage("All your conversations, memories, DAG graphs, and reminders have been exported successfully to your JSON backup file.")
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
                    loadUserProfileData()

                    AlertDialog.Builder(this)
                        .setTitle("Data Imported & Merged Successfully 🎉")
                        .setMessage("Imported items added to your app:\n\n" +
                                "• ${result.conversationCount} Conversations\n" +
                                "• ${result.memoryCount} Memory Experiences (prefixed with 'I_')\n" +
                                "• ${result.edgeCount} DAG Connections\n" +
                                "• ${result.reminderCount} Reminders\n" +
                                "• ${result.notificationCount} Notifications")
                        .setPositiveButton("Awesome") { dialog, _ ->
                            dialog.dismiss()
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

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)

        btnBack = findViewById(R.id.btnBack)
        btnClearCache = findViewById(R.id.btnClearCache)
        btnDeleteUserData = findViewById(R.id.btnDeleteUserData)
        btnOpenModelMarketplace = findViewById(R.id.btnOpenModelMarketplace)
        tvSettingsModelIcon = findViewById(R.id.tvSettingsModelIcon)
        tvSettingsActiveModelName = findViewById(R.id.tvSettingsActiveModelName)
        tvSettingsActiveModelBadge = findViewById(R.id.tvSettingsActiveModelBadge)

        rlAvatarContainer = findViewById(R.id.rlAvatarContainer)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        ivProfilePic = findViewById(R.id.ivProfilePic)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)

        tvUserName = findViewById(R.id.tvUserName)
        btnEditName = findViewById(R.id.btnEditName)
        btnMorningTime = findViewById(R.id.btnMorningTime)
        tvMorningTimeValue = findViewById(R.id.tvMorningTimeValue)
        btnManageReminders = findViewById(R.id.btnManageReminders)

        btnExportAllData = findViewById(R.id.btnExportAllData)
        btnImportAllData = findViewById(R.id.btnImportAllData)
        btnOpenDeviceHelp = findViewById(R.id.btnOpenDeviceHelp)
        switchAppLock = findViewById(R.id.switchAppLock)

        switchAppLock.isChecked = AppLockManager.isAppLockEnabled(this)
        switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked == AppLockManager.isAppLockEnabled(this)) return@setOnCheckedChangeListener

            if (isChecked && !AppLockManager.isDeviceSecure(this)) {
                switchAppLock.isChecked = false
                AlertDialog.Builder(this)
                    .setTitle("Phone Screen Lock Required 🔒")
                    .setMessage("To enable App Lock, please set up a PIN, pattern, or fingerprint lock in your phone's system settings first.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnCheckedChangeListener
            }

            pendingAppLockEnableState = isChecked
            val intent = AppLockManager.createDeviceCredentialIntent(
                this,
                if (isChecked) "Confirm App Lock Activation" else "Confirm App Lock Deactivation",
                if (isChecked) "Authenticate with your phone lock to enable App Lock." else "Authenticate with your phone lock to disable App Lock."
            )
            if (intent != null) {
                appLockToggleAuthLauncher.launch(intent)
            } else {
                AppLockManager.setAppLockEnabled(this, isChecked)
                AppLockManager.applySecureFlag(this)
                Toast.makeText(this, if (isChecked) "App Lock enabled" else "App Lock disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Load saved user profile data
        loadUserProfileData()
        updateMorningTimeDisplay()

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        btnMorningTime.setOnClickListener {
            showMorningTimePickerDialog()
        }

        btnManageReminders.setOnClickListener {
            val intent = Intent(this, RemindersActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        btnOpenModelMarketplace.setOnClickListener {
            val intent = Intent(this, ModelMarketplaceActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        btnExportAllData.setOnClickListener {
            createExportFileLauncher.launch("memossist_backup_${System.currentTimeMillis()}.json")
        }

        btnImportAllData.setOnClickListener {
            openImportFileLauncher.launch(arrayOf("application/json", "*/*"))
        }

        btnOpenDeviceHelp.setOnClickListener {
            val intent = Intent(this, DeviceHelpActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        btnClearCache.setOnClickListener {
            Toast.makeText(this, "Cached index data cleared", Toast.LENGTH_SHORT).show()
        }

        btnDeleteUserData.setOnClickListener {
            promptDeleteUserDataWithAuth()
        }

        // Tap avatar container to pick profile picture
        val pickAvatarListener = View.OnClickListener {
            selectImageLauncher.launch("image/*")
        }
        rlAvatarContainer.setOnClickListener(pickAvatarListener)
        btnChangeAvatar.setOnClickListener(pickAvatarListener)

        // Tap edit pencil or name to open edit name dialog
        val editNameListener = View.OnClickListener {
            showEditNameDialog()
        }
        btnEditName.setOnClickListener(editNameListener)
        tvUserName.setOnClickListener(editNameListener)
    }

    override fun onResume() {
        super.onResume()
        updateActiveModelDisplay()
    }

    private fun updateActiveModelDisplay() {
        val currentModel = NoeonAiEngine.getSelectedModel(this)
        tvSettingsModelIcon.text = currentModel.icon
        tvSettingsActiveModelName.text = currentModel.name
        tvSettingsActiveModelBadge.text = currentModel.badge
    }

    private fun loadUserProfileData() {
        val savedName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"
        updateNameDisplay(savedName)

        val savedAvatarUriStr = prefs.getString("user_avatar_uri", null)
        if (!savedAvatarUriStr.isNull_or_Empty()) {
            displayAvatar(Uri.parse(savedAvatarUriStr))
        } else {
            showInitialsAvatar(savedName)
        }
    }

    private fun showEditNameDialog() {
        val currentName = tvUserName.text.toString()
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_username, null)
        val etDialogUserName: EditText = dialogView.findViewById(R.id.etDialogUserName)
        val btnCancel: TextView = dialogView.findViewById(R.id.btnDialogCancel)
        val btnSave: TextView = dialogView.findViewById(R.id.btnDialogSave)

        etDialogUserName.setText(currentName)
        etDialogUserName.setSelection(currentName.length)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newName = etDialogUserName.text.toString().trim()
            if (newName.isNotEmpty()) {
                prefs.edit().putString("user_name", newName).apply()
                updateNameDisplay(newName)
                Toast.makeText(this, "Name updated to $newName", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateNameDisplay(name: String) {
        tvUserName.text = name
        tvAvatarInitials.text = if (name.isNotEmpty()) name.take(1).uppercase() else "D"
    }

    private fun saveAvatarUri(uriStr: String) {
        prefs.edit().putString("user_avatar_uri", uriStr).apply()
    }

    private fun displayAvatar(uri: Uri) {
        try {
            ivProfilePic.setImageURI(uri)
            ivProfilePic.visibility = View.VISIBLE
            tvAvatarInitials.visibility = View.GONE
        } catch (e: Exception) {
            val savedName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"
            showInitialsAvatar(savedName)
        }
    }

    private fun showInitialsAvatar(name: String) {
        ivProfilePic.visibility = View.GONE
        tvAvatarInitials.visibility = View.VISIBLE
        tvAvatarInitials.text = if (name.isNotEmpty()) name.take(1).uppercase() else "D"
    }

    private fun String?.isNull_or_Empty(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    private fun promptDeleteUserDataWithAuth() {
        AlertDialog.Builder(this)
            .setTitle("Delete All User Data?")
            .setMessage("This action will permanently delete all chat history, memory experiences, and DAG graph connections.\n\nYour username, profile picture, and downloaded AI models will be preserved.\n\nPhone screen lock authentication is required to proceed.")
            .setPositiveButton("Authenticate & Delete") { dialog, _ ->
                dialog.dismiss()
                authenticateDeviceLockAndExecute()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun authenticateDeviceLockAndExecute() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Confirm Data Deletion",
                "Authenticate with your phone screen lock to permanently delete all chats and memory vault data."
            )
            if (intent != null) {
                deviceCredentialAuthLauncher.launch(intent)
            } else {
                executeDeleteAllUserData()
            }
        } else {
            executeDeleteAllUserData()
        }
    }

    private fun executeDeleteAllUserData() {
        // 1. Wipe chat history
        ChatRepository.clearAllConversations(this)

        // 2. Wipe memory vault experiences
        MemoryVaultRepository.clearAllMemories(this)

        // 3. Wipe DAG graph connection edges
        ExperienceDagRepository.clearAllEdges(this)

        Toast.makeText(this, "All user chats, memory experiences & DAG graph data deleted.", Toast.LENGTH_LONG).show()
    }

    private fun updateMorningTimeDisplay() {
        val hour = prefs.getInt("morning_briefing_hour", 7)
        val ampm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        tvMorningTimeValue.text = "$displayHour:00 $ampm (${if (hour == 7) "Default" else "Custom"})"
    }

    private fun showMorningTimePickerDialog() {
        val options = arrayOf("6:00 AM", "7:00 AM (Default)", "8:00 AM", "9:00 AM", "10:00 AM")
        val hours = intArrayOf(6, 7, 8, 9, 10)
        val currentHour = prefs.getInt("morning_briefing_hour", 7)
        val selectedIndex = hours.indexOf(currentHour).coerceAtLeast(1)

        AlertDialog.Builder(this)
            .setTitle("Select Morning Start-of-Day Briefing Time")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val chosenHour = hours[which]
                prefs.edit().putInt("morning_briefing_hour", chosenHour).apply()
                updateMorningTimeDisplay()
                Toast.makeText(this, "Morning briefing set to ${options[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
