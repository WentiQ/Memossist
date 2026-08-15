package com.example.apptempleate

/**
 * The seven message routing states defined for Memossist local classification pipeline.
 */
enum class MessageType(
    val displayName: String,
    val iconEmoji: String,
    val description: String
) {
    TELLING("Statement", "💬", "Saves personal facts & information to Memory Vault"),
    ASKING("Question", "❓", "Answers queries using your Memory Vault"),
    MIXED("Statement + Question", "💬❓", "Saves new facts and answers your question"),
    REMINDER_ONLY("Reminder", "⏰", "Schedules reminder alarms for future tasks"),
    REMINDER_AND_TELLING("Reminder + Statement", "⏰💬", "Schedules reminder and saves personal facts"),
    REMINDER_AND_ASKING("Reminder + Question", "⏰❓", "Schedules reminder and answers your question"),
    REMINDER_AND_MIXED("Reminder + Both", "⏰💬❓", "Schedules reminder, saves facts, and answers question")
}

/**
 * Represents the complete result of local sentence-level reminder classification,
 * non-reminder intent classification, routing state, and execution performance metrics.
 */
data class ClassificationResult(
    val originalUserMessage: String,
    val sentences: List<String>,
    val sentenceLabels: List<Int>,
    val reminderSentences: List<String>,
    val nonReminderSentences: List<String>,
    val intent: String,
    val messageType: MessageType,
    val confidence: Float = 1.0f,
    val isFallback: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val segmentationTimeMs: Long = 0L,
    val reminderClassifyTimeMs: Long = 0L,
    val intentClassifyTimeMs: Long = 0L,
    val totalClassificationTimeMs: Long = 0L
)
