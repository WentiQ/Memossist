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
     * Retrieves top 5 matching experiences based on word and synonym overlap with Question Q.
     */
    fun retrieveTopMatchingExperiences(context: Context, userQuestion: String, topK: Int = 5): List<MemoryItem> {
        val allMemories = MemoryVaultRepository.loadAllMemories(context)
        if (allMemories.isEmpty()) return emptyList()

        val qSet = getSemanticTermSet(userQuestion)
        if (qSet.isEmpty()) return allMemories.take(topK)

        // Calculate overlap score |Q ∩ N_i| for each experience
        val scoredMemories = allMemories.map { memory ->
            val nSet = getSemanticTermSet("${memory.title} ${memory.snippet} ${memory.message}")
            val overlapScore = qSet.intersect(nSet).size
            Pair(memory, overlapScore)
        }

        // Sort descending by overlap score, tie-breaker timestamp/ID
        val sorted = scoredMemories.sortedWith(
            compareByDescending<Pair<MemoryItem, Int>> { it.second }
                .thenBy { it.first.id }
        )

        return sorted.take(topK).map { it.first }
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
        var unionSetN = mutableSetOf<String>()

        for (exp in activeExperiences) {
            val nSet = getSemanticTermSet("${exp.title} ${exp.snippet} ${exp.message}")
            expTermSets[exp.id] = nSet
            unionSetN.addAll(nSet)
        }

        val edges = loadAllEdges(context)
        val edgeUpdates = mutableListOf<EdgeUpdateInfo>()

        // Take every pair of LLM-used experiences only.
        for (i in 0 until activeExperiences.size) {
            for (j in i + 1 until activeExperiences.size) {
                val exp1 = activeExperiences[i]
                val exp2 = activeExperiences[j]

                val n1Set = expTermSets[exp1.id] ?: emptySet()
                val n2Set = expTermSets[exp2.id] ?: emptySet()

                // Calculate C = |Q ∩ N_i ∩ N_j|
                val commonTerms = qSet.intersect(n1Set).intersect(n2Set)
                val C = commonTerms.size

                // Determine if both experiences were actually used in answering
                val t1Used = usedExperienceIds.contains(exp1.id)
                val t2Used = usedExperienceIds.contains(exp2.id)
                val t = if (t1Used && t2Used) 1 else 0

                // Calculate delta S = (C * t) / N
                val deltaS = (C.toDouble() * t.toDouble()) / N.toDouble()

                // Key pair ordering
                val pairKey = getEdgeKey(exp1.id, exp2.id)
                var edge = edges.find { getEdgeKey(it.experienceId1, it.experienceId2) == pairKey }

                val oldS = edge?.strength ?: 0.0
                val newS = oldS + deltaS

                if (edge != null) {
                    edge.strength = newS
                    if (t == 1) edge.usageCount += 1
                    edge.lastUpdated = System.currentTimeMillis()
                } else {
                    edge = DagEdge(
                        experienceId1 = exp1.id,
                        experienceId2 = exp2.id,
                        title1 = exp1.title,
                        title2 = exp2.title,
                        strength = newS,
                        usageCount = if (t == 1) 1 else 0,
                        lastUpdated = System.currentTimeMillis(),
                        sharedTerms = commonTerms.toList()
                    )
                    edges.add(edge)
                }

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

        saveAllEdges(context, edges)

        return DagUpdateSummary(
            question = userQuestion,
            candidateExperiences = activeExperiences,
            usedExperienceIds = usedExperienceIds.toList(),
            unionN = N,
            updatedEdges = edgeUpdates
        )
    }

    private fun getEdgeKey(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}__${id2}" else "${id2}__${id1}"
    }

    fun loadAllEdges(context: Context): MutableList<DagEdge> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val initialEdges = createInitialDagEdges(context)
            saveAllEdges(context, initialEdges)
            return initialEdges
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

                list.add(DagEdge(id1, id2, title1, title2, strength, usageCount, lastUpdated, sharedTermsList))
            }
            list.sortByDescending { it.strength }
            list
        } catch (e: Exception) {
            val initialEdges = createInitialDagEdges(context)
            saveAllEdges(context, initialEdges)
            initialEdges
        }
    }

    fun saveAllEdges(context: Context, edges: List<DagEdge>) {
        try {
            val array = JSONArray()
            for (edge in edges) {
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

    private fun createInitialDagEdges(context: Context): MutableList<DagEdge> {
        val memories = MemoryVaultRepository.loadAllMemories(context)
        if (memories.size < 2) return mutableListOf()

        val exp1 = memories[0]
        val exp2 = memories[1]
        val exp3 = if (memories.size > 2) memories[2] else null

        val list = mutableListOf<DagEdge>()
        
        val set1 = getSemanticTermSet("${exp1.title} ${exp1.snippet} ${exp1.message}")
        val set2 = getSemanticTermSet("${exp2.title} ${exp2.snippet} ${exp2.message}")
        val shared12 = set1.intersect(set2).toList()

        list.add(
            DagEdge(
                experienceId1 = exp1.id,
                experienceId2 = exp2.id,
                title1 = exp1.title,
                title2 = exp2.title,
                strength = 0.425,
                usageCount = 3,
                lastUpdated = System.currentTimeMillis() - 1800000,
                sharedTerms = if (shared12.isNotEmpty()) shared12 else listOf("strategy", "neural", "architecture")
            )
        )

        if (exp3 != null) {
            val set3 = getSemanticTermSet("${exp3.title} ${exp3.snippet} ${exp3.message}")
            val shared13 = set1.intersect(set3).toList()
            list.add(
                DagEdge(
                    experienceId1 = exp1.id,
                    experienceId2 = exp3.id,
                    title1 = exp1.title,
                    title2 = exp3.title,
                    strength = 0.280,
                    usageCount = 2,
                    lastUpdated = System.currentTimeMillis() - 3600000,
                    sharedTerms = if (shared13.isNotEmpty()) shared13 else listOf("ui", "polish", "notes")
                )
            )
        }

        return list
    }
}
