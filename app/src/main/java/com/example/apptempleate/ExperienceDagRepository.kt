package com.example.apptempleate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DagEdge(
    val experienceId1: String,
    val experienceId2: String,
    val title1: String,
    val title2: String,
    var strength: Double,
    var usageCount: Int,
    var lastUpdated: Long,
    val sharedTerms: List<String>
)

data class EdgeUpdateInfo(
    val exp1Id: String,
    val exp1Title: String,
    val exp2Id: String,
    val exp2Title: String,
    val commonCountC: Int,
    val usedT: Int,
    val deltaS: Double,
    val newStrengthS: Double,
    val commonTerms: List<String>
)

data class DagUpdateSummary(
    val question: String,
    val candidateExperiences: List<MemoryItem>,
    val usedExperienceIds: List<String>,
    val unionN: Int,
    val updatedEdges: List<EdgeUpdateInfo>
)

object ExperienceDagRepository {

    private const val FILE_NAME = "memossist_dag_edges.json"

    /**
     * Extracts word and synonym set N_i for a text segment using LinguisticAnalyzer.
     * Strictly contains ONLY Nouns, Verbs, Adjectives, and Adverbs.
     */
    fun getSemanticTermSet(text: String): Set<String> {
        val items = LinguisticAnalyzer.extractWordsAndSynonyms(text)
        val set = mutableSetOf<String>()
        for (item in items) {
            val word = item.word.lowercase().trim()
            if (LinguisticAnalyzer.isContentPosWord(word)) {
                set.add(word)
            }
            for (syn in item.synonyms) {
                val synTrim = syn.lowercase().trim()
                if (LinguisticAnalyzer.isContentPosWord(synTrim)) {
                    set.add(synTrim)
                }
            }
        }
        return set
    }

    /**
     * Retrieves top matching experiences with Dynamic Graph Expansion:
     * 1. First retrieves top 5 candidate experiences based on semantic overlap.
     * 2. For rank i (1 to 5), retrieves top (5 - i) connected experiences with non-zero connection strength (S_ij > 0.0), sorted descending by strength.
     * 3. Performs union set deduplication across all items while preserving rank order.
     * 4. Returns the final expanded union list of candidate experiences.
     */
    fun retrieveTopMatchingExperiences(context: Context, userQuestion: String, topK: Int = 5): List<MemoryItem> {
        val allMemories = MemoryVaultRepository.loadAllMemories(context)
        if (allMemories.isEmpty()) return emptyList()

        val qSet = getSemanticTermSet(userQuestion)
        val sortedMemories = if (qSet.isEmpty()) {
            allMemories
        } else {
            val scored = allMemories.map { memory ->
                val nSet = getSemanticTermSet("${memory.title} ${memory.snippet} ${memory.message}")
                val overlapScore = qSet.intersect(nSet).size
                Pair(memory, overlapScore)
            }
            scored.sortedWith(
                compareByDescending<Pair<MemoryItem, Int>> { it.second }
                    .thenBy { it.first.id }
            ).map { it.first }
        }

        // 1. Initial Top 5 Candidate Experiences
        val top5 = sortedMemories.take(topK)
        if (top5.isEmpty()) return emptyList()

        val memoryMap = allMemories.associateBy { it.id }
        val allEdges = loadAllEdges(context)

        // Result list maintaining insertion order (top5 first)
        val expandedCandidates = mutableListOf<MemoryItem>()
        expandedCandidates.addAll(top5)

        // 2. For each rank i in top5 (1-indexed: i = 1..5), find top (5 - i) connected experiences by strength
        for ((index, exp) in top5.withIndex()) {
            val rankI = index + 1
            val maxConnectedToFetch = 5 - rankI // For rank 1 -> 4, rank 2 -> 3, rank 3 -> 2, rank 4 -> 1, rank 5 -> 0
            if (maxConnectedToFetch <= 0) continue

            // Find all edges connected to exp.id with non-zero strength (> 0.0), sorted descending by strength
            val connectedEdges = allEdges.filter { edge ->
                (edge.experienceId1.equals(exp.id, ignoreCase = true) ||
                 edge.experienceId2.equals(exp.id, ignoreCase = true)) &&
                edge.strength > 0.0
            }.sortedByDescending { it.strength }

            // Take top (5 - i) connected experience IDs
            val topConnectedIds = connectedEdges.take(maxConnectedToFetch).map { edge ->
                if (edge.experienceId1.equals(exp.id, ignoreCase = true)) edge.experienceId2 else edge.experienceId1
            }

            for (connId in topConnectedIds) {
                val connMemory = memoryMap[connId] ?: allMemories.find { it.id.equals(connId, ignoreCase = true) }
                if (connMemory != null) {
                    expandedCandidates.add(connMemory)
                }
            }
        }

        // 3. Union set: eliminate duplicates by ID while preserving rank order
        return expandedCandidates.distinctBy { it.id }
    }

