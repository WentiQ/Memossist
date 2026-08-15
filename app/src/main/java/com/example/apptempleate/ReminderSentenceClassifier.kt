package com.example.apptempleate

import android.content.Context
import java.io.InputStream

/**
 * MODEL 1: Reminder Sentence Classifier
 * Evaluates ONE sentence at a time.
 * Output:
 *  0 = NON_REMINDER
 *  1 = REMINDER
 */
object ReminderSentenceClassifier {
    private const val ASSET_PATH = "models/reminder_classifier.json"
    private var instance: LocalTextClassifier? = null

    @Synchronized
    fun init(context: Context) {
        if (instance == null) {
            instance = LocalTextClassifier.fromAsset(context, ASSET_PATH)
        }
    }

    @Synchronized
    fun initFromStream(stream: InputStream) {
        if (instance == null) {
            instance = LocalTextClassifier.fromInputStream(stream)
        }
    }

    /**
     * Classifies a single sentence.
     * @return Pair of label (0 = NON_REMINDER, 1 = REMINDER) and probability score
     */
    fun classifySentence(context: Context?, sentence: String): Pair<Int, Float> {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return Pair(0, 0f)

        val lower = trimmed.lowercase()

        // 1. Explicit Non-Reminder Declarations (Personal facts, preferences, static states, past events)
        if (isStaticFactualStatement(lower)) {
            return Pair(0, 0.95f)
        }

        // 2. Explicit Strong Reminder Patterns (Requests, future deadlines, events, tasks)
        if (isStrongReminderPattern(lower)) {
            return Pair(1, 0.95f)
        }

        // 3. Question without reminder trigger -> Non-reminder
        if (lower.endsWith("?") && !lower.contains("remind me") && !lower.contains("can you remind") && !lower.contains("don't forget") && !lower.contains("dont forget")) {
            return Pair(0, 0.95f)
        }

        val classifier = getClassifier(context)
        if (classifier != null) {
            val result = classifier.predictBinary(trimmed)
            // If ML says reminder, require at least one action or time marker to avoid false positives
            if (result.label == 1 && result.probability >= 0.55f && hasAnyActionOrTimeCue(lower)) {
                return Pair(1, result.probability)
            }
            return Pair(0, (1.0f - result.probability).coerceAtLeast(0.70f))
        }

        return fallbackRuleBased(trimmed)
    }

    @Synchronized
    private fun getClassifier(context: Context?): LocalTextClassifier? {
        if (instance != null) return instance
        if (context != null) {
            try {
                instance = LocalTextClassifier.fromAsset(context, ASSET_PATH)
            } catch (e: Exception) {
                android.util.Log.e("ReminderClassifier", "Failed to load reminder model asset", e)
            }
        }
        return instance
    }

    private fun isStaticFactualStatement(lower: String): Boolean {
        // Exclude reminder triggers first
        if (lower.contains("remind me") || lower.contains("remember to") || lower.contains("don't forget") || lower.contains("dont forget") || lower.contains("set a reminder")) {
            return false
        }

        // Static preference / identity / fact statements
        val staticPrefixes = listOf(
            "my favorite ", "my name is ", "i am ", "i'm ", "i live in ", "i was born in ",
            "i work as ", "i work at ", "i work for ", "i work in ", "my job is ",
            "i like ", "i love ", "i prefer ", "i enjoy ", "i dislike ", "i hate ",
            "my dog is ", "my cat is ", "my pet is ", "my car is ", "my house is ", "my hobby is ",
            "i know ", "i think ", "i believe ", "the weather is ", "it is sunny", "it is raining"
        )
        if (staticPrefixes.any { lower.startsWith(it) } || lower.contains(" favorite ")) {
            // Unless it explicitly contains future event keywords
            val futureEvents = listOf("tomorrow", "tonight", "next week", "next month", "appointment", "meeting", "exam", "deadline", "flight")
            if (futureEvents.none { lower.contains(it) }) {
                return true
            }
        }

        // Statements of possession / static facts: "i have a dog", "i have two cats", "i have blue eyes", etc.
        if (lower.startsWith("i have ") || lower.startsWith("i've got ") || lower.startsWith("i got ")) {
            val reminderKeywords = listOf(
                "exam", "meeting", "appointment", "deadline", "flight", "interview", "conference",
                "consultation", "lecture", "class at", "presentation", "session", "reservation",
                "to attend", "to do", "to submit", "to pay", "to call", "to clean", "to go",
                "tomorrow", "tonight", "next ", "at ", "on "
            )
            if (reminderKeywords.none { lower.contains(it) }) {
                return true
            }
        }

        // Past events
        val pastMarkers = listOf("yesterday", "last night", "last week", "last month", "last year", "already ")
        if (pastMarkers.any { lower.contains(it) }) {
            return true
        }

        return false
    }

    private fun isStrongReminderPattern(lower: String): Boolean {
        val explicitTriggers = listOf(
            "remind me", "can you remind", "please remind", "remember to", "don't forget to",
            "dont forget to", "set a reminder", "notify me", "alert me", "my task is to",
            "commitment to"
        )
        if (explicitTriggers.any { lower.contains(it) }) return true

        val futureEvents = listOf(
            "have an exam", "have a meeting", "have an appointment", "doctor appointment",
            "dentist appointment", "flight departs", "flight leaves", "flight to", "exam is",
            "meeting is", "appointment is", "deadline is", "conference is", "due tomorrow",
            "due next", "due on", "due at", "take cake out of oven", "fast for blood test",
            "renew passport", "board train", "check in for flight"
        )
        if (futureEvents.any { lower.contains(it) }) return true

        // Action verbs with explicit future/temporal cue
        val hasTemporalCue = lower.contains("tomorrow") || lower.contains("tonight") ||
                lower.contains("later today") || lower.contains("next ") ||
                Regex("\\b(at|by)\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b").containsMatchIn(lower) ||
                Regex("\\bin\\s+\\d+\\s+(min|minute|hour|day)s?\\b").containsMatchIn(lower)

        val hasObligationVerb = lower.contains("have to ") || lower.contains("need to ") ||
                lower.contains("must ") || lower.contains("supposed to ") ||
                lower.contains("plan to ") || lower.contains("going to ") || lower.contains("will ")

        return hasTemporalCue && hasObligationVerb
    }

    private fun hasAnyActionOrTimeCue(lower: String): Boolean {
        val timeOrActionWords = listOf(
            "tomorrow", "tonight", "later", "next", "morning", "afternoon", "evening",
            "pm", "am", "deadline", "schedule", "exam", "meeting", "appointment", "flight",
            "interview", "lecture", "consultation", "presentation", "reservation", "homework",
            "bill", "rent", "prescription", "medicine", "lawn", "faucet", "trash", "bins",
            "groceries", "renew", "pay", "submit", "attend", "cancel", "pick up", "clean",
            "feed", "call", "take", "fast", "order", "buy", "check"
        )
        return timeOrActionWords.any { lower.contains(it) }
    }

    private fun fallbackRuleBased(sentence: String): Pair<Int, Float> {
        val lower = sentence.lowercase()
        val isRem = isStrongReminderPattern(lower)
        return Pair(if (isRem) 1 else 0, if (isRem) 0.95f else 0.1f)
    }
}
