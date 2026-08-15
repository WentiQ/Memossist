package com.example.apptempleate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDecayCalculatorTest {

    @Test
    fun testInitialStrengthFormula() {
        // Example from specifications:
        // importance = 0.8, confidence = 0.9, stability = 0.7
        // S_0 = (0.50)(0.8) + (0.20)(0.9) + (0.30)(0.7) = 0.40 + 0.18 + 0.21 = 0.79
        val s0 = MemoryDecayCalculator.calculateInitialStrength(
            importance = 0.8,
            confidence = 0.9,
            stability = 0.7
        )
        assertEquals(0.79, s0, 1e-6)
    }

    @Test
    fun testHalfLifeFormula() {
        // Example from specifications:
        // I = 0.8, T = 0.7
        // H = 7 + (120)(0.8) + (120)(0.7) = 7 + 96 + 84 = 187 days
        val h = MemoryDecayCalculator.calculateHalfLifeDays(
            importance = 0.8,
            stability = 0.7
        )
        assertEquals(187.0, h, 1e-6)
    }

    @Test
    fun testTimeDecayFactor() {
        val halfLife = 187.0

        // At D = 0 -> F_time = 2^0 = 1.0
        val f0 = MemoryDecayCalculator.calculateTimeDecayFactor(0.0, halfLife)
        assertEquals(1.0, f0, 1e-6)

        // At exactly one half life (D = H) -> F_time = 2^(-1) = 0.5
        val fHalf = MemoryDecayCalculator.calculateTimeDecayFactor(halfLife, halfLife)
        assertEquals(0.5, fHalf, 1e-6)

        // At two half lives (D = 2H) -> F_time = 2^(-2) = 0.25
        val fDouble = MemoryDecayCalculator.calculateTimeDecayFactor(halfLife * 2.0, halfLife)
        assertEquals(0.25, fDouble, 1e-6)
    }

    @Test
    fun testAccessBoostDiminishingReturns() {
        // B_access = 1 + 0.15 * ln(1 + accessCount)
        val b0 = MemoryDecayCalculator.calculateAccessBoost(0)
        assertEquals(1.00, b0, 1e-6)

        val b1 = MemoryDecayCalculator.calculateAccessBoost(1)
        assertEquals(1.0 + 0.15 * Math.log(2.0), b1, 1e-3)
        assertEquals(1.104, b1, 0.01)

        val b5 = MemoryDecayCalculator.calculateAccessBoost(5)
        assertEquals(1.0 + 0.15 * Math.log(6.0), b5, 1e-3)
        assertEquals(1.269, b5, 0.01)

        val b20 = MemoryDecayCalculator.calculateAccessBoost(20)
        assertEquals(1.0 + 0.15 * Math.log(21.0), b20, 1e-3)
        assertEquals(1.457, b20, 0.01)
    }

    @Test
    fun testReinforcementBoost() {
        // B_reinforce = 1 + 0.25 * ln(1 + reinforcementCount)
        val r0 = MemoryDecayCalculator.calculateReinforcementBoost(0)
        assertEquals(1.00, r0, 1e-6)

        val r1 = MemoryDecayCalculator.calculateReinforcementBoost(1)
        assertEquals(1.0 + 0.25 * Math.log(2.0), r1, 1e-3)

        val r3 = MemoryDecayCalculator.calculateReinforcementBoost(3)
        assertEquals(1.0 + 0.25 * Math.log(4.0), r3, 1e-3)
    }

    @Test
    fun testUserReinforcementEvent() {
        // Example from specifications:
        // current base strength = 0.42, importance = 0.7
        // R = 0.10 + 0.10 * 0.7 = 0.17
        // S_new_base = 0.42 + 0.17 = 0.59
        val now = 100_000_000L
        val item = MemoryItem(
            id = "EXP-101",
            title = "Kotlin",
            snippet = "I like Kotlin",
            message = "I like Kotlin",
            timestamp = "Just now",
            location = "Local",
            importance = 0.7,
            confidence = 0.9,
            stability = 0.8,
            createdAt = now,
            lastAccessedAt = now,
            accessCount = 0,
            reinforcementCount = 0,
            lastReinforcedAt = now,
            baseStrength = 0.42,
            strength = 0.42
        )

        val reinforced = MemoryDecayCalculator.applyReinforcement(item, now)
        assertEquals(0.59, reinforced.baseStrength, 1e-6)
        assertEquals(1, reinforced.reinforcementCount)
        assertEquals(now, reinforced.lastReinforcedAt)
    }

    @Test
    fun testUsedExperienceApplication() {
        val now = 200_000_000L
        val item = MemoryItem(
            id = "EXP-202",
            title = "Android",
            snippet = "Building Android apps",
            message = "Building Android apps",
            timestamp = "Just now",
            location = "Local",
            importance = 0.5,
            confidence = 0.8,
            stability = 0.6,
            createdAt = now - 5000L,
            lastAccessedAt = now - 5000L,
            accessCount = 1,
            reinforcementCount = 1,
            lastReinforcedAt = now - 5000L,
            baseStrength = 0.50,
            strength = 0.50
        )

        // R = 0.10 + 0.10 * 0.5 = 0.15
        // newBaseStrength = min(1.0, 0.50 + 0.15) = 0.65
        val updated = MemoryDecayCalculator.applyUsedExperience(item, now)
        assertEquals(2, updated.accessCount)
        assertEquals(2, updated.reinforcementCount)
        assertEquals(now, updated.lastAccessedAt)
        assertEquals(now, updated.lastReinforcedAt)
        assertEquals(0.65, updated.baseStrength, 1e-6)
        assertTrue(updated.strength > 0.60)
    }

    @Test
    fun testForgettingThreshold() {
        assertTrue(MemoryDecayCalculator.shouldForget(0.149))
        assertTrue(MemoryDecayCalculator.shouldForget(0.00))
        assertFalse(MemoryDecayCalculator.shouldForget(0.150))
        assertFalse(MemoryDecayCalculator.shouldForget(0.75))
    }

    @Test
    fun testParameterEvaluatorJsonParsing() {
        val validJson = """{"importance": 0.85, "confidence": 0.95, "stability": 0.75}"""
        val parsed = MemoryParameterEvaluator.parseAndValidateResponse(validJson)
        assertNotNull(parsed)
        assertEquals(0.85, parsed!!.importance, 1e-6)
        assertEquals(0.95, parsed.confidence, 1e-6)
        assertEquals(0.75, parsed.stability, 1e-6)

        // Handles markdown or surrounding text
        val embeddedJson = """Here is the result: ```json {"importance": 0.4, "confidence": 0.9, "stability": 0.3} ```"""
        val parsedEmbedded = MemoryParameterEvaluator.parseAndValidateResponse(embeddedJson)
        assertNotNull(parsedEmbedded)
        assertEquals(0.4, parsedEmbedded!!.importance, 1e-6)
        assertEquals(0.9, parsedEmbedded.confidence, 1e-6)
        assertEquals(0.3, parsedEmbedded.stability, 1e-6)

        // Out of range rejected
        val invalidRange = """{"importance": 1.5, "confidence": 0.9, "stability": 0.3}"""
        assertNull(MemoryParameterEvaluator.parseAndValidateResponse(invalidRange))

        // Missing field rejected
        val missingField = """{"importance": 0.5, "stability": 0.3}"""
        assertNull(MemoryParameterEvaluator.parseAndValidateResponse(missingField))
    }

    @Test
    fun testBatchParameterEvaluatorJsonParsing() {
        val facts = listOf(
            FactForEvaluation("EXP-001", "I like building robots."),
            FactForEvaluation("EXP-002", "I am studying in 3rd year at IIT Tirupati.")
        )
        val rawBatchJson = """
            [
              {"id": "EXP-001", "importance": 0.85, "confidence": 0.95, "stability": 0.75},
              {"id": "EXP-002", "importance": 0.90, "confidence": 0.95, "stability": 0.80}
            ]
        """.trimIndent()

        val results = MemoryParameterEvaluator.parseAndValidateBatchResponse(rawBatchJson, facts)
        assertEquals(2, results.size)
        assertEquals("EXP-001", results[0].experienceId)
        assertEquals(0.85, results[0].importance, 1e-6)
        assertEquals(0.95, results[0].confidence, 1e-6)
        assertEquals(0.75, results[0].stability, 1e-6)
        assertEquals(0.84, results[0].strength, 1e-6)

        assertEquals("EXP-002", results[1].experienceId)
        assertEquals(0.90, results[1].importance, 1e-6)
        assertEquals(0.95, results[1].confidence, 1e-6)
        assertEquals(0.80, results[1].stability, 1e-6)
        assertEquals(0.88, results[1].strength, 1e-6)
    }

    @Test
    fun testPositionalAndFuzzyBatchParsing() {
        val facts = listOf(
            FactForEvaluation("EXP-101", "I like building robots."),
            FactForEvaluation("EXP-102", "I am studying in 3rd year at IIT Tirupati.")
        )
        // Array where second element doesn't have exact ID but is at index 1
        val rawBatchJson = """
            [
              {"id": "EXP-101", "importance": 0.85, "confidence": 0.95, "stability": 0.75},
              {"importance": 0.70, "confidence": 0.80, "stability": 0.60}
            ]
        """.trimIndent()

        val results = MemoryParameterEvaluator.parseAndValidateBatchResponse(rawBatchJson, facts)
        assertEquals(2, results.size)
        assertEquals("EXP-101", results[0].experienceId)
        assertEquals(0.85, results[0].importance, 1e-6)

        assertEquals("EXP-102", results[1].experienceId)
        assertEquals(0.70, results[1].importance, 1e-6)
        assertEquals(0.80, results[1].confidence, 1e-6)
        assertEquals(0.60, results[1].stability, 1e-6)
    }
}
