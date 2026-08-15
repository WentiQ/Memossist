package com.example.apptempleate

import android.util.Log

data class MemoryDecayDebugInfo(
    val memoryId: String,
    val baseStrength: Double,
    val currentStrength: Double,
    val importance: Double,
    val confidence: Double,
    val stability: Double,
    val halfLifeDays: Double,
    val daysSinceReinforcement: Double,
    val timeDecayFactor: Double,
    val accessBoost: Double,
    val reinforcementBoost: Double,
    val accessCount: Int,
    val reinforcementCount: Int,
    val shouldForget: Boolean
)

/**
 * Deterministic calculation engine for the Memossist Memory Forgetting System.
 * Implements mathematical formulas for:
 * 1. Initial Strength: S_0 = 0.50*I + 0.20*C + 0.30*T
 * 2. Decay Half-Life: H = 7 + 120*I + 120*T (days)
 * 3. Time Decay: F_time = 2^(-D / H)
 * 4. Access Boost: B_access = 1 + 0.15 * ln(1 + A)
 * 5. Reinforcement Boost: B_reinforce = 1 + 0.25 * ln(1 + R_c)
 * 6. Current Strength: S_current = clamp(baseStrength * F_time * B_access * B_reinforce, 0.0, 1.0)
 * 7. User Reinforcement: R = 0.10 + 0.10*I, baseStrength = min(1.0, baseStrength + R)
 * 8. Forgetting Threshold: S_current < 0.15 -> FORGET
 */
object MemoryDecayCalculator {

    private const val TAG = "MemoryDecay"

    /**
     * Calculates initial strength for a newly extracted memory:
     * S_0 = 0.50 * importance + 0.20 * confidence + 0.30 * stability
     */
    fun calculateInitialStrength(
        importance: Double,
        confidence: Double,
        stability: Double
    ): Double {
        val clampedI = importance.coerceIn(0.0, 1.0)
        val clampedC = confidence.coerceIn(0.0, 1.0)
        val clampedT = stability.coerceIn(0.0, 1.0)
        val s0 = (MemoryDecayConfig.WEIGHT_IMPORTANCE * clampedI) +
                (MemoryDecayConfig.WEIGHT_CONFIDENCE * clampedC) +
                (MemoryDecayConfig.WEIGHT_STABILITY * clampedT)
        return s0.coerceIn(0.0, 1.0)
    }

    /**
     * Calculates the memory's half-life in days:
     * H = 7 + 120 * importance + 120 * stability
     */
    fun calculateHalfLifeDays(
        importance: Double,
        stability: Double
    ): Double {
        val clampedI = importance.coerceIn(0.0, 1.0)
        val clampedT = stability.coerceIn(0.0, 1.0)
        return MemoryDecayConfig.HALF_LIFE_BASE_DAYS +
                (MemoryDecayConfig.HALF_LIFE_IMPORTANCE_FACTOR * clampedI) +
                (MemoryDecayConfig.HALF_LIFE_STABILITY_FACTOR * clampedT)
    }

