package com.example.apptempleate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemossistPromptBuilderTest {

    private val sampleCandidates = listOf(
        MemoryItem(
            id = "EXP-000001",
            title = "Kotlin Coroutines",
            snippet = "Explored Kotlin Coroutines flow",
            message = "I studied Kotlin coroutines and dispatchers.",
            timestamp = "2026-08-14 10:00 AM",
            location = "Office",
            tag = "Study",
            timeAgo = "Earlier today"
        )
    )

    @Test
    fun testPrompt_ReminderOnly() {
        val cr = ClassificationResult(
            originalUserMessage = "I have an exam tomorrow.",
            sentences = listOf("I have an exam tomorrow."),
            sentenceLabels = listOf(1),
            reminderSentences = listOf("I have an exam tomorrow."),
            nonReminderSentences = emptyList(),
            intent = "TELLING",
            messageType = MessageType.REMINDER_ONLY
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("You are Memossist, a warm personal reminder assistant."))
        assertTrue(prompt.contains("The app has already classified this message as REMINDER ONLY."))
        assertTrue(prompt.contains("[EXTRACTED_REMINDERS: {\"title\":\"<task/event>\""))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))

        // Must NOT contain fact extraction instructions or candidate experiences block
        assertFalse(prompt.contains("EXTRACTED_FACTS:"))
        assertFalse(prompt.contains("=== CANDIDATE EXPERIENCES ==="))
    }

    @Test
    fun testPrompt_Asking() {
        val cr = ClassificationResult(
            originalUserMessage = "What is Kotlin?",
            sentences = listOf("What is Kotlin?"),
            sentenceLabels = listOf(0),
            reminderSentences = emptyList(),
            nonReminderSentences = listOf("What is Kotlin?"),
            intent = "ASKING",
            messageType = MessageType.ASKING
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("You are Memossist, a warm, intelligent assistant with access to a Memory Vault."))
        assertTrue(prompt.contains("The app has already classified this message as ASKING ONLY."))
        assertTrue(prompt.contains("[USED_EXPERIENCES: EXP-ID1, EXP-ID2 or NONE]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))
        assertTrue(prompt.contains("=== CANDIDATE EXPERIENCES ==="))
        assertTrue(prompt.contains("[ID: EXP-000001]"))

        // Must NOT contain reminder extraction instructions or fact extraction instructions
        assertFalse(prompt.contains("EXTRACTED_REMINDERS:"))
        assertFalse(prompt.contains("EXTRACTED_FACTS:"))
    }

    @Test
    fun testPrompt_Telling() {
        val cr = ClassificationResult(
            originalUserMessage = "I'm learning Kotlin.",
            sentences = listOf("I'm learning Kotlin."),
            sentenceLabels = listOf(0),
            reminderSentences = emptyList(),
            nonReminderSentences = listOf("I'm learning Kotlin."),
            intent = "TELLING",
            messageType = MessageType.TELLING
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("You are Memossist, a warm personal memory assistant with a Memory Vault."))
        assertTrue(prompt.contains("The app has already classified this message as TELLING ONLY."))
        assertTrue(prompt.contains("[EXTRACTED_FACTS: [\"fact 1\",\"fact 2\"]]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))
        // Must NOT contain candidate experiences or reminder extraction instructions
        assertFalse(prompt.contains("=== CANDIDATE EXPERIENCES ==="))
        assertFalse(prompt.contains("EXTRACTED_REMINDERS:"))
    }

    @Test
    fun testPrompt_Mixed() {
        val cr = ClassificationResult(
            originalUserMessage = "I'm learning Kotlin. What should I learn next?",
            sentences = listOf("I'm learning Kotlin.", "What should I learn next?"),
            sentenceLabels = listOf(0, 0),
            reminderSentences = emptyList(),
            nonReminderSentences = listOf("I'm learning Kotlin.", "What should I learn next?"),
            intent = "MIXED",
            messageType = MessageType.MIXED
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("The app has already classified this message as MIXED (Statements + Questions)."))
        assertTrue(prompt.contains("[EXTRACTED_FACTS: [\"fact 1\",\"fact 2\"]]"))
        assertTrue(prompt.contains("[USED_EXPERIENCES: EXP-ID1, EXP-ID2 or NONE]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))
        assertTrue(prompt.contains("=== CANDIDATE EXPERIENCES ==="))

        // Must NOT contain reminder extraction instructions
        assertFalse(prompt.contains("EXTRACTED_REMINDERS:"))
    }

    @Test
    fun testPrompt_ReminderAndAsking() {
        val cr = ClassificationResult(
            originalUserMessage = "I have an exam tomorrow. What should I study?",
            sentences = listOf("I have an exam tomorrow.", "What should I study?"),
            sentenceLabels = listOf(1, 0),
            reminderSentences = listOf("I have an exam tomorrow."),
            nonReminderSentences = listOf("What should I study?"),
            intent = "ASKING",
            messageType = MessageType.REMINDER_AND_ASKING
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("The app has already classified this message as REMINDER + ASKING."))
        assertTrue(prompt.contains("[EXTRACTED_REMINDERS: {\"title\":\"<task/event>\""))
        assertTrue(prompt.contains("[USED_EXPERIENCES: EXP-ID1, EXP-ID2 or NONE]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))
        assertTrue(prompt.contains("=== CANDIDATE EXPERIENCES ==="))

        // Must NOT contain fact extraction instructions
        assertFalse(prompt.contains("EXTRACTED_FACTS:"))
    }

    @Test
    fun testPrompt_ReminderAndTelling() {
        val cr = ClassificationResult(
            originalUserMessage = "I have an exam tomorrow. I'm nervous.",
            sentences = listOf("I have an exam tomorrow.", "I'm nervous."),
            sentenceLabels = listOf(1, 0),
            reminderSentences = listOf("I have an exam tomorrow."),
            nonReminderSentences = listOf("I'm nervous."),
            intent = "TELLING",
            messageType = MessageType.REMINDER_AND_TELLING
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("The app has already classified this message as REMINDER + TELLING."))
        assertTrue(prompt.contains("[EXTRACTED_REMINDERS: {\"title\":\"<task/event>\""))
        assertTrue(prompt.contains("[EXTRACTED_FACTS: [\"fact 1\",\"fact 2\"]]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))

        // Must NOT contain candidate experiences or used experiences
        assertFalse(prompt.contains("USED_EXPERIENCES:"))
        assertFalse(prompt.contains("=== CANDIDATE EXPERIENCES ==="))
    }

    @Test
    fun testPrompt_ReminderAndMixed() {
        val cr = ClassificationResult(
            originalUserMessage = "I have an exam tomorrow. I'm nervous. What should I study?",
            sentences = listOf("I have an exam tomorrow.", "I'm nervous.", "What should I study?"),
            sentenceLabels = listOf(1, 0, 0),
            reminderSentences = listOf("I have an exam tomorrow."),
            nonReminderSentences = listOf("I'm nervous.", "What should I study?"),
            intent = "MIXED",
            messageType = MessageType.REMINDER_AND_MIXED
        )

        val prompt = MemossistPromptBuilder.build(cr, sampleCandidates)

        assertTrue(prompt.contains("The app has already classified this message as REMINDER + MIXED (Reminder + Statement + Question)."))
        assertTrue(prompt.contains("[EXTRACTED_REMINDERS: {\"title\":\"<task/event>\""))
        assertTrue(prompt.contains("[EXTRACTED_FACTS: [\"fact 1\",\"fact 2\"]]"))
        assertTrue(prompt.contains("[USED_EXPERIENCES: EXP-ID1, EXP-ID2 or NONE]"))
        assertTrue(prompt.contains("[HUMANOID_ANSWER]"))
        assertTrue(prompt.contains("=== CANDIDATE EXPERIENCES ==="))
    }

    @Test
    fun testPrompt_FallbackSafety() {
        val fallbackPrompt = MemossistPromptBuilder.buildFallbackFullPrompt(sampleCandidates)
        assertTrue(fallbackPrompt.contains("You are Memossist, a warm humanoid AI with access to the user's Memory Vault."))
        assertTrue(fallbackPrompt.contains("[INTENT: ASKING|TELLING|MIXED]"))
        assertTrue(fallbackPrompt.contains("=== CANDIDATE EXPERIENCES ==="))
    }

    @Test
    fun testTokenReduction_SpecializedPromptsVsFallback() {
        val fallbackPrompt = MemossistPromptBuilder.buildFallbackFullPrompt(sampleCandidates)
        val fallbackTokens = MemossistPromptBuilder.estimateTokenCount(fallbackPrompt)

        val askingCr = ClassificationResult(
            originalUserMessage = "What is Kotlin?",
            sentences = listOf("What is Kotlin?"),
            sentenceLabels = listOf(0),
            reminderSentences = emptyList(),
            nonReminderSentences = listOf("What is Kotlin?"),
            intent = "ASKING",
            messageType = MessageType.ASKING
        )
        val askingPrompt = MemossistPromptBuilder.build(askingCr, sampleCandidates)
        val askingTokens = MemossistPromptBuilder.estimateTokenCount(askingPrompt)

        val reminderCr = ClassificationResult(
            originalUserMessage = "I have an exam tomorrow.",
            sentences = listOf("I have an exam tomorrow."),
            sentenceLabels = listOf(1),
            reminderSentences = listOf("I have an exam tomorrow."),
            nonReminderSentences = emptyList(),
            intent = "TELLING",
            messageType = MessageType.REMINDER_ONLY
        )
        val reminderPrompt = MemossistPromptBuilder.build(reminderCr, sampleCandidates)
        val reminderTokens = MemossistPromptBuilder.estimateTokenCount(reminderPrompt)

        assertTrue(askingTokens < fallbackTokens)
        assertTrue(reminderTokens < fallbackTokens)
    }
}
