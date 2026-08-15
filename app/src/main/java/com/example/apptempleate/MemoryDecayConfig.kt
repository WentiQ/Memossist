package com.example.apptempleate

/**
 * Centralized configuration parameters for the Memossist Memory Forgetting & Decay System.
 * All algorithm weights and thresholds are defined here for easy inspection and future tuning.
 */
object MemoryDecayConfig {
    // Initial Strength Weights: S_0 = 0.50 * I + 0.20 * C + 0.30 * T
    const val WEIGHT_IMPORTANCE = 0.50
    const val WEIGHT_CONFIDENCE = 0.20
    const val WEIGHT_STABILITY = 0.30

    // Half-Life (in days): H = 7 + 120 * I + 120 * T
    const val HALF_LIFE_BASE_DAYS = 7.0
    const val HALF_LIFE_IMPORTANCE_FACTOR = 120.0
    const val HALF_LIFE_STABILITY_FACTOR = 120.0

    // Time decay base (exponential base 2)
    const val TIME_DECAY_BASE = 2.0
    const val MILLIS_PER_DAY = 86_400_000.0

    // Access Boost: B_access = 1 + 0.15 * ln(1 + accessCount)
    const val ACCESS_BOOST_FACTOR = 0.15

    // Reinforcement Boost: B_reinforce = 1 + 0.25 * ln(1 + reinforcementCount)
    const val REINFORCE_BOOST_FACTOR = 0.25

    // User Reinforcement Amount: R = 0.10 + 0.10 * importance
    const val REINFORCE_BASE_AMOUNT = 0.10
    const val REINFORCE_IMPORTANCE_FACTOR = 0.10

    // Forgetting Threshold: S_current < 0.15 -> FORGET, S_current >= 0.15 -> KEEP
    const val FORGET_THRESHOLD = 0.15

    // Safe Defaults for Data Migration of Legacy Memories
    const val DEFAULT_MIGRATION_IMPORTANCE = 0.50
    const val DEFAULT_MIGRATION_CONFIDENCE = 0.80
    const val DEFAULT_MIGRATION_STABILITY = 0.50
}
