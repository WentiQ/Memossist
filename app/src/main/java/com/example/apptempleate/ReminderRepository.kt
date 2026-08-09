package com.example.apptempleate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ReminderRepository {

    private const val FILE_NAME = "memossist_reminders.json"

    fun loadAllReminders(context: Context): MutableList<ReminderItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return mutableListOf()
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ReminderItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val description = obj.optString("description", "")
                val eventTimeMillis = obj.getLong("eventTimeMillis")
                val importance = obj.optString("importance", "MEDIUM")
                val category = obj.optString("category", "PERSONAL")
                val isActive = obj.optBoolean("isActive", true)
                val isCompleted = obj.optBoolean("isCompleted", false)
                val createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis())

                val triggersList = mutableListOf<ReminderTrigger>()
                val triggersArray = obj.optJSONArray("triggers")
                if (triggersArray != null) {
                    for (j in 0 until triggersArray.length()) {
                        val tObj = triggersArray.getJSONObject(j)
                        val triggerId = tObj.getString("triggerId")
                        val reminderId = tObj.optString("reminderId", id)
                        val triggerTimeMillis = tObj.getLong("triggerTimeMillis")
                        val type = tObj.optString("type", "CUSTOM")
                        val deliveryStyle = tObj.optString("deliveryStyle", "NOTIFICATION")
                        val humanoidMessage = tObj.getString("humanoidMessage")
                        val isTriggered = tObj.optBoolean("isTriggered", false)

                        triggersList.add(
                            ReminderTrigger(
                                triggerId = triggerId,
                                reminderId = reminderId,
                                triggerTimeMillis = triggerTimeMillis,
                                type = type,
                                deliveryStyle = deliveryStyle,
                                humanoidMessage = humanoidMessage,
                                isTriggered = isTriggered
                            )
                        )
                    }
                }

                list.add(
                    ReminderItem(
                        id = id,
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
                )
            }

            list.sortBy { it.eventTimeMillis }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun saveAllReminders(context: Context, reminders: List<ReminderItem>) {
        try {
            val array = JSONArray()
            for (item in reminders) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("description", item.description)
                    put("eventTimeMillis", item.eventTimeMillis)
                    put("importance", item.importance)
                    put("category", item.category)
                    put("isActive", item.isActive)
                    put("isCompleted", item.isCompleted)
                    put("createdTimestamp", item.createdTimestamp)

                    val tArray = JSONArray()
                    for (t in item.triggers) {
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
                array.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addOrUpdateReminder(context: Context, reminder: ReminderItem) {
        val list = loadAllReminders(context)
        val existingIndex = list.indexOfFirst { it.id == reminder.id }
        if (existingIndex >= 0) {
            list[existingIndex] = reminder
        } else {
            list.add(0, reminder)
        }
        saveAllReminders(context, list)

        if (reminder.isActive && !reminder.isCompleted) {
            scheduleSystemAlarmsForReminder(context, reminder)
        } else {
            cancelSystemAlarmsForReminder(context, reminder)
        }
    }

    fun toggleReminderActive(context: Context, reminderId: String): Boolean {
        val list = loadAllReminders(context)
        val item = list.find { it.id == reminderId } ?: return false
        item.isActive = !item.isActive
        saveAllReminders(context, list)

        if (item.isActive && !item.isCompleted) {
            scheduleSystemAlarmsForReminder(context, item)
        } else {
            cancelSystemAlarmsForReminder(context, item)
        }
        return item.isActive
    }

    fun toggleReminderCompleted(context: Context, reminderId: String): Boolean {
        val list = loadAllReminders(context)
        val item = list.find { it.id == reminderId } ?: return false
        item.isCompleted = !item.isCompleted
        saveAllReminders(context, list)

        if (item.isCompleted) {
            cancelSystemAlarmsForReminder(context, item)
        } else if (item.isActive) {
            scheduleSystemAlarmsForReminder(context, item)
        }
        return item.isCompleted
    }

    fun deleteReminder(context: Context, reminderId: String) {
        val list = loadAllReminders(context)
        val item = list.find { it.id == reminderId }
        if (item != null) {
            cancelSystemAlarmsForReminder(context, item)
            list.remove(item)
            saveAllReminders(context, list)
        }
    }

    fun markTriggerAsFired(context: Context, triggerId: String) {
        val list = loadAllReminders(context)
        for (item in list) {
            val trigger = item.triggers.find { it.triggerId == triggerId }
            if (trigger != null) {
                trigger.isTriggered = true
                saveAllReminders(context, list)
                break
            }
        }
    }

    fun scheduleSystemAlarmsForReminder(context: Context, reminder: ReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        for (trigger in reminder.triggers) {
            if (trigger.isTriggered || trigger.triggerTimeMillis <= now) continue

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.apptempleate.ACTION_TRIGGER_REMINDER"
                putExtra("EXTRA_REMINDER_ID", reminder.id)
                putExtra("EXTRA_TRIGGER_ID", trigger.triggerId)
                putExtra("EXTRA_TITLE", reminder.title)
                putExtra("EXTRA_MESSAGE", trigger.humanoidMessage)
                putExtra("EXTRA_DELIVERY_STYLE", trigger.deliveryStyle)
                putExtra("EXTRA_IMPORTANCE", reminder.importance)
                putExtra("EXTRA_EVENT_TIME", reminder.eventTimeMillis)
            }

            val requestCode = trigger.triggerId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        trigger.triggerTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        trigger.triggerTimeMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    trigger.triggerTimeMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelSystemAlarmsForReminder(context: Context, reminder: ReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (trigger in reminder.triggers) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val requestCode = trigger.triggerId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun triggerTestAlarmImmediately(context: Context, reminder: ReminderItem) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.apptempleate.ACTION_TRIGGER_REMINDER"
            putExtra("EXTRA_REMINDER_ID", reminder.id)
            putExtra("EXTRA_TRIGGER_ID", "TEST_${UUID.randomUUID()}")
            putExtra("EXTRA_TITLE", "⏰ Test Alarm: ${reminder.title}")
            putExtra(
                "EXTRA_MESSAGE",
                "Hey Dinesh, this is a test alert for your reminder: '${reminder.title}'. Extra class starts at ${reminder.getFormattedEventTimeOnly()}!"
            )
            putExtra("EXTRA_DELIVERY_STYLE", "FULLSCREEN_ALARM")
            putExtra("EXTRA_IMPORTANCE", reminder.importance)
            putExtra("EXTRA_EVENT_TIME", reminder.eventTimeMillis)
        }
        context.sendBroadcast(intent)
    }
}
