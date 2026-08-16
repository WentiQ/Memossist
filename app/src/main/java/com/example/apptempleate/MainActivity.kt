package com.example.apptempleate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnHamburger: ImageButton
    private lateinit var vSidebarUnreadBadge: View
    private lateinit var btnHeaderModelPicker: LinearLayout
    private lateinit var tvHeaderModelIcon: TextView
    private lateinit var tvHeaderModelName: TextView
    private lateinit var btnHeaderNotifications: ImageButton
    private lateinit var vHeaderUnreadBadge: View
    private lateinit var btnDeleteCurrentChat: ImageButton
    private lateinit var mainContentContainer: ConstraintLayout

    private lateinit var llGreetingContainer: LinearLayout
    private lateinit var tvGreetingTitle: TextView
    private lateinit var tvGreetingPrompt: TextView
    private lateinit var llWorkspaceRemindersSection: LinearLayout
    private lateinit var rvWorkspaceReminders: RecyclerView
    private lateinit var vWorkspaceMidGradientOverlay: View
    private lateinit var workspaceRemindersAdapter: WorkspaceRemindersAdapter

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var btnScrollToBottom: ImageButton

    private lateinit var etChatInput: EditText
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnLiveVoice: ImageButton

    // Splash / Logo Loading Screen & Dynamic Logo Views
    private lateinit var flSplashOverlay: FrameLayout
    private lateinit var ivSplashLogo: ImageView
    private lateinit var ivSidebarLogo: ImageView
    private lateinit var btnSplashUnlock: TextView

    // Sidebar Views
    private lateinit var btnSidebarNewChat: LinearLayout
    private lateinit var btnNavHome: LinearLayout
    private lateinit var btnNavReminders: LinearLayout
    private lateinit var btnNavVault: LinearLayout
    private lateinit var btnNavInsights: LinearLayout
    private lateinit var btnNavConnections: LinearLayout
    private lateinit var rvSidebarHistory: RecyclerView
    private lateinit var sidebarHistoryAdapter: SidebarHistoryAdapter
    private lateinit var llPinnedSettings: LinearLayout
    private lateinit var btnSidebarHelp: ImageButton

    private var currentConversation: Conversation? = null
    private var allConversations: MutableList<Conversation> = mutableListOf()

    private var isNewChatState = true
    private var newChatSessionTimestamp = 0L
    private var chatListScrollState: Parcelable? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private var touchStartX = 0f
    private var touchStartY = 0f

    // Attachment Preview & Selection Views
    private lateinit var llAttachmentPreviewContainer: LinearLayout
    private lateinit var rvAttachmentPreviews: RecyclerView
    private lateinit var attachmentPreviewAdapter: AttachmentPreviewAdapter
    private val pendingAttachments: MutableList<MediaAttachment> = mutableListOf()

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            for (uri in uris) {
                val attachment = AttachmentStorageHelper.saveUriToInternalStorage(this, uri)
                if (attachment != null) {
                    pendingAttachments.add(attachment)
                }
            }
            updateAttachmentPreviewUI()
        }
    }

    // Notification Permission Launcher for Android 13+ (API 33+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission needed for system status bar alerts", Toast.LENGTH_LONG).show()
        }
    }

    // Audio Record Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSilentSpeechToText()
        } else {
            Toast.makeText(this, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_launch", true)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        AppLifecycleTracker.init(application)

        // Remove window title & hide action bar completely
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)

        // Prompt for system notifications permission on Android 13+
        checkAndRequestNotificationPermission()

        // Initialize Navigation & Main Layout Views
        drawerLayout = findViewById(R.id.drawerLayout)
        btnHamburger = findViewById(R.id.btnHamburger)
        vSidebarUnreadBadge = findViewById(R.id.vSidebarUnreadBadge)
        btnHeaderModelPicker = findViewById(R.id.btnHeaderModelPicker)
        tvHeaderModelIcon = findViewById(R.id.tvHeaderModelIcon)
        tvHeaderModelName = findViewById(R.id.tvHeaderModelName)
        btnHeaderNotifications = findViewById(R.id.btnHeaderNotifications)
        vHeaderUnreadBadge = findViewById(R.id.vHeaderUnreadBadge)
        btnDeleteCurrentChat = findViewById(R.id.btnDeleteCurrentChat)
        mainContentContainer = findViewById(R.id.mainContentContainer)

        // Initialize Splash Overlay & Logo Views
        flSplashOverlay = findViewById<FrameLayout>(R.id.flSplashOverlay)
        ivSplashLogo = findViewById(R.id.ivSplashLogo)
        ivSidebarLogo = findViewById(R.id.ivSidebarLogo)
        btnSplashUnlock = findViewById(R.id.btnSplashUnlock)
        updateAppLogos()

        btnSplashUnlock.setOnClickListener {
            checkAndPromptAppLockIfRequired()
        }

        // Initialize Sidebar Views
        btnSidebarNewChat = findViewById(R.id.btnSidebarNewChat)
        btnNavHome = findViewById(R.id.btnNavHome)
        btnNavReminders = findViewById(R.id.btnNavReminders)
        btnNavVault = findViewById(R.id.btnNavVault)
        btnNavInsights = findViewById(R.id.btnNavInsights)
        btnNavConnections = findViewById(R.id.btnNavConnections)
        rvSidebarHistory = findViewById(R.id.rvSidebarHistory)
        llPinnedSettings = findViewById(R.id.llPinnedSettings)
        btnSidebarHelp = findViewById(R.id.btnSidebarHelp)

        // Initialize Greeting Views
        llGreetingContainer = findViewById(R.id.llGreetingContainer)
        tvGreetingTitle = findViewById(R.id.tvGreetingTitle)
        tvGreetingPrompt = findViewById(R.id.tvGreetingPrompt)

        // Initialize Active Chat RecyclerView
        rvChatMessages = findViewById(R.id.rvChatMessages)
        chatAdapter = ChatAdapter(
            onMessageLongClick = { message ->
                if (!message.isUser && !message.isThinking && !message.awaitingTypeConfirmation) {
                    val logText = message.debugLog ?: "=== 🧠 MEMOSSIST AI DIAGNOSTIC LOGS ===\n" +
                            "Model Engine: ${NoeonAiEngine.getSelectedModel(this).name}\n\n" +
                            "=== CLEAN HUMANOID ANSWER ===\n${message.text}\n\n" +
                            "=== DAG NODE CONNECTION STRENGTH CALCULATIONS ===\n" +
                            "Formula Applied: S_ij_new = S_ij_old + (|Q ∩ Ni ∩ Nj| × t) / N\n" +
                            "POS Sets Filtered: Nouns, Verbs, Adjectives, Adverbs"

                    val logsSheet = AiMessageLogsBottomSheet(logText)
                    logsSheet.show(supportFragmentManager, "AiMessageLogsBottomSheet")
                }
            },
            onUserMessageLongClick = { userMessage ->
                showEditLastMessageDialog(userMessage)
            },
            onChangeTypeClicked = { aiMessage ->
                showInFlightTypeCorrectionBottomSheet(aiMessage)
            },
            onConfirmationRequestClicked = { aiMessage ->
                showLowConfidenceSelectorBottomSheet(aiMessage)
            }
        )
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = chatAdapter

        // Smooth Keyboard & Resize Scroll Adjuster (Only trigger on actual soft keyboard pop-up)
        rvChatMessages.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (oldBottom - bottom > 200 && chatAdapter.itemCount > 0) {
                rvChatMessages.post {
                    rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }

        // Floating Scroll to Bottom Button Setup
        btnScrollToBottom = findViewById(R.id.btnScrollToBottom)
        btnScrollToBottom.setOnClickListener {
            if (chatAdapter.itemCount > 0) {
                rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
            hideScrollToBottomButton()
        }

        rvChatMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateScrollToBottomButtonVisibility()
            }
        })


        // Initialize Attachment Preview Views
        llAttachmentPreviewContainer = findViewById(R.id.llAttachmentPreviewContainer)
        rvAttachmentPreviews = findViewById(R.id.rvAttachmentPreviews)
        attachmentPreviewAdapter = AttachmentPreviewAdapter { itemToRemove ->
            pendingAttachments.remove(itemToRemove)
            updateAttachmentPreviewUI()
        }
        rvAttachmentPreviews.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAttachmentPreviews.adapter = attachmentPreviewAdapter

        // Initialize Sidebar History RecyclerView (Only history scrolls!)
        sidebarHistoryAdapter = SidebarHistoryAdapter(
            onItemClick = { conv ->
                loadConversationIntoView(conv)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onItemLongClick = { conv ->
                showChatOptionsDialog(conv)
            }
        )
        rvSidebarHistory.layoutManager = LinearLayoutManager(this)
        rvSidebarHistory.adapter = sidebarHistoryAdapter

        // Initialize Chat Input Views
        etChatInput = findViewById(R.id.etChatInput)
        btnPlus = findViewById(R.id.btnPlus)
        btnMic = findViewById(R.id.btnMic)
        btnLiveVoice = findViewById(R.id.btnLiveVoice)

        // Default to Fresh New Chat Greeting Session on App Start
        startNewConversationSession()

        // Set Dynamic Premium Time-of-Day Greeting with User Name
        updateGreetingText()

        // Initialize Workspace Upcoming Reminders (Next 24h)
        setupWorkspaceReminders()

        // Load Saved Conversations & Populate Sidebar Recent History List
        refreshSidebarHistory()

        // Setup Touch Swipe Gesture Detection
        setupSwipeTouchListener()

        // Hamburger Menu Click -> Smoothly open side menu drawer
        btnHamburger.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Header Model Picker Pill Click -> Open Gemini-Style Quick Model Switcher Sheet
        btnHeaderModelPicker.setOnClickListener {
            showQuickModelPickerSheet()
        }

        // Header Notification Center Button -> Open Notifications Popup Sheet (Last 30 days)
        btnHeaderNotifications.setOnClickListener {
            val notifSheet = NotificationsBottomSheet(
                onDismissCallback = {
                    updateUnreadNotificationBadge()
                }
            )
            notifSheet.show(supportFragmentManager, "NotificationsBottomSheet")
        }

        // Header Delete Active Chat Button
        btnDeleteCurrentChat.setOnClickListener {
            currentConversation?.let { conv ->
                showDeleteConfirmationDialog(conv)
            }
        }

        // Sidebar "+ New Chat" Button Click
        btnSidebarNewChat.setOnClickListener {
            startNewConversationSession()
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Started new chat", Toast.LENGTH_SHORT).show()
        }

        // Sidebar Fixed Workspace Navigation Clicks
        btnNavHome.setOnClickListener {
            startNewConversationSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnNavReminders.setOnClickListener {
            val intent = Intent(this, RemindersActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnNavVault.setOnClickListener {
            val intent = Intent(this, MemoryVaultActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnNavInsights.setOnClickListener {
            val intent = Intent(this, CognitiveInsightsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnNavConnections.setOnClickListener {
            val intent = Intent(this, ConnectionsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Pinned Bottom Settings Click -> Open Settings Activity
        llPinnedSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Pinned Bottom Sidebar Help Icon Click -> Open Device Help Activity
        btnSidebarHelp.setOnClickListener {
            val intent = Intent(this, DeviceHelpActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Dynamic Mic / Send Icon Morphing based on input text presence
        etChatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                if (hasText && !isListening) {
                    btnMic.setImageResource(R.drawable.ic_send)
                    btnMic.contentDescription = "Send Message"
                    btnMic.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.icon_tint))
                } else if (!isListening) {
                    btnMic.setImageResource(R.drawable.ic_mic)
                    btnMic.contentDescription = "Voice Input"
                    btnMic.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.icon_tint))
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle Chat Input Send (keyboard enter / action send)
        etChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL) {
                sendMessage()
                true
            } else {
                false
            }
        }

        btnPlus.setOnClickListener {
            try {
                openDocumentLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Cannot open file picker: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        // Smart Microphone / Send Action Button Click
        btnMic.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if ((text.isNotEmpty() || pendingAttachments.isNotEmpty()) && !isListening) {
                sendMessage()
            } else {
                toggleSilentSpeechToText()
            }
        }

        // Open Voice Conversation Activity with smooth animation
        btnLiveVoice.setOnClickListener {
            openVoiceConversationSmoothly()
        }

        // Show App Lock authentication after initial UI/logo load screen has been displayed
        window.decorView.post {
            checkAndPromptAppLockIfRequired()
        }
    }

    private fun updateAttachmentPreviewUI() {
        attachmentPreviewAdapter.setAttachments(pendingAttachments.toList())
        if (pendingAttachments.isNotEmpty()) {
            llAttachmentPreviewContainer.visibility = View.VISIBLE
        } else {
            llAttachmentPreviewContainer.visibility = View.GONE
        }
        updateMicOrSendButtonState()
    }

    private var chatBroadcastReceiver: android.content.BroadcastReceiver? = null

    private fun registerChatBroadcastReceiver() {
        if (chatBroadcastReceiver == null) {
            chatBroadcastReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val convId = intent?.getStringExtra(ChatAiForegroundService.EXTRA_CONVERSATION_ID) ?: return
                    val action = intent.action

                    runOnUiThread {
                        if (currentConversation?.id == convId) {
                            when (action) {
                                ChatAiForegroundService.ACTION_CHAT_STEP_UPDATE -> {
                                    val stepText = intent.getStringExtra(ChatAiForegroundService.EXTRA_STEP_TEXT) ?: ""
                                    val targetMsgId = intent.getStringExtra(ChatAiForegroundService.EXTRA_TARGET_MESSAGE_ID) ?: ""
                                    val aiMsg = if (targetMsgId.isNotEmpty()) {
                                        currentConversation?.messages?.find { it.id == targetMsgId }
                                    } else {
                                        currentConversation?.messages?.findLast { it.isThinking }
                                    }
                                    if (aiMsg != null) {
                                        aiMsg.thinkingStatus = stepText
                                        chatAdapter.updateThinkingStep(aiMsg.id, stepText)
                                    }
                                }
                                ChatAiForegroundService.ACTION_CHAT_TOKEN_STREAM -> {
                                    val partialText = intent.getStringExtra(ChatAiForegroundService.EXTRA_PARTIAL_TEXT) ?: ""
                                    val targetMsgId = intent.getStringExtra(ChatAiForegroundService.EXTRA_TARGET_MESSAGE_ID) ?: ""
                                    val aiMsg = if (targetMsgId.isNotEmpty()) {
                                        currentConversation?.messages?.find { it.id == targetMsgId }
                                    } else {
                                        currentConversation?.messages?.findLast { it.isThinking }
                                    }
                                    if (aiMsg != null) {
                                        aiMsg.text = partialText
                                        chatAdapter.updateStreamingText(aiMsg.id, partialText)
                                    }
                                }
                                ChatAiForegroundService.ACTION_PARAM_EVALUATION_UPDATE -> {
                                    val msgId = intent.getStringExtra(ChatAiForegroundService.EXTRA_MESSAGE_ID) ?: ""
                                    val paramStatus = intent.getStringExtra(ChatAiForegroundService.EXTRA_PARAM_STATUS)
                                    val paramText = intent.getStringExtra(ChatAiForegroundService.EXTRA_PARAM_TEXT)
                                    val msg = currentConversation?.messages?.find { it.id == msgId }
                                    if (msg != null) {
                                        msg.paramEvaluationStatus = paramStatus
                                        msg.paramEvaluationText = paramText
                                    }
                                    chatAdapter.updateParamEvaluation(msgId, paramStatus, paramText)
                                }
                                ChatAiForegroundService.ACTION_CHAT_COMPLETED -> {
                                    val updated = ChatRepository.loadAllConversations(this@MainActivity).find { it.id == convId }
                                    if (updated != null) {
                                        if (currentConversation?.id == convId) {
                                            updated.hasUnread = false
                                            ChatRepository.saveOrUpdateConversation(this@MainActivity, updated)
                                            currentConversation = updated
                                            chatAdapter.setMessages(updated.messages)
                                        }
                                    }
                                    refreshSidebarHistory()
                                }
                            }
                        } else if (action == ChatAiForegroundService.ACTION_CHAT_COMPLETED) {
                            refreshSidebarHistory()
                        }
                    }
                }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(ChatAiForegroundService.ACTION_CHAT_STEP_UPDATE)
                addAction(ChatAiForegroundService.ACTION_CHAT_TOKEN_STREAM)
                addAction(ChatAiForegroundService.ACTION_CHAT_COMPLETED)
                addAction(ChatAiForegroundService.ACTION_PARAM_EVALUATION_UPDATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(chatBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(chatBroadcastReceiver, filter)
            }
        }
    }

    private fun unregisterChatBroadcastReceiver() {
        try {
            chatBroadcastReceiver?.let { unregisterReceiver(it) }
            chatBroadcastReceiver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        chatListScrollState = rvChatMessages.layoutManager?.onSaveInstanceState()
        unregisterChatBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applySavedTheme(this)
        registerChatBroadcastReceiver()
        updateAppLogos()
        updateGreetingText()
        refreshSidebarHistory()
        updateHeaderActiveModel()
        updateUnreadNotificationBadge()
        refreshWorkspaceReminders()

        val handled = handleIncomingIntent(intent)
        if (!handled) {
            val activeId = currentConversation?.id
            if (activeId != null) {
                activeConversationId = activeId
                val updated = ChatRepository.loadAllConversations(this).find { it.id == activeId }
                if (updated != null && updated.messages.isNotEmpty()) {
                    loadConversationIntoView(updated, restoreScroll = true)
                } else {
                    if (updated != null && updated.messages.isEmpty()) {
                        ChatRepository.deleteConversation(this, activeId)
                    }
                    startNewConversationSession()
                }
            } else if (newChatSessionTimestamp > 0L && 
                       allConversations.isNotEmpty() && 
                       allConversations[0].lastUpdated > newChatSessionTimestamp && 
                       allConversations[0].messages.isNotEmpty()) {
                // Voice call created/updated a conversation after starting a New Chat
                val latest = allConversations[0]
                loadConversationIntoView(latest, restoreScroll = false)
            } else if (isNewChatState) {
                // Keep exact New Chat greeting state if no voice messages were created
                activeConversationId = null
                llGreetingContainer.visibility = View.VISIBLE
                rvChatMessages.visibility = View.GONE
                btnDeleteCurrentChat.visibility = View.GONE
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val targetConvId = intent.getStringExtra("OPEN_CONVERSATION_ID")
        if (targetConvId.isNullOrEmpty()) return false

        val conversations = ChatRepository.loadAllConversations(this)
        val targetConv = conversations.find { it.id == targetConvId }

        if (targetConv != null) {
            loadConversationIntoView(targetConv, restoreScroll = false)
            intent.removeExtra("OPEN_CONVERSATION_ID")
            return true
        }
        return false
    }

    private fun updateUnreadNotificationBadge() {
        val unreadCount = NotificationHistoryRepository.getUnreadCount(this)
        vHeaderUnreadBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toggleSilentSpeechToText() {
        if (isListening) {
            stopSilentSpeechToText()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startSilentSpeechToText()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startSilentSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    btnMic.setImageResource(R.drawable.ic_mic)
                    btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
                    etChatInput.hint = "Listening silently..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    updateMicOrSendButtonState()
                }

                override fun onError(error: Int) {
                    isListening = false
                    updateMicOrSendButtonState()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        if (spokenText.isNotBlank()) {
                            etChatInput.setText(spokenText)
                            etChatInput.setSelection(spokenText.length)
                        }
                    }
                    updateMicOrSendButtonState()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        if (spokenText.isNotBlank()) {
                            etChatInput.setText(spokenText)
                            etChatInput.setSelection(spokenText.length)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            isListening = false
            updateMicOrSendButtonState()
        }
    }

    private fun stopSilentSpeechToText() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateMicOrSendButtonState()
    }

    private fun updateMicOrSendButtonState() {
        val hasText = etChatInput.text.toString().trim().isNotEmpty()
        val hasAttachments = pendingAttachments.isNotEmpty()
        if (hasText || hasAttachments) {
            btnMic.setImageResource(R.drawable.ic_send)
            btnMic.contentDescription = "Send Message"
            btnMic.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_tint))
        } else {
            btnMic.setImageResource(R.drawable.ic_mic)
            btnMic.contentDescription = "Voice Input"
            btnMic.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_tint))
        }
        etChatInput.hint = "Ask Memossist..."
    }

    private fun sendMessage() {
        val userText = etChatInput.text.toString().trim()
        val currentAttachments = pendingAttachments.toList()
        if (userText.isEmpty() && currentAttachments.isEmpty()) return

        etChatInput.setText("")
        pendingAttachments.clear()
        updateAttachmentPreviewUI()
        isNewChatState = false

        // Create or get active conversation
        val conversationTitle = when {
            userText.isNotEmpty() -> if (userText.length > 28) userText.take(28) + "..." else userText
            currentAttachments.isNotEmpty() -> "Attachment: ${currentAttachments[0].fileName}"
            else -> "New Chat"
        }

        val activeConv = currentConversation ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = conversationTitle,
            lastUpdated = System.currentTimeMillis()
        ).also {
            currentConversation = it
            allConversations.add(0, it)
        }

        // Add user message with attached media/files
        val userMsg = ChatMessage(
            conversationId = activeConv.id,
            text = userText,
            isUser = true,
            attachments = currentAttachments
        )
        activeConv.messages.add(userMsg)
        activeConv.lastUpdated = System.currentTimeMillis()

        // Show active chat list & hide greeting & workspace reminders
        llGreetingContainer.visibility = View.GONE
        if (this::llWorkspaceRemindersSection.isInitialized) {
            llWorkspaceRemindersSection.visibility = View.GONE
        }
        if (this::vWorkspaceMidGradientOverlay.isInitialized) {
            vWorkspaceMidGradientOverlay.visibility = View.GONE
        }
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE
        // Perform lightweight local pre-analysis to check if user confirmation is required for low-confidence routing
        val preClassification = MessageAnalyzer.analyze(this@MainActivity, userText)
        val initialTimer = ResponseStatsRepository.formatTimerStringForCase(this@MainActivity, 0L, preClassification.messageType)

        if (preClassification.requiresConfirmation) {
            val aiMsg = ChatMessage(
                conversationId = activeConv.id,
                text = "",
                isUser = false,
                isThinking = false,
                awaitingTypeConfirmation = true,
                detectedMessageType = preClassification.messageType,
                classificationConfidence = preClassification.confidence
            )
            activeConv.messages.add(aiMsg)

            chatAdapter.setMessages(activeConv.messages)
            rvChatMessages.post {
                if (chatAdapter.itemCount > 0) {
                    rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }

            ChatRepository.saveOrUpdateConversation(this@MainActivity, activeConv)
            refreshSidebarHistory()

            // Directly show intent selection popup bottom-sheet for effortless selection
            showLowConfidenceSelectorBottomSheet(aiMsg, preClassification)
        } else {
            // Check if there are already messages in progress globally or in this chat to display queue status
            val globalPending = ChatAiForegroundService.getGlobalPendingCount()
            val existingInChat = activeConv.messages.count { it.isThinking }
            val queuePosition = maxOf(globalPending, existingInChat)
            val initialStatus = if (queuePosition == 0) {
                "🔍 Processing message… ($initialTimer)"
            } else {
                "⏳ In queue $queuePosition: Waiting for previous response…"
            }

            // Add temporary thinking message for live step progress animation
            val aiMsg = ChatMessage(
                conversationId = activeConv.id,
                text = "",
                isUser = false,
                isThinking = true,
                thinkingStatus = initialStatus
            )
            activeConv.messages.add(aiMsg)

            chatAdapter.setMessages(activeConv.messages)
            rvChatMessages.post {
                if (chatAdapter.itemCount > 0) {
                    rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }

            // Save conversation state immediately to disk
            ChatRepository.saveOrUpdateConversation(this@MainActivity, activeConv)
            refreshSidebarHistory()

            // Launch Foreground Service with WakeLock to guarantee 100% background LLM execution
            BatteryOptimizationHelper.requestExemptionIfNeeded(this@MainActivity)
            ChatAiForegroundService.startService(
                context = this@MainActivity,
                conversationId = activeConv.id,
                userMessage = userText,
                userAttachments = currentAttachments,
                targetMessageId = aiMsg.id
            )
        }
    }

    private fun showLowConfidenceSelectorBottomSheet(aiMessage: ChatMessage, preClassification: ClassificationResult? = null) {
        val detectedType = preClassification?.messageType ?: aiMessage.detectedMessageType ?: MessageType.TELLING
        val confPct = ((preClassification?.confidence ?: aiMessage.classificationConfidence) * 100).toInt()

        val sheet = MessageTypeSelectorBottomSheet(
            title = "Confirm Message Intent",
            subtitle = "Detected ${detectedType.displayName} ($confPct% confidence). Select the desired processing mode:",
            currentlySelected = detectedType,
            onTypeSelected = { selectedType ->
                startPipelineWithConfirmedType(aiMessage, selectedType)
            }
        )
        sheet.show(supportFragmentManager, "LowConfidenceSelectorBottomSheet")
    }

    private fun startPipelineWithConfirmedType(aiMessage: ChatMessage, selectedType: MessageType) {
        val activeConv = currentConversation ?: return
        val userMsg = activeConv.messages.findLast { it.isUser } ?: return

        val initialTimer = ResponseStatsRepository.formatTimerStringForCase(this@MainActivity, 0L, selectedType)

        aiMessage.awaitingTypeConfirmation = false
        aiMessage.isThinking = true
        aiMessage.thinkingStatus = "🔍 Classifying as ${selectedType.displayName}… ($initialTimer)"
        aiMessage.text = ""

        chatAdapter.setMessages(activeConv.messages)
        ChatRepository.saveOrUpdateConversation(this@MainActivity, activeConv)

        BatteryOptimizationHelper.requestExemptionIfNeeded(this@MainActivity)
        ChatAiForegroundService.startService(
            context = this@MainActivity,
            conversationId = activeConv.id,
            userMessage = userMsg.text,
            userAttachments = userMsg.attachments,
            targetMessageId = aiMessage.id,
            forcedMessageType = selectedType
        )
    }

    private fun showInFlightTypeCorrectionBottomSheet(aiMessage: ChatMessage) {
        val activeConv = currentConversation ?: return
        val userMsg = activeConv.messages.findLast { it.isUser } ?: return

        val sheet = MessageTypeSelectorBottomSheet(
            currentlySelected = null,
            onTypeSelected = { selectedType ->
                // 1. Cancel in-flight pipeline execution
                ChatAiForegroundService.cancelActiveChat(this@MainActivity, activeConv.id)

                val initialTimer = ResponseStatsRepository.formatTimerStringForCase(this@MainActivity, 0L, selectedType)

                // 2. Reset AI message state
                aiMessage.isThinking = true
                aiMessage.awaitingTypeConfirmation = false
                aiMessage.text = ""
                aiMessage.thinkingStatus = "⚡ Restarting with ${selectedType.displayName}… ($initialTimer)"

                chatAdapter.setMessages(activeConv.messages)
                ChatRepository.saveOrUpdateConversation(this@MainActivity, activeConv)

                // 3. Restart pipeline with user-chosen type
                BatteryOptimizationHelper.requestExemptionIfNeeded(this@MainActivity)
                ChatAiForegroundService.startService(
                    context = this@MainActivity,
                    conversationId = activeConv.id,
                    userMessage = userMsg.text,
                    userAttachments = userMsg.attachments,
                    targetMessageId = aiMessage.id,
                    forcedMessageType = selectedType
                )
            }
        )
        sheet.show(supportFragmentManager, "MessageTypeSelectorBottomSheet")
    }

    private fun updateScrollToBottomButtonVisibility() {
        if (rvChatMessages.visibility != View.VISIBLE || chatAdapter.itemCount <= 1) {
            hideScrollToBottomButton()
            return
        }
        val canScrollDown = rvChatMessages.canScrollVertically(1)
        if (canScrollDown) {
            showScrollToBottomButton()
        } else {
            hideScrollToBottomButton()
        }
    }

    private fun showScrollToBottomButton() {
        if (btnScrollToBottom.visibility != View.VISIBLE) {
            btnScrollToBottom.visibility = View.VISIBLE
            btnScrollToBottom.alpha = 0f
            btnScrollToBottom.scaleX = 0.7f
            btnScrollToBottom.scaleY = 0.7f
            btnScrollToBottom.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start()
        }
    }

    private fun hideScrollToBottomButton() {
        if (btnScrollToBottom.visibility == View.VISIBLE) {
            btnScrollToBottom.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(150)
                .withEndAction {
                    btnScrollToBottom.visibility = View.GONE
                }
                .start()
        }
    }

    private fun loadConversationIntoView(conversation: Conversation, restoreScroll: Boolean = false) {
        isNewChatState = false
        if (conversation.hasUnread) {
            conversation.hasUnread = false
            ChatRepository.saveOrUpdateConversation(this, conversation)
        }
        currentConversation = conversation
        activeConversationId = conversation.id
        llGreetingContainer.visibility = View.GONE
        if (this::llWorkspaceRemindersSection.isInitialized) {
            llWorkspaceRemindersSection.visibility = View.GONE
        }
        if (this::vWorkspaceMidGradientOverlay.isInitialized) {
            vWorkspaceMidGradientOverlay.visibility = View.GONE
        }
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE

        chatAdapter.setMessages(conversation.messages)
        refreshSidebarHistory()
        if (restoreScroll && chatListScrollState != null) {
            rvChatMessages.layoutManager?.onRestoreInstanceState(chatListScrollState)
            rvChatMessages.post { updateScrollToBottomButtonVisibility() }
        } else if (conversation.messages.isNotEmpty()) {
            rvChatMessages.post {
                if (chatAdapter.itemCount > 0) {
                    rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }
                updateScrollToBottomButtonVisibility()
            }
        } else {
            updateScrollToBottomButtonVisibility()
        }
    }

    private fun startNewConversationSession() {
        isNewChatState = true
        newChatSessionTimestamp = System.currentTimeMillis()
        currentConversation = null
        activeConversationId = null
        chatListScrollState = null
        llGreetingContainer.visibility = View.VISIBLE
        refreshWorkspaceReminders()
        rvChatMessages.visibility = View.GONE
        btnDeleteCurrentChat.visibility = View.GONE
        btnScrollToBottom.visibility = View.GONE
        chatAdapter.setMessages(emptyList())
    }

    private fun showDeleteConfirmationDialog(conversation: Conversation) {
        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete \"${conversation.title}\"?")
            .setPositiveButton("Delete") { dialog, _ ->
                ChatRepository.deleteConversation(this, conversation.id)
                if (currentConversation?.id == conversation.id) {
                    startNewConversationSession()
                }
                refreshSidebarHistory()
                Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showChatOptionsDialog(conversation: Conversation) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvOptDialogTitle: TextView = dialogView.findViewById(R.id.tvOptDialogTitle)
        val btnOptPin: View = dialogView.findViewById(R.id.btnOptPin)
        val ivOptPinIcon: ImageView = dialogView.findViewById(R.id.ivOptPinIcon)
        val tvOptPinText: TextView = dialogView.findViewById(R.id.tvOptPinText)
        val btnOptRename: View = dialogView.findViewById(R.id.btnOptRename)
        val btnOptDelete: View = dialogView.findViewById(R.id.btnOptDelete)

        tvOptDialogTitle.text = conversation.title
        tvOptPinText.text = if (conversation.isPinned) "Unpin Chat" else "Pin Chat"
        ivOptPinIcon.setImageResource(R.drawable.ic_pin)

        // Action 1: Toggle Pin
        btnOptPin.setOnClickListener {
            ChatRepository.togglePinConversation(this, conversation.id)
            refreshSidebarHistory()
            val msg = if (conversation.isPinned) "Chat unpinned" else "Chat pinned to top"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Action 2: Rename Chat
        btnOptRename.setOnClickListener {
            dialog.dismiss()
            showRenameChatDialog(conversation)
        }

        // Action 3: Delete Chat
        btnOptDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(conversation)
        }

        dialog.show()
    }

    private fun showRenameChatDialog(conversation: Conversation) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_username, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etInput: EditText = dialogView.findViewById(R.id.etDialogUserName)
        val btnCancel: TextView = dialogView.findViewById(R.id.btnDialogCancel)
        val btnSave: TextView = dialogView.findViewById(R.id.btnDialogSave)

        etInput.setText(conversation.title)
        etInput.setSelection(conversation.title.length)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val newTitle = etInput.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                ChatRepository.renameConversation(this, conversation.id, newTitle)
                refreshSidebarHistory()
                if (currentConversation?.id == conversation.id) {
                    currentConversation?.title = newTitle
                }
                Toast.makeText(this, "Conversation renamed", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun refreshSidebarHistory() {
        allConversations = ChatRepository.loadAllConversations(this)
        sidebarHistoryAdapter.setConversations(allConversations)
        updateSidebarMenuBadge()
    }

    private fun updateSidebarMenuBadge() {
        val hasAnyUnread = allConversations.any { it.hasUnread }
        if (this::vSidebarUnreadBadge.isInitialized) {
            vSidebarUnreadBadge.visibility = if (hasAnyUnread) View.VISIBLE else View.GONE
        }
    }

    private fun openVoiceConversationSmoothly() {
        val intent = Intent(this, VoiceConversationActivity::class.java).apply {
            putExtra("CONVERSATION_ID", currentConversation?.id)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun setupSwipeTouchListener() {
        mainContentContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY

                    if (abs(diffX) > abs(diffY) && abs(diffX) > 120) {
                        if (diffX > 0) {
                            // Swipe Right -> Smoothly open Sidebar Menu
                            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                drawerLayout.openDrawer(GravityCompat.START)
                            }
                        } else {
                            // Swipe Left -> Smoothly open Voice Conversation Page
                            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                openVoiceConversationSmoothly()
                            }
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun updateGreetingText() {
        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "User") ?: "User"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning, $userName"
            in 12..16 -> "Good afternoon, $userName"
            in 17..21 -> "Good evening, $userName"
            else -> "Good night, $userName"
        }
        tvGreetingTitle.text = timeGreeting

        val allMemories = MemoryVaultRepository.loadAllMemories(this)
        val allConversations = ChatRepository.loadAllConversations(this)
        val hasAnyData = allMemories.isNotEmpty() || allConversations.isNotEmpty()

        if (!hasAnyData) {
            tvGreetingPrompt.text = "Welcome to Memossist! Your personal AI memory vault is ready. Start typing or tap the mic to record your first memory, ask a question, or set smart reminders."
        } else {
            tvGreetingPrompt.text = "What do you want to recall?"
        }
    }

    private fun updateHeaderActiveModel() {
        val activeModel = NoeonAiEngine.getSelectedModel(this)
        tvHeaderModelIcon.visibility = View.GONE
        tvHeaderModelName.text = activeModel.name
    }

    private fun showQuickModelPickerSheet() {
        val pickerSheet = ModelPickerBottomSheet(
            onModelChanged = { _ ->
                updateHeaderActiveModel()
            }
        )
        pickerSheet.show(supportFragmentManager, "ModelPickerBottomSheet")
    }

    private fun setupWorkspaceReminders() {
        llWorkspaceRemindersSection = findViewById(R.id.llWorkspaceRemindersSection)
        rvWorkspaceReminders = findViewById(R.id.rvWorkspaceReminders)
        vWorkspaceMidGradientOverlay = findViewById(R.id.vWorkspaceMidGradientOverlay)

        workspaceRemindersAdapter = WorkspaceRemindersAdapter { reminder ->
            val intent = Intent(this, RemindersActivity::class.java).apply {
                putExtra("HIGHLIGHT_REMINDER_ID", reminder.id)
            }
            startActivity(intent)
        }
        rvWorkspaceReminders.layoutManager = LinearLayoutManager(this)
        rvWorkspaceReminders.adapter = workspaceRemindersAdapter

        refreshWorkspaceReminders()
    }

    private fun refreshWorkspaceReminders() {
        if (!isNewChatState && currentConversation != null) {
            if (this::llWorkspaceRemindersSection.isInitialized) {
                llWorkspaceRemindersSection.visibility = View.GONE
            }
            if (this::vWorkspaceMidGradientOverlay.isInitialized) {
                vWorkspaceMidGradientOverlay.visibility = View.GONE
            }
            return
        }
        val upcomingList = ReminderRepository.getRemindersInNext24Hours(this)
        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"

        if (this::llWorkspaceRemindersSection.isInitialized) {
            if (upcomingList.isNotEmpty()) {
                llWorkspaceRemindersSection.visibility = View.VISIBLE
                if (this::vWorkspaceMidGradientOverlay.isInitialized) {
                    vWorkspaceMidGradientOverlay.visibility = View.VISIBLE
                }
                workspaceRemindersAdapter.setReminders(upcomingList, userName)
            } else {
                llWorkspaceRemindersSection.visibility = View.GONE
                if (this::vWorkspaceMidGradientOverlay.isInitialized) {
                    vWorkspaceMidGradientOverlay.visibility = View.GONE
                }
            }
        }
    }

    private fun updateAppLogos() {
        val logoRes = ThemeManager.getLogoDrawable(this)
        if (this::ivSplashLogo.isInitialized) {
            ivSplashLogo.setImageResource(logoRes)
        }
        if (this::ivSidebarLogo.isInitialized) {
            ivSidebarLogo.setImageResource(logoRes)
        }
    }

    private fun checkAndPromptAppLockIfRequired() {
        updateAppLogos()
        if (AppLockManager.isAppLockEnabled(this) && !AppLockManager.isSessionAuthenticated) {
            if (this::flSplashOverlay.isInitialized) {
                flSplashOverlay.visibility = View.VISIBLE
                flSplashOverlay.alpha = 1.0f
            }
            if (this::btnSplashUnlock.isInitialized) {
                btnSplashUnlock.visibility = View.GONE
            }

            AppLockManager.showBiometricPrompt(
                activity = this,
                title = "Unlock Memossist",
                subtitle = "Authenticate to access your Vault and chats",
                onSuccess = {
                    AppLockManager.isSessionAuthenticated = true
                    dismissSplashOverlaySmoothly()
                },
                onFailure = {
                    if (this::btnSplashUnlock.isInitialized) {
                        btnSplashUnlock.visibility = View.VISIBLE
                    }
                    Toast.makeText(this, "Authentication required to unlock Memossist", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            dismissSplashOverlaySmoothly()
        }
    }

    private fun dismissSplashOverlaySmoothly() {
        if (this::flSplashOverlay.isInitialized && flSplashOverlay.visibility == View.VISIBLE) {
            flSplashOverlay.postDelayed({
                flSplashOverlay.animate()
                    .alpha(0f)
                    .setDuration(350L)
                    .withEndAction {
                        flSplashOverlay.visibility = View.GONE
                    }
                    .start()
            }, 300L)
        }
    }

    private fun showEditLastMessageDialog(userMessage: ChatMessage) {
        val conv = currentConversation ?: return
        val lastUserMsg = conv.messages.findLast { it.isUser }
        if (lastUserMsg == null || lastUserMsg.id != userMessage.id) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_message, null, false)
        val etEditMessageText = dialogView.findViewById<EditText>(R.id.etEditMessageText)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelEditMessage)
        val btnEditInChatBar = dialogView.findViewById<TextView>(R.id.btnEditInChatBar)
        val btnConfirmResend = dialogView.findViewById<TextView>(R.id.btnConfirmResendMessage)

        etEditMessageText.setText(userMessage.text)
        etEditMessageText.setSelection(userMessage.text.length)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnEditInChatBar.setOnClickListener {
            dialog.dismiss()
            val newText = etEditMessageText.text.toString().trim()

            // Revert previous exchange actions from disk
            val reverted = ChatRepository.revertLastUserMessage(this@MainActivity, conv.id)
            if (reverted != null) {
                val updated = ChatRepository.loadAllConversations(this@MainActivity).find { it.id == conv.id }
                if (updated != null) {
                    currentConversation = updated
                    chatAdapter.setMessages(updated.messages)
                }

                // Place edited text & attachments in chat bar for user to edit further
                etChatInput.setText(newText)
                etChatInput.setSelection(newText.length)
                pendingAttachments.clear()
                pendingAttachments.addAll(reverted.attachments)
                updateAttachmentPreviewUI()
                updateMicOrSendButtonState()

                etChatInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(etChatInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

                Toast.makeText(this@MainActivity, "Previous response reverted. You can now edit and send.", Toast.LENGTH_SHORT).show()
            }
        }

        btnConfirmResend.setOnClickListener {
            dialog.dismiss()
            val newText = etEditMessageText.text.toString().trim()
            if (newText.isEmpty() && userMessage.attachments.isEmpty()) {
                Toast.makeText(this@MainActivity, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Revert previous exchange actions from disk
            val reverted = ChatRepository.revertLastUserMessage(this@MainActivity, conv.id)
            if (reverted != null) {
                val updated = ChatRepository.loadAllConversations(this@MainActivity).find { it.id == conv.id }
                if (updated != null) {
                    currentConversation = updated
                    chatAdapter.setMessages(updated.messages)
                }

                // Put text into input and trigger sendMessage
                etChatInput.setText(newText)
                pendingAttachments.clear()
                pendingAttachments.addAll(reverted.attachments)
                updateAttachmentPreviewUI()
                updateMicOrSendButtonState()

                sendMessage()
            }
        }

        dialog.show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            checkAndPromptAppLockIfRequired()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        activeConversationId = null
    }

    companion object {
        @Volatile
        var activeConversationId: String? = null
    }
}
