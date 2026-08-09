package com.example.apptempleate

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ChatRepository {

    private const val FILE_NAME = "memossist_conversations.json"
    private val mainHandler = Handler(Looper.getMainLooper())

    interface ChatPipelineCallback {
        fun onStepUpdate(stepText: String)
        fun onTokenStream(partialText: String)
        fun onCompleted(cleanHumanoidAnswer: String, debugLogText: String)
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
        sb.append("=== RETRIEVED CANDIDATE EXPERIENCES (TOP 5) ===\n")

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
        callback: ChatPipelineCallback
    ) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

        executor.execute {
            // STEP 1: Retrieve Candidate Memories from Memory Vault
            mainHandler.post { callback.onStepUpdate("Step 1/6: Retrieving candidate memories…") }
            val topCandidates = ExperienceDagRepository.retrieveTopMatchingExperiences(context, userMessage, topK = 5)
            val systemPromptStr = NoeonAiEngine.buildSystemPrompt(topCandidates)

            // STEP 2: LLM Intent Understanding, Fact Filtering & Humanoid Answer Synthesis
            mainHandler.post { callback.onStepUpdate("Step 2/6: LLM generating response…") }

            val tokenBuffer = StringBuilder()
            val llmResult = NoeonAiEngine.processMessagePipeline(
                context = context,
                userMessage = userMessage,
                candidateExperiences = topCandidates,
                onTokenGenerated = { token ->
                    tokenBuffer.append(token)
                    val currentStreamText = tokenBuffer.toString()
                    mainHandler.post { callback.onTokenStream(currentStreamText) }
                }
            )

            // STEP 3: Save Facts Extracted Strictly by LLM into Memory Vault
            mainHandler.post { callback.onStepUpdate("Step 3/6: Reading LLM extracted facts…") }
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
                        timeAgo = "Just now"
                    )
                    MemoryVaultRepository.saveMemory(context, memoryItem)
                }
            }

            mainHandler.post { callback.onStepUpdate("Step 4/6: Updating memory vault records…") }

            // Step 4 & 5: Calculate DAG connection strengths using ONLY the relevant IDs returned from LLM
            mainHandler.post { callback.onStepUpdate("Step 5/6: Updating used-memory connections…") }

            val returnedUsedIdsSet = llmResult.relevantExperienceIds.toSet()

            val dagSummary = ExperienceDagRepository.updateDagConnections(
                context = context,
                userQuestion = userMessage,
                candidateExperiences = topCandidates,
                usedExperienceIds = returnedUsedIdsSet
            )

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

            mainHandler.post { callback.onStepUpdate("Step 6/6: Preparing the answer…") }
            // Show only the answer in chat; the temporary progress bubble is replaced.
            mainHandler.post {
                callback.onCompleted(llmResult.cleanHumanoidAnswer, finalDebugLog)
            }
        }
    }

    fun loadAllConversations(context: Context): MutableList<Conversation> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val defaultList = mutableListOf<Conversation>()
            saveAllConversations(context, defaultList)
            return defaultList
        }

        return try {
            val jsonStr = file.readText()
            val conversationsArray = JSONArray(jsonStr)
            val conversations = mutableListOf<Conversation>()

            for (i in 0 until conversationsArray.length()) {
                val convObj = conversationsArray.getJSONObject(i)
                val id = convObj.getString("id")
                val title = convObj.getString("title")
                val lastUpdated = convObj.optLong("lastUpdated", System.currentTimeMillis())
                val isPinned = convObj.optBoolean("isPinned", false)

                val messagesList = mutableListOf<ChatMessage>()
                val msgArray = convObj.getJSONArray("messages")
                for (j in 0 until msgArray.length()) {
                    val msgObj = msgArray.getJSONObject(j)
                    val msgId = msgObj.optString("id", UUID.randomUUID().toString())
                    val msgConvId = msgObj.optString("conversationId", id)
                    val text = msgObj.getString("text")
                    val isUser = msgObj.getBoolean("isUser")
                    val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                    val debugLog = msgObj.optString("debugLog", null)

                    messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp, false, null as String?, debugLog))
                }

                conversations.add(Conversation(id, title, lastUpdated, isPinned, messagesList))
            }
            
            conversations.sortWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.lastUpdated })
            conversations
        } catch (e: Exception) {
            val defaultList = mutableListOf<Conversation>()
            saveAllConversations(context, defaultList)
            defaultList
        }
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
                            put("debugLog", msg.debugLog)
                        }
                        msgArray.put(msgObj)
                    }
                    put("messages", msgArray)
                }
                conversationsArray.put(convObj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(conversationsArray.toString())
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
