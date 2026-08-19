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

    @Synchronized
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
            val now = System.currentTimeMillis()

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

                // Forgetting & Decay Parameters with backward-compatible defaults
                val importance = obj.optDouble("importance", MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE)
                val confidence = obj.optDouble("confidence", MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE)
                val stability = obj.optDouble("stability", MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY)
                val createdAt = obj.optLong("createdAt", now)
                val lastAccessedAt = obj.optLong("lastAccessedAt", createdAt)
                val accessCount = obj.optInt("accessCount", 0)
                val reinforcementCount = obj.optInt("reinforcementCount", 0)
                val lastReinforcedAt = obj.optLong("lastReinforcedAt", createdAt)

                val defaultBaseStrength = MemoryDecayCalculator.calculateInitialStrength(importance, confidence, stability)
                val baseStrength = obj.optDouble("baseStrength", defaultBaseStrength)
                val strength = obj.optDouble("strength", defaultBaseStrength)

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
                        attachmentsJson = rawAttachmentsJson,
                        importance = importance,
                        confidence = confidence,
                        stability = stability,
                        createdAt = createdAt,
                        lastAccessedAt = lastAccessedAt,
                        accessCount = accessCount,
                        reinforcementCount = reinforcementCount,
                        lastReinforcedAt = lastReinforcedAt,
                        baseStrength = baseStrength,
                        strength = strength
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
    fun deleteMemory(context: Context, memoryId: String) {
        val memories = loadAllMemories(context)
        val target = memories.find { it.id == memoryId }
        val removed = memories.removeAll { it.id == memoryId }
        if (removed) {
            target?.let { MemoryStorageManager.cleanupMemoryAttachments(it) }
            ExperienceDagRepository.removeEdgesForMemory(context, memoryId)
            saveAllMemories(context, memories)
        }
    }

    @Synchronized
    fun clearAllMemories(context: Context) {
        val memories = loadAllMemories(context)
        for (mem in memories) {
            MemoryStorageManager.cleanupMemoryAttachments(mem)
        }
        ExperienceDagRepository.clearAllEdges(context)
        saveAllMemories(context, emptyList())
    }

    /**
     * Checks storage limit and capacity before saving newly extracted memories.
     * Prunes weakest memories if in AUTO mode, or prompts user in ASK mode.
     */
    @Synchronized
    fun saveExtractedMemoriesWithLimitCheck(
        context: Context,
        newMemories: List<MemoryItem>
    ): MemoryStorageManager.CapacityCheckResult {
        if (newMemories.isEmpty()) return MemoryStorageManager.CapacityCheckResult.FitsWithoutPruning

        val checkResult = MemoryStorageManager.checkCapacityAndPlan(context, newMemories)
        when (checkResult) {
            is MemoryStorageManager.CapacityCheckResult.FitsWithoutPruning,
            is MemoryStorageManager.CapacityCheckResult.AutoPruned -> {
                for (mem in newMemories) {
                    saveMemory(context, mem)
                }
            }
            is MemoryStorageManager.CapacityCheckResult.NeedsConfirmation -> {
                // Pending user approval; memories will be saved upon confirmation
            }
            is MemoryStorageManager.CapacityCheckResult.CannotFit -> {
                android.util.Log.w("MemoryVaultRepository", "Cannot fit new memories: ${checkResult.reason}")
            }
        }
        return checkResult
    }

    @Synchronized
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
                    put("importance", item.importance)
                    put("confidence", item.confidence)
                    put("stability", item.stability)
                    put("createdAt", item.createdAt)
                    put("lastAccessedAt", item.lastAccessedAt)
                    put("accessCount", item.accessCount)
                    put("reinforcementCount", item.reinforcementCount)
                    put("lastReinforcedAt", item.lastReinforcedAt)
                    put("baseStrength", item.baseStrength)
                    put("strength", item.strength)
                }
                array.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Applies usage/reinforcement updates to all memories matching the given used IDs ([USED_EXPERIENCES]).
     */
    @Synchronized
    fun applyUsedExperiences(context: Context, usedExperienceIds: Set<String>) {
        if (usedExperienceIds.isEmpty()) return
        val memories = loadAllMemories(context)
        var changed = false
        val now = System.currentTimeMillis()

        for (i in memories.indices) {
            val memory = memories[i]
            if (usedExperienceIds.contains(memory.id)) {
                memories[i] = MemoryDecayCalculator.applyUsedExperience(memory, now)
                changed = true
            }
        }

        if (changed) {
            saveAllMemories(context, memories)
        }
    }

    /**
     * Applies explicit user reinforcement to a specific memory.
     */
    @Synchronized
    fun applyUserReinforcement(context: Context, memoryId: String): MemoryItem? {
        val memories = loadAllMemories(context)
        val index = memories.indexOfFirst { it.id.equals(memoryId, ignoreCase = true) }
        if (index == -1) return null

        val now = System.currentTimeMillis()
        val reinforced = MemoryDecayCalculator.applyReinforcement(memories[index], now)
        memories[index] = reinforced
        saveAllMemories(context, memories)
        return reinforced
    }

    /**
     * Executes decay calculation over all memories:
     * 1. Recalculates current strength for each memory.
     * 2. Removes/forgets memories whose strength is below FORGET_THRESHOLD (0.15).
     * 3. Keeps and updates remaining memories.
     * 4. Persists the updated list to storage.
     * Returns the list of retained memories.
     */
    @Synchronized
    fun recalculateAndPruneMemories(context: Context): List<MemoryItem> {
        val memories = loadAllMemories(context)
        if (memories.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val keptMemories = mutableListOf<MemoryItem>()
        val prunedMemoryIds = mutableSetOf<String>()
        var prunedCount = 0

        for (item in memories) {
            val currentStrength = MemoryDecayCalculator.calculateCurrentStrength(item, now)
            val updated = item.copy(strength = currentStrength)
            if (MemoryDecayCalculator.shouldForget(currentStrength)) {
                prunedCount++
                prunedMemoryIds.add(item.id)
                MemoryStorageManager.cleanupMemoryAttachments(item)
                MemoryDecayCalculator.logDebugInfo(updated, now, "Periodic Decay: PRUNED (Strength < 0.15)")
            } else {
                keptMemories.add(updated)
                MemoryDecayCalculator.logDebugInfo(updated, now, "Periodic Decay: KEPT")
            }
        }

        if (prunedCount > 0) {
            incrementForgottenMemoriesCount(context, prunedCount)
            ExperienceDagRepository.removeEdgesForMemories(context, prunedMemoryIds)
        }

        saveAllMemories(context, keptMemories)
        android.util.Log.i("MemoryVaultRepository", "Decay cycle finished: ${keptMemories.size} kept, $prunedCount forgotten/pruned.")
        return keptMemories
    }

    fun getForgottenMemoriesCount(context: Context): Int {
        val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("forgotten_memories_count", 0)
    }

    fun incrementForgottenMemoriesCount(context: Context, delta: Int) {
        if (delta <= 0) return
        val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("forgotten_memories_count", 0)
        prefs.edit().putInt("forgotten_memories_count", current + delta).apply()
    }

    fun getMemoryById(context: Context, memoryId: String): MemoryItem? {
        return loadAllMemories(context).find { it.id.equals(memoryId, ignoreCase = true) }
    }

    fun formatCurrentTime(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getCurrentLocation(): String {
        return "37.7749° N, 122.4194° W • Central Cognitive Workspace"
    }

    private fun createInitialMemories(): MutableList<MemoryItem> {
        return mutableListOf()
    }
}
