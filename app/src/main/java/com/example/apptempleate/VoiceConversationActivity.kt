package com.example.apptempleate

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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

    enum class AudioRoute {
        SPEAKER,
        EARPIECE,
        BLUETOOTH,
        WIRED_HEADSET
    }

    private lateinit var btnBackVoice: ImageButton
    private lateinit var btnEndCall: ImageButton
    private lateinit var btnMuteMic: ImageButton
    private lateinit var btnSpeakerToggle: ImageButton
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvVoiceSubStatus: TextView
    private lateinit var tvAudioRouteBadge: TextView
    private lateinit var leafOrbView: LeafOrbView
    private lateinit var flMisTouchShield: android.widget.FrameLayout
    private lateinit var tvShieldSubTitle: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private var activeConversation: Conversation? = null

    private var isMuted = false
    private var isTtsReady = false
    private var isListening = false
    private var isAiResponding = false
    private var isCallEnding = false

    // Audio & Sensors
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    private var proximitySensor: Sensor? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var isPhoneNearEar = false
    private var currentAudioRoute: AudioRoute = AudioRoute.SPEAKER
    private var userManuallyForcedRoute: AudioRoute? = null

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

        btnBackVoice = findViewById(R.id.btnBackVoice)
        btnEndCall = findViewById(R.id.btnEndCall)
        btnMuteMic = findViewById(R.id.btnMuteMic)
        btnSpeakerToggle = findViewById(R.id.btnSpeakerToggle)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        tvVoiceSubStatus = findViewById(R.id.tvVoiceSubStatus)
        tvAudioRouteBadge = findViewById(R.id.tvAudioRouteBadge)
        leafOrbView = findViewById(R.id.leafOrbView)
        flMisTouchShield = findViewById(R.id.flMisTouchShield)
        tvShieldSubTitle = findViewById(R.id.tvShieldSubTitle)

        // Block all touch events when shield is visible
        flMisTouchShield.setOnTouchListener { _, _ -> true }

        // Setup Sensors & WakeLocks
        setupSensorsAndAudio()

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
        }

        // Start Foreground Service to keep Microphone & CPU active
        VoiceForegroundService.startService(this)

        // Register Broadcast Receivers
        registerCallStoppedReceiver()
        registerChatAiBroadcastReceiver()
        registerAudioDeviceReceivers()

        // Initialize TextToSpeech engine
        textToSpeech = TextToSpeech(this, this)

        // Setup Controls
        btnBackVoice.setOnClickListener {
            cleanupEmptyConversationIfNeeded()
            stopAllVoiceEngines()
            finishWithSmoothAnimation()
        }

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
                if (!isAiResponding) {
                    tvVoiceStatus.text = "Listening..."
                    tvVoiceSubStatus.text = "Speak now..."
                    startListeningLoop()
                }
                Toast.makeText(this, "Microphone Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeakerToggle.setOnClickListener {
            handleManualAudioToggle()
        }

        // Apply default audio routing (Speaker by default)
        evaluateAndApplyAudioRoute()

        // Check audio permissions & start Gemini Voice loop
        checkAndStartVoiceCall()
    }

    private fun setupSensorsAndAudio() {
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Memossist:VoiceProximityLock"
            )
        }

        proximitySensor?.let { sensor ->
            sensorManager.registerListener(
                proximityEventListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Register Audio Device Callback for modern Android
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    private val proximityEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || proximitySensor == null) return
            val distance = event.values[0]
            val maxRange = proximitySensor!!.maximumRange
            val isNear = distance < maxRange && distance < 5.0f

            if (isPhoneNearEar != isNear) {
                isPhoneNearEar = isNear
                userManuallyForcedRoute = null // Reset manual override on physical motion
                evaluateAndApplyAudioRoute()

                if (isNear) {
                    // Activate full-screen touch shield for mis-touch prevention
                    flMisTouchShield.visibility = android.view.View.VISIBLE
                    tvShieldSubTitle.text = when (currentAudioRoute) {
                        AudioRoute.EARPIECE -> "In call via earpiece • Screen locked"
                        AudioRoute.BLUETOOTH -> "Pocket protection active • Audio on Bluetooth"
                        AudioRoute.WIRED_HEADSET -> "Pocket protection active • Audio on Headset"
                        else -> "Touch protection active"
                    }

                    // Also engage proximity screen-off wake lock
                    if (proximityWakeLock?.isHeld == false) {
                        try {
                            proximityWakeLock?.acquire(15 * 60 * 1000L) // 15 mins max safe timeout
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // Deactivate touch shield & restore screen
                    flMisTouchShield.visibility = android.view.View.GONE
                    if (proximityWakeLock?.isHeld == true) {
                        try {
                            proximityWakeLock?.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            userManuallyForcedRoute = null
            evaluateAndApplyAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            userManuallyForcedRoute = null
            evaluateAndApplyAudioRoute()
        }
    }

    private var audioBroadcastReceiver: BroadcastReceiver? = null

    private fun registerAudioDeviceReceivers() {
        if (audioBroadcastReceiver == null) {
            audioBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    userManuallyForcedRoute = null
                    evaluateAndApplyAudioRoute()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(audioBroadcastReceiver, filter)
            }
        }
    }

    private fun unregisterAudioDeviceReceivers() {
        try {
            audioBroadcastReceiver?.let { unregisterReceiver(it) }
            audioBroadcastReceiver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }

    private fun isWiredHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun evaluateAndApplyAudioRoute() {
        if (isCallEnding) return

        val targetRoute: AudioRoute = if (userManuallyForcedRoute != null) {
            userManuallyForcedRoute!!
        } else if (isBluetoothHeadsetConnected()) {
            AudioRoute.BLUETOOTH
        } else if (isWiredHeadsetConnected()) {
            AudioRoute.WIRED_HEADSET
        } else if (isPhoneNearEar) {
            AudioRoute.EARPIECE
        } else {
            AudioRoute.SPEAKER // Speaker by default
        }

        applyAudioRoute(targetRoute)
    }

    private fun applyAudioRoute(route: AudioRoute) {
        currentAudioRoute = route
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        when (route) {
            AudioRoute.BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btDev = audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (btDev != null) {
                        audioManager.setCommunicationDevice(btDev)
                    }
                } else {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    audioManager.isSpeakerphoneOn = false
                }
                btnSpeakerToggle.setImageResource(R.drawable.ic_bluetooth)
                tvAudioRouteBadge.text = "Bluetooth"
            }
            AudioRoute.WIRED_HEADSET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val wiredDev = audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    }
                    if (wiredDev != null) {
                        audioManager.setCommunicationDevice(wiredDev)
                    }
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
                btnSpeakerToggle.setImageResource(R.drawable.ic_headphones)
                tvAudioRouteBadge.text = "Headset"
            }
            AudioRoute.EARPIECE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpieceDev = audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpieceDev != null) {
                        audioManager.setCommunicationDevice(earpieceDev)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
                btnSpeakerToggle.setImageResource(R.drawable.ic_earpiece)
                tvAudioRouteBadge.text = "Earpiece"
            }
            AudioRoute.SPEAKER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerDev = audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDev != null) {
                        audioManager.setCommunicationDevice(speakerDev)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                } else {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                btnSpeakerToggle.setImageResource(R.drawable.ic_speaker)
                tvAudioRouteBadge.text = "Speaker"
            }
        }
    }

    private fun handleManualAudioToggle() {
        val hasExternalDevice = isBluetoothHeadsetConnected() || isWiredHeadsetConnected()

        if (hasExternalDevice) {
            // Toggle between External Headset and Speaker
            if (currentAudioRoute == AudioRoute.SPEAKER) {
                userManuallyForcedRoute = if (isBluetoothHeadsetConnected()) AudioRoute.BLUETOOTH else AudioRoute.WIRED_HEADSET
            } else {
                userManuallyForcedRoute = AudioRoute.SPEAKER
            }
        } else {
            // Toggle between Speaker and Earpiece
            userManuallyForcedRoute = if (currentAudioRoute == AudioRoute.SPEAKER) {
                AudioRoute.EARPIECE
            } else {
                AudioRoute.SPEAKER
            }
        }

        evaluateAndApplyAudioRoute()
        Toast.makeText(this, "Switched to ${tvAudioRouteBadge.text}", Toast.LENGTH_SHORT).show()
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
                            if (!isMuted && !isCallEnding) {
                                tvVoiceStatus.text = "Listening..."
                                tvVoiceSubStatus.text = "Speak now..."
                                startListeningLoop()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            isAiResponding = false
                            if (!isMuted && !isCallEnding) {
                                tvVoiceStatus.text = "Listening..."
                                tvVoiceSubStatus.text = "Speak now..."
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
        if (isCallEnding || isMuted || isAiResponding) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (isAiResponding || isMuted || isCallEnding) {
                        stopListeningLoop()
                        return
                    }
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
                    if (!isAiResponding) {
                        tvVoiceStatus.text = "Thinking..."
                        tvVoiceSubStatus.text = "Processing voice input..."
                    }
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isCallEnding || isAiResponding || isMuted) return
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isCallEnding && !isAiResponding && !isMuted) {
                            startListeningLoop()
                        }
                    }, 1200)
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
                    if (!isMuted && !isCallEnding && !isAiResponding) {
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
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processUserVoiceInput(userText: String) {
        if (isCallEnding || userText.isBlank()) return

        // 1. Immediately stop listening and set AI responding flag to prevent speech recognizer restarts
        stopListeningLoop()
        isAiResponding = true

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

        // 2. Update Title dynamically from first spoken user prompt
        if (conv.title == "New Chat" || conv.messages.isEmpty() || conv.messages.size <= 1) {
            conv.title = if (userText.length > 28) userText.take(28) + "..." else userText
        }

        // 3. Add User Message and Thinking Message to Chat Conversation in real-time
        val userMsg = ChatMessage(
            conversationId = conv.id,
            text = userText,
            isUser = true
        )
        conv.messages.add(userMsg)

        val preClass = MessageAnalyzer.analyze(this, userText)
        val initialTimer = ResponseStatsRepository.formatTimerStringForCase(this, 0L, preClass.messageType)

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

        // 4. Launch ChatAiForegroundService so generation continues strictly in background even if user exits call
        ChatAiForegroundService.startService(
            context = this,
            conversationId = conv.id,
            userMessage = userText,
            userAttachments = emptyList(),
            targetMessageId = aiMsg.id
        )
    }

    private fun speakAiResponse(text: String) {
        stopListeningLoop()

        if (text.isBlank()) {
            isAiResponding = false
            if (!isMuted && !isCallEnding) {
                tvVoiceStatus.text = "Listening..."
                tvVoiceSubStatus.text = "Speak now..."
                startListeningLoop()
            }
            return
        }

        if (isTtsReady) {
            isAiResponding = true
            tvVoiceStatus.text = "Responding..."
            tvVoiceSubStatus.text = "Memossist Live Voice"

            val utteranceId = "MEMOSSIST_LIVE_${UUID.randomUUID()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            }
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                isAiResponding = false
                if (!isMuted && !isCallEnding) {
                    tvVoiceStatus.text = "Listening..."
                    tvVoiceSubStatus.text = "Speak now..."
                    startListeningLoop()
                }
            }
        } else {
            isAiResponding = false
            if (!isMuted && !isCallEnding) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isCallEnding && !isMuted && !isAiResponding) {
                        tvVoiceStatus.text = "Listening..."
                        tvVoiceSubStatus.text = "Speak now..."
                        startListeningLoop()
                    }
                }, 1500)
            }
        }
    }

    private var voiceCallStoppedReceiver: BroadcastReceiver? = null

    private fun registerCallStoppedReceiver() {
        if (voiceCallStoppedReceiver == null) {
            voiceCallStoppedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == VoiceForegroundService.ACTION_VOICE_CALL_STOPPED_EVENT) {
                        Toast.makeText(this@VoiceConversationActivity, "Voice call ended from notification", Toast.LENGTH_SHORT).show()
                        finishWithSmoothAnimation()
                    }
                }
            }
            val filter = IntentFilter(VoiceForegroundService.ACTION_VOICE_CALL_STOPPED_EVENT)
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
            // Restore normal audio mode
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }

            // Unregister sensors
            sensorManager.unregisterListener(proximityEventListener)
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }

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
        unregisterAudioDeviceReceivers()
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
