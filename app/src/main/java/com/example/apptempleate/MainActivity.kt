package com.example.apptempleate

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var btnHamburger: ImageButton
    private lateinit var btnHeaderNewChat: ImageButton
    private lateinit var btnDeleteCurrentChat: ImageButton
    private lateinit var mainContentContainer: ConstraintLayout

    private lateinit var llGreetingContainer: LinearLayout
    private lateinit var tvGreetingTitle: TextView
    private lateinit var tvGreetingPrompt: TextView

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var etChatInput: EditText
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnLiveVoice: ImageButton
    private lateinit var llPinnedSettings: LinearLayout

    private var currentConversation: Conversation? = null
    private var allConversations: MutableList<Conversation> = mutableListOf()

    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove window title & hide action bar header completely
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)

        // Initialize Navigation & Layout Views
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        btnHamburger = findViewById(R.id.btnHamburger)
        btnHeaderNewChat = findViewById(R.id.btnHeaderNewChat)
        btnDeleteCurrentChat = findViewById(R.id.btnDeleteCurrentChat)
        mainContentContainer = findViewById(R.id.mainContentContainer)
        llPinnedSettings = findViewById(R.id.llPinnedSettings)

        // Initialize Greeting Views
        llGreetingContainer = findViewById(R.id.llGreetingContainer)
        tvGreetingTitle = findViewById(R.id.tvGreetingTitle)
        tvGreetingPrompt = findViewById(R.id.tvGreetingPrompt)

        // Initialize Active Chat RecyclerView
        rvChatMessages = findViewById(R.id.rvChatMessages)
        chatAdapter = ChatAdapter()
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = chatAdapter

        // Initialize Chat Input Views
        etChatInput = findViewById(R.id.etChatInput)
        btnPlus = findViewById(R.id.btnPlus)
        btnMic = findViewById(R.id.btnMic)
        btnLiveVoice = findViewById(R.id.btnLiveVoice)

        // Set Dynamic Premium Time-of-Day Greeting
        updateGreetingText()

        // Load Saved Conversations & Populate Sidebar Recent History
        refreshSidebarHistory()

        // Setup Touch Swipe Gesture Detection
        setupSwipeTouchListener()

        // Hamburger Menu Click -> Smoothly open side menu drawer
        btnHamburger.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Header New Chat Button -> Start New Chat Session
        btnHeaderNewChat.setOnClickListener {
            startNewConversationSession()
            Toast.makeText(this, "Started new chat", Toast.LENGTH_SHORT).show()
        }

        // Header Delete Active Chat Button
        btnDeleteCurrentChat.setOnClickListener {
            currentConversation?.let { conv ->
                showDeleteConfirmationDialog(conv)
            }
        }

        // Sidebar Header "+ New Chat" Button Click
        val headerView = navView.getHeaderView(0)
        val btnSidebarNewChat: View? = headerView?.findViewById(R.id.btnSidebarNewChat)
        btnSidebarNewChat?.setOnClickListener {
            startNewConversationSession()
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Started new chat", Toast.LENGTH_SHORT).show()
        }

        // Pinned Bottom Settings Click -> Open Settings Activity
        llPinnedSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Side Navigation Drawer Item Clicks
        navView.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when {
                id == R.id.nav_home -> {
                    startNewConversationSession()
                }
                id == R.id.nav_vault -> {
                    val intent = Intent(this, MemoryVaultActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                id == R.id.nav_insights -> {
                    val intent = Intent(this, CognitiveInsightsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                id == R.id.nav_connections -> {
                    val intent = Intent(this, ConnectionsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                else -> {
                    // Check if a recent conversation item was clicked
                    val selectedConv = allConversations.find { it.id.hashCode() == id }
                    if (selectedConv != null) {
                        loadConversationIntoView(selectedConv)
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

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
            Toast.makeText(this, "Add attachments", Toast.LENGTH_SHORT).show()
        }

        btnMic.setOnClickListener {
            sendMessage()
        }

        // Open Voice Conversation Activity with smooth animation
        btnLiveVoice.setOnClickListener {
            openVoiceConversationSmoothly()
        }
    }

    private fun sendMessage() {
        val userText = etChatInput.text.toString().trim()
        if (userText.isEmpty()) return

        etChatInput.setText("")

        // Create or get active conversation
        val activeConv = currentConversation ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = if (userText.length > 28) userText.take(28) + "..." else userText,
            lastUpdated = System.currentTimeMillis()
        ).also {
            currentConversation = it
            allConversations.add(0, it)
        }

        // Add user message
        val userMsg = ChatMessage(
            conversationId = activeConv.id,
            text = userText,
            isUser = true
        )
        activeConv.messages.add(userMsg)
        activeConv.lastUpdated = System.currentTimeMillis()

        // Show active chat list & hide greeting
        llGreetingContainer.visibility = View.GONE
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE

        chatAdapter.setMessages(activeConv.messages)
        rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

        // Save progress to local repository
        ChatRepository.saveOrUpdateConversation(this, activeConv)
        refreshSidebarHistory()

        // Generate AI Response with simulated typing delay
        Handler(Looper.getMainLooper()).postDelayed({
            val aiResponseText = ChatRepository.generateAiResponse(userText)
            val aiMsg = ChatMessage(
                conversationId = activeConv.id,
                text = aiResponseText,
                isUser = false
            )
            activeConv.messages.add(aiMsg)
            activeConv.lastUpdated = System.currentTimeMillis()

            chatAdapter.setMessages(activeConv.messages)
            rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

            ChatRepository.saveOrUpdateConversation(this@MainActivity, activeConv)
            refreshSidebarHistory()
        }, 600)
    }

    private fun loadConversationIntoView(conversation: Conversation) {
        currentConversation = conversation
        llGreetingContainer.visibility = View.GONE
        rvChatMessages.visibility = View.VISIBLE
        btnDeleteCurrentChat.visibility = View.VISIBLE

        chatAdapter.setMessages(conversation.messages)
        if (conversation.messages.isNotEmpty()) {
            rvChatMessages.scrollToPosition(conversation.messages.size - 1)
        }
    }

    private fun startNewConversationSession() {
        currentConversation = null
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
        ivOptPinIcon.setImageResource(if (conversation.isPinned) R.drawable.ic_pin else R.drawable.ic_pin)

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

        val tvTitle: TextView = dialogView.findViewById(R.id.etDialogUserName)
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
        val historyGroupItem = navView.menu.findItem(R.id.nav_recent_history_group)
        val subMenu = historyGroupItem?.subMenu
        subMenu?.clear()

        for (conv in allConversations) {
            val titleText = if (conv.isPinned) "📌 ${conv.title}" else conv.title
            val menuItem = subMenu?.add(Menu.NONE, conv.id.hashCode(), Menu.NONE, titleText)
            menuItem?.setIcon(if (conv.isPinned) R.drawable.ic_pin else R.drawable.ic_chat_history)
        }

        // Attach long-press handler to navigation view child views
        Handler(Looper.getMainLooper()).postDelayed({
            for (i in 0 until navView.childCount) {
                val child = navView.getChildAt(i)
                if (child is ViewGroup) {
                    attachLongClickListenerToMenuViews(child)
                }
            }
        }, 150)
    }

    private fun attachLongClickListenerToMenuViews(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                attachLongClickListenerToMenuViews(child)
            } else if (child is TextView) {
                val text = child.text.toString()
                val matchedConv = allConversations.find { 
                    text == it.title || text == "📌 ${it.title}"
                }
                if (matchedConv != null) {
                    val parentRow = child.parent as? View ?: child
                    parentRow.setOnLongClickListener {
                        showChatOptionsDialog(matchedConv)
                        true
                    }
                }
            }
        }
    }

    private fun openVoiceConversationSmoothly() {
        val intent = Intent(this, VoiceConversationActivity::class.java)
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
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            in 17..21 -> "Good evening,"
            else -> "Good night,"
        }
        tvGreetingTitle.text = timeGreeting
    }
}
