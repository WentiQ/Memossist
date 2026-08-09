package com.example.apptempleate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class ChatAiForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        val initialNotification = buildForegroundNotification("🔍 Step 1/6: Retrieving candidate memories…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Memossist:ChatAiLlmWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(30 * 60 * 1000L) // 30 min max timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        val userMessage = intent?.getStringExtra(EXTRA_USER_MESSAGE) ?: ""
        val attachmentsJson = intent?.getStringExtra(EXTRA_ATTACHMENTS_JSON)
        val userAttachments = MemoryVaultRepository.parseAttachments(attachmentsJson)

        if (userMessage.isEmpty() && userAttachments.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        // Execute LLM Pipeline strictly inside Foreground Service process
        executor.execute {
            ChatRepository.processChatMessageWithPipeline(
                context = this@ChatAiForegroundService,
                userMessage = userMessage,
                userAttachments = userAttachments,
                callback = object : ChatRepository.ChatPipelineCallback {
                    override fun onStepUpdate(stepText: String) {
                        updateNotification(stepText)

                        // Synchronize step status on disk
                        try {
                            val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                            val conv = conversations.find { it.id == conversationId }
                            if (conv != null) {
                                val aiMsg = conv.messages.find { it.isThinking }
                                if (aiMsg != null) {
                                    aiMsg.thinkingStatus = stepText
                                    ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Broadcast step update to MainActivity if visible
                        val updateIntent = Intent(ACTION_CHAT_STEP_UPDATE).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_STEP_TEXT, stepText)
                        }
                        sendBroadcast(updateIntent)
                    }

                    override fun onTokenStream(partialText: String) {
                        try {
                            val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                            val conv = conversations.find { it.id == conversationId }
                            if (conv != null) {
                                val aiMsg = conv.messages.find { it.isThinking }
                                if (aiMsg != null) {
                                    aiMsg.text = partialText
                                    ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val streamIntent = Intent(ACTION_CHAT_TOKEN_STREAM).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_PARTIAL_TEXT, partialText)
                        }
                        sendBroadcast(streamIntent)
                    }

                    override fun onCompleted(cleanHumanoidAnswer: String, debugLogText: String, usedAttachments: List<MediaAttachment>) {
                        try {
                            val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                            val conv = conversations.find { it.id == conversationId }
                            if (conv != null) {
                                var aiMsg = conv.messages.find { it.isThinking }
                                if (aiMsg == null) {
                                    aiMsg = ChatMessage(
                                        conversationId = conversationId,
                                        text = cleanHumanoidAnswer,
                                        isUser = false
                                    ).also { conv.messages.add(it) }
                                }

                                aiMsg.isThinking = false
                                aiMsg.thinkingStatus = null
                                aiMsg.text = cleanHumanoidAnswer
                                aiMsg.debugLog = debugLogText
                                aiMsg.attachments = usedAttachments
                                conv.lastUpdated = System.currentTimeMillis()
                                ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val completedIntent = Intent(ACTION_CHAT_COMPLETED).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_ANSWER_TEXT, cleanHumanoidAnswer)
                            putExtra(EXTRA_DEBUG_LOG, debugLogText)
                        }
                        sendBroadcast(completedIntent)

                        // If user is currently in background, throw status bar completion notification
                        if (!AppLifecycleTracker.isAppInForeground) {
                            ChatRepository.sendChatAnswerNotification(
                                this@ChatAiForegroundService,
                                userMessage,
                                cleanHumanoidAnswer
                            )
                        }

                        // Clean up service & WakeLock
                        try {
                            if (wakeLock?.isHeld == true) {
                                wakeLock?.release()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        stopForeground(true)
                        stopSelf()
                    }
                }
            )
        }

        return START_REDELIVER_INTENT
    }

    private fun updateNotification(stepText: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(stepText))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildForegroundNotification(stepText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sparkles)
            .setContentTitle("Memossist AI Processing 🧠")
            .setContentText(stepText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Background AI Processing",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Ongoing status notification while Memossist LLM processes chat responses in the background"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "memossist_chat_bg_channel"
        const val NOTIFICATION_ID = 9991

        const val EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID"
        const val EXTRA_USER_MESSAGE = "EXTRA_USER_MESSAGE"
        const val EXTRA_ATTACHMENTS_JSON = "EXTRA_ATTACHMENTS_JSON"

        const val ACTION_CHAT_STEP_UPDATE = "com.example.apptempleate.ACTION_CHAT_STEP_UPDATE"
        const val ACTION_CHAT_TOKEN_STREAM = "com.example.apptempleate.ACTION_CHAT_TOKEN_STREAM"
        const val ACTION_CHAT_COMPLETED = "com.example.apptempleate.ACTION_CHAT_COMPLETED"
        
        const val EXTRA_STEP_TEXT = "EXTRA_STEP_TEXT"
        const val EXTRA_PARTIAL_TEXT = "EXTRA_PARTIAL_TEXT"
        const val EXTRA_ANSWER_TEXT = "EXTRA_ANSWER_TEXT"
        const val EXTRA_DEBUG_LOG = "EXTRA_DEBUG_LOG"

        fun startService(context: Context, conversationId: String, userMessage: String, userAttachments: List<MediaAttachment>) {
            try {
                val intent = Intent(context, ChatAiForegroundService::class.java).apply {
                    putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(EXTRA_USER_MESSAGE, userMessage)
                    putExtra(EXTRA_ATTACHMENTS_JSON, MemoryVaultRepository.serializeAttachments(userAttachments))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
