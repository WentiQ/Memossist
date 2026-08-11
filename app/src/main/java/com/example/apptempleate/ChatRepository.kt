package com.example.apptempleate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ChatRepository {

    private const val FILE_NAME = "memossist_conversations.json"
    private const val BACKUP_FILE_NAME = "memossist_conversations_backup.json"
    private val mainHandler = Handler(Looper.getMainLooper())

    interface ChatPipelineCallback {
        fun onStepUpdate(stepText: String)
        fun onTokenStream(partialText: String)
        fun onCompleted(cleanHumanoidAnswer: String, debugLogText: String, usedAttachments: List<MediaAttachment>)
    }

    private fun buildLlmSystemPrompt(userPrompt: String, candidateExperiences: List<MemoryItem>): String {
        val sb = StringBuilder()
        sb.append("=== SYSTEM INSTRUCTION FOR MEMOSSIST LLM ENGINE ===\n")
        sb.append("You are Memossist, an intelligent humanoid AI assistant with access to the user's Memory Vault.\n")
        sb.append("Use the top candidate experiences provided below to answer the user's question.\n\n")
        sb.append("INSTRUCTION RULES:\n")
        sb.append("1. Provide a natural, humanoid, insightful answer synthesized in your own conversational words.\n")
        sb.append("2. DO NOT just copy-paste exact statements or dump raw memory text verbatim.\n")
        sb.append("3. Declare ONLY the IDs of experiences actually used to answer in format: [USED_EXPERIENCES: EXP-ID1, EXP-ID2]\n\n")
        sb.append("=== CANDIDATE EXPERIENCES ===\n")

        if (candidateExperiences.isEmpty()) {
            sb.append("(No relevant stored experiences found in Memory Vault)\n")
        } else {
            for ((index, exp) in candidateExperiences.withIndex()) {
                sb.append("${index + 1}. [ID: ${exp.id}] Title: ${exp.title}\n")
                sb.append("   Time: ${exp.timestamp}\n")
                sb.append("   Location: ${exp.location}\n")
                sb.append("   Content: ${exp.message}\n\n")
            }
        }

        sb.append("=== USER QUESTION ===\n")
        sb.append("\"$userPrompt\"\n")

        return sb.toString()
    }

    /**
     * Executes the 6-step workflow:
     * Step 1: Send user message & top 5 candidate experiences to LLM.
     * Step 2: LLM intent understanding (asking vs telling) & selective fact extraction into Memory Vault.
     * Step 3: LLM returns humanoid answer, relevant experience IDs (if used), & extracted fact.
     * Step 4: Extract relevant experience IDs & user message.
     * Step 5: Calculate DAG connection strengths using ONLY the relevant IDs returned.
     * Step 6: Show ONLY the clean humanoid answer in chat bubble (Long press opens full debug log).
     */
    fun processChatMessageWithPipeline(
        context: Context,
        userMessage: String,
        userAttachments: List<MediaAttachment> = emptyList(),
        callback: ChatPipelineCallback
    ) {
            // The caller is the chat foreground service's single worker.  Do not
            // create a detached executor here: doing so lets the service lose
            // ownership of an in-flight inference when the activity goes away.
            val startTimeMs = System.currentTimeMillis()
            var currentBaseStepText = "Step 1/6: Retrieving candidate memories…"
            var isPipelineRunning = true

            val tickerExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            val tickerFuture = tickerExecutor.scheduleAtFixedRate({
                if (isPipelineRunning) {
                    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
                    val (avgSec, totalCount) = ResponseStatsRepository.getStats(context)
                    val timerStr = ResponseStatsRepository.formatTimerString(context, elapsedSec, avgSec, totalCount)
                    callback.onStepUpdate("$currentBaseStepText ($timerStr)")
                }
            }, 0L, 1L, java.util.concurrent.TimeUnit.SECONDS)

            // STEP 1: Retrieve Candidate Memories from Memory Vault
            currentBaseStepText = "Step 1/6: Retrieving candidate memories…"
            val topCandidates = ExperienceDagRepository.retrieveTopMatchingExperiences(context, userMessage, topK = 5)
            val systemPromptStr = NoeonAiEngine.buildSystemPrompt(topCandidates)

            // STEP 2: LLM Intent Understanding, Fact Filtering & Humanoid Answer Synthesis
            currentBaseStepText = "Step 2/6: LLM generating response…"

            val tokenBuffer = StringBuilder()
            val llmResult = NoeonAiEngine.processMessagePipeline(
                context = context,
                userMessage = userMessage,
                candidateExperiences = topCandidates,
                onTokenGenerated = { token ->
                    tokenBuffer.append(token)
                    val currentStreamText = tokenBuffer.toString()
                    callback.onTokenStream(currentStreamText)
                }
            )

            // STEP 3: Save Facts Extracted Strictly by LLM into Memory Vault (with user attachments)
            currentBaseStepText = "Step 3/6: Reading LLM extracted facts & reminders…"
            if (llmResult.extractedInformativeFacts.isNotEmpty()) {
                for (fact in llmResult.extractedInformativeFacts) {
                    val expId = "EXP-${UUID.randomUUID().toString().take(6).uppercase()}"
                    val memoryItem = MemoryItem(
                        id = expId,
                        title = if (fact.length > 32) fact.take(32) + "..." else fact,
                        snippet = if (fact.length > 70) fact.take(70) + "..." else fact,
                        message = fact,
                        timestamp = MemoryVaultRepository.formatCurrentTime(),
                        location = MemoryVaultRepository.getCurrentLocation(),
                        tag = "Chat Fact",
                        timeAgo = "Just now",
                        attachments = userAttachments
                    )
                    MemoryVaultRepository.saveMemory(context, memoryItem)
                }
            }

            // Extract and set Smart Reminders if present in user message
            val extractedReminder = ReminderExtractor.extractAndCreateReminder(context, userMessage, llmResult.extractedReminderTag)
            var reminderConfirmationBanner = ""
            if (extractedReminder != null) {
                ReminderRepository.addOrUpdateReminder(context, extractedReminder)
                reminderConfirmationBanner = "\n\n⏰ **Reminder Set!** ${extractedReminder.getCategoryIconText()} `${extractedReminder.title}` on ${extractedReminder.getFormattedEventDateTime()} (${extractedReminder.triggers.size} scheduled alerts)."
            }

            currentBaseStepText = "Step 4/6: Updating memory vault records…"

            // Step 4 & 5: Calculate DAG connection strengths using ONLY the relevant IDs returned from LLM
            currentBaseStepText = "Step 5/6: Updating used-memory connections…"

            val returnedUsedIdsSet = llmResult.relevantExperienceIds.toSet()

            val dagSummary = ExperienceDagRepository.updateDagConnections(
                context = context,
                userQuestion = userMessage,
                candidateExperiences = topCandidates,
                usedExperienceIds = returnedUsedIdsSet
            )

            // Gather any media attachments linked to the used experiences
            val usedExperienceAttachments = mutableListOf<MediaAttachment>()
            if (returnedUsedIdsSet.isNotEmpty()) {
                val allVaultMemories = MemoryVaultRepository.loadAllMemories(context)
                for (usedId in returnedUsedIdsSet) {
                    val foundMem = allVaultMemories.find { it.id.equals(usedId, ignoreCase = true) }
                    if (foundMem != null && foundMem.attachments.isNotEmpty()) {
                        usedExperienceAttachments.addAll(foundMem.attachments)
                    }
                }
            }

            // Build detailed developer diagnostic log string
            val debugLogBuilder = StringBuilder()
            debugLogBuilder.append("=== 🤖 LLM ENGINE & MODEL ===\n")
            debugLogBuilder.append("Model: ${llmResult.modelName}\n")
            debugLogBuilder.append("Engine Path: ${NoeonAiEngine.getActiveModelFilePath(context)}\n\n")

            debugLogBuilder.append("=== 💬 FULL SYSTEM PROMPT SENT TO LLM ===\n")
            debugLogBuilder.append(systemPromptStr)
            debugLogBuilder.append("\n\n")

            debugLogBuilder.append("=== 📤 RAW LLM OUTPUT ===\n")
            debugLogBuilder.append(llmResult.cleanHumanoidAnswer)
            debugLogBuilder.append("\n\n[USED_EXPERIENCES: ${llmResult.relevantExperienceIds.joinToString(", ")}]\n")
            debugLogBuilder.append("[EXTRACTED_FACTS: ${llmResult.extractedInformativeFacts}]\n")
            if (extractedReminder != null) {
                debugLogBuilder.append("[EXTRACTED_REMINDER: Title=${extractedReminder.title}, Time=${extractedReminder.getFormattedEventDateTime()}, Triggers=${extractedReminder.triggers.size}]\n")
            }
            debugLogBuilder.append("\n")

            debugLogBuilder.append("=== 🧠 INTENT UNDERSTANDING & VAULT ACTION ===\n")
            debugLogBuilder.append("Intent Classified: ${llmResult.intent}\n")
            debugLogBuilder.append("Saved facts: ${llmResult.extractedInformativeFacts.ifEmpty { listOf("None") }.joinToString()}\n\n")

            debugLogBuilder.append("=== 🧮 MATHEMATICAL DAG CONNECTION STRENGTH CALCULATIONS ===\n")
            debugLogBuilder.append("Retrived Top 5 Candidate Experiences: ${topCandidates.size}\n")
            debugLogBuilder.append("Union Vocabulary Size (N): ${dagSummary.unionN} POS terms (Nouns, Verbs, Adjectives, Adverbs)\n")
            debugLogBuilder.append("LLM Returned Used Experience IDs (t=1): ${llmResult.relevantExperienceIds.joinToString(", ")}\n")
            debugLogBuilder.append("Formula Applied: S_ij_new = S_ij_old + (|Q ∩ Ni ∩ Nj| × t) / N\n\n")

            if (dagSummary.updatedEdges.isNotEmpty()) {
                for (edge in dagSummary.updatedEdges) {
                    val deltaStr = String.format("%.4f", edge.deltaS)
                    val newSStr = String.format("%.4f", edge.newStrengthS)
                    val tLabel = if (edge.usedT == 1) "t=1 (Both Used)" else "t=0 (Unused)"
                    debugLogBuilder.append("• Edge: [${edge.exp1Title}] ↔ [${edge.exp2Title}]\n")
                    debugLogBuilder.append("  ↳ C = |Q ∩ N1 ∩ N2| = ${edge.commonCountC} shared POS terms (${edge.commonTerms.joinToString(", ")})\n")
                    debugLogBuilder.append("  ↳ ΔS = (${edge.commonCountC} × ${edge.usedT}) / ${dagSummary.unionN} = +$deltaStr | New S_ij = $newSStr ($tLabel)\n\n")
                }
            } else {
                debugLogBuilder.append("No active candidate pairs available for edge calculation.\n")
            }

            val finalDebugLog = debugLogBuilder.toString()

            val finalAnswer = llmResult.cleanHumanoidAnswer + reminderConfirmationBanner

            currentBaseStepText = "Step 6/6: Preparing the answer…"

            // Stop 1-second ticker loop
            isPipelineRunning = false
            try { tickerFuture.cancel(true) } catch (e: Exception) {}
            try { tickerExecutor.shutdown() } catch (e: Exception) {}

            // Record exact duration for online running average calculation
            val durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0f
            ResponseStatsRepository.recordNewResponseTime(context, durationSec)

            // Invoke completion callback directly on execution thread
            callback.onCompleted(finalAnswer, finalDebugLog, usedExperienceAttachments)

            // If the app is in background or screen turned off, send a status bar notification
            if (!AppLifecycleTracker.isAppInForeground) {
                sendChatAnswerNotification(context, userMessage, finalAnswer)
            }
    }

    fun sendChatAnswerNotification(context: Context, userQuery: String, cleanAnswerText: String, conversationId: String? = null) {
        // Do not post or record notification if user is currently inside that particular chat
        if (AppLifecycleTracker.isAppInForeground && conversationId != null) {
            val activeConvId = MainActivity.activeConversationId
            if (activeConvId == conversationId) {
                return
            }
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "memossist_chat_answers_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = notificationManager.getNotificationChannel(channelId)
                if (existing == null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Chat AI Answers",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications when Memossist AI completes generating your chat response"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                        enableLights(true)
                        lightColor = android.graphics.Color.BLUE
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }

            // Save to 30-day Notification Center History
            NotificationHistoryRepository.addNotification(
                context = context,
                notification = NotificationItem(
                    id = UUID.randomUUID().toString(),
                    reminderId = null,
                    conversationId = conversationId,
                    title = "Memossist Answer Ready 💬",
                    message = if (cleanAnswerText.length > 120) cleanAnswerText.take(120) + "..." else cleanAnswerText,
                    timestamp = System.currentTimeMillis(),
                    type = "CHAT_ANSWER",
                    isRead = false
                )
            )

            // Intent to open MainActivity directly into target conversation
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!conversationId.isNullOrEmpty()) {
                    putExtra("OPEN_CONVERSATION_ID", conversationId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                userQuery.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_sparkles)
                .setContentTitle("Memossist Answer Ready 💬")
                .setContentText(cleanAnswerText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cleanAnswerText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 400, 200, 400))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify((userQuery.hashCode()), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAllConversations(context: Context): MutableList<Conversation> {
        val file = File(context.filesDir, FILE_NAME)
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)

        if (!file.exists() && backupFile.exists()) {
            try { backupFile.copyTo(file, overwrite = true) } catch (e: Exception) {}
        }

        if (!file.exists()) {
            return mutableListOf()
        }

        val conversations = mutableListOf<Conversation>()
        try {
            val jsonStr = file.readText()
            val conversationsArray = JSONArray(jsonStr)

            for (i in 0 until conversationsArray.length()) {
                try {
                    val convObj = conversationsArray.getJSONObject(i)
                    val id = convObj.getString("id")
                    val title = convObj.getString("title")
                    val lastUpdated = convObj.optLong("lastUpdated", System.currentTimeMillis())
                    val isPinned = convObj.optBoolean("isPinned", false)

                    val messagesList = mutableListOf<ChatMessage>()
                    val msgArray = convObj.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until msgArray.length()) {
                        try {
                            val msgObj = msgArray.getJSONObject(j)
                            val msgId = msgObj.optString("id", UUID.randomUUID().toString())
                            val msgConvId = msgObj.optString("conversationId", id)
                            val text = msgObj.optString("text", "")
                            val isUser = msgObj.optBoolean("isUser", false)
                            val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                            val isThinking = msgObj.optBoolean("isThinking", false)
                            val thinkingStatus = msgObj.optString("thinkingStatus", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val debugLog = msgObj.optString("debugLog", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val rawAttJson = msgObj.optString("attachmentsJson", null)
                            val msgAtts = MemoryVaultRepository.parseAttachments(rawAttJson)

                            messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp, isThinking, thinkingStatus, debugLog, msgAtts))
                        } catch (me: Exception) {
                            me.printStackTrace()
                        }
                    }

                    conversations.add(Conversation(id, title, lastUpdated, isPinned, messagesList))
                } catch (ce: Exception) {
                    ce.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If primary file fails, attempt reading from backup safely without overwriting primary file
            if (backupFile.exists()) {
                try {
                    val backupStr = backupFile.readText()
                    val backupArray = JSONArray(backupStr)
                    for (i in 0 until backupArray.length()) {
                        val convObj = backupArray.getJSONObject(i)
                        val id = convObj.getString("id")
                        val title = convObj.getString("title")
                        val lastUpdated = convObj.optLong("lastUpdated", System.currentTimeMillis())
                        val isPinned = convObj.optBoolean("isPinned", false)

                        val messagesList = mutableListOf<ChatMessage>()
                        val msgArray = convObj.optJSONArray("messages") ?: JSONArray()
                        for (j in 0 until msgArray.length()) {
                            val msgObj = msgArray.getJSONObject(j)
                            val msgId = msgObj.optString("id", UUID.randomUUID().toString())
                            val msgConvId = msgObj.optString("conversationId", id)
                            val text = msgObj.optString("text", "")
                            val isUser = msgObj.optBoolean("isUser", false)
                            val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                            val isThinking = msgObj.optBoolean("isThinking", false)
                            val thinkingStatus = msgObj.optString("thinkingStatus", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val debugLog = msgObj.optString("debugLog", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val rawAttJson = msgObj.optString("attachmentsJson", null)
                            val msgAtts = MemoryVaultRepository.parseAttachments(rawAttJson)

                            messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp, isThinking, thinkingStatus, debugLog, msgAtts))
                        }

                        conversations.add(Conversation(id, title, lastUpdated, isPinned, messagesList))
                    }
                } catch (be: Exception) {
                    be.printStackTrace()
                }
            }
        }

        conversations.sortWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.lastUpdated })
        return conversations
    }

    fun saveAllConversations(context: Context, conversations: List<Conversation>) {
        try {
            val conversationsArray = JSONArray()
            for (conv in conversations) {
                val convObj = JSONObject().apply {
                    put("id", conv.id)
                    put("title", conv.title)
                    put("lastUpdated", conv.lastUpdated)
                    put("isPinned", conv.isPinned)

                    val msgArray = JSONArray()
                    for (msg in conv.messages) {
                        val msgObj = JSONObject().apply {
                            put("id", msg.id)
                            put("conversationId", msg.conversationId)
                            put("text", msg.text)
                            put("isUser", msg.isUser)
                            put("timestamp", msg.timestamp)
                            put("isThinking", msg.isThinking)
                            put("thinkingStatus", msg.thinkingStatus)
                            put("debugLog", msg.debugLog)
                            put("attachmentsJson", MemoryVaultRepository.serializeAttachments(msg.attachments))
                        }
                        msgArray.put(msgObj)
                    }
                    put("messages", msgArray)
                }
                conversationsArray.put(convObj)
            }

            val jsonStr = conversationsArray.toString()
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonStr)

            // Write backup copy safely
            try {
                val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
                backupFile.writeText(jsonStr)
            } catch (be: Exception) {
                be.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePinConversation(context: Context, conversationId: String) {
        val conversations = loadAllConversations(context)
        val conv = conversations.find { it.id == conversationId }
        if (conv != null) {
            conv.isPinned = !conv.isPinned
            saveAllConversations(context, conversations)
        }
    }

    fun renameConversation(context: Context, conversationId: String, newTitle: String) {
        val conversations = loadAllConversations(context)
        val conv = conversations.find { it.id == conversationId }
        if (conv != null) {
            conv.title = newTitle
            saveAllConversations(context, conversations)
        }
    }

    fun deleteConversation(context: Context, conversationId: String) {
        val conversations = loadAllConversations(context)
        conversations.removeAll { it.id == conversationId }
        saveAllConversations(context, conversations)
    }

    fun saveOrUpdateConversation(context: Context, conversation: Conversation) {
        val conversations = loadAllConversations(context)
        val index = conversations.indexOfFirst { it.id == conversation.id }
        if (index != -1) {
            conversations[index] = conversation
        } else {
            conversations.add(0, conversation)
        }
        saveAllConversations(context, conversations)
    }

    fun generateAiResponse(context: Context, userPrompt: String): String {
        val topCandidates = ExperienceDagRepository.retrieveTopMatchingExperiences(context, userPrompt, topK = 5)
        val llmResult = NoeonAiEngine.processMessagePipeline(context, userPrompt, topCandidates)

        llmResult.extractedInformativeFacts.forEach { fact ->
            MemoryVaultRepository.saveMemory(context, MemoryItem(
                id = "EXP-${UUID.randomUUID().toString().take(6).uppercase()}",
                title = fact.take(32),
                snippet = fact.take(70),
                message = fact,
                timestamp = MemoryVaultRepository.formatCurrentTime(),
                location = MemoryVaultRepository.getCurrentLocation(),
                tag = "Chat Fact",
                timeAgo = "Just now"
            ))
        }

        ExperienceDagRepository.updateDagConnections(
            context = context,
            userQuestion = userPrompt,
            candidateExperiences = topCandidates,
            usedExperienceIds = llmResult.relevantExperienceIds.toSet()
        )

        return llmResult.cleanHumanoidAnswer
    }

    fun clearAllConversations(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

}
