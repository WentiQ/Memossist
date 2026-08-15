package com.example.apptempleate

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

class VoiceConversationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var btnEndCall: ImageButton
    private lateinit var btnMuteMic: ImageButton
    private lateinit var btnSpeakerToggle: ImageButton
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvVoiceSubStatus: TextView
    private lateinit var leafOrbView: LeafOrbView

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private var activeConversation: Conversation? = null

    private var isMuted = false
    private var isSpeakerOn = true
    private var isTtsReady = false
    private var isListening = false
    private var isAiResponding = false
    private var isCallEnding = false

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningLoop()
        } else {
            Toast.makeText(this, "Microphone permission is required for Voice Call", Toast.LENGTH_SHORT).show()
            finishWithSmoothAnimation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove window title & hide action bar header completely
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_voice_conversation)

        btnEndCall = findViewById(R.id.btnEndCall)
        btnMuteMic = findViewById(R.id.btnMuteMic)
        btnSpeakerToggle = findViewById(R.id.btnSpeakerToggle)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        tvVoiceSubStatus = findViewById(R.id.tvVoiceSubStatus)
        leafOrbView = findViewById(R.id.leafOrbView)

        // Load active conversation passed from MainActivity or create new one
        val convId = intent.getStringExtra("CONVERSATION_ID")
        if (convId != null) {
            activeConversation = ChatRepository.loadAllConversations(this).find { it.id == convId }
        }

        if (activeConversation == null) {
            activeConversation = Conversation(
                id = UUID.randomUUID().toString(),
                title = "New Chat",
                lastUpdated = System.currentTimeMillis()
            )
            // Empty conversation is kept in-memory only and not saved to disk until user gives input
        }

        // Start Foreground Service to keep Microphone & CPU active when screen turns off or app is backgrounded
        VoiceForegroundService.startService(this)

        // Register Broadcast Receiver for End Call action from Notification Bar
        registerCallStoppedReceiver()
        registerChatAiBroadcastReceiver()

        // Initialize TextToSpeech engine
        textToSpeech = TextToSpeech(this, this)

        // Setup Controls
        btnEndCall.setOnClickListener {
            cleanupEmptyConversationIfNeeded()
            stopAllVoiceEngines()
            Toast.makeText(this, "Voice call ended", Toast.LENGTH_SHORT).show()
            finishWithSmoothAnimation()
        }

        btnMuteMic.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                btnMuteMic.setImageResource(R.drawable.ic_mic_off)
                tvVoiceStatus.text = "Muted"
                tvVoiceSubStatus.text = "Microphone paused. Tap to unmute."
                stopListeningLoop()
                Toast.makeText(this, "Microphone Muted", Toast.LENGTH_SHORT).show()
            } else {
                btnMuteMic.setImageResource(R.drawable.ic_mic)
                tvVoiceStatus.text = "Listening..."
                tvVoiceSubStatus.text = "Speak now..."
                startListeningLoop()
                Toast.makeText(this, "Microphone Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeakerToggle.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            audioManager.isSpeakerphoneOn = isSpeakerOn
            val message = if (isSpeakerOn) "Speakerphone ON" else "Earpiece ON"
            btnSpeakerToggle.alpha = if (isSpeakerOn) 1.0f else 0.5f
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Check audio permissions & start Gemini Voice loop
        checkAndStartVoiceCall()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            isAiResponding = true
                            tvVoiceStatus.text = "Responding..."
                            tvVoiceSubStatus.text = "Memossist is speaking"
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            isAiResponding = false
                            if (!isMuted) {
                                tvVoiceStatus.text = "Listening..."
                                tvVoiceSubStatus.text = "Speak now..."
                                startListeningLoop()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            isAiResponding = false
                            if (!isMuted) {
                                startListeningLoop()
                            }
                        }
                    }
                })

                // If conversation is brand new, speak initial welcome greeting
                if (activeConversation?.messages.isNullOrEmpty()) {
                    speakAiResponse("Hello! I'm Memossist Live. How can I help you today?")
                }
            }
        }
    }

    private fun checkAndStartVoiceCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningLoop()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningLoop() {
        if (isMuted || isAiResponding) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    tvVoiceStatus.text = "Listening..."
                    tvVoiceSubStatus.text = "Speak to Memossist..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    if (isCallEnding) return
                    tvVoiceStatus.text = "Thinking..."
                    tvVoiceSubStatus.text = "Processing voice input..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isCallEnding) return
                    if (!isMuted && !isAiResponding) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isCallEnding) startListeningLoop()
                        }, 1200)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    if (isCallEnding) return
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val userSpeech = matches[0]
                        if (userSpeech.isNotBlank()) {
                            processUserVoiceInput(userSpeech)
                            return
                        }
                    }
                    if (!isMuted && !isCallEnding) {
                        startListeningLoop()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopListeningLoop() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processUserVoiceInput(userText: String) {
        if (isCallEnding || userText.isBlank()) return

        tvVoiceStatus.text = "Thinking..."
        tvVoiceSubStatus.text = "\"$userText\""

        // Always reload the latest conversation state from disk before modifying it so we don't overwrite completed AI responses
        val diskConv = activeConversation?.id?.let { convId ->
            ChatRepository.loadAllConversations(this).find { it.id == convId }
        }
        val conv = diskConv ?: activeConversation ?: return
        activeConversation = conv

        // Reset any prior messages so only the latest AI answer receives thinking status
        conv.messages.forEach { msg ->
            msg.isThinking = false
            msg.thinkingStatus = null
        }

        // 1. Update Title dynamically from first spoken user prompt (just like standard chat)
        if (conv.title == "New Chat" || conv.messages.isEmpty() || conv.messages.size <= 1) {
            conv.title = if (userText.length > 28) userText.take(28) + "..." else userText
        }

        // 2. Add User Message and Thinking Message to Chat Conversation in real-time
        val userMsg = ChatMessage(
            conversationId = conv.id,
            text = userText,
            isUser = true
        )
        conv.messages.add(userMsg)

        val (avgSec, totalCount) = ResponseStatsRepository.getStats(this)
        val initialTimer = ResponseStatsRepository.formatTimerString(this, 0L, avgSec, totalCount)

        val aiMsg = ChatMessage(
            conversationId = conv.id,
            text = "",
            isUser = false,
            isThinking = true,
            thinkingStatus = "🔍 Processing message… ($initialTimer)"
        )
        conv.messages.add(aiMsg)

        conv.lastUpdated = System.currentTimeMillis()
        ChatRepository.saveOrUpdateConversation(this, conv)

        // 3. Launch ChatAiForegroundService so generation continues strictly in background even if user exits call
        ChatAiForegroundService.startService(
            context = this,
            conversationId = conv.id,
            userMessage = userText,
            userAttachments = emptyList(),
            targetMessageId = aiMsg.id
        )
    }

    private fun speakAiResponse(text: String) {
        if (isTtsReady) {
            stopListeningLoop()
            isAiResponding = true
            tvVoiceStatus.text = "Responding..."
            tvVoiceSubStatus.text = "Memossist Live Voice"

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MEMOSSIST_LIVE_UTTERANCE")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MEMOSSIST_LIVE_UTTERANCE")
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isMuted) startListeningLoop()
            }, 2000)
        }
    }

    private var voiceCallStoppedReceiver: android.content.BroadcastReceiver? = null

    private fun registerCallStoppedReceiver() {
        if (voiceCallStoppedReceiver == null) {
            voiceCallStoppedReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == VoiceForegroundService.ACTION_VOICE_CALL_STOPPED_EVENT) {
                        Toast.makeText(this@VoiceConversationActivity, "Voice call ended from notification", Toast.LENGTH_SHORT).show()
                        finishWithSmoothAnimation()
                    }
                }
            }
            val filter = android.content.IntentFilter(VoiceForegroundService.ACTION_VOICE_CALL_STOPPED_EVENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(voiceCallStoppedReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(voiceCallStoppedReceiver, filter)
            }
        }
    }

    private fun unregisterCallStoppedReceiver() {
        try {
            voiceCallStoppedReceiver?.let { unregisterReceiver(it) }
            voiceCallStoppedReceiver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAllVoiceEngines() {
        isCallEnding = true
        try {
            VoiceForegroundService.stopService(this)
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var chatAiBroadcastReceiver: BroadcastReceiver? = null

    private fun registerChatAiBroadcastReceiver() {
        if (chatAiBroadcastReceiver == null) {
            chatAiBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    val convId = intent.getStringExtra(ChatAiForegroundService.EXTRA_CONVERSATION_ID) ?: return

                    if (activeConversation?.id == convId) {
                        if (action == ChatAiForegroundService.ACTION_CHAT_STEP_UPDATE) {
                            val stepText = intent.getStringExtra(ChatAiForegroundService.EXTRA_STEP_TEXT) ?: ""
                            runOnUiThread {
                                tvVoiceStatus.text = "Thinking..."
                                tvVoiceSubStatus.text = stepText
                            }
                        } else if (action == ChatAiForegroundService.ACTION_CHAT_COMPLETED) {
                            val cleanAnswer = intent.getStringExtra(ChatAiForegroundService.EXTRA_ANSWER_TEXT) ?: ""
                            val updatedConv = ChatRepository.loadAllConversations(this@VoiceConversationActivity).find { it.id == convId }
                            if (updatedConv != null) {
                                activeConversation = updatedConv
                            }
                            runOnUiThread {
                                speakAiResponse(cleanAnswer)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ChatAiForegroundService.ACTION_CHAT_STEP_UPDATE)
                addAction(ChatAiForegroundService.ACTION_CHAT_COMPLETED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(chatAiBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(chatAiBroadcastReceiver, filter)
            }
        }
    }

    private fun unregisterChatAiBroadcastReceiver() {
        try {
            val receiver = chatAiBroadcastReceiver
            if (receiver != null) {
                unregisterReceiver(receiver)
            }
            chatAiBroadcastReceiver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupEmptyConversationIfNeeded() {
        val conv = activeConversation ?: return
        val diskConv = ChatRepository.loadAllConversations(this).find { it.id == conv.id }
        val hasMessages = conv.messages.isNotEmpty() || (diskConv != null && diskConv.messages.isNotEmpty())
        if (!hasMessages) {
            ChatRepository.deleteConversation(this, conv.id)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupEmptyConversationIfNeeded()
        unregisterCallStoppedReceiver()
        unregisterChatAiBroadcastReceiver()
        stopAllVoiceEngines()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        cleanupEmptyConversationIfNeeded()
        stopAllVoiceEngines()
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
