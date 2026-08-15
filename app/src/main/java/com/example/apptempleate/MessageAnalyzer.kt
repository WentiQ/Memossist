package com.example.apptempleate

import android.content.Context

/**
 * MessageAnalyzer coordinates the two-stage local offline classification pipeline:
 * 1. Sentence segmentation
 * 2. Model 1: Sentence-level reminder classification
 * 3. Routing decision tree
 * 4. Model 2: Non-reminder intent classification
 * 5. MessageType resolution & execution metrics collection
 */
object MessageAnalyzer {

    const val CONFIDENCE_CONFIRM_THRESHOLD = 0.70f

    fun analyze(
        context: Context?,
        userMessage: String,
        forcedMessageType: MessageType? = null
    ): ClassificationResult {
        val startTime = System.currentTimeMillis()
        val trimmed = userMessage.trim()

        if (forcedMessageType != null) {
            val sentences = SentenceSegmenter.segment(trimmed)
            val isReminder = forcedMessageType in listOf(
                MessageType.REMINDER_ONLY,
                MessageType.REMINDER_AND_TELLING,
                MessageType.REMINDER_AND_ASKING,
                MessageType.REMINDER_AND_MIXED
            )
            val intent = when (forcedMessageType) {
                MessageType.ASKING, MessageType.REMINDER_AND_ASKING -> "ASKING"
                MessageType.MIXED, MessageType.REMINDER_AND_MIXED -> "MIXED"
                else -> "TELLING"
            }
            return ClassificationResult(
                originalUserMessage = userMessage,
                sentences = sentences,
                sentenceLabels = if (isReminder) sentences.map { 1 } else sentences.map { 0 },
                reminderSentences = if (isReminder) sentences else emptyList(),
                nonReminderSentences = if (!isReminder) sentences else emptyList(),
                intent = intent,
                messageType = forcedMessageType,
                confidence = 1.0f,
                isFallback = false,
                requiresConfirmation = false,
                totalClassificationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (trimmed.isEmpty()) {
            return ClassificationResult(
                originalUserMessage = userMessage,
                sentences = emptyList(),
                sentenceLabels = emptyList(),
                reminderSentences = emptyList(),
                nonReminderSentences = emptyList(),
                intent = "TELLING",
                messageType = MessageType.TELLING,
                confidence = 1.0f,
                isFallback = false,
                requiresConfirmation = false,
                segmentationTimeMs = 0L,
                reminderClassifyTimeMs = 0L,
                intentClassifyTimeMs = 0L,
                totalClassificationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            // Stage 1: Sentence Segmentation
            val segStart = System.currentTimeMillis()
            val sentences = SentenceSegmenter.segment(trimmed)
            val segTime = System.currentTimeMillis() - segStart

            // Stage 2: Model 1 - Reminder Sentence Classifier on every sentence
            val remStart = System.currentTimeMillis()
            val sentenceLabels = mutableListOf<Int>()
            val reminderSentences = mutableListOf<String>()
            val nonReminderSentences = mutableListOf<String>()
            var minConfidence = 1.0f

            for (sentence in sentences) {
                val (label, prob) = ReminderSentenceClassifier.classifySentence(context, sentence)
                sentenceLabels.add(label)
                val conf = prob.coerceIn(0.50f, 1.0f)
                if (conf < minConfidence) {
                    minConfidence = conf
                }
                if (label == 1) {
                    reminderSentences.add(sentence)
                } else {
                    nonReminderSentences.add(sentence)
                }
            }
            val remTime = System.currentTimeMillis() - remStart

            val allReminders = sentenceLabels.isNotEmpty() && sentenceLabels.all { it == 1 }
            val allNonReminders = sentenceLabels.isNotEmpty() && sentenceLabels.all { it == 0 }

            val intentStart = System.currentTimeMillis()
            val intent: String
            val messageType: MessageType

            if (allReminders) {
                // CASE 1: All sentences are reminders -> REMINDER_ONLY (Model 2 is skipped)
                intent = "TELLING"
                messageType = MessageType.REMINDER_ONLY
            } else if (allNonReminders) {
                // CASE 2: All sentences are non-reminders -> Run Model 2 on combined text
                val combinedText = sentences.joinToString(" ")
                val (classifiedIntent, conf) = NonReminderIntentClassifier.classifyIntent(context, combinedText)
                intent = classifiedIntent
                val intentConf = conf.coerceIn(0.50f, 1.0f)
                if (intentConf < minConfidence) {
                    minConfidence = intentConf
                }
                messageType = when (intent) {
                    "ASKING" -> MessageType.ASKING
                    "TELLING" -> MessageType.TELLING
                    "MIXED" -> MessageType.MIXED
                    else -> MessageType.TELLING
                }
            } else {
                // CASE 3: Mixed (both 1 and 0) -> Run Model 2 ONLY on non-reminder sentences
                val nonReminderText = nonReminderSentences.joinToString(" ")
                val (classifiedIntent, conf) = NonReminderIntentClassifier.classifyIntent(context, nonReminderText)
                intent = classifiedIntent
                val intentConf = conf.coerceIn(0.50f, 1.0f)
                if (intentConf < minConfidence) {
                    minConfidence = intentConf
                }
                messageType = when (intent) {
                    "ASKING" -> MessageType.REMINDER_AND_ASKING
                    "TELLING" -> MessageType.REMINDER_AND_TELLING
                    "MIXED" -> MessageType.REMINDER_AND_MIXED
                    else -> MessageType.REMINDER_AND_TELLING
                }
            }
            val intentTime = System.currentTimeMillis() - intentStart
            val totalTime = System.currentTimeMillis() - startTime

            val requiresConfirm = minConfidence < CONFIDENCE_CONFIRM_THRESHOLD

            return ClassificationResult(
                originalUserMessage = userMessage,
                sentences = sentences,
                sentenceLabels = sentenceLabels,
                reminderSentences = reminderSentences,
                nonReminderSentences = nonReminderSentences,
                intent = intent,
                messageType = messageType,
                confidence = minConfidence,
                isFallback = false,
                requiresConfirmation = requiresConfirm,
                segmentationTimeMs = segTime,
                reminderClassifyTimeMs = remTime,
                intentClassifyTimeMs = intentTime,
                totalClassificationTimeMs = totalTime
            )
        } catch (e: Exception) {
            android.util.Log.e("MessageAnalyzer", "Error during local message analysis, falling back", e)
            val totalTime = System.currentTimeMillis() - startTime
            return ClassificationResult(
                originalUserMessage = userMessage,
                sentences = listOf(trimmed),
                sentenceLabels = listOf(0),
                reminderSentences = emptyList(),
                nonReminderSentences = listOf(trimmed),
                intent = "TELLING",
                messageType = MessageType.TELLING,
                confidence = 0f,
                isFallback = true,
                requiresConfirmation = true,
                segmentationTimeMs = 0L,
                reminderClassifyTimeMs = 0L,
                intentClassifyTimeMs = 0L,
                totalClassificationTimeMs = totalTime
            )
        }
    }
}
