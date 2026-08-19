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
    private val memoryEvaluatorExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    interface ChatPipelineCallback {
        fun onStepUpdate(stepText: String)
        fun onTokenStream(partialText: String)
        fun onCompleted(
            cleanHumanoidAnswer: String,
            debugLogText: String,
            usedAttachments: List<MediaAttachment>,
            createdMemoryIds: List<String> = emptyList(),
            createdReminderId: String? = null,
            factsToEvaluate: List<Pair<String, String>> = emptyList()
        )
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

    private val activeCancellations = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>()

    fun cancelActivePipeline(conversationId: String) {
        activeCancellations[conversationId]?.set(true)
    }

    fun registerPipeline(conversationId: String): java.util.concurrent.atomic.AtomicBoolean {
        val flag = java.util.concurrent.atomic.AtomicBoolean(false)
        activeCancellations[conversationId] = flag
        return flag
    }

    fun isPipelineCancelled(conversationId: String): Boolean {
        return activeCancellations[conversationId]?.get() == true
    }

    /**
     * Executes the complete Memossist Real-time Pipeline synchronously on the calling thread:
     * Step 1: Local Message Classification & Routing (Model 1 & Model 2)
     * Step 2: Retrieve Top-5 Candidate Memories (Only if ASKING or MIXED)
     * Step 3: Stream live step progress strings & elapsed timer to UI
     * Step 4: Run GGUF Native Inference with tailored System Prompt
     * Step 5: Calculate DAG connection strengths using ONLY the relevant IDs returned.
     * Step 6: Show ONLY the clean humanoid answer in chat bubble (Long press opens full debug log).
     */
    fun processChatMessageWithPipeline(
        context: Context,
        conversationId: String = "",
        userMessage: String,
        userAttachments: List<MediaAttachment> = emptyList(),
        forcedMessageType: MessageType? = null,
        callback: ChatPipelineCallback
    ) {
            val cancelFlag = if (conversationId.isNotEmpty()) registerPipeline(conversationId) else java.util.concurrent.atomic.AtomicBoolean(false)
            fun isCancelled(): Boolean = cancelFlag.get()

            // The caller is the chat foreground service's single worker.  Do not
            // create a detached executor here: doing so lets the service lose
            // ownership of an in-flight inference when the activity goes away.
            val startTimeMs = System.currentTimeMillis()
            var currentBaseStepText = "🔍 Classifying message locally…"
            var isPipelineRunning = true
            var currentClassificationType: MessageType? = forcedMessageType

            fun updateLiveStep(stepText: String) {
                if (isCancelled()) return
                currentBaseStepText = stepText
                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
                val timerStr = ResponseStatsRepository.formatTimerStringForCase(context, elapsedSec, currentClassificationType)
                callback.onStepUpdate("$currentBaseStepText ($timerStr)")
            }

            val tickerExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            val tickerFuture = tickerExecutor.scheduleAtFixedRate({
                if (isPipelineRunning && !isCancelled()) {
                    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
                    val timerStr = ResponseStatsRepository.formatTimerStringForCase(context, elapsedSec, currentClassificationType)
                    callback.onStepUpdate("$currentBaseStepText ($timerStr)")
                }
            }, 0L, 1L, java.util.concurrent.TimeUnit.SECONDS)

            // STEP 1: Local Message Classification & Routing
            val classification = MessageAnalyzer.analyze(context, userMessage, forcedMessageType)
            currentClassificationType = classification.messageType

            if (isCancelled()) {
                isPipelineRunning = false
                try { tickerFuture.cancel(true) } catch (e: Exception) {}
                try { tickerExecutor.shutdownNow() } catch (e: Exception) {}
                return
            }

            val totalSteps = when (classification.messageType) {
                MessageType.REMINDER_ONLY -> 4
                MessageType.TELLING -> 4
                MessageType.ASKING -> 5
                MessageType.MIXED -> 6
                MessageType.REMINDER_AND_ASKING -> 6
                MessageType.REMINDER_AND_TELLING -> 5
                MessageType.REMINDER_AND_MIXED -> 7
            }

            var currentStep = 1
            updateLiveStep("Step $currentStep/$totalSteps: Classifying message locally…")

            val needsMemoryCandidates = when (classification.messageType) {
                MessageType.ASKING,
                MessageType.MIXED,
                MessageType.REMINDER_AND_ASKING,
                MessageType.REMINDER_AND_MIXED -> true
                MessageType.REMINDER_ONLY,
                MessageType.TELLING,
                MessageType.REMINDER_AND_TELLING -> false
            }

            if (isCancelled()) {
                isPipelineRunning = false
                try { tickerFuture.cancel(true) } catch (e: Exception) {}
                try { tickerExecutor.shutdownNow() } catch (e: Exception) {}
                return
            }

            // Retrieve candidate experiences ONLY if asking or mixed intent is involved
            val topCandidates = if (needsMemoryCandidates) {
                currentStep++
                updateLiveStep("Step $currentStep/$totalSteps: Retrieving candidate memories…")
                ExperienceDagRepository.retrieveTopMatchingExperiences(context, userMessage, topK = 5)
            } else {
                emptyList()
            }

            if (isCancelled()) {
                isPipelineRunning = false
                try { tickerFuture.cancel(true) } catch (e: Exception) {}
                try { tickerExecutor.shutdownNow() } catch (e: Exception) {}
                return
            }

            // LLM Response Generation Step
            currentStep++
            val llmStepDescription = when (classification.messageType) {
                MessageType.REMINDER_ONLY -> "Extracting reminder with LLM…"
                MessageType.TELLING -> "Extracting facts with LLM…"
                MessageType.ASKING -> "Synthesizing answer with LLM…"
                MessageType.MIXED -> "Extracting facts & answering with LLM…"
                MessageType.REMINDER_AND_ASKING -> "Extracting reminder & answering with LLM…"
                MessageType.REMINDER_AND_TELLING -> "Extracting reminder & facts with LLM…"
                MessageType.REMINDER_AND_MIXED -> "Extracting reminder, facts & answering with LLM…"
            }
            updateLiveStep("Step $currentStep/$totalSteps: $llmStepDescription")

            val tokenBuffer = StringBuilder()
            val llmResult = NoeonAiEngine.processMessagePipeline(
                context = context,
                userMessage = userMessage,
                candidateExperiences = topCandidates,
                classificationResult = classification,
                onTokenGenerated = { token ->
                    if (!isCancelled()) {
                        tokenBuffer.append(token)
                        val currentStreamText = tokenBuffer.toString()
                        callback.onTokenStream(currentStreamText)
                    }
                }
            )

            if (isCancelled()) {
                isPipelineRunning = false
                try { tickerFuture.cancel(true) } catch (e: Exception) {}
                try { tickerExecutor.shutdownNow() } catch (e: Exception) {}
                return
            }

            // Save Facts Extracted Strictly by LLM into Memory Vault (with user attachments)
            val createdMemoryIds = mutableListOf<String>()
            val factsToEvaluate = mutableListOf<Pair<String, String>>()
            if (llmResult.extractedInformativeFacts.isNotEmpty() || classification.messageType in listOf(MessageType.TELLING, MessageType.MIXED, MessageType.REMINDER_AND_TELLING, MessageType.REMINDER_AND_MIXED)) {
                currentStep++
                updateLiveStep("Step $currentStep/$totalSteps: Saving facts to Memory Vault…")
                    val newMemoriesList = mutableListOf<MemoryItem>()
                    val now = System.currentTimeMillis()
                    for (fact in llmResult.extractedInformativeFacts) {
                        val expId = "EXP-${UUID.randomUUID().toString().take(6).uppercase()}"
                        createdMemoryIds.add(expId)
                        val initialStrength = MemoryDecayCalculator.calculateInitialStrength(
                            importance = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
                            confidence = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
                            stability = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY
                        )
                        val memoryItem = MemoryItem(
                            id = expId,
                            title = if (fact.length > 32) fact.take(32) + "..." else fact,
                            snippet = if (fact.length > 70) fact.take(70) + "..." else fact,
                            message = fact,
                            timestamp = MemoryVaultRepository.formatCurrentTime(),
                            location = MemoryVaultRepository.getCurrentLocation(),
                            tag = "Chat Fact",
                            timeAgo = "Just now",
                            attachments = userAttachments,
                            importance = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
                            confidence = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
                            stability = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY,
                            createdAt = now,
                            lastAccessedAt = now,
                            accessCount = 0,
                            reinforcementCount = 0,
                            lastReinforcedAt = now,
                            baseStrength = initialStrength,
                            strength = initialStrength
                        )
                        newMemoriesList.add(memoryItem)
                    }

                    val saveResult = MemoryVaultRepository.saveExtractedMemoriesWithLimitCheck(context, newMemoriesList)
                    when (saveResult) {
                        is MemoryStorageManager.CapacityCheckResult.FitsWithoutPruning,
                        is MemoryStorageManager.CapacityCheckResult.AutoPruned -> {
                            newMemoriesList.forEach { factsToEvaluate.add(Pair(it.id, it.message)) }
                        }
                        is MemoryStorageManager.CapacityCheckResult.NeedsConfirmation -> {
                            android.util.Log.i("ChatRepository", "Memory limit reached: awaiting user confirmation to prune memories.")
                        }
                        is MemoryStorageManager.CapacityCheckResult.CannotFit -> {
                            android.util.Log.w("ChatRepository", "Memory limit reached: cannot fit new memories (${saveResult.reason}).")
                        }
                    }
            }

            // Extract and set Smart Reminders if present in user message
            val extractedReminders = ReminderExtractor.extractAndCreateAllReminders(context, userMessage, llmResult.extractedReminderTag)
            var createdReminderId: String? = null
            var reminderConfirmationBanner = ""
            if (classification.messageType in listOf(MessageType.REMINDER_ONLY, MessageType.REMINDER_AND_ASKING, MessageType.REMINDER_AND_TELLING, MessageType.REMINDER_AND_MIXED) || extractedReminders.isNotEmpty()) {
                currentStep++
                updateLiveStep("Step $currentStep/$totalSteps: Scheduling smart reminder alerts…")
                if (extractedReminders.isNotEmpty()) {
                    createdReminderId = extractedReminders.first().id
                    for (reminder in extractedReminders) {
                        ReminderRepository.addOrUpdateReminder(context, reminder)
                    }
                    reminderConfirmationBanner = if (extractedReminders.size == 1) {
                        val single = extractedReminders[0]
                        "\n\n⏰ **Reminder Set!** ${single.getCategoryIconText()} `${single.title}` on ${single.getFormattedEventDateTime()} (${single.triggers.size} scheduled alerts)."
                    } else {
                        val items = extractedReminders.joinToString("\n") {
                            "• ${it.getCategoryIconText()} `${it.title}` on ${it.getFormattedEventDateTime()} (${it.triggers.size} scheduled alerts)"
                        }
                        "\n\n⏰ **Reminders Set (${extractedReminders.size}):**\n$items"
                    }
                }
            }

            // Calculate DAG connection strengths & apply Used Experience updates using ONLY the relevant IDs returned from LLM
            val returnedUsedIdsSet = llmResult.relevantExperienceIds.toSet()
            if (returnedUsedIdsSet.isNotEmpty()) {
                MemoryVaultRepository.applyUsedExperiences(context, returnedUsedIdsSet)
            }

            val dagSummary = if (needsMemoryCandidates) {
                currentStep++
                updateLiveStep("Step $currentStep/$totalSteps: Updating memory connection strengths…")
                ExperienceDagRepository.updateDagConnections(
                    context = context,
                    userQuestion = userMessage,
                    candidateExperiences = topCandidates,
                    usedExperienceIds = returnedUsedIdsSet
                )
            } else {
                DagUpdateSummary(userMessage, emptyList(), emptyList(), 0, emptyList())
            }


            updateLiveStep("Step $totalSteps/$totalSteps: Preparing answer…")

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

            debugLogBuilder.append("=== ⚡ LOCAL CLASSIFICATION & ROUTING ===\n")
            val classResult = llmResult.classificationResult
            if (classResult != null) {
                debugLogBuilder.append("Message Type: ${classResult.messageType}\n")
                debugLogBuilder.append("Fallback Invoked: ${classResult.isFallback}\n")
                debugLogBuilder.append("Sentences (${classResult.sentences.size}): ${classResult.sentences}\n")
                debugLogBuilder.append("Sentence Reminder Labels: ${classResult.sentenceLabels} (1=Rem, 0=Non-Rem)\n")
                debugLogBuilder.append("Reminder Sentences: ${classResult.reminderSentences.ifEmpty { listOf("None") }}\n")
                debugLogBuilder.append("Non-Reminder Sentences: ${classResult.nonReminderSentences.ifEmpty { listOf("None") }}\n")
                debugLogBuilder.append("Classified Intent: ${classResult.intent} (Confidence: ${String.format("%.2f", classResult.confidence)})\n")
                debugLogBuilder.append("Latency Breakdown:\n")
                debugLogBuilder.append("  • Sentence Segmentation: ${classResult.segmentationTimeMs} ms\n")
                debugLogBuilder.append("  • Model 1 (Reminder Classifier): ${classResult.reminderClassifyTimeMs} ms\n")
                debugLogBuilder.append("  • Model 2 (Intent Classifier): ${classResult.intentClassifyTimeMs} ms\n")
                debugLogBuilder.append("  • Total Local Classification: ${classResult.totalClassificationTimeMs} ms\n\n")
            } else {
                debugLogBuilder.append("Message Type: ${llmResult.messageType}\n\n")
            }

            debugLogBuilder.append("=== 📊 PROMPT TOKEN OPTIMIZATION & LATENCY ===\n")
            val tokenDiff = llmResult.legacyTokenCount - llmResult.promptTokenCount
            val pctReduction = if (llmResult.legacyTokenCount > 0) (tokenDiff * 100.0 / llmResult.legacyTokenCount) else 0.0
            debugLogBuilder.append("Prompt Tokens Used: ~${llmResult.promptTokenCount} tokens\n")
            debugLogBuilder.append("Legacy Prompt Tokens: ~${llmResult.legacyTokenCount} tokens\n")
            debugLogBuilder.append("Token Reduction: ${String.format("%.1f", pctReduction)}% (${if (tokenDiff >= 0) "-$tokenDiff" else "+${-tokenDiff}"} tokens saved)\n")
            debugLogBuilder.append("Prompt Construction Time: ${llmResult.promptBuildTimeMs} ms\n")
            debugLogBuilder.append("LLM Inference Duration: ${llmResult.inferenceTimeMs} ms\n")
            debugLogBuilder.append("Total Pipeline Duration: ${llmResult.totalPipelineTimeMs} ms\n\n")

            debugLogBuilder.append("=== 💬 SYSTEM PROMPT SENT TO LLM ===\n")
            debugLogBuilder.append(llmResult.systemPromptUsed)
            debugLogBuilder.append("\n\n")

            debugLogBuilder.append("=== 📤 RAW LLM OUTPUT ===\n")
            debugLogBuilder.append(llmResult.cleanHumanoidAnswer)
            debugLogBuilder.append("\n\n[USED_EXPERIENCES: ${llmResult.relevantExperienceIds.joinToString(", ")}]\n")
            debugLogBuilder.append("[EXTRACTED_FACTS: ${llmResult.extractedInformativeFacts}]\n")
            if (extractedReminders.isNotEmpty()) {
                val remLog = extractedReminders.joinToString("; ") { "Title=${it.title}, Time=${it.getFormattedEventDateTime()}, Triggers=${it.triggers.size}" }
                debugLogBuilder.append("[EXTRACTED_REMINDERS (${extractedReminders.size}): $remLog]\n")
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

            // Stop 1-second ticker loop
            isPipelineRunning = false
            try { tickerFuture.cancel(true) } catch (e: Exception) {}
            try { tickerExecutor.shutdown() } catch (e: Exception) {}

            // Record exact duration for online running average calculation
            val durationSec = (System.currentTimeMillis() - startTimeMs) / 1000.0f
            ResponseStatsRepository.recordCaseResponseTime(context, classification.messageType, durationSec)
            if (!isCancelled()) {
                // Invoke completion callback directly on execution thread
                callback.onCompleted(finalAnswer, finalDebugLog, usedExperienceAttachments, createdMemoryIds, createdReminderId, factsToEvaluate)
            }
    }

    fun sendChatAnswerNotification(context: Context, userQuery: String, cleanAnswerText: String, conversationId: String? = null) {
        val targetConvId = if (!conversationId.isNullOrEmpty()) {
            conversationId
        } else {
            val cleanSnippet = cleanAnswerText.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim().lowercase().take(40)
            val conversations = loadAllConversations(context)
            conversations.find { conv ->
                conv.messages.any { !it.isUser && it.text.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim().lowercase().contains(cleanSnippet) }
            }?.id
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
                        description = "Notifications for AI responses generated in background"
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (targetConvId != null) {
                    putExtra("OPEN_CONVERSATION_ID", targetConvId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayQuery = if (userQuery.length > 50) userQuery.take(50) + "…" else userQuery
            val displayAnswer = if (cleanAnswerText.length > 120) cleanAnswerText.take(120) + "…" else cleanAnswerText

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_sparkles)
                .setContentTitle(if (displayQuery.isNotBlank()) "Re: $displayQuery" else "Memossist AI Response")
                .setContentText(displayAnswer)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cleanAnswerText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1000
            notificationManager.notify(notificationId, notification)

            // Save to Notification Center
            NotificationHistoryRepository.addNotification(
                context = context,
                notification = NotificationItem(
                    id = UUID.randomUUID().toString(),
                    reminderId = null,
                    conversationId = targetConvId,
                    title = if (displayQuery.isNotBlank()) "Re: $displayQuery" else "Memossist AI Response",
                    message = if (cleanAnswerText.length > 120) cleanAnswerText.take(120) + "..." else cleanAnswerText,
                    timestamp = System.currentTimeMillis(),
                    type = "CHAT_ANSWER",
                    isRead = false
                )
            )

            // Mark conversation as unread if user is not currently viewing this specific conversation
            if (!targetConvId.isNullOrEmpty() && MainActivity.activeConversationId != targetConvId) {
                val conversations = loadAllConversations(context)
                val conv = conversations.find { it.id == targetConvId }
                if (conv != null) {
                    conv.hasUnread = true
                    saveOrUpdateConversation(context, conv)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun loadAllConversations(context: Context): MutableList<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val primaryFile = File(context.filesDir, FILE_NAME)
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)

        try {
            if (primaryFile.exists()) {
                val jsonStr = primaryFile.readText()
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val convObj = array.getJSONObject(i)
                    val id = convObj.getString("id")
                    val title = convObj.getString("title")
                    val lastUpdated = convObj.optLong("lastUpdated", System.currentTimeMillis())
                    val isPinned = convObj.optBoolean("isPinned", false)
                    val hasUnread = convObj.optBoolean("hasUnread", false)

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

                            val memIdsArray = msgObj.optJSONArray("createdMemoryIds")
                            val createdMemIds = mutableListOf<String>()
                            if (memIdsArray != null) {
                                for (k in 0 until memIdsArray.length()) {
                                    createdMemIds.add(memIdsArray.getString(k))
                                }
                            }
                            val createdReminderId = msgObj.optString("createdReminderId", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val paramEvaluationStatus = msgObj.optString("paramEvaluationStatus", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val paramEvaluationText = msgObj.optString("paramEvaluationText", null).takeIf { !it.isNullOrEmpty() && it != "null" }

                            messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp, isThinking, thinkingStatus, debugLog, msgAtts, createdMemIds, createdReminderId, false, null, 1.0f, paramEvaluationStatus, paramEvaluationText))
                        } catch (me: Exception) {
                            me.printStackTrace()
                        }
                    }

                    if (messagesList.isNotEmpty()) {
                        conversations.add(Conversation(id, title, lastUpdated, isPinned, hasUnread, messagesList))
                    }
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
                        val hasUnread = convObj.optBoolean("hasUnread", false)

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

                            val memIdsArray = msgObj.optJSONArray("createdMemoryIds")
                            val createdMemIds = mutableListOf<String>()
                            if (memIdsArray != null) {
                                for (k in 0 until memIdsArray.length()) {
                                    createdMemIds.add(memIdsArray.getString(k))
                                }
                            }
                            val createdReminderId = msgObj.optString("createdReminderId", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val paramEvaluationStatus = msgObj.optString("paramEvaluationStatus", null).takeIf { !it.isNullOrEmpty() && it != "null" }
                            val paramEvaluationText = msgObj.optString("paramEvaluationText", null).takeIf { !it.isNullOrEmpty() && it != "null" }

                            messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp, isThinking, thinkingStatus, debugLog, msgAtts, createdMemIds, createdReminderId, false, null, 1.0f, paramEvaluationStatus, paramEvaluationText))
                        }

                        if (messagesList.isNotEmpty()) {
                            conversations.add(Conversation(id, title, lastUpdated, isPinned, hasUnread, messagesList))
                        }
                    }
                } catch (be: Exception) {
                    be.printStackTrace()
                }
            }
        }

        conversations.sortWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.lastUpdated })
        return conversations
    }

    @Synchronized
    fun saveAllConversations(context: Context, conversations: List<Conversation>) {
        try {
            val conversationsArray = JSONArray()
            for (conv in conversations) {
                if (conv.messages.isEmpty()) continue
                val convObj = JSONObject().apply {
                    put("id", conv.id)
                    put("title", conv.title)
                    put("lastUpdated", conv.lastUpdated)
                    put("isPinned", conv.isPinned)
                    put("hasUnread", conv.hasUnread)

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
                            put("createdMemoryIds", JSONArray(msg.createdMemoryIds))
                            put("createdReminderId", msg.createdReminderId)
                            put("paramEvaluationStatus", msg.paramEvaluationStatus)
                            put("paramEvaluationText", msg.paramEvaluationText)
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

    /**
     * Reverts the actions of the most recent user interaction in a chat (saved facts, reminders, DAG edges, and messages)
     * and returns the user message to allow editing and resending.
     */
    @Synchronized
    fun revertLastUserMessage(context: Context, conversationId: String): ChatMessage? {
        val conversations = loadAllConversations(context)
        val conv = conversations.find { it.id == conversationId } ?: return null

        val lastUserMsgIdx = conv.messages.indexOfLast { it.isUser }
        if (lastUserMsgIdx == -1) return null
        val lastUserMsg = conv.messages[lastUserMsgIdx]

        val subsequentMessages = conv.messages.subList(lastUserMsgIdx, conv.messages.size).toList()
        for (msg in subsequentMessages) {
            for (memId in msg.createdMemoryIds) {
                MemoryVaultRepository.deleteMemory(context, memId)
            }
            if (!msg.createdReminderId.isNullOrEmpty()) {
                ReminderRepository.deleteReminder(context, msg.createdReminderId!!)
            }
        }

        // Revert DAG connection increments if candidate memories were used
        val aiResponseMsg = subsequentMessages.find { !it.isUser }
        if (aiResponseMsg != null) {
            val usedIds = aiResponseMsg.debugLog?.let { log ->
                val usedMatch = Regex("\\[USED_EXPERIENCES:\\s*(.*?)\\]").find(log)
                usedMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() }?.filter { it.startsWith("EXP-", ignoreCase = true) }?.toSet()
            } ?: emptySet()
            if (usedIds.isNotEmpty()) {
                val candidates = ExperienceDagRepository.retrieveTopMatchingExperiences(context, lastUserMsg.text, topK = 5)
                ExperienceDagRepository.revertDagConnections(context, lastUserMsg.text, candidates, usedIds)
            }
        }

        while (conv.messages.size > lastUserMsgIdx) {
            conv.messages.removeAt(conv.messages.size - 1)
        }
        conv.lastUpdated = System.currentTimeMillis()
        saveOrUpdateConversation(context, conv)

        return lastUserMsg
    }

    @Synchronized
    fun togglePinConversation(context: Context, conversationId: String) {
        val conversations = loadAllConversations(context)
        val conv = conversations.find { it.id == conversationId }
        if (conv != null) {
            conv.isPinned = !conv.isPinned
            saveAllConversations(context, conversations)
        }
    }

    @Synchronized
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

    @Synchronized
    fun saveOrUpdateConversation(context: Context, conversation: Conversation) {
        val conversations = loadAllConversations(context)
        val index = conversations.indexOfFirst { it.id == conversation.id }
        if (conversation.messages.isEmpty()) {
            if (index != -1) {
                conversations.removeAt(index)
                saveAllConversations(context, conversations)
            }
            return
        }
        if (index != -1) {
            conversations[index] = conversation
        } else {
            conversations.add(0, conversation)
        }
        saveAllConversations(context, conversations)
    }

    fun generateAiResponse(context: Context, userPrompt: String): String {
        val classification = MessageAnalyzer.analyze(context, userPrompt)
        val needsMemoryCandidates = when (classification.messageType) {
            MessageType.ASKING,
            MessageType.MIXED,
            MessageType.REMINDER_AND_ASKING,
            MessageType.REMINDER_AND_MIXED -> true
            MessageType.REMINDER_ONLY,
            MessageType.TELLING,
            MessageType.REMINDER_AND_TELLING -> false
        }

        val topCandidates = if (needsMemoryCandidates || classification.isFallback) {
            ExperienceDagRepository.retrieveTopMatchingExperiences(context, userPrompt, topK = 5)
        } else {
            emptyList()
        }

        val llmResult = NoeonAiEngine.processMessagePipeline(
            context = context,
            userMessage = userPrompt,
            candidateExperiences = topCandidates,
            classificationResult = classification
        )

        val now = System.currentTimeMillis()
        val factsToEvaluate = mutableListOf<Pair<String, String>>()
        val newMemoriesList = mutableListOf<MemoryItem>()
        llmResult.extractedInformativeFacts.forEach { fact ->
            val expId = "EXP-${UUID.randomUUID().toString().take(6).uppercase()}"
            val initialStrength = MemoryDecayCalculator.calculateInitialStrength(
                importance = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
                confidence = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
                stability = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY
            )
            val item = MemoryItem(
                id = expId,
                title = fact.take(32),
                snippet = fact.take(70),
                message = fact,
                timestamp = MemoryVaultRepository.formatCurrentTime(),
                location = MemoryVaultRepository.getCurrentLocation(),
                tag = "Chat Fact",
                timeAgo = "Just now",
                importance = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
                confidence = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
                stability = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 0,
                reinforcementCount = 0,
                lastReinforcedAt = now,
                baseStrength = initialStrength,
                strength = initialStrength
            )
            newMemoriesList.add(item)
        }

        val saveResult = MemoryVaultRepository.saveExtractedMemoriesWithLimitCheck(context, newMemoriesList)
        when (saveResult) {
            is MemoryStorageManager.CapacityCheckResult.FitsWithoutPruning,
            is MemoryStorageManager.CapacityCheckResult.AutoPruned -> {
                newMemoriesList.forEach { factsToEvaluate.add(Pair(it.id, it.message)) }
            }
            is MemoryStorageManager.CapacityCheckResult.NeedsConfirmation -> {
                android.util.Log.i("ChatRepository", "Memory limit reached: awaiting user confirmation to prune memories.")
            }
            is MemoryStorageManager.CapacityCheckResult.CannotFit -> {
                android.util.Log.w("ChatRepository", "Memory limit reached: cannot fit new memories (${saveResult.reason}).")
            }
        }

        if (factsToEvaluate.isNotEmpty()) {
            val appContext = context.applicationContext
            memoryEvaluatorExecutor.execute {
                for ((expId, fact) in factsToEvaluate) {
                    try {
                        val params = MemoryParameterEvaluator.evaluate(appContext, fact)
                        val evaluatedStrength = MemoryDecayCalculator.calculateInitialStrength(
                            params.importance,
                            params.confidence,
                            params.stability
                        )
                        val existing = MemoryVaultRepository.getMemoryById(appContext, expId)
                        if (existing != null) {
                            val updated = existing.copy(
                                importance = params.importance,
                                confidence = params.confidence,
                                stability = params.stability,
                                baseStrength = evaluatedStrength,
                                strength = evaluatedStrength
                            )
                            MemoryVaultRepository.updateMemory(appContext, updated)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (llmResult.relevantExperienceIds.isNotEmpty()) {
            MemoryVaultRepository.applyUsedExperiences(context, llmResult.relevantExperienceIds.toSet())
        }

        if (topCandidates.isNotEmpty() && llmResult.relevantExperienceIds.isNotEmpty()) {
            ExperienceDagRepository.updateDagConnections(
                context = context,
                userQuestion = userPrompt,
                candidateExperiences = topCandidates,
                usedExperienceIds = llmResult.relevantExperienceIds.toSet()
            )
        }

        return llmResult.cleanHumanoidAnswer
    }

    fun clearAllConversations(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

}
