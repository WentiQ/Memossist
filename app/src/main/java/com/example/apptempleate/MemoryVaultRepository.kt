package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object MemoryVaultRepository {

    private const val FILE_NAME = "memossist_vault_memories.json"

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
                        isPinned = isPinned
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
        val memories = loadAllMemories(context)
        memories.add(0, memoryItem)
        saveAllMemories(context, memories)
    }

    fun saveAllMemories(context: Context, memories: List<MemoryItem>) {
        try {
            val array = JSONArray()
            for (item in memories) {
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

        return mutableListOf(
            MemoryItem(
                id = "EXP-9081",
                title = "Quarterly Strategy & Cognitive Notes",
                snippet = "Key takeaways from the strategy session covering neural architecture designs...",
                message = "Key takeaways from the strategy session covering neural architecture designs, memory retrieval benchmarks, and UI state flows.",
                timestamp = now,
                location = loc,
                tag = "Audio",
                timeAgo = "4 mins ago",
                isPinned = true
            ),
            MemoryItem(
                id = "EXP-7742",
                title = "AI Agentic Workflow Ideas",
                snippet = "Explored multi-agent delegation patterns for autonomous contextual search...",
                message = "Explored multi-agent delegation patterns for autonomous contextual search and live tactile background rendering.",
                timestamp = now,
                location = loc,
                tag = "Idea",
                timeAgo = "2 hours ago",
                isPinned = true
            ),
            MemoryItem(
                id = "EXP-6105",
                title = "Product Roadmap & UI Polish Transcript",
                snippet = "Transcript of live voice conversation discussing smooth swipe gestures...",
                message = "Transcript of live voice conversation discussing smooth swipe gestures, sidebar navigation, and clean white interface styling.",
                timestamp = now,
                location = loc,
                tag = "Document",
                timeAgo = "Yesterday"
            )
        )
    }
}
