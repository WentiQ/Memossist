package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Manages memory storage limits, device space checks, and capacity management for Memossist.
 *
 * Core Rules:
 * 1. Default Limit = Existing Memory Vault Data + Available Phone Storage.
 * 2. Max Allowable Limit for custom setting = Existing Memory Vault Data + Available Phone Storage.
 * 3. Incremental space required = max(0, Custom Limit - Existing Memory Vault Data) <= Available Phone Storage.
 * 4. When the limit is reached, existing memories with the lowest calculated strength are pruned.
 * 5. Supports Auto-delete vs Ask Before Deleting preferences.
 */
object MemoryStorageManager {

    private const val PREFS_NAME = "MemossistMemoryStoragePrefs"
    private const val KEY_LIMIT_BYTES = "memory_limit_bytes"
    private const val KEY_LIMIT_UNIT = "memory_limit_unit"
    private const val KEY_LIMIT_VALUE = "memory_limit_value"
    private const val KEY_PRUNE_MODE = "memory_prune_mode"

    const val UNIT_B = "B"
    const val UNIT_KB = "KB"
    const val UNIT_MB = "MB"
    const val UNIT_GB = "GB"
    const val UNIT_UNLIMITED = "UNLIMITED"

    enum class PruneMode {
        AUTO,
        ASK
    }

