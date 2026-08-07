package com.example.apptempleate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
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
                title = "Voice Call Conversation",
                lastUpdated = System.currentTimeMillis()
            )
            ChatRepository.saveOrUpdateConversation(this, activeConversation!!)
        }

        // Initialize TextToSpeech engine
        textToSpeech = TextToSpeech(this, this)

        // Setup Controls
        btnEndCall.setOnClickListener {
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
                    tvVoiceStatus.text = "Thinking..."
                    tvVoiceSubStatus.text = "Processing voice input..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (!isMuted && !isAiResponding) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            startListeningLoop()
                        }, 1200)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val userSpeech = matches[0]
                        if (userSpeech.isNotBlank()) {
                            processUserVoiceInput(userSpeech)
                            return
                        }
                    }
                    if (!isMuted) {
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
        tvVoiceStatus.text = "Thinking..."
        tvVoiceSubStatus.text = "\"$userText\""

        val conv = activeConversation ?: return

        // 1. Update Title if it was default
        if (conv.messages.isEmpty()) {
            conv.title = if (userText.length > 28) userText.take(28) + "..." else userText
        }

        // 2. Add User Message to Chat Conversation in real-time
        val userMsg = ChatMessage(
            conversationId = conv.id,
            text = userText,
            isUser = true
        )
        conv.messages.add(userMsg)
        conv.lastUpdated = System.currentTimeMillis()
        ChatRepository.saveOrUpdateConversation(this, conv)

        // 3. Automatically save voice call experience into Memory Vault
        val expId = "EXP-${UUID.randomUUID().toString().take(6).uppercase()}"
        val memoryItem = MemoryItem(
            id = expId,
            title = if (userText.length > 32) userText.take(32) + "..." else userText,
            snippet = if (userText.length > 70) userText.take(70) + "..." else userText,
            message = userText,
            timestamp = MemoryVaultRepository.formatCurrentTime(),
            location = MemoryVaultRepository.getCurrentLocation(),
            tag = "Audio",
            timeAgo = "Just now"
        )
        MemoryVaultRepository.saveMemory(this, memoryItem)

        // 4. Generate AI Response and speak it out
        val aiResponse = ChatRepository.generateAiResponse(userText)

        // 5. Add AI Message to Chat Conversation in real-time
        val aiMsg = ChatMessage(
            conversationId = conv.id,
            text = aiResponse,
            isUser = false
        )
        conv.messages.add(aiMsg)
        conv.lastUpdated = System.currentTimeMillis()
        ChatRepository.saveOrUpdateConversation(this, conv)

        speakAiResponse(aiResponse)
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

    private fun stopAllVoiceEngines() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllVoiceEngines()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        stopAllVoiceEngines()
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
