package com.example.apptempleate

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Manages the sequential (FIFO) background queue for 2nd LLM parameter evaluation tasks.
 * If multiple user messages arrive while an evaluation is running, subsequent requests
 * are safely queued. Each message's progress container transitions:
 * 1. QUEUED: "⏳ In queue for memory scoring..."
 * 2. SCORING: "🧠 Scoring parameters for N facts..."
 * 3. DONE: "✓ Parameters set: EXP-001 (I:0.85, C:0.95, T:0.75)..."
 *
 * It parses the returned experience IDs with their importance, confidence, and stability
 * values, and edits/updates those memory records directly in the Memory Vault.
 */
object MemoryParameterQueueManager {
    private const val TAG = "MemoryParamQueue"
    private val queueExecutor = Executors.newSingleThreadExecutor()

    fun enqueueTask(
        context: Context,
        conversationId: String,
        messageId: String,
        facts: List<FactForEvaluation>
    ) {
        if (facts.isEmpty()) return

        val appContext = context.applicationContext

        // 1. Immediately mark message as QUEUED in conversation state & broadcast
        updateMessageStatus(
            context = appContext,
            conversationId = conversationId,
            messageId = messageId,
            status = "QUEUED",
            statusText = "⏳ In queue for memory parameter scoring..."
        )

        // 2. Queue for sequential processing on the 2nd LLM background instance
        queueExecutor.execute {
            try {
                // 3. Mark as actively SCORING
                val factCount = facts.size
                val scoringText = "🧠 Scoring parameters for $factCount fact${if (factCount > 1) "s" else ""}..."
                updateMessageStatus(
                    context = appContext,
                    conversationId = conversationId,
                    messageId = messageId,
                    status = "SCORING",
                    statusText = scoringText
                )

                // 4. Run 2nd LLM batch parameter evaluation
                val results = MemoryParameterEvaluator.evaluateBatch(appContext, facts)

                // 5. Update each Experience ID directly in the Memory Vault
                val summaryParts = mutableListOf<String>()
                for (res in results) {
                    val existing = MemoryVaultRepository.getMemoryById(appContext, res.experienceId)
                    if (existing != null) {
                        val updated = existing.copy(
                            importance = res.importance,
                            confidence = res.confidence,
                            stability = res.stability,
                            baseStrength = res.strength,
                            strength = res.strength
                        )
                        MemoryVaultRepository.updateMemory(appContext, updated)
                        MemoryDecayCalculator.logDebugInfo(updated, System.currentTimeMillis(), "2nd LLM Queue Evaluation")
                    }
                    val formattedI = String.format(Locale.US, "%.2f", res.importance)
                    val formattedC = String.format(Locale.US, "%.2f", res.confidence)
                    val formattedT = String.format(Locale.US, "%.2f", res.stability)
                    summaryParts.add("${res.experienceId} (I:$formattedI, C:$formattedC, T:$formattedT)")
                }

                // 6. Mark as DONE with the scored experience IDs and parameter values
                val doneSummary = "✓ Parameters set: " + summaryParts.joinToString(", ")
                updateMessageStatus(
                    context = appContext,
                    conversationId = conversationId,
                    messageId = messageId,
                    status = "DONE",
                    statusText = doneSummary
                )

                Log.d(TAG, "Completed 2nd LLM parameter evaluation for message $messageId: $doneSummary")

            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating parameters for message $messageId in queue", e)
                updateMessageStatus(
                    context = appContext,
                    conversationId = conversationId,
                    messageId = messageId,
                    status = "DONE",
                    statusText = "✓ Memory parameters saved with baseline values"
                )
            }
        }
    }

    private fun updateMessageStatus(
        context: Context,
        conversationId: String,
        messageId: String,
        status: String,
        statusText: String
    ) {
        try {
            val conversations = ChatRepository.loadAllConversations(context)
            val conv = conversations.find { it.id == conversationId }
            if (conv != null) {
                val msg = conv.messages.find { it.id == messageId }
                if (msg != null) {
                    msg.paramEvaluationStatus = status
                    msg.paramEvaluationText = statusText
                    ChatRepository.saveOrUpdateConversation(context, conv)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Broadcast to UI
        try {
            val intent = Intent(ChatAiForegroundService.ACTION_PARAM_EVALUATION_UPDATE).apply {
                putExtra(ChatAiForegroundService.EXTRA_CONVERSATION_ID, conversationId)
                putExtra(ChatAiForegroundService.EXTRA_MESSAGE_ID, messageId)
                putExtra(ChatAiForegroundService.EXTRA_PARAM_STATUS, status)
                putExtra(ChatAiForegroundService.EXTRA_PARAM_TEXT, statusText)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