    /**
     * Calculates floating-point elapsed days since the given timestamp.
     */
    fun calculateDaysSince(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Double {
        val diff = (nowMs - timestampMs).coerceAtLeast(0L)
        return diff.toDouble() / MemoryDecayConfig.MILLIS_PER_DAY
    }

    /**
     * Calculates exponential time decay factor:
     * F_time = 2 ^ (-D / H)
     */
    fun calculateTimeDecayFactor(
        daysSinceReinforcement: Double,
        halfLifeDays: Double
    ): Double {
        if (halfLifeDays <= 0.0) return 0.0
        val exponent = -daysSinceReinforcement / halfLifeDays
        return Math.pow(MemoryDecayConfig.TIME_DECAY_BASE, exponent)
    }

    /**
     * Calculates access boost with diminishing returns:
     * B_access = 1 + 0.15 * ln(1 + accessCount)
     */
    fun calculateAccessBoost(accessCount: Int): Double {
        val count = accessCount.coerceAtLeast(0)
        return 1.0 + (MemoryDecayConfig.ACCESS_BOOST_FACTOR * Math.log(1.0 + count.toDouble()))
    }

    /**
     * Calculates reinforcement boost with diminishing returns:
     * B_reinforce = 1 + 0.25 * ln(1 + reinforcementCount)
     */
    fun calculateReinforcementBoost(reinforcementCount: Int): Double {
        val count = reinforcementCount.coerceAtLeast(0)
        return 1.0 + (MemoryDecayConfig.REINFORCE_BOOST_FACTOR * Math.log(1.0 + count.toDouble()))
    }

    /**
     * Calculates the current memory strength based on baseStrength, time decay, and boosts:
     * S_current = clamp(baseStrength * F_time * B_access * B_reinforce, 0.0, 1.0)
     */
    fun calculateCurrentStrength(
        memory: MemoryItem,
        nowMs: Long = System.currentTimeMillis()
    ): Double {
        val halfLife = calculateHalfLifeDays(memory.importance, memory.stability)
        val days = calculateDaysSince(memory.lastReinforcedAt, nowMs)
        val fTime = calculateTimeDecayFactor(days, halfLife)
        val bAccess = calculateAccessBoost(memory.accessCount)
        val bReinforce = calculateReinforcementBoost(memory.reinforcementCount)
        val current = memory.baseStrength * fTime * bAccess * bReinforce
        return current.coerceIn(0.0, 1.0)
    }

    /**
     * Applies a reinforcement event to an existing memory:
     * R = 0.10 + 0.10 * importance
     * baseStrength = min(1.0, baseStrength + R)
     * reinforcementCount += 1
     * lastReinforcedAt = nowMs
     * Recalculates current strength.
     */
    fun applyReinforcement(
        memory: MemoryItem,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryItem {
        val r = MemoryDecayConfig.REINFORCE_BASE_AMOUNT +
                (MemoryDecayConfig.REINFORCE_IMPORTANCE_FACTOR * memory.importance.coerceIn(0.0, 1.0))
        val newBaseStrength = (memory.baseStrength + r).coerceIn(0.0, 1.0)
        val newReinforcementCount = memory.reinforcementCount + 1
        val updated = memory.copy(
            baseStrength = newBaseStrength,
            reinforcementCount = newReinforcementCount,
            lastReinforcedAt = nowMs
        )
        val newStrength = calculateCurrentStrength(updated, nowMs)
        logDebugInfo(updated.copy(strength = newStrength), nowMs, "User Reinforcement Applied")
        return updated.copy(strength = newStrength)
    }

    /**
     * Updates an experience that was explicitly used in an LLM answer ([USED_EXPERIENCES]):
     * accessCount += 1
     * lastAccessedAt = nowMs
     * reinforcementCount += 1
     * lastReinforcedAt = nowMs
     * baseStrength = min(1.0, baseStrength + 0.10 + 0.10 * importance)
     * Recalculates current strength.
     */
    fun applyUsedExperience(
        memory: MemoryItem,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryItem {
        val r = MemoryDecayConfig.REINFORCE_BASE_AMOUNT +
                (MemoryDecayConfig.REINFORCE_IMPORTANCE_FACTOR * memory.importance.coerceIn(0.0, 1.0))
        val newBaseStrength = (memory.baseStrength + r).coerceIn(0.0, 1.0)
        val newAccessCount = memory.accessCount + 1
        val newReinforcementCount = memory.reinforcementCount + 1
        val updated = memory.copy(
            accessCount = newAccessCount,
            lastAccessedAt = nowMs,
            reinforcementCount = newReinforcementCount,
            lastReinforcedAt = nowMs,
            baseStrength = newBaseStrength
        )
        val newStrength = calculateCurrentStrength(updated, nowMs)
        logDebugInfo(updated.copy(strength = newStrength), nowMs, "Used Experience Applied")
        return updated.copy(strength = newStrength)
    }

    /**
     * Checks if a memory's strength drops below the forgetting threshold.
     */
    fun shouldForget(strength: Double): Boolean {
        return strength < MemoryDecayConfig.FORGET_THRESHOLD
    }

    /**
     * Generates a detailed debug diagnostic object for inspecting memory decay formulas.
     */
    fun getDebugInfo(
        memory: MemoryItem,
        nowMs: Long = System.currentTimeMillis()
    ): MemoryDecayDebugInfo {
        val halfLife = calculateHalfLifeDays(memory.importance, memory.stability)
        val days = calculateDaysSince(memory.lastReinforcedAt, nowMs)
        val fTime = calculateTimeDecayFactor(days, halfLife)
        val bAccess = calculateAccessBoost(memory.accessCount)
        val bReinforce = calculateReinforcementBoost(memory.reinforcementCount)
        val strength = (memory.baseStrength * fTime * bAccess * bReinforce).coerceIn(0.0, 1.0)

        return MemoryDecayDebugInfo(
            memoryId = memory.id,
            baseStrength = memory.baseStrength,
            currentStrength = strength,
            importance = memory.importance,
            confidence = memory.confidence,
            stability = memory.stability,
            halfLifeDays = halfLife,
            daysSinceReinforcement = days,
            timeDecayFactor = fTime,
            accessBoost = bAccess,
            reinforcementBoost = bReinforce,
            accessCount = memory.accessCount,
            reinforcementCount = memory.reinforcementCount,
            shouldForget = shouldForget(strength)
        )
    }

    fun logDebugInfo(
        memory: MemoryItem,
        nowMs: Long = System.currentTimeMillis(),
        actionContext: String = "Calculation"
    ) {
        val info = getDebugInfo(memory, nowMs)
        runCatching {
            Log.d(TAG, "[$actionContext] ID: ${info.memoryId} | BaseS: ${String.format("%.4f", info.baseStrength)} | CurrS: ${String.format("%.4f", info.currentStrength)} | Imp: ${String.format("%.2f", info.importance)} | Conf: ${String.format("%.2f", info.confidence)} | Stab: ${String.format("%.2f", info.stability)} | HalfLife: ${String.format("%.1f", info.halfLifeDays)}d | DaysSinceReinforce: ${String.format("%.2f", info.daysSinceReinforcement)}d | F_time: ${String.format("%.4f", info.timeDecayFactor)} | B_acc: ${String.format("%.4f", info.accessBoost)} (count=${info.accessCount}) | B_reinf: ${String.format("%.4f", info.reinforcementBoost)} (count=${info.reinforcementCount}) | Decision: ${if (info.shouldForget) "FORGET" else "KEEP"}")
        }
    }
}
