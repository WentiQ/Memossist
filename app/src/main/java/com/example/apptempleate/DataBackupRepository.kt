package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class ImportResult(
    val conversationCount: Int,
    val memoryCount: Int,
    val edgeCount: Int,
    val reminderCount: Int,
    val notificationCount: Int
)

object DataBackupRepository {

    private const val BACKUP_VERSION = 1
    private const val APP_NAME = "Memossist"

    /**
     * Exports all user app data into a structured JSON string.
     */
    fun exportAllData(context: Context): String {
        val rootObj = JSONObject()
        rootObj.put("version", BACKUP_VERSION)
        rootObj.put("appName", APP_NAME)
        rootObj.put("exportTimestamp", System.currentTimeMillis())

        // 1. User Preferences
        val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
        val prefObj = JSONObject().apply {
            put("userName", prefs.getString("user_name", "Dinesh"))
            put("morningBriefingHour", prefs.getInt("morning_briefing_hour", 7))
            put("userAvatarUri", prefs.getString("user_avatar_uri", null))
        }
        rootObj.put("userPreferences", prefObj)

        // 2. Conversations & Chat Messages
        val conversations = ChatRepository.loadAllConversations(context)
        val convArray = JSONArray()
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
            convArray.put(convObj)
        }
        rootObj.put("conversations", convArray)

        // 3. Memory Vault Experiences
        val memories = MemoryVaultRepository.loadAllMemories(context)
        val memArray = JSONArray()
        for (mem in memories) {
            val memObj = JSONObject().apply {
                put("id", mem.id)
                put("title", mem.title)
                put("snippet", mem.snippet)
                put("message", mem.message)
                put("timestamp", mem.timestamp)
                put("location", mem.location)
                put("tag", mem.tag)
                put("timeAgo", mem.timeAgo)
                put("isPinned", mem.isPinned)
                put("wordSynonymsJson", mem.wordSynonymsJson)
                put("attachmentsJson", MemoryVaultRepository.serializeAttachments(mem.attachments))
            }
            memArray.put(memObj)
        }
        rootObj.put("memories", memArray)

        // 4. DAG Edges
        val edges = ExperienceDagRepository.loadAllEdges(context)
        val edgeArray = JSONArray()
        for (edge in edges) {
            val edgeObj = JSONObject().apply {
                put("experienceId1", edge.experienceId1)
                put("experienceId2", edge.experienceId2)
                put("title1", edge.title1)
                put("title2", edge.title2)
                put("strength", edge.strength)
                put("usageCount", edge.usageCount)
                put("lastUpdated", edge.lastUpdated)

                val termsArr = JSONArray()
                edge.sharedTerms.forEach { termsArr.put(it) }
                put("sharedTerms", termsArr)
            }
            edgeArray.put(edgeObj)
        }
        rootObj.put("dagEdges", edgeArray)

        // 5. Reminders
        val reminders = ReminderRepository.loadAllReminders(context)
        val remArray = JSONArray()
        for (rem in reminders) {
            val remObj = JSONObject().apply {
                put("id", rem.id)
                put("title", rem.title)
                put("description", rem.description)
                put("eventTimeMillis", rem.eventTimeMillis)
                put("importance", rem.importance)
                put("category", rem.category)
                put("isActive", rem.isActive)
                put("isCompleted", rem.isCompleted)
                put("createdTimestamp", rem.createdTimestamp)

                val tArray = JSONArray()
                for (t in rem.triggers) {
                    val tObj = JSONObject().apply {
                        put("triggerId", t.triggerId)
                        put("reminderId", t.reminderId)
                        put("triggerTimeMillis", t.triggerTimeMillis)
                        put("type", t.type)
                        put("deliveryStyle", t.deliveryStyle)
                        put("humanoidMessage", t.humanoidMessage)
                        put("isTriggered", t.isTriggered)
                    }
                    tArray.put(tObj)
                }
                put("triggers", tArray)
            }
            remArray.put(remObj)
        }
        rootObj.put("reminders", remArray)

        // 6. Notification History
        val notifications = NotificationHistoryRepository.loadLast30DaysNotifications(context)
        val notifArray = JSONArray()
        for (notif in notifications) {
            val notifObj = JSONObject().apply {
                put("id", notif.id)
                put("reminderId", notif.reminderId)
                put("title", notif.title)
                put("message", notif.message)
                put("timestamp", notif.timestamp)
                put("type", notif.type)
                put("isRead", notif.isRead)
            }
            notifArray.put(notifObj)
        }
        rootObj.put("notifications", notifArray)