    /**
     * Updates DAG edge connections according to the exact mathematical formula:
     * S_ij_new = S_ij_old + (C * t) / N
     * where:
     * - Q = set of words/synonyms in question
     * - N_i = set of words/synonyms in experience i
     * - C = |Q ∩ N_i ∩ N_j| (common semantic content between Q, N_i, N_j)
     * - N = |N_1 ∪ N_2 ∪ ... ∪ N_k| (union size across all top candidate experiences)
     * - t = 1 if BOTH experience i and j were actually used in the answer, 0 otherwise
     */
    fun updateDagConnections(
        context: Context,
        userQuestion: String,
        candidateExperiences: List<MemoryItem>,
        usedExperienceIds: Set<String>
    ): DagUpdateSummary {

        val qSet = getSemanticTermSet(userQuestion)
        val candidateTermSetN = mutableSetOf<String>()
        for (cand in candidateExperiences) {
            candidateTermSetN.addAll(getSemanticTermSet("${cand.title} ${cand.snippet} ${cand.message}"))
        }
        val N = candidateTermSetN.size.coerceAtLeast(1)

        val activeExperiences = candidateExperiences.filter { it.id in usedExperienceIds }
        if (activeExperiences.size < 2) {
            return DagUpdateSummary(userQuestion, activeExperiences, usedExperienceIds.toList(), N, emptyList())
        }
        
        // Compute N_i for each candidate experience
        val expTermSets = mutableMapOf<String, Set<String>>()

        for (exp in activeExperiences) {
            val nSet = getSemanticTermSet("${exp.title} ${exp.snippet} ${exp.message}")
            expTermSets[exp.id] = nSet
        }

        val edges = loadAllEdges(context)
        val edgeUpdates = mutableListOf<EdgeUpdateInfo>()

        // Calculate and form connections ONLY between pairs of actually used experiences
        for (i in 0 until activeExperiences.size) {
            for (j in i + 1 until activeExperiences.size) {
                val exp1 = activeExperiences[i]
                val exp2 = activeExperiences[j]

                val n1Set = expTermSets[exp1.id] ?: emptySet()
                val n2Set = expTermSets[exp2.id] ?: emptySet()

                // Calculate C = |Q ∩ N_i ∩ N_j|
                val commonTerms = qSet.intersect(n1Set).intersect(n2Set)
                val C = commonTerms.size

                // Both experiences are verified used (t = 1)
                val t = 1

                // Calculate delta S = (C * t) / N
                val deltaS = (C.toDouble() * t.toDouble()) / N.toDouble()

                // Key pair ordering
                val pairKey = getEdgeKey(exp1.id, exp2.id)
                var edge = edges.find { getEdgeKey(it.experienceId1, it.experienceId2) == pairKey }

                val oldS = edge?.strength ?: 0.0
                val newS = oldS + deltaS

                if (edge != null) {
                    edge.strength = newS
                    edge.usageCount += 1
                    edge.lastUpdated = System.currentTimeMillis()
                    val mergedShared = (edge.sharedTerms + commonTerms).distinct()
                    if (mergedShared.isNotEmpty()) {
                        val idx = edges.indexOfFirst { getEdgeKey(it.experienceId1, it.experienceId2) == pairKey }
                        if (idx != -1) {
                            edges[idx] = edge.copy(sharedTerms = mergedShared)
                        }
                    }
                } else if (newS > 0.0) {
                    // Only form a new connection if mathematical calculation produces non-zero strength
                    edge = DagEdge(
                        experienceId1 = exp1.id,
                        experienceId2 = exp2.id,
                        title1 = exp1.title,
                        title2 = exp2.title,
                        strength = newS,
                        usageCount = 1,
                        lastUpdated = System.currentTimeMillis(),
                        sharedTerms = commonTerms.toList()
                    )
                    edges.add(edge)
                }

                if (deltaS > 0.0 || edge != null) {
                    edgeUpdates.add(
                        EdgeUpdateInfo(
                            exp1Id = exp1.id,
                            exp1Title = exp1.title,
                            exp2Id = exp2.id,
                            exp2Title = exp2.title,
                            commonCountC = C,
                            usedT = t,
                            deltaS = deltaS,
                            newStrengthS = newS,
                            commonTerms = commonTerms.toList()
                        )
                    )
                }
            }
        }

        saveAllEdges(context, edges)

        return DagUpdateSummary(
            question = userQuestion,
            candidateExperiences = activeExperiences,
            usedExperienceIds = usedExperienceIds.toList(),
            unionN = N,
            updatedEdges = edgeUpdates
        )
    }

