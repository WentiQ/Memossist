package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ChatRepository {

    private const val FILE_NAME = "memossist_conversations.json"

    fun loadAllConversations(context: Context): MutableList<Conversation> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val defaultList = createDefaultConversations()
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

                    messagesList.add(ChatMessage(msgId, msgConvId, text, isUser, timestamp))
                }

                conversations.add(Conversation(id, title, lastUpdated, isPinned, messagesList))
            }
            
            // Sort: Pinned first, then newest lastUpdated first
            conversations.sortWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.lastUpdated })
            conversations
        } catch (e: Exception) {
            val defaultList = createDefaultConversations()
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

    fun getConversationById(context: Context, id: String): Conversation? {
        return loadAllConversations(context).find { it.id == id }
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
        val activeModel = NoeonAiEngine.getSelectedModel(context)
        val modelFile = RealModelDownloader.getModelFile(context, activeModel)
        val fileStatus = if (modelFile.exists() && modelFile.length() > 0) {
            "Local GGUF File: ${modelFile.name} (${String.format("%.1f", modelFile.length() / (1024.0 * 1024.0))} MB)"
        } else {
            "System Default Engine"
        }

        val lower = userPrompt.lowercase()
        val modelTag = "${activeModel.icon} [${activeModel.name} | $fileStatus]"

        val baseResponse = when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! I am executing local inference via $modelTag. How can I assist you with your memory vault or notes today?"
            lower.contains("vault") || lower.contains("memory") ->
                "I've indexed your Memory Vault using $modelTag. All your memories and concept nodes remain perfectly synced regardless of model switches."
            lower.contains("insight") || lower.contains("recall") ->
                "Based on your cognitive metrics processed via $modelTag, your recall efficiency peaked at 98.4% across 142 active contexts."
            lower.contains("connection") || lower.contains("link") ->
                "Correlated 248 concept nodes in Memory Vault via $modelTag, linking Product Strategy to your active UI tokens."
            else ->
                "Processed via $modelTag: \"$userPrompt\". Memossist has linked this insight into your active cognitive context graph."
        }

        return baseResponse
    }

    // Overload for backward compatibility if called without context
    fun generateAiResponse(userPrompt: String): String {
        val lower = userPrompt.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! How can I assist you with your memory vault or notes today?"
            lower.contains("vault") || lower.contains("memory") ->
                "I've indexed your Memory Vault. You have saved entries spanning audio transcripts, strategy documents, and cognitive notes."
            else ->
                "I've processed your query: \"$userPrompt\". Memossist has linked this insight into your active cognitive context index."
        }
    }

    private fun createDefaultConversations(): MutableList<Conversation> {
        val conv1 = Conversation(
            id = "conv-101",
            title = "AI System Architecture",
            lastUpdated = System.currentTimeMillis() - 3600000,
            isPinned = true,
            messages = mutableListOf(
                ChatMessage("m1", "conv-101", "What are the core modules of the AI agent architecture?", true),
                ChatMessage("m2", "conv-101", "The core modules comprise Cognitive Memory Vault, Context Graph Engine, and Live Voice Synthesizer.", false)
            )
        )

        val conv2 = Conversation(
            id = "conv-2",
            title = "Voice Transcript - Aug 7",
            lastUpdated = System.currentTimeMillis() - 7200000,
            isPinned = false,
            messages = mutableListOf(
                ChatMessage("m3", "conv-2", "Summarize my morning audio voice note.", true),
                ChatMessage("m4", "conv-2", "Morning voice transcript summarized: Focused on UI polish, white background alignment, and sidebar menu gesture polish.", false)
            )
        )

        val conv3 = Conversation(
            id = "conv-3",
            title = "Product Roadmap & Tokens",
            lastUpdated = System.currentTimeMillis() - 10800000,
            isPinned = false,
            messages = mutableListOf(
                ChatMessage("m5", "conv-3", "Show connected concept nodes for product strategy.", true),
                ChatMessage("m6", "conv-3", "Product Strategy is linked with 34 reference nodes to UI Tokens and Memory Vault indexes.", false)
            )
        )

        return mutableListOf(conv1, conv2, conv3)
    }
}