        return rootObj.toString(2)
    }

    /**
     * Imports and merges backup JSON data into the app.
     * All imported item IDs are assigned a prefix "I_" to guarantee zero ID collision
     * with existing app data and clearly mark imported items.
     */
    fun importAllData(context: Context, jsonStr: String): ImportResult {
        val rootObj = JSONObject(jsonStr)

        // 1. User Preferences (Restore username & briefing hour if present)
        if (rootObj.has("userPreferences")) {
            try {
                val prefObj = rootObj.getJSONObject("userPreferences")
                val prefs = context.getSharedPreferences("MemossistPrefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                if (prefObj.has("userName")) {
                    val importedName = prefObj.getString("userName")
                    if (importedName.isNotBlank() && prefs.getString("user_name", "Dinesh") == "Dinesh") {
                        editor.putString("user_name", importedName)
                    }
                }
                if (prefObj.has("morningBriefingHour")) {
                    editor.putInt("morning_briefing_hour", prefObj.getInt("morningBriefingHour"))
                }
                editor.apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Map to track old experience ID -> new "I_" prefixed experience ID
        val expIdMap = mutableMapOf<String, String>()

        // 2. Import & Merge Memories (Experience Vault)
        var importedMemCount = 0
        val existingMemories = MemoryVaultRepository.loadAllMemories(context)
        val existingMemIds = existingMemories.map { it.id }.toSet()
        val mergedMemories = existingMemories.toMutableList()

        if (rootObj.has("memories")) {
            val memArray = rootObj.getJSONArray("memories")
            for (i in 0 until memArray.length()) {
                try {
                    val memObj = memArray.getJSONObject(i)
                    val oldId = memObj.getString("id")
                    val newId = if (oldId.startsWith("I_")) oldId else "I_$oldId"
                    expIdMap[oldId] = newId

                    if (existingMemIds.contains(newId)) continue

                    val title = memObj.getString("title")
                    val snippet = memObj.optString("snippet", title)
                    val message = memObj.optString("message", snippet)
                    val timestamp = memObj.optString("timestamp", MemoryVaultRepository.formatCurrentTime())
                    val location = memObj.optString("location", MemoryVaultRepository.getCurrentLocation())
                    val tag = memObj.optString("tag", "Imported")
                    val timeAgo = memObj.optString("timeAgo", "Imported")
                    val isPinned = memObj.optBoolean("isPinned", false)
                    val wordSynonymsJson = if (memObj.has("wordSynonymsJson") && !memObj.isNull("wordSynonymsJson")) memObj.getString("wordSynonymsJson") else null
                    val rawAttachmentsJson = if (memObj.has("attachmentsJson") && !memObj.isNull("attachmentsJson")) memObj.getString("attachmentsJson") else null
                    val attachmentsList = MemoryVaultRepository.parseAttachments(rawAttachmentsJson)

                    val memoryItem = MemoryItem(
                        id = newId,
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
                    mergedMemories.add(memoryItem)
                    importedMemCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            MemoryVaultRepository.saveAllMemories(context, mergedMemories)
        }

        // 3. Import & Merge DAG Edges
        var importedEdgeCount = 0
        val existingEdges = ExperienceDagRepository.loadAllEdges(context)
        val mergedEdges = existingEdges.toMutableList()

        if (rootObj.has("dagEdges")) {
            val edgeArray = rootObj.getJSONArray("dagEdges")
            for (i in 0 until edgeArray.length()) {
                try {
                    val edgeObj = edgeArray.getJSONObject(i)
                    val oldId1 = edgeObj.getString("experienceId1")
                    val oldId2 = edgeObj.getString("experienceId2")

                    val newId1 = expIdMap[oldId1] ?: (if (oldId1.startsWith("I_")) oldId1 else "I_$oldId1")
                    val newId2 = expIdMap[oldId2] ?: (if (oldId2.startsWith("I_")) oldId2 else "I_$oldId2")

                    val title1 = edgeObj.optString("title1", "Experience $newId1")
                    val title2 = edgeObj.optString("title2", "Experience $newId2")
                    val strength = edgeObj.getDouble("strength")
                    val usageCount = edgeObj.optInt("usageCount", 1)
                    val lastUpdated = edgeObj.optLong("lastUpdated", System.currentTimeMillis())

                    val sharedTermsList = mutableListOf<String>()
                    val termsArr = edgeObj.optJSONArray("sharedTerms")
                    if (termsArr != null) {
                        for (j in 0 until termsArr.length()) {
                            sharedTermsList.add(termsArr.getString(j))
                        }
                    }

                    // Check if edge pair already exists in mergedEdges
                    val exists = mergedEdges.any {
                        (it.experienceId1 == newId1 && it.experienceId2 == newId2) ||
                        (it.experienceId1 == newId2 && it.experienceId2 == newId1)
                    }

                    if (!exists) {
                        mergedEdges.add(
                            DagEdge(
                                experienceId1 = newId1,
                                experienceId2 = newId2,
                                title1 = title1,
                                title2 = title2,
                                strength = strength,
                                usageCount = usageCount,
                                lastUpdated = lastUpdated,
                                sharedTerms = sharedTermsList
                            )
                        )
                        importedEdgeCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ExperienceDagRepository.saveAllEdges(context, mergedEdges)
        }

        // 4. Import & Merge Conversations & Chat Messages
        var importedConvCount = 0
        val existingConversations = ChatRepository.loadAllConversations(context)
        val existingConvIds = existingConversations.map { it.id }.toSet()
        val mergedConversations = existingConversations.toMutableList()

        if (rootObj.has("conversations")) {
            val convArray = rootObj.getJSONArray("conversations")
            for (i in 0 until convArray.length()) {
                try {
                    val convObj = convArray.getJSONObject(i)
                    val oldConvId = convObj.getString("id")
                    val newConvId = if (oldConvId.startsWith("I_")) oldConvId else "I_$oldConvId"

                    if (existingConvIds.contains(newConvId)) continue

                    val title = convObj.getString("title")
                    val lastUpdated = convObj.optLong("lastUpdated", System.currentTimeMillis())
                    val isPinned = convObj.optBoolean("isPinned", false)

                    val messagesList = mutableListOf<ChatMessage>()
                    val msgArray = convObj.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until msgArray.length()) {
                        val msgObj = msgArray.getJSONObject(j)
                        val oldMsgId = msgObj.optString("id", java.util.UUID.randomUUID().toString())
                        val newMsgId = if (oldMsgId.startsWith("I_")) oldMsgId else "I_$oldMsgId"

                        val text = msgObj.optString("text", "")
                        val isUser = msgObj.optBoolean("isUser", false)
                        val timestamp = msgObj.optLong("timestamp", System.currentTimeMillis())
                        val isThinking = msgObj.optBoolean("isThinking", false)
                        val thinkingStatus = if (msgObj.has("thinkingStatus") && !msgObj.isNull("thinkingStatus")) msgObj.getString("thinkingStatus").takeIf { it.isNotEmpty() && it != "null" } else null
                        val debugLog = if (msgObj.has("debugLog") && !msgObj.isNull("debugLog")) msgObj.getString("debugLog").takeIf { it.isNotEmpty() && it != "null" } else null
                        val rawAttJson = if (msgObj.has("attachmentsJson") && !msgObj.isNull("attachmentsJson")) msgObj.getString("attachmentsJson") else null
                        val msgAtts = MemoryVaultRepository.parseAttachments(rawAttJson)

                        messagesList.add(
                            ChatMessage(
                                id = newMsgId,
                                conversationId = newConvId,
                                text = text,
                                isUser = isUser,
                                timestamp = timestamp,
                                isThinking = isThinking,
                                thinkingStatus = thinkingStatus,
                                debugLog = debugLog,
                                attachments = msgAtts
                            )
                        )
                    }

                    mergedConversations.add(Conversation(newConvId, title, lastUpdated, isPinned, messagesList))
                    importedConvCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ChatRepository.saveAllConversations(context, mergedConversations)
        }

        // 5. Import & Merge Reminders
        var importedReminderCount = 0
        val existingReminders = ReminderRepository.loadAllReminders(context)
        val existingReminderIds = existingReminders.map { it.id }.toSet()
        val mergedReminders = existingReminders.toMutableList()

        if (rootObj.has("reminders")) {
            val remArray = rootObj.getJSONArray("reminders")
            for (i in 0 until remArray.length()) {
                try {
                    val remObj = remArray.getJSONObject(i)
                    val oldRemId = remObj.getString("id")
                    val newRemId = if (oldRemId.startsWith("I_")) oldRemId else "I_$oldRemId"

                    if (existingReminderIds.contains(newRemId)) continue

                    val title = remObj.getString("title")
                    val description = remObj.optString("description", "")
                    val eventTimeMillis = remObj.getLong("eventTimeMillis")
                    val importance = remObj.optString("importance", "MEDIUM")
                    val category = remObj.optString("category", "PERSONAL")
                    val isActive = remObj.optBoolean("isActive", true)
                    val isCompleted = remObj.optBoolean("isCompleted", false)
                    val createdTimestamp = remObj.optLong("createdTimestamp", System.currentTimeMillis())

                    val triggersList = mutableListOf<ReminderTrigger>()
                    val triggersArray = remObj.optJSONArray("triggers")
                    if (triggersArray != null) {
                        for (j in 0 until triggersArray.length()) {
                            val tObj = triggersArray.getJSONObject(j)
                            val oldTrigId = tObj.getString("triggerId")
                            val newTrigId = if (oldTrigId.startsWith("I_")) oldTrigId else "I_$oldTrigId"

                            val triggerTimeMillis = tObj.getLong("triggerTimeMillis")
                            val type = tObj.optString("type", "CUSTOM")
                            val deliveryStyle = tObj.optString("deliveryStyle", "NOTIFICATION")
                            val humanoidMessage = tObj.getString("humanoidMessage")
                            val isTriggered = tObj.optBoolean("isTriggered", false)

                            triggersList.add(
                                ReminderTrigger(
                                    triggerId = newTrigId,
                                    reminderId = newRemId,
                                    triggerTimeMillis = triggerTimeMillis,
                                    type = type,
                                    deliveryStyle = deliveryStyle,
                                    humanoidMessage = humanoidMessage,
                                    isTriggered = isTriggered
                                )
                            )
                        }
                    }

                    val reminderItem = ReminderItem(
                        id = newRemId,
                        title = title,
                        description = description,
                        eventTimeMillis = eventTimeMillis,
                        importance = importance,
                        category = category,
                        isActive = isActive,
                        isCompleted = isCompleted,
                        createdTimestamp = createdTimestamp,
                        triggers = triggersList
                    )

                    mergedReminders.add(reminderItem)
                    importedReminderCount++

                    if (reminderItem.isActive && !reminderItem.isCompleted) {
                        ReminderRepository.scheduleSystemAlarmsForReminder(context, reminderItem)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            ReminderRepository.saveAllReminders(context, mergedReminders)
        }

        // 6. Import & Merge Notifications
        var importedNotifCount = 0
        val existingNotifs = NotificationHistoryRepository.loadLast30DaysNotifications(context)
        val existingNotifIds = existingNotifs.map { it.id }.toSet()
        val mergedNotifs = existingNotifs.toMutableList()

        if (rootObj.has("notifications")) {
            val notifArray = rootObj.getJSONArray("notifications")
            for (i in 0 until notifArray.length()) {
                try {
                    val notifObj = notifArray.getJSONObject(i)
                    val oldNotifId = notifObj.getString("id")
                    val newNotifId = if (oldNotifId.startsWith("I_")) oldNotifId else "I_$oldNotifId"

                    if (existingNotifIds.contains(newNotifId)) continue

                    val oldRemId = if (notifObj.has("reminderId") && !notifObj.isNull("reminderId")) notifObj.getString("reminderId").takeIf { it.isNotEmpty() && it != "null" } else null
                    val newRemId = if (oldRemId != null) (if (oldRemId.startsWith("I_")) oldRemId else "I_$oldRemId") else null
                    val title = notifObj.getString("title")
                    val message = notifObj.getString("message")
                    val timestamp = notifObj.getLong("timestamp")
                    val type = notifObj.optString("type", "SYSTEM")
                    val isRead = notifObj.optBoolean("isRead", false)

                    mergedNotifs.add(
                        NotificationItem(
                            id = newNotifId,
                            reminderId = newRemId,
                            title = title,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead
                        )
                    )
                    importedNotifCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            NotificationHistoryRepository.saveAllNotifications(context, mergedNotifs)
        }

        return ImportResult(
            conversationCount = importedConvCount,
            memoryCount = importedMemCount,
            edgeCount = importedEdgeCount,
            reminderCount = importedReminderCount,
            notificationCount = importedNotifCount
        )
    }
}