    /**
     * Reverts DAG edge increments made by a previous interaction when the user edits their message.
     */
    fun revertDagConnections(
        context: Context,
        userQuestion: String,
        candidateExperiences: List<MemoryItem>,
        usedExperienceIds: Set<String>
    ) {
        if (usedExperienceIds.size < 2) return
        val qSet = getSemanticTermSet(userQuestion)
        val candidateTermSetN = mutableSetOf<String>()
        for (cand in candidateExperiences) {
            candidateTermSetN.addAll(getSemanticTermSet("${cand.title} ${cand.snippet} ${cand.message}"))
        }
        val N = candidateTermSetN.size.coerceAtLeast(1)

        val activeExperiences = candidateExperiences.filter { it.id in usedExperienceIds }
        if (activeExperiences.size < 2) return

        val expTermSets = mutableMapOf<String, Set<String>>()
        for (exp in activeExperiences) {
            expTermSets[exp.id] = getSemanticTermSet("${exp.title} ${exp.snippet} ${exp.message}")
        }

        val edges = loadAllEdges(context)
        for (i in 0 until activeExperiences.size) {
            for (j in i + 1 until activeExperiences.size) {
                val exp1 = activeExperiences[i]
                val exp2 = activeExperiences[j]
                val n1Set = expTermSets[exp1.id] ?: emptySet()
                val n2Set = expTermSets[exp2.id] ?: emptySet()
                val commonTerms = qSet.intersect(n1Set).intersect(n2Set)
                val C = commonTerms.size
                val deltaS = (C.toDouble() * 1.0) / N.toDouble()
                val pairKey = getEdgeKey(exp1.id, exp2.id)
                val edge = edges.find { getEdgeKey(it.experienceId1, it.experienceId2) == pairKey }
                if (edge != null) {
                    edge.strength = (edge.strength - deltaS).coerceAtLeast(0.0)
                    edge.usageCount = (edge.usageCount - 1).coerceAtLeast(0)
                    edge.lastUpdated = System.currentTimeMillis()
                }
            }
        }
        // Filter out edges that decayed or reverted to 0.0 strength
        val nonZeroEdges = edges.filter { it.strength > 0.0 }
        saveAllEdges(context, nonZeroEdges)
    }

    private fun getEdgeKey(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}__${id2}" else "${id2}__${id1}"
    }

    fun loadAllEdges(context: Context): MutableList<DagEdge> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return mutableListOf()
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DagEdge>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id1 = obj.getString("experienceId1")
                val id2 = obj.getString("experienceId2")
                val title1 = obj.optString("title1", "Experience $id1")
                val title2 = obj.optString("title2", "Experience $id2")
                val strength = obj.getDouble("strength")
                val usageCount = obj.optInt("usageCount", 1)
                val lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                
                val sharedTermsList = mutableListOf<String>()
                val termsArr = obj.optJSONArray("sharedTerms")
                if (termsArr != null) {
                    for (j in 0 until termsArr.length()) {
                        sharedTermsList.add(termsArr.getString(j))
                    }
                }

                if (strength > 0.0) {
                    list.add(DagEdge(id1, id2, title1, title2, strength, usageCount, lastUpdated, sharedTermsList))
                }
            }
            list.sortByDescending { it.strength }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAllEdges(context: Context, edges: List<DagEdge>) {
        try {
            val array = JSONArray()
            for (edge in edges) {
                if (edge.strength <= 0.0) continue
                val obj = JSONObject().apply {
                    put("experienceId1", edge.experienceId1)
                    put("experienceId2", edge.experienceId2)
                    put("title1", edge.title1)
                    put("title2", edge.title2)
                    put("strength", edge.strength)
                    put("usageCount", edge.usageCount)
                    put("lastUpdated", edge.lastUpdated)

                    val termsArr = JSONArray()
                    edge.sharedTerms.forEach { termsArr.put(it) }
                    put("sharedTerms", termsArr)
                }
                array.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllEdges(context: Context) {
        saveAllEdges(context, emptyList())
    }
}
