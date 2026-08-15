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
import java.util.concurrent.TimeUnit

class ChatAiForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val wakeLockWatchdog = Executors.newSingleThreadScheduledExecutor()
    private val pendingWork = AtomicInteger(0)
    @Volatile private var lastNotificationUpdateMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        // The service, not the Activity or pipeline callbacks, owns CPU liveness.
        // Some OEMs proxy/release app wake locks during screen-off; this lightweight
        // watchdog restores the lock without depending on UI or token callbacks.
        wakeLockWatchdog.scheduleAtFixedRate(
            { acquireWakeLock() },
            2L,
            2L,
            TimeUnit.SECONDS
        )
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

    @Synchronized
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

    private fun updateMessageThinkingStatus(conversationId: String, targetMessageId: String?, statusText: String) {
        if (targetMessageId.isNullOrEmpty()) return
        try {
            val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
            val conv = conversations.find { it.id == conversationId }
            if (conv != null) {
                val aiMsg = conv.messages.find { it.id == targetMessageId }
                if (aiMsg != null) {
                    aiMsg.thinkingStatus = statusText
                    ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""

        if (action == ACTION_CANCEL_PIPELINE) {
            if (conversationId.isNotEmpty()) {
                ChatRepository.cancelActivePipeline(conversationId)
            }
            return START_NOT_STICKY
        }

        val userMessage = intent?.getStringExtra(EXTRA_USER_MESSAGE) ?: ""
        val targetMessageId = intent?.getStringExtra(EXTRA_TARGET_MESSAGE_ID)
        val attachmentsJson = intent?.getStringExtra(EXTRA_ATTACHMENTS_JSON)
        val userAttachments = MemoryVaultRepository.parseAttachments(attachmentsJson)
        val forcedTypeName = intent?.getStringExtra(EXTRA_FORCED_MESSAGE_TYPE)
        val forcedMessageType = forcedTypeName?.let { runCatching { MessageType.valueOf(it) }.getOrNull() }

        if (userMessage.isEmpty() && userAttachments.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        pendingWork.incrementAndGet()

        val request = QueuedChatRequest(
            conversationId = conversationId,
            targetMessageId = targetMessageId ?: "",
            userMessage = userMessage,
            userAttachments = userAttachments,
            forcedMessageType = forcedMessageType
        )
        chatRequestQueue.add(request)

        val queuePos = chatRequestQueue.indexOf(request)
        if (queuePos > 0 && !targetMessageId.isNullOrEmpty()) {
            val queueText = "⏳ In queue $queuePos: Waiting for previous response…"
            updateMessageThinkingStatus(conversationId, targetMessageId, queueText)
            val updateIntent = Intent(ACTION_CHAT_STEP_UPDATE).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_TARGET_MESSAGE_ID, targetMessageId)
                putExtra(EXTRA_STEP_TEXT, queueText)
            }
            sendBroadcast(updateIntent)
        }

        // This executor is deliberately owned by the foreground service. The
        // pipeline runs sequentially (FIFO) on it.
        executor.execute {
            try {
                // When this item starts execution, update relative queue positions of all remaining items
                val activeIdx = chatRequestQueue.indexOf(request)
                if (activeIdx != -1) {
                    for (i in (activeIdx + 1) until chatRequestQueue.size) {
                        val other = chatRequestQueue[i]
                        val relPos = i - activeIdx
                        val queueStatus = "⏳ In queue $relPos: Waiting for previous response…"
                        updateMessageThinkingStatus(other.conversationId, other.targetMessageId, queueStatus)
                        val updateIntent = Intent(ACTION_CHAT_STEP_UPDATE).apply {
                            putExtra(EXTRA_CONVERSATION_ID, other.conversationId)
                            putExtra(EXTRA_TARGET_MESSAGE_ID, other.targetMessageId)
                            putExtra(EXTRA_STEP_TEXT, queueStatus)
                        }
                        sendBroadcast(updateIntent)
                    }
                }

                ChatRepository.processChatMessageWithPipeline(
                context = this@ChatAiForegroundService,
                conversationId = conversationId,
                userMessage = userMessage,
                userAttachments = userAttachments,
                forcedMessageType = forcedMessageType,
                callback = object : ChatRepository.ChatPipelineCallback {
                    var lastStepDiskSyncMs = 0L

                    override fun onStepUpdate(stepText: String) {
                        updateNotification(stepText)

                        // Throttle step status disk synchronization to avoid blocking I/O on every 1s tick
                        val now = System.currentTimeMillis()
                        if (now - lastStepDiskSyncMs > 3000L) {
                            lastStepDiskSyncMs = now
                            try {
                                val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                                val conv = conversations.find { it.id == conversationId }
                                if (conv != null) {
                                    val aiMsg = (if (!targetMessageId.isNullOrEmpty()) conv.messages.find { it.id == targetMessageId } else null)
                                        ?: conv.messages.findLast { it.isThinking }
                                        ?: conv.messages.findLast { !it.isUser }
                                    if (aiMsg != null) {
                                        aiMsg.thinkingStatus = stepText
                                        ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Broadcast step update to UI immediately with targetMessageId
                        val updateIntent = Intent(ACTION_CHAT_STEP_UPDATE).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_TARGET_MESSAGE_ID, targetMessageId)
                            putExtra(EXTRA_STEP_TEXT, stepText)
                        }
                        sendBroadcast(updateIntent)
                    }

                    var lastStreamDiskSyncMs = 0L

                    override fun onTokenStream(partialText: String) {
                        val now = System.currentTimeMillis()
                        if (now - lastStreamDiskSyncMs > 1500L) {
                            lastStreamDiskSyncMs = now
                            try {
                                val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                                val conv = conversations.find { it.id == conversationId }
                                if (conv != null) {
                                    val aiMsg = (if (!targetMessageId.isNullOrEmpty()) conv.messages.find { it.id == targetMessageId } else null)
                                        ?: conv.messages.findLast { it.isThinking }
                                        ?: conv.messages.findLast { !it.isUser }
                                    if (aiMsg != null) {
                                        aiMsg.text = partialText
                                        ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val streamIntent = Intent(ACTION_CHAT_TOKEN_STREAM).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_TARGET_MESSAGE_ID, targetMessageId)
                            putExtra(EXTRA_PARTIAL_TEXT, partialText)
                        }
                        sendBroadcast(streamIntent)
                    }

                    override fun onCompleted(
                        cleanHumanoidAnswer: String,
                        debugLogText: String,
                        usedAttachments: List<MediaAttachment>,
                        createdMemoryIds: List<String>,
                        createdReminderId: String?,
                        factsToEvaluate: List<Pair<String, String>>
                    ) {
                        var targetAiMsgId: String? = null
                        try {
                            val conversations = ChatRepository.loadAllConversations(this@ChatAiForegroundService)
                            val conv = conversations.find { it.id == conversationId }
                            if (conv != null) {
                                var aiMsg = if (!targetMessageId.isNullOrEmpty()) conv.messages.find { it.id == targetMessageId } else null
                                if (aiMsg == null) {
                                    aiMsg = conv.messages.findLast { it.isThinking }
                                }
                                if (aiMsg == null) {
                                    aiMsg = conv.messages.findLast { !it.isUser }
                                }
                                if (aiMsg == null) {
                                    aiMsg = ChatMessage(
                                        conversationId = conversationId,
                                        text = cleanHumanoidAnswer,
                                        isUser = false
                                    ).also { conv.messages.add(it) }
                                }

                                targetAiMsgId = aiMsg.id
                                aiMsg.isThinking = false
                                aiMsg.thinkingStatus = null
                                aiMsg.text = cleanHumanoidAnswer
                                aiMsg.debugLog = debugLogText
                                aiMsg.attachments = usedAttachments
                                aiMsg.createdMemoryIds = createdMemoryIds
                                aiMsg.createdReminderId = createdReminderId

                                // Reset only THIS message's thinking state so other queued messages preserve their queue status
                                conv.lastUpdated = System.currentTimeMillis()
                                ChatRepository.saveOrUpdateConversation(this@ChatAiForegroundService, conv)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Enqueue 2nd LLM parameter scoring in FIFO queue if facts were extracted
                        if (factsToEvaluate.isNotEmpty() && !targetAiMsgId.isNullOrEmpty()) {
                            val factsForEval = factsToEvaluate.map { FactForEvaluation(it.first, it.second) }
                            MemoryParameterQueueManager.enqueueTask(
                                context = this@ChatAiForegroundService,
                                conversationId = conversationId,
                                messageId = targetAiMsgId,
                                facts = factsForEval
                            )
                        }

                        val completedIntent = Intent(ACTION_CHAT_COMPLETED).apply {
                            putExtra(EXTRA_CONVERSATION_ID, conversationId)
                            putExtra(EXTRA_TARGET_MESSAGE_ID, targetMessageId)
                            putExtra(EXTRA_ANSWER_TEXT, cleanHumanoidAnswer)
                            putExtra(EXTRA_DEBUG_LOG, debugLogText)
                        }
                        sendBroadcast(completedIntent)

                        // Send completion notification (will be suppressed if user is inside that chat)
                        ChatRepository.sendChatAnswerNotification(
                            this@ChatAiForegroundService,
                            userMessage,
                            cleanHumanoidAnswer,
                            conversationId
                        )

                    }
                }
                )
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Background chat pipeline failed", t)
                persistFailure(conversationId, t)
            } finally {
                chatRequestQueue.remove(request)
                // Shift and broadcast remaining items in queue
                for (i in 0 until chatRequestQueue.size) {
                    val other = chatRequestQueue[i]
                    val newPos = i // If i == 0, it is now next to start; if i > 0, it is in queue i
                    if (newPos > 0) {
                        val queueStatus = "⏳ In queue $newPos: Waiting for previous response…"
                        updateMessageThinkingStatus(other.conversationId, other.targetMessageId, queueStatus)
                        val updateIntent = Intent(ACTION_CHAT_STEP_UPDATE).apply {
                            putExtra(EXTRA_CONVERSATION_ID, other.conversationId)
                            putExtra(EXTRA_TARGET_MESSAGE_ID, other.targetMessageId)
                            putExtra(EXTRA_STEP_TEXT, queueStatus)
                        }
                        sendBroadcast(updateIntent)
                    }
                }
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
        updateNotification("Generating your answer in the background…", force = true)
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
            val aiMessage = conversation?.messages?.findLast { it.isThinking } ?: conversation?.messages?.findLast { !it.isUser }
            if (conversation != null && aiMessage != null) {
                aiMessage.isThinking = false
                aiMessage.thinkingStatus = null
                aiMessage.text = message
                aiMessage.debugLog = "Background inference failed: ${error.message ?: error.javaClass.simpleName}"
                conversation.messages.forEach { msg ->
                    if (msg != aiMessage) {
                        msg.isThinking = false
                        msg.thinkingStatus = null
                    }
                }
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

    private fun updateNotification(stepText: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        // Updating an ongoing notification every second causes ColorOS to proxy
        // wake locks. Status is useful, but a 5-second cadence is sufficient.
        if (!force && now - lastNotificationUpdateMs < 1_000L) return
        lastNotificationUpdateMs = now
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
        wakeLockWatchdog.shutdownNow()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChatAiForegroundService"
        // A new ID replaces the previous channel whose user/device settings may
        // still allow a sound for every status refresh.
        const val CHANNEL_ID = "memossist_chat_bg_channel_v2"
        const val NOTIFICATION_ID = 9991

        data class QueuedChatRequest(
            val conversationId: String,
            val targetMessageId: String,
            val userMessage: String,
            val userAttachments: List<MediaAttachment>,
            val forcedMessageType: MessageType?
        )

        private val chatRequestQueue = java.util.concurrent.CopyOnWriteArrayList<QueuedChatRequest>()

        fun getGlobalPendingCount(): Int = chatRequestQueue.size

        fun getGlobalQueuePosition(conversationId: String, messageId: String): Int {
            val idx = chatRequestQueue.indexOfFirst { it.conversationId == conversationId && it.targetMessageId == messageId }
            return if (idx >= 0) idx else chatRequestQueue.size
        }

        const val EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID"
        const val EXTRA_USER_MESSAGE = "EXTRA_USER_MESSAGE"
        const val EXTRA_TARGET_MESSAGE_ID = "EXTRA_TARGET_MESSAGE_ID"
        const val EXTRA_ATTACHMENTS_JSON = "EXTRA_ATTACHMENTS_JSON"
        const val EXTRA_FORCED_MESSAGE_TYPE = "EXTRA_FORCED_MESSAGE_TYPE"

        const val ACTION_CHAT_STEP_UPDATE = "com.example.apptempleate.ACTION_CHAT_STEP_UPDATE"
        const val ACTION_CHAT_TOKEN_STREAM = "com.example.apptempleate.ACTION_CHAT_TOKEN_STREAM"
        const val ACTION_CHAT_COMPLETED = "com.example.apptempleate.ACTION_CHAT_COMPLETED"
        const val ACTION_CANCEL_PIPELINE = "com.example.apptempleate.ACTION_CANCEL_PIPELINE"
        const val ACTION_PARAM_EVALUATION_UPDATE = "com.example.apptempleate.ACTION_PARAM_EVALUATION_UPDATE"
        
        const val EXTRA_STEP_TEXT = "EXTRA_STEP_TEXT"
        const val EXTRA_PARTIAL_TEXT = "EXTRA_PARTIAL_TEXT"
        const val EXTRA_ANSWER_TEXT = "EXTRA_ANSWER_TEXT"
        const val EXTRA_DEBUG_LOG = "EXTRA_DEBUG_LOG"
        const val EXTRA_MESSAGE_ID = "EXTRA_MESSAGE_ID"
        const val EXTRA_PARAM_STATUS = "EXTRA_PARAM_STATUS"
        const val EXTRA_PARAM_TEXT = "EXTRA_PARAM_TEXT"

        fun startService(
            context: Context,
            conversationId: String,
            userMessage: String,
            userAttachments: List<MediaAttachment> = emptyList(),
            targetMessageId: String? = null,
            forcedMessageType: MessageType? = null
        ) {
            try {
                val intent = Intent(context, ChatAiForegroundService::class.java).apply {
                    putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(EXTRA_USER_MESSAGE, userMessage)
                    if (!targetMessageId.isNullOrEmpty()) {
                        putExtra(EXTRA_TARGET_MESSAGE_ID, targetMessageId)
                    }
                    if (forcedMessageType != null) {
                        putExtra(EXTRA_FORCED_MESSAGE_TYPE, forcedMessageType.name)
                    }
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

        fun cancelActiveChat(context: Context, conversationId: String) {
            try {
                ChatRepository.cancelActivePipeline(conversationId)
                val intent = Intent(context, ChatAiForegroundService::class.java).apply {
                    action = ACTION_CANCEL_PIPELINE
                    putExtra(EXTRA_CONVERSATION_ID, conversationId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
