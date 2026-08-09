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
    private lateinit var workspaceRemindersAdapter: WorkspaceRemindersAdapter

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var etChatInput: EditText
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnLiveVoice: ImageButton

    // Sidebar Views
    private lateinit var btnSidebarNewChat: View
    private lateinit var btnNavHome: View
    private lateinit var btnNavReminders: View
    private lateinit var btnNavVault: View
    private lateinit var btnNavInsights: View
    private lateinit var btnNavConnections: View
    private lateinit var rvSidebarHistory: RecyclerView
    private lateinit var sidebarHistoryAdapter: SidebarHistoryAdapter
    private lateinit var llPinnedSettings: LinearLayout

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
        btnHeaderModelPicker = findViewById(R.id.btnHeaderModelPicker)
        tvHeaderModelIcon = findViewById(R.id.tvHeaderModelIcon)
        tvHeaderModelName = findViewById(R.id.tvHeaderModelName)
        btnHeaderNotifications = findViewById(R.id.btnHeaderNotifications)
        vHeaderUnreadBadge = findViewById(R.id.vHeaderUnreadBadge)
        btnDeleteCurrentChat = findViewById(R.id.btnDeleteCurrentChat)
        mainContentContainer = findViewById(R.id.mainContentContainer)

        // Initialize Sidebar Views
        btnSidebarNewChat = findViewById(R.id.btnSidebarNewChat)
        btnNavHome = findViewById(R.id.btnNavHome)
        btnNavReminders = findViewById(R.id.btnNavReminders)
        btnNavVault = findViewById(R.id.btnNavVault)
        btnNavInsights = findViewById(R.id.btnNavInsights)
        btnNavConnections = findViewById(R.id.btnNavConnections)
        rvSidebarHistory = findViewById(R.id.rvSidebarHistory)
        llPinnedSettings = findViewById(R.id.llPinnedSettings)

        // Initialize Greeting Views
        llGreetingContainer = findViewById(R.id.llGreetingContainer)
        tvGreetingTitle = findViewById(R.id.tvGreetingTitle)
        tvGreetingPrompt = findViewById(R.id.tvGreetingPrompt)

        // Initialize Active Chat RecyclerView
        rvChatMessages = findViewById(R.id.rvChatMessages)
        chatAdapter = ChatAdapter(
            onMessageLongClick = { message ->
                if (!message.isUser && !message.isThinking) {
                    val logText = message.debugLog ?: "=== 🧠 MEMOSSIST AI DIAGNOSTIC LOGS ===\n" +
                            "Model Engine: ${NoeonAiEngine.getSelectedModel(this).name}\n\n" +
                            "=== CLEAN HUMANOID ANSWER ===\n${message.text}\n\n" +
                            "=== DAG NODE CONNECTION STRENGTH CALCULATIONS ===\n" +
                            "Formula Applied: S_ij_new = S_ij_old + (|Q ∩ Ni ∩ Nj| × t) / N\n" +
                            "POS Sets Filtered: Nouns, Verbs, Adjectives, Adverbs"

                    val logsSheet = AiMessageLogsBottomSheet(logText)
                    logsSheet.show(supportFragmentManager, "AiMessageLogsBottomSheet")
                }
            }
        )
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = chatAdapter

        // Smooth Keyboard & Resize Scroll Adjuster
        rvChatMessages.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatAdapter.itemCount > 0) {
                rvChatMessages.post {
                    rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }

        // Swipe Left or Right to remove chat message
        val chatSwipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val conv = currentConversation ?: return
                if (position in 0 until conv.messages.size) {
                    conv.messages.removeAt(position)
                    conv.lastUpdated = System.currentTimeMillis()
                    ChatRepository.saveOrUpdateConversation(this@MainActivity, conv)
                    chatAdapter.setMessages(conv.messages)
                    Toast.makeText(this@MainActivity, "Message removed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(chatSwipeHandler).attachToRecyclerView(rvChatMessages)

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

        // Dynamic Mic / Send Icon Morphing based on input text presence
        etChatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                if (hasText && !isListening) {
                    btnMic.setImageResource(R.drawable.ic_send)
                    btnMic.contentDescription = "Send Message"
                    btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#121417"))
                } else if (!isListening) {
                    btnMic.setImageResource(R.drawable.ic_mic)
                    btnMic.contentDescription = "Voice Input"
                    btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
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

                    runOnUiThread {
                        if (currentConversation?.id == convId) {
                            val updated = ChatRepository.loadAllConversations(this@MainActivity).find { it.id == convId }
                            if (updated != null) {
                                loadConversationIntoView(updated, restoreScroll = true)
                            }
                        }
                        refreshSidebarHistory()
                    }
                }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(ChatAiForegroundService.ACTION_CHAT_STEP_UPDATE)
                addAction(ChatAiForegroundService.ACTION_CHAT_TOKEN_STREAM)
                addAction(ChatAiForegroundService.ACTION_CHAT_COMPLETED)
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
        registerChatBroadcastReceiver()
        updateGreetingText()
        refreshSidebarHistory()
        updateHeaderActiveModel()
        updateUnreadNotificationBadge()
        refreshWorkspaceReminders()

        val activeId = currentConversation?.id
        if (activeId != null) {
            val updated = ChatRepository.loadAllConversations(this).find { it.id == activeId }
            if (updated != null) {
                loadConversationIntoView(updated, restoreScroll = true)
            }
        } else if (allConversations.isNotEmpty() && 
                   allConversations[0].lastUpdated >= newChatSessionTimestamp && 
                   allConversations[0].messages.isNotEmpty()) {
            // Voice call created/updated a conversation after starting a New Chat
            val latest = allConversations[0]
            loadConversationIntoView(latest, restoreScroll = false)
        } else if (isNewChatState) {
            // Keep exact New Chat greeting state if no voice messages were created
            llGreetingContainer.visibility = View.VISIBLE
            rvChatMessages.visibility = View.GONE
            btnDeleteCurrentChat.visibility = View.GONE
        }
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
            btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#121417"))
        } else {
            btnMic.setImageResource(R.drawable.ic_mic)
            btnMic.contentDescription = "Voice Input"
            btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
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

        // Show active chat list & hide greeting
        llGreetingContainer.visibility = View.GONE
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE

        // Add temporary thinking message for live step progress animation
        val aiMsg = ChatMessage(
            conversationId = activeConv.id,
            text = "",
            isUser = false,
            isThinking = true,
            thinkingStatus = "🔍 Step 1/6: Retrieving candidate experiences..."
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
        ChatAiForegroundService.startService(
            context = this@MainActivity,
            conversationId = activeConv.id,
            userMessage = userText,
            userAttachments = currentAttachments
        )
    }

    private fun loadConversationIntoView(conversation: Conversation, restoreScroll: Boolean = false) {
        isNewChatState = false
        currentConversation = conversation
        llGreetingContainer.visibility = View.GONE
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE

        chatAdapter.setMessages(conversation.messages)
        if (restoreScroll && chatListScrollState != null) {
            rvChatMessages.layoutManager?.onRestoreInstanceState(chatListScrollState)
        } else if (conversation.messages.isNotEmpty()) {
            rvChatMessages.post {
                if (chatAdapter.itemCount > 0) {
                    rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun startNewConversationSession() {
        isNewChatState = true
        newChatSessionTimestamp = System.currentTimeMillis()
        currentConversation = null
        chatListScrollState = null
        llGreetingContainer.visibility = View.VISIBLE
        rvChatMessages.visibility = View.GONE
        btnDeleteCurrentChat.visibility = View.GONE
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
        val userName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning, $userName"
            in 12..16 -> "Good afternoon, $userName"
            in 17..21 -> "Good evening, $userName"
            else -> "Good night, $userName"
        }
        tvGreetingTitle.text = timeGreeting
    }

    private fun updateHeaderActiveModel() {
        val activeModel = NoeonAiEngine.getSelectedModel(this)
        tvHeaderModelIcon.text = activeModel.icon
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
        val upcomingList = ReminderRepository.getRemindersInNext24Hours(this)
        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"

        if (upcomingList.isNotEmpty()) {
            llWorkspaceRemindersSection.visibility = View.VISIBLE
            workspaceRemindersAdapter.setReminders(upcomingList, userName)
        } else {
            llWorkspaceRemindersSection.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