    data class StorageStatus(
        val usedBytes: Long,
        val limitBytes: Long,
        val maxAllowableBytes: Long,
        val deviceFreeBytes: Long,
        val isUnlimited: Boolean,
        val pruneMode: PruneMode
    ) {
        val usedFormatted: String get() = formatBytes(usedBytes)
        val limitFormatted: String get() = if (isUnlimited) "Unlimited (Device Free: ${formatBytes(deviceFreeBytes)})" else formatBytes(limitBytes)
        val deviceFreeFormatted: String get() = formatBytes(deviceFreeBytes)
        val maxAllowableFormatted: String get() = formatBytes(maxAllowableBytes)
        val usagePercentage: Int
            get() = if (limitBytes <= 0L) 0 else ((usedBytes.toDouble() / limitBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    data class PruneCandidatePlan(
        val memoriesToPrune: List<MemoryItem>,
        val bytesNeeded: Long,
        val estimatedFreedBytes: Long,
        val newMemories: List<MemoryItem>
    )

    open class CapacityCheckResult {
        object FitsWithoutPruning : CapacityCheckResult()
        data class AutoPruned(val prunedCount: Int, val freedBytes: Long) : CapacityCheckResult()
        data class NeedsConfirmation(val plan: PruneCandidatePlan) : CapacityCheckResult()
        data class CannotFit(val reason: String) : CapacityCheckResult()
    }

    // Pending confirmation plan for "Ask before deleting" mode
    @Volatile
    var pendingPrunePlan: PruneCandidatePlan? = null

    // Callback when foreground activity is available to prompt user
    var onPruneConfirmationRequiredListener: ((PruneCandidatePlan) -> Unit)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Available free storage on the device filesystem.
     */
    fun getAvailableDeviceStorageBytes(context: Context): Long {
        return try {
            context.filesDir.usableSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Total storage capacity of the device filesystem.
     */
    fun getTotalDeviceStorageBytes(context: Context): Long {
        return try {
            context.filesDir.totalSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Calculates the exact storage occupied by existing memories (JSON text + attachment files on disk).
     */
    fun getCurrentVaultSizeBytes(context: Context): Long {
        var totalBytes = 0L
        try {
            val vaultFile = File(context.filesDir, "memossist_vault_memories.json")
            if (vaultFile.exists()) {
                totalBytes += vaultFile.length()
            }

            val memories = MemoryVaultRepository.loadAllMemories(context)
            for (mem in memories) {
                for (att in mem.attachments) {
                    if (att.filePath.isNotEmpty()) {
                        val f = File(att.filePath)
                        if (f.exists()) {
                            totalBytes += f.length()
                        } else if (att.fileSize > 0) {
                            totalBytes += att.fileSize
                        }
                    } else if (att.fileSize > 0) {
                        totalBytes += att.fileSize
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalBytes
    }

    /**
     * Approximates byte size for a single memory item (JSON string + attachments).
     */
    fun calculateMemoryItemSizeBytes(item: MemoryItem): Long {
        var bytes = 0L
        bytes += item.id.toByteArray().size
        bytes += item.title.toByteArray().size
        bytes += item.snippet.toByteArray().size
        bytes += item.message.toByteArray().size
        bytes += item.timestamp.toByteArray().size
        bytes += item.location.toByteArray().size
        bytes += item.tag.toByteArray().size
        bytes += (item.wordSynonymsJson?.toByteArray()?.size ?: 0)
        bytes += 256L // Overhead for numeric fields & JSON keys

        for (att in item.attachments) {
            if (att.fileSize > 0) {
                bytes += att.fileSize
            } else if (att.filePath.isNotEmpty()) {
                val f = File(att.filePath)
                if (f.exists()) {
                    bytes += f.length()
                }
            }
        }
        return bytes
    }

    /**
     * Approximates byte size for a list of memory items.
     */
    fun calculateMemoriesBatchSizeBytes(items: List<MemoryItem>): Long {
        return items.sumOf { calculateMemoryItemSizeBytes(it) }
    }

    /**
     * Max Allowable Limit = Existing Memory Vault Data + Available Phone Storage.
     */
    fun getMaxAllowableLimitBytes(context: Context): Long {
        val existingVault = getCurrentVaultSizeBytes(context)
        val availablePhone = getAvailableDeviceStorageBytes(context)
        return (existingVault + availablePhone).coerceAtLeast(0L)
    }

    /**
     * Returns the user's custom limit in bytes, or -1L if Unlimited.
     */
    fun getCustomLimitBytes(context: Context): Long {
        return getPrefs(context).getLong(KEY_LIMIT_BYTES, -1L)
    }

    fun getLimitUnit(context: Context): String {
        return getPrefs(context).getString(KEY_LIMIT_UNIT, UNIT_UNLIMITED) ?: UNIT_UNLIMITED
    }

    fun getLimitValue(context: Context): Double {
        return getPrefs(context).getFloat(KEY_LIMIT_VALUE, 0f).toDouble()
    }

    fun isUnlimited(context: Context): Boolean {
        return getCustomLimitBytes(context) <= 0L || getLimitUnit(context) == UNIT_UNLIMITED
    }

    /**
     * Effective Limit = Custom Limit if set and valid, capped by Max Allowable Limit.
     * Default: Max Allowable Limit (Existing Vault + Device Free Storage).
     */
    fun getEffectiveLimitBytes(context: Context): Long {
        val maxAllowable = getMaxAllowableLimitBytes(context)
        val custom = getCustomLimitBytes(context)
        return if (custom > 0L) {
            Math.min(custom, maxAllowable)
        } else {
            maxAllowable
        }
    }

    /**
     * Validates and sets the memory limit.
     * Rule: Incremental space needed <= Available phone storage.
     * (i.e. bytes <= Max Allowable Limit).
     */
    fun getUnitMultiplier(unit: String): Long {
        return when (unit.uppercase()) {
            UNIT_B -> 1L
            UNIT_KB -> 1024L
            UNIT_MB -> 1024L * 1024L
            UNIT_GB -> 1024L * 1024L * 1024L
            else -> 1024L * 1024L
        }
    }

    fun setMemoryLimit(context: Context, value: Double, unit: String): Boolean {
        if (unit.equals(UNIT_UNLIMITED, ignoreCase = true) || value <= 0.0) {
            getPrefs(context).edit()
                .putLong(KEY_LIMIT_BYTES, -1L)
                .putString(KEY_LIMIT_UNIT, UNIT_UNLIMITED)
                .putFloat(KEY_LIMIT_VALUE, 0f)
                .apply()
            return true
        }

        val multiplier = getUnitMultiplier(unit)
        val targetBytes = (value * multiplier).toLong()
        val maxAllowable = getMaxAllowableLimitBytes(context)

        if (targetBytes > maxAllowable) {
            return false
        }

        getPrefs(context).edit()
            .putLong(KEY_LIMIT_BYTES, targetBytes)
            .putString(KEY_LIMIT_UNIT, unit.uppercase())
            .putFloat(KEY_LIMIT_VALUE, value.toFloat())
            .apply()
        return true
    }

    fun getPruneMode(context: Context): PruneMode {
        val modeStr = getPrefs(context).getString(KEY_PRUNE_MODE, PruneMode.AUTO.name)
        return try {
            PruneMode.valueOf(modeStr ?: PruneMode.AUTO.name)
        } catch (e: Exception) {
            PruneMode.AUTO
        }
    }

    fun setPruneMode(context: Context, mode: PruneMode) {
        getPrefs(context).edit()
            .putString(KEY_PRUNE_MODE, mode.name)
            .apply()
    }

    fun getStorageStatus(context: Context): StorageStatus {
        val used = getCurrentVaultSizeBytes(context)
        val free = getAvailableDeviceStorageBytes(context)
        val maxAllowable = used + free
        val isUnlim = isUnlimited(context)
        val limit = if (isUnlim) maxAllowable else getEffectiveLimitBytes(context)
        val pruneMode = getPruneMode(context)

        return StorageStatus(
            usedBytes = used,
            limitBytes = limit,
            maxAllowableBytes = maxAllowable,
            deviceFreeBytes = free,
            isUnlimited = isUnlim,
            pruneMode = pruneMode
        )
    }

    /**
     * Checks if new memories can fit within the configured limit and phone storage.
     * If space is insufficient, constructs a pruning plan for existing memories with lowest strength.
     */
    fun checkCapacityAndPlan(context: Context, newMemories: List<MemoryItem>): CapacityCheckResult {
        if (newMemories.isEmpty()) return CapacityCheckResult.FitsWithoutPruning

        val neededBytes = calculateMemoriesBatchSizeBytes(newMemories)
        val currentUsedBytes = getCurrentVaultSizeBytes(context)
        val deviceFreeBytes = getAvailableDeviceStorageBytes(context)
        val effectiveLimitBytes = getEffectiveLimitBytes(context)

        val spaceAvailableInLimit = (effectiveLimitBytes - currentUsedBytes).coerceAtLeast(0L)

        // If fits both within configured limit and within physical device storage
        if (neededBytes <= spaceAvailableInLimit && neededBytes <= deviceFreeBytes) {
            return CapacityCheckResult.FitsWithoutPruning
        }

        // Space is deficient: calculate how many bytes must be freed
        val bytesToFree = Math.max(
            neededBytes - spaceAvailableInLimit,
            neededBytes - deviceFreeBytes
        )

        val plan = planPruning(context, bytesToFree, newMemories)
            ?: return CapacityCheckResult.CannotFit("Not enough existing memories can be freed to accommodate new memories.")

        val pruneMode = getPruneMode(context)
        return if (pruneMode == PruneMode.AUTO) {
            val freed = executePruning(context, plan.memoriesToPrune)
            CapacityCheckResult.AutoPruned(plan.memoriesToPrune.size, freed)
        } else {
            pendingPrunePlan = plan
            onPruneConfirmationRequiredListener?.invoke(plan)
            CapacityCheckResult.NeedsConfirmation(plan)
        }
    }

    /**
     * Finds existing memories with the lowest calculated strength to free [bytesToFree].
     * Pinned memories are only considered as a last resort.
     */
    fun planPruning(
        context: Context,
        bytesToFree: Long,
        newMemories: List<MemoryItem>
    ): PruneCandidatePlan? {
        val existing = MemoryVaultRepository.loadAllMemories(context)
        if (existing.isEmpty()) return null

        val now = System.currentTimeMillis()

        // Calculate current decayed strength for all existing memories
        val evaluated = existing.map { mem ->
            val currStrength = MemoryDecayCalculator.calculateCurrentStrength(mem, now)
            val size = calculateMemoryItemSizeBytes(mem)
            Triple(mem.copy(strength = currStrength), currStrength, size)
        }

        // Sort: unpinned first by strength ascending (weakest first), then pinned by strength ascending
        val sorted = evaluated.sortedWith(
            compareBy<Triple<MemoryItem, Double, Long>> { it.first.isPinned }
                .thenBy { it.second }
        )

        var accumulatedFreed = 0L
        val candidates = mutableListOf<MemoryItem>()

        for (item in sorted) {
            candidates.add(item.first)
            accumulatedFreed += item.third
            if (accumulatedFreed >= bytesToFree) {
                break
            }
        }

        return PruneCandidatePlan(candidates, bytesToFree, accumulatedFreed, newMemories)
    }

    /**
     * Deletes the given memories and their media attachment files from disk.
     */
    fun executePruning(context: Context, memoriesToPrune: List<MemoryItem>): Long {
        if (memoriesToPrune.isEmpty()) return 0L
        var totalFreed = 0L
        val allMemories = MemoryVaultRepository.loadAllMemories(context)
        val pruneIds = memoriesToPrune.map { it.id }.toSet()

        val remaining = mutableListOf<MemoryItem>()
        for (mem in allMemories) {
            if (pruneIds.contains(mem.id)) {
                totalFreed += calculateMemoryItemSizeBytes(mem)
                cleanupMemoryAttachments(mem)
            } else {
                remaining.add(mem)
            }
        }

        MemoryVaultRepository.saveAllMemories(context, remaining)
        MemoryVaultRepository.incrementForgottenMemoriesCount(context, memoriesToPrune.size)
        ExperienceDagRepository.removeEdgesForMemories(context, pruneIds)
        return totalFreed
    }

    /**
     * Deletes media attachment files from disk when a memory is deleted.
     */
    fun cleanupMemoryAttachments(memory: MemoryItem) {
        for (att in memory.attachments) {
            if (att.filePath.isNotEmpty()) {
                try {
                    val f = File(att.filePath)
                    if (f.exists() && f.isFile) {
                        f.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Called when the user agrees to pruning in "Ask before deleting" mode.
     */
    fun confirmPendingPruneAndSave(context: Context): Boolean {
        val plan = pendingPrunePlan ?: return false
        executePruning(context, plan.memoriesToPrune)
        for (newMem in plan.newMemories) {
            MemoryVaultRepository.saveMemory(context, newMem)
        }
        pendingPrunePlan = null
        return true
    }

    /**
     * Called when the user disagrees / rejects pruning in "Ask before deleting" mode.
     */
    fun rejectPendingPrune() {
        pendingPrunePlan = null
    }

    data class RankedMemoryItem(
        val memory: MemoryItem,
        val rank: Int,
        val currentStrength: Double,
        val sizeBytes: Long
    ) {
        val sizeFormatted: String get() = formatBytes(sizeBytes)
        val strengthFormatted: String get() = String.format("%.2f", currentStrength)
        val strengthLabel: String
            get() = when {
                currentStrength < 0.15 -> "Decayed (< 0.15)"
                currentStrength < 0.35 -> "Very Weak"
                currentStrength < 0.60 -> "Moderate"
                else -> "Strong"
            }
    }

    /**
     * Returns all existing memories ranked from lowest to highest calculated strength.
     */
    fun getRankedMemoriesWithDetails(context: Context): List<RankedMemoryItem> {
        val all = MemoryVaultRepository.loadAllMemories(context)
        if (all.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val evaluated = all.map { mem ->
            val currStrength = MemoryDecayCalculator.calculateCurrentStrength(mem, now)
            val size = calculateMemoryItemSizeBytes(mem)
            Triple(mem.copy(strength = currStrength), currStrength, size)
        }

        val sorted = evaluated.sortedWith(
            compareBy<Triple<MemoryItem, Double, Long>> { it.first.isPinned }
                .thenBy { it.second }
        )

        return sorted.mapIndexed { index, triple ->
            RankedMemoryItem(
                memory = triple.first,
                rank = index + 1,
                currentStrength = triple.second,
                sizeBytes = triple.third
            )
        }
    }

    /**
     * Identifies the optimal subset of lowest-strength memories to free [targetBytes].
     */
    fun findRecommendedMemoriesToFreeBytes(context: Context, targetBytes: Long): List<MemoryItem> {
        if (targetBytes <= 0L) return emptyList()
        val ranked = getRankedMemoriesWithDetails(context)
        var accumulated = 0L
        val selected = mutableListOf<MemoryItem>()

        for (item in ranked) {
            selected.add(item.memory)
            accumulated += item.sizeBytes
            if (accumulated >= targetBytes) {
                break
            }
        }
        return selected
    }

    /**
     * Identifies the top [targetCount] lowest-strength memories.
     */
    fun findRecommendedMemoriesByCount(context: Context, targetCount: Int): List<MemoryItem> {
        if (targetCount <= 0) return emptyList()
        val ranked = getRankedMemoriesWithDetails(context)
        return ranked.take(targetCount).map { it.memory }
    }

    /**
     * Batch deletes memories by their ID set and cleans up associated attachment files.
     */
    fun deleteMemoriesBatch(context: Context, memoryIds: Set<String>): Long {
        if (memoryIds.isEmpty()) return 0L
        val all = MemoryVaultRepository.loadAllMemories(context)
        var totalFreed = 0L
        val remaining = mutableListOf<MemoryItem>()

        for (mem in all) {
            if (memoryIds.contains(mem.id)) {
                totalFreed += calculateMemoryItemSizeBytes(mem)
                cleanupMemoryAttachments(mem)
            } else {
                remaining.add(mem)
            }
        }

        MemoryVaultRepository.saveAllMemories(context, remaining)
        MemoryVaultRepository.incrementForgottenMemoriesCount(context, memoryIds.size)
        ExperienceDagRepository.removeEdgesForMemories(context, memoryIds)
        return totalFreed
    }

    /**
     * Formats raw bytes into readable B, KB, MB, GB string.
     */
    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024L -> String.format("%.1f KB", b.toDouble() / 1024.0)
            b < 1024L * 1024L * 1024L -> String.format("%.1f MB", b.toDouble() / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", b.toDouble() / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
