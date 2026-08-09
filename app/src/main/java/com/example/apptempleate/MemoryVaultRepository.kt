package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MemoryVaultRepository {

    private const val FILE_NAME = "memossist_vault_memories.json"

    fun parseAttachments(jsonStr: String?): List<MediaAttachment> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<MediaAttachment>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MediaAttachment(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        fileName = obj.getString("fileName"),
                        filePath = obj.getString("filePath"),
                        mimeType = obj.getString("mimeType"),
                        fileSize = obj.optLong("fileSize", 0L),
                        formattedSize = obj.optString("formattedSize", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeAttachments(list: List<MediaAttachment>): String {
        if (list.isEmpty()) return "[]"
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("mimeType", item.mimeType)
                put("fileSize", item.fileSize)
                put("formattedSize", item.formattedSize)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun loadAllMemories(context: Context): MutableList<MemoryItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val initialList = createInitialMemories()
            saveAllMemories(context, initialList)
            return initialList
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<MemoryItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val snippet = obj.optString("snippet", title)
                val message = obj.optString("message", snippet)
                val timestamp = obj.optString("timestamp", formatCurrentTime())
                val location = obj.optString("location", getCurrentLocation())
                val tag = obj.optString("tag", "Chat")
                val timeAgo = obj.optString("timeAgo", "Recent")
                val isPinned = obj.optBoolean("isPinned", false)
                var wordSynonymsJson = obj.optString("wordSynonymsJson", null)
                val rawAttachmentsJson = obj.optString("attachmentsJson", null)
                val attachmentsList = parseAttachments(rawAttachmentsJson)

                if (wordSynonymsJson.isNullOrEmpty()) {
                    val extracted = LinguisticAnalyzer.extractWordsAndSynonyms(message)
                    wordSynonymsJson = LinguisticAnalyzer.toJsonString(extracted)
                }

                list.add(
                    MemoryItem(
                        id = id,
                        title = title,
                        snippet = snippet,
                        message = message,
                        timestamp = timestamp,
                        location = location,
                        tag = tag,
                        timeAgo = timeAgo,
                        isPinned = isPinned,
                        wordSynonymsJson = wordSynonymsJson,
                        attachments = attachmentsList,
                        attachmentsJson = rawAttachmentsJson
                    )
                )
            }
            list
        } catch (e: Exception) {
            val initialList = createInitialMemories()
            saveAllMemories(context, initialList)
            initialList
        }
    }

    fun saveMemory(context: Context, memoryItem: MemoryItem) {
        val itemToSave = if (memoryItem.wordSynonymsJson.isNullOrEmpty()) {
            val extracted = LinguisticAnalyzer.extractWordsAndSynonyms(memoryItem.message)
            memoryItem.copy(
                wordSynonymsJson = LinguisticAnalyzer.toJsonString(extracted),
                attachmentsJson = serializeAttachments(memoryItem.attachments)
            )
        } else {
            memoryItem.copy(attachmentsJson = serializeAttachments(memoryItem.attachments))
        }
        val memories = loadAllMemories(context)
        memories.add(0, itemToSave)
        saveAllMemories(context, memories)
    }

    fun updateMemory(context: Context, updatedMemory: MemoryItem) {
        val extracted = LinguisticAnalyzer.extractWordsAndSynonyms(updatedMemory.message)
        val itemToSave = updatedMemory.copy(
            wordSynonymsJson = LinguisticAnalyzer.toJsonString(extracted),
            attachmentsJson = serializeAttachments(updatedMemory.attachments)
        )
        
        val memories = loadAllMemories(context)
        val index = memories.indexOfFirst { it.id == itemToSave.id }
        if (index != -1) {
            memories[index] = itemToSave
            saveAllMemories(context, memories)
        }
    }

    fun deleteMemory(context: Context, memoryId: String) {
        val memories = loadAllMemories(context)
        val removed = memories.removeAll { it.id == memoryId }
        if (removed) {
            saveAllMemories(context, memories)
        }
    }

    fun clearAllMemories(context: Context) {
        saveAllMemories(context, emptyList())
    }

    fun saveAllMemories(context: Context, memories: List<MemoryItem>) {
        try {
            val array = JSONArray()
            for (item in memories) {
                val synonymsJson = if (item.wordSynonymsJson.isNullOrEmpty()) {
                    val extracted = LinguisticAnalyzer.extractWordsAndSynonyms(item.message)
                    LinguisticAnalyzer.toJsonString(extracted)
                } else {
                    item.wordSynonymsJson
                }

                val attJson = if (item.attachmentsJson.isNullOrEmpty() && item.attachments.isNotEmpty()) {
                    serializeAttachments(item.attachments)
                } else {
                    item.attachmentsJson ?: "[]"
                }

                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("snippet", item.snippet)
                    put("message", item.message)
                    put("timestamp", item.timestamp)
                    put("location", item.location)
                    put("tag", item.tag)
                    put("timeAgo", item.timeAgo)
                    put("isPinned", item.isPinned)
                    put("wordSynonymsJson", synonymsJson)
                    put("attachmentsJson", attJson)
                }
                array.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun formatCurrentTime(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getCurrentLocation(): String {
        return "37.7749° N, 122.4194° W • Central Cognitive Workspace"
    }

    private fun createInitialMemories(): MutableList<MemoryItem> {
        val now = formatCurrentTime()
        val loc = getCurrentLocation()

        fun createWithAnalysis(id: String, title: String, snippet: String, message: String, tag: String, timeAgo: String, isPinned: Boolean): MemoryItem {
            val extracted = LinguisticAnalyzer.extractWordsAndSynonyms(message)
            return MemoryItem(
                id = id,
                title = title,
                snippet = snippet,
                message = message,
                timestamp = now,
                location = loc,
                tag = tag,
                timeAgo = timeAgo,
                isPinned = isPinned,
                wordSynonymsJson = LinguisticAnalyzer.toJsonString(extracted)
            )
        }

        return mutableListOf(
            createWithAnalysis(
                id = "EXP-9081",
                title = "Quarterly Strategy & Cognitive Notes",
                snippet = "Key takeaways from the strategy session covering neural architecture designs...",
                message = "Key takeaways from the strategy session covering neural architecture designs, memory retrieval benchmarks, and UI state flows.",
                tag = "Audio",
                timeAgo = "4 mins ago",
                isPinned = true
            ),
            createWithAnalysis(
                id = "EXP-7742",
                title = "AI Agentic Workflow Ideas",
                snippet = "Explored multi-agent delegation patterns for autonomous contextual search...",
                message = "Explored multi-agent delegation patterns for autonomous contextual search and live tactile background rendering.",
                tag = "Idea",
                timeAgo = "2 hours ago",
                isPinned = true
            ),
            createWithAnalysis(
                id = "EXP-6105",
                title = "Product Roadmap & UI Polish Transcript",
                snippet = "Transcript of live voice conversation discussing smooth swipe gestures...",
                message = "Transcript of live voice conversation discussing smooth swipe gestures, sidebar navigation, and clean white interface styling.",
                tag = "Document",
                timeAgo = "Yesterday",
                isPinned = false
            )
        )
    }
}
