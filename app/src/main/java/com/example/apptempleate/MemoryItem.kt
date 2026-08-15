package com.example.apptempleate

/**
 * Memory Item representing an experience stored in the Memory Vault.
 * Includes the core parameters used by the Memory Forgetting & Decay System:
 * - importance (0.0..1.0)
 * - confidence (0.0..1.0)
 * - stability (0.0..1.0)
 * - createdAt (timestamp ms)
 * - lastAccessedAt (timestamp ms)
 * - accessCount (number of times retrieved and used)
 * - reinforcementCount (number of reinforcement events)
 * - lastReinforcedAt (timestamp ms)
 * - baseStrength (persistent baseline strength)
 * - strength (current calculated decayed strength)
 */
data class MemoryItem(
    val id: String,
    val title: String,
    val snippet: String,
    val message: String,
    val timestamp: String,
    val location: String,
    val tag: String = "Chat",
    val timeAgo: String = "Just now",
    val isPinned: Boolean = false,
    val wordSynonymsJson: String? = null,
    val attachments: List<MediaAttachment> = emptyList(),
    val attachmentsJson: String? = null,
    val importance: Double = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
    val confidence: Double = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
    val stability: Double = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = createdAt,
    val accessCount: Int = 0,
    val reinforcementCount: Int = 0,
    val lastReinforcedAt: Long = createdAt,
    val baseStrength: Double = 0.50,
    val strength: Double = 0.50
) {
    /** Alias property for message content conforming to system nomenclature */
    val content: String get() = message
}
