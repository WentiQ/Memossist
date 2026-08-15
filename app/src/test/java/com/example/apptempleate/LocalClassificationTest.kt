package com.example.apptempleate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class LocalClassificationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val remFile = File("src/main/assets/models/reminder_classifier.json")
            if (remFile.exists()) {
                remFile.inputStream().use {
                    ReminderSentenceClassifier.initFromStream(it)
                }
            }

            val intFile = File("src/main/assets/models/intent_classifier.json")
            if (intFile.exists()) {
                intFile.inputStream().use {
                    NonReminderIntentClassifier.initFromStream(it)
                }
            }
        }
    }

    @Test
    fun testSentenceSegmenter_multipleSentences() {
        val input = "I have an exam tomorrow. I'm learning Kotlin. Remind me to call Mom at 8."
        val sentences = SentenceSegmenter.segment(input)
        assertEquals(3, sentences.size)
        assertEquals("I have an exam tomorrow.", sentences[0])
        assertEquals("I'm learning Kotlin.", sentences[1])
        assertEquals("Remind me to call Mom at 8.", sentences[2])
    }

    @Test
    fun testSentenceSegmenter_singleSentence() {
        val input = "What is Kotlin?"
        val sentences = SentenceSegmenter.segment(input)
        assertEquals(1, sentences.size)
        assertEquals("What is Kotlin?", sentences[0])
    }

    @Test
    fun testReminderSentenceClassifier() {
        val (l1, _) = ReminderSentenceClassifier.classifySentence(null, "I have an exam tomorrow.")
        assertEquals(1, l1)

        val (l2, _) = ReminderSentenceClassifier.classifySentence(null, "I'm learning Kotlin.")
        assertEquals(0, l2)

        val (l3, _) = ReminderSentenceClassifier.classifySentence(null, "Remind me to call Mom at 8.")
        assertEquals(1, l3)

        val (l4, _) = ReminderSentenceClassifier.classifySentence(null, "What should I study?")
        assertEquals(0, l4)

        val (l5, _) = ReminderSentenceClassifier.classifySentence(null, "My favorite color is blue.")
        assertEquals(0, l5)

        val (l6, _) = ReminderSentenceClassifier.classifySentence(null, "I live in Chicago.")
        assertEquals(0, l6)

        val (l7, _) = ReminderSentenceClassifier.classifySentence(null, "I have two dogs.")
        assertEquals(0, l7)

        val (l8, _) = ReminderSentenceClassifier.classifySentence(null, "I like coffee.")
        assertEquals(0, l8)
    }

    @Test
    fun testNonReminderIntentClassifier() {
        val (i1, _) = NonReminderIntentClassifier.classifyIntent(null, "What is Kotlin?")
        assertEquals("ASKING", i1)

        val (i2, _) = NonReminderIntentClassifier.classifyIntent(null, "I'm learning Kotlin.")
        assertEquals("TELLING", i2)

        val (i3, _) = NonReminderIntentClassifier.classifyIntent(null, "I'm learning Kotlin. What should I learn next?")
        assertEquals("MIXED", i3)
    }

    @Test
    fun testRouting_Example1_ReminderOnly() {
        val msg = "I have an exam tomorrow."
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.REMINDER_ONLY, result.messageType)
        assertEquals("TELLING", result.intent)
        assertEquals(listOf(1), result.sentenceLabels)
        assertEquals(listOf("I have an exam tomorrow."), result.reminderSentences)
        assertTrue(result.nonReminderSentences.isEmpty())
        assertEquals(msg, result.originalUserMessage)
        assertFalse(result.isFallback)
    }

    @Test
    fun testRouting_Example2_Asking() {
        val msg = "What is Kotlin?"
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.ASKING, result.messageType)
        assertEquals("ASKING", result.intent)
        assertEquals(listOf(0), result.sentenceLabels)
        assertTrue(result.reminderSentences.isEmpty())
        assertEquals(listOf("What is Kotlin?"), result.nonReminderSentences)
        assertEquals(msg, result.originalUserMessage)
    }

    @Test
    fun testRouting_Example3_Telling() {
        val messages = listOf(
            "I'm learning Kotlin.",
            "My favorite color is blue.",
            "I live in Chicago.",
            "I have two dogs.",
            "My name is Dinesh.",
            "I work as a software engineer.",
            "I like drinking coffee in the morning.",
            "I bought a new laptop yesterday."
        )

        for (msg in messages) {
            val result = MessageAnalyzer.analyze(null, msg)
            assertEquals("Expected TELLING for: $msg", MessageType.TELLING, result.messageType)
            assertEquals("Expected intent TELLING for: $msg", "TELLING", result.intent)
            assertTrue("Expected no reminders for: $msg", result.reminderSentences.isEmpty())
            assertFalse("Expected non-empty nonReminderSentences for: $msg", result.nonReminderSentences.isEmpty())
            assertEquals(msg, result.originalUserMessage)
        }
    }

    @Test
    fun testRouting_Example4_Mixed() {
        val msg = "I'm learning Kotlin. What should I learn next?"
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.MIXED, result.messageType)
        assertEquals("MIXED", result.intent)
        assertEquals(listOf(0, 0), result.sentenceLabels)
        assertTrue(result.reminderSentences.isEmpty())
        assertEquals(2, result.nonReminderSentences.size)
        assertEquals(msg, result.originalUserMessage)
    }

    @Test
    fun testRouting_Example5_ReminderAndAsking() {
        val msg = "I have an exam tomorrow. What should I study?"
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.REMINDER_AND_ASKING, result.messageType)
        assertEquals("ASKING", result.intent)
        assertEquals(listOf(1, 0), result.sentenceLabels)
        assertEquals(listOf("I have an exam tomorrow."), result.reminderSentences)
        assertEquals(listOf("What should I study?"), result.nonReminderSentences)
        assertEquals(msg, result.originalUserMessage)
    }

    @Test
    fun testRouting_Example6_ReminderAndTelling() {
        val msg = "I have an exam tomorrow. I'm nervous."
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.REMINDER_AND_TELLING, result.messageType)
        assertEquals("TELLING", result.intent)
        assertEquals(listOf(1, 0), result.sentenceLabels)
        assertEquals(listOf("I have an exam tomorrow."), result.reminderSentences)
        assertEquals(listOf("I'm nervous."), result.nonReminderSentences)
        assertEquals(msg, result.originalUserMessage)
    }

    @Test
    fun testRouting_Example7_ReminderAndMixed() {
        val msg = "I have an exam tomorrow. I'm nervous. What should I study?"
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.REMINDER_AND_MIXED, result.messageType)
        assertEquals("MIXED", result.intent)
        assertEquals(listOf(1, 0, 0), result.sentenceLabels)
        assertEquals(listOf("I have an exam tomorrow."), result.reminderSentences)
        assertEquals(listOf("I'm nervous.", "What should I study?"), result.nonReminderSentences)
        assertEquals(msg, result.originalUserMessage)
    }

    @Test
    fun testPreserveOriginalUserMessage() {
        val complex = "I have an exam tomorrow. I'm learning Kotlin. What should I study?"
        val result = MessageAnalyzer.analyze(null, complex)
        assertEquals(complex, result.originalUserMessage)
        assertEquals(listOf(1, 0, 0), result.sentenceLabels)
        assertEquals(MessageType.REMINDER_AND_MIXED, result.messageType)
    }

    @Test
    fun testPromptBuilder_TellingOnly_ILikeIcecreams() {
        val msg = "I like icecreams"
        val result = MessageAnalyzer.analyze(null, msg)
        assertEquals(MessageType.TELLING, result.messageType)
        assertFalse("Fallback should not be invoked", result.isFallback)

        val prompt = MemossistPromptBuilder.build(result, emptyList())
        assertTrue("Prompt must contain TELLING ONLY", prompt.contains("TELLING ONLY"))
        assertTrue("Prompt must contain [EXTRACTED_FACTS", prompt.contains("[EXTRACTED_FACTS:"))
        assertTrue("Prompt must contain [HUMANOID_ANSWER]", prompt.contains("[HUMANOID_ANSWER]"))
        assertFalse("Prompt must NOT contain CANDIDATE EXPERIENCES", prompt.contains("=== CANDIDATE EXPERIENCES ==="))
        assertFalse("Prompt must NOT contain [INTENT: header", prompt.contains("[INTENT:"))
        assertFalse("Prompt must NOT contain [USED_EXPERIENCES", prompt.contains("[USED_EXPERIENCES:"))
    }

    @Test
    fun testPromptBuilder_AllSevenTypes() {
        val mockExp = listOf(
            MemoryItem("EXP-1", "Title", "Snippet", "Content", "Timestamp", "Location", "Tag", "TimeAgo")
        )

        // 1. REMINDER_ONLY
        val resRem = MessageAnalyzer.analyze(null, "I have an exam tomorrow.")
        assertEquals(MessageType.REMINDER_ONLY, resRem.messageType)
        val pRem = MemossistPromptBuilder.build(resRem, mockExp)
        assertTrue(pRem.contains("REMINDER ONLY"))
        assertTrue(pRem.contains("[EXTRACTED_REMINDERS:"))
        assertFalse(pRem.contains("=== CANDIDATE EXPERIENCES ==="))

        // 2. ASKING
        val resAsk = MessageAnalyzer.analyze(null, "What is Kotlin?")
        assertEquals(MessageType.ASKING, resAsk.messageType)
        val pAsk = MemossistPromptBuilder.build(resAsk, mockExp)
        assertTrue(pAsk.contains("ASKING ONLY"))
        assertTrue(pAsk.contains("[USED_EXPERIENCES:"))
        assertTrue(pAsk.contains("=== CANDIDATE EXPERIENCES ==="))

        // 3. TELLING
        val resTell = MessageAnalyzer.analyze(null, "I like icecreams")
        assertEquals(MessageType.TELLING, resTell.messageType)
        val pTell = MemossistPromptBuilder.build(resTell, mockExp)
        assertTrue(pTell.contains("TELLING ONLY"))
        assertTrue(pTell.contains("[EXTRACTED_FACTS:"))
        assertFalse(pTell.contains("=== CANDIDATE EXPERIENCES ==="))

        // 4. MIXED
        val resMix = MessageAnalyzer.analyze(null, "I'm learning Kotlin. What should I learn next?")
        assertEquals(MessageType.MIXED, resMix.messageType)
        val pMix = MemossistPromptBuilder.build(resMix, mockExp)
        assertTrue(pMix.contains("MIXED (Statements + Questions)"))
        assertTrue(pMix.contains("[EXTRACTED_FACTS:"))
        assertTrue(pMix.contains("[USED_EXPERIENCES:"))
        assertTrue(pMix.contains("=== CANDIDATE EXPERIENCES ==="))

        // 5. REMINDER_AND_ASKING
        val resRemAsk = MessageAnalyzer.analyze(null, "I have an exam tomorrow. What should I study?")
        assertEquals(MessageType.REMINDER_AND_ASKING, resRemAsk.messageType)
        val pRemAsk = MemossistPromptBuilder.build(resRemAsk, mockExp)
        assertTrue(pRemAsk.contains("REMINDER + ASKING"))
        assertTrue(pRemAsk.contains("[EXTRACTED_REMINDERS:"))
        assertTrue(pRemAsk.contains("[USED_EXPERIENCES:"))
        assertTrue(pRemAsk.contains("=== CANDIDATE EXPERIENCES ==="))

        // 6. REMINDER_AND_TELLING
        val resRemTell = MessageAnalyzer.analyze(null, "I have an exam tomorrow. I'm nervous.")
        assertEquals(MessageType.REMINDER_AND_TELLING, resRemTell.messageType)
        val pRemTell = MemossistPromptBuilder.build(resRemTell, mockExp)
        assertTrue(pRemTell.contains("REMINDER + TELLING"))
        assertTrue(pRemTell.contains("[EXTRACTED_REMINDERS:"))
        assertTrue(pRemTell.contains("[EXTRACTED_FACTS:"))
        assertFalse(pRemTell.contains("=== CANDIDATE EXPERIENCES ==="))

        // 7. REMINDER_AND_MIXED
        val resRemMix = MessageAnalyzer.analyze(null, "I have an exam tomorrow. I'm nervous. What should I study?")
        assertEquals(MessageType.REMINDER_AND_MIXED, resRemMix.messageType)
        val pRemMix = MemossistPromptBuilder.build(resRemMix, mockExp)
        assertTrue(pRemMix.contains("REMINDER + MIXED"))
        assertTrue(pRemMix.contains("[EXTRACTED_REMINDERS:"))
        assertTrue(pRemMix.contains("[EXTRACTED_FACTS:"))
        assertTrue(pRemMix.contains("[USED_EXPERIENCES:"))
        assertTrue(pRemMix.contains("=== CANDIDATE EXPERIENCES ==="))
    }

    @Test
    fun testForcedMessageTypeOverride() {
        val ambiguousText = "Maybe I will go somewhere later or maybe not"
        for (targetType in MessageType.values()) {
            val res = MessageAnalyzer.analyze(null, ambiguousText, forcedMessageType = targetType)
            assertEquals("Expected forced type to match $targetType", targetType, res.messageType)
            assertEquals(1.0f, res.confidence, 0.001f)
            assertFalse(res.requiresConfirmation)
        }
    }

    @Test
    fun testConfirmationThresholdProperty() {
        val highConfMsg = "Remind me to call Mom tomorrow at 5 PM."
        val highConfRes = MessageAnalyzer.analyze(null, highConfMsg)
        assertEquals(MessageType.REMINDER_ONLY, highConfRes.messageType)
        assertTrue(highConfRes.confidence >= 0.70f)
        assertFalse(highConfRes.requiresConfirmation)
    }
}
