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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors

class ChatAiForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val pendingWork = AtomicInteger(0)

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
                // The service releases this in finishWork/onDestroy. A timed lock
                // would let a longer local-model inference get suspended mid-answer.
                wakeLock?.acquire()
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
        pendingWork.incrementAndGet()

        // This executor is deliberately owned by the foreground service. The
        // pipeline now runs synchronously on it, so an Activity lifecycle change
        // cannot orphan or pause the native inference work.
        executor.execute {
            try {
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

                    }
                }
                )
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Background chat pipeline failed", t)
                persistFailure(conversationId, t)
            } finally {
                finishWork(startId)
            }
        }

        // If Android reclaims the process, re-deliver the persisted request and
        // resume it in a fresh foreground service instead of silently abandoning it.
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.i(TAG, "UI task removed; keeping background inference alive")
        acquireWakeLock()
        updateNotification("Generating your answer in the background…")
        super.onTaskRemoved(rootIntent)
    }

    private fun finishWork(startId: Int) {
        if (pendingWork.decrementAndGet() > 0) return
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Unable to release chat wake lock", e)
        }
        stopForeground(true)
        stopSelfResult(startId)
    }

    private fun persistFailure(conversationId: String, error: Throwable) {
        val message = "I couldn't complete that response. Please try again."
        try {
            val conversations = ChatRepository.loadAllConversations(this)
            val conversation = conversations.find { it.id == conversationId }
            val aiMessage = conversation?.messages?.find { it.isThinking }
            if (conversation != null && aiMessage != null) {
                aiMessage.isThinking = false
                aiMessage.thinkingStatus = null
                aiMessage.text = message
                aiMessage.debugLog = "Background inference failed: ${error.message ?: error.javaClass.simpleName}"
                conversation.lastUpdated = System.currentTimeMillis()
                ChatRepository.saveOrUpdateConversation(this, conversation)
            }
        } catch (saveError: Exception) {
            android.util.Log.e(TAG, "Unable to persist failed chat response", saveError)
        }
        sendBroadcast(Intent(ACTION_CHAT_COMPLETED).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_ANSWER_TEXT, message)
        })
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
            .setOnlyAlertOnce(true)
            .setSilent(true)
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
                    NotificationManager.IMPORTANCE_LOW
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
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChatAiForegroundService"
        // A new ID replaces the previous channel whose user/device settings may
        // still allow a sound for every status refresh.
        const val CHANNEL_ID = "memossist_chat_bg_channel_v2"
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
