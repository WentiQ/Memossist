package com.example.apptempleate

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class MemoryParameters(
    val importance: Double,
    val confidence: Double,
    val stability: Double
)

data class FactForEvaluation(
    val experienceId: String,
    val factText: String
)

data class EvaluatedFactResult(
    val experienceId: String,
    val importance: Double,
    val confidence: Double,
    val stability: Double,
    val strength: Double
)

/**
 * Dedicated service for evaluating memory parameters (importance, confidence, stability)
 * for newly extracted Memory Vault facts using a secondary LLM call with local validation and fallback.
 */
object MemoryParameterEvaluator {
    private const val TAG = "MemoryParamEvaluator"

    val FALLBACK_PARAMETERS = MemoryParameters(
        importance = MemoryDecayConfig.DEFAULT_MIGRATION_IMPORTANCE,
        confidence = MemoryDecayConfig.DEFAULT_MIGRATION_CONFIDENCE,
        stability = MemoryDecayConfig.DEFAULT_MIGRATION_STABILITY
    )

    fun buildBatchSystemPrompt(): String {
        return "Score memory facts on scale 0.0-1.0: importance (value to remember), confidence (stated certainty), stability (longevity).\n" +
                "Output ONLY a raw JSON array of objects with keys \"id\", \"importance\", \"confidence\", \"stability\". No markdown.\n" +
                "Format:\n" +
                "[{\"id\": \"EXP-000001\", \"importance\": 0.85, \"confidence\": 0.95, \"stability\": 0.75}]"
    }

    fun buildBatchUserPrompt(facts: List<FactForEvaluation>): String {
        val sb = StringBuilder("Facts:\n")
        for (f in facts) {
            sb.append("[ID: ").append(f.experienceId).append("] \"").append(f.factText).append("\"\n")
        }
        return sb.toString().trim()
    }

    fun parseAndValidateBatchResponse(
        rawOutput: String,
        expectedFacts: List<FactForEvaluation>
    ): List<EvaluatedFactResult> {
        val parsedMap = mutableMapOf<String, MemoryParameters>()
        val parsedList = mutableListOf<MemoryParameters>()

        if (rawOutput.isNotBlank()) {
            try {
                val jsonStart = rawOutput.indexOf('[')
                val jsonEnd = rawOutput.lastIndexOf(']')
                if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                    val arrayStr = rawOutput.substring(jsonStart, jsonEnd + 1)
                    val array = JSONArray(arrayStr)
                    for (i in 0 until array.length()) {
                        val obj = try { array.getJSONObject(i) } catch (_: Exception) { array.optJSONObject(i) } ?: continue
                        val id = if (obj.has("id")) obj.getString("id").trim() else ""

                        val imp = if (obj.has("importance")) obj.optDouble("importance", Double.NaN) else Double.NaN
                        val conf = if (obj.has("confidence")) obj.optDouble("confidence", Double.NaN) else Double.NaN
                        val stab = if (obj.has("stability")) obj.optDouble("stability", Double.NaN) else Double.NaN

                        if (!imp.isNaN() && !conf.isNaN() && !stab.isNaN()) {
                            val params = MemoryParameters(
                                importance = imp.coerceIn(0.0, 1.0),
                                confidence = conf.coerceIn(0.0, 1.0),
                                stability = stab.coerceIn(0.0, 1.0)
                            )
                            if (id.isNotEmpty()) {
                                parsedMap[id.uppercase()] = params
                            }
                            parsedList.add(params)
                        }
                    }
                }
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "Batch JSON array parsing failed, trying individual objects fallback", e) }
            }

            // Fallback: regex search for individual JSON objects / ID matches
            if (parsedMap.size < expectedFacts.size) {
                for (fact in expectedFacts) {
                    val upperId = fact.experienceId.uppercase()
                    if (parsedMap.containsKey(upperId)) continue
                    val idEscaped = Regex.escape(fact.experienceId)
                    val idPattern = Regex("(?s)\\{[^{}]*[\"']id[\"']\\s*:\\s*[\"']$idEscaped[\"'][^{}]*\\}", RegexOption.IGNORE_CASE)
                    val match = idPattern.find(rawOutput)
                    if (match != null) {
                        try {
                            val obj = JSONObject(match.value)
                            val imp = if (obj.has("importance")) obj.optDouble("importance", Double.NaN) else Double.NaN
                            val conf = if (obj.has("confidence")) obj.optDouble("confidence", Double.NaN) else Double.NaN
                            val stab = if (obj.has("stability")) obj.optDouble("stability", Double.NaN) else Double.NaN
                            if (!imp.isNaN() && !conf.isNaN() && !stab.isNaN()) {
                                val params = MemoryParameters(
                                    importance = imp.coerceIn(0.0, 1.0),
                                    confidence = conf.coerceIn(0.0, 1.0),
                                    stability = stab.coerceIn(0.0, 1.0)
                                )
                                parsedMap[upperId] = params
                                if (!parsedList.contains(params)) {
                                    parsedList.add(params)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        val results = mutableListOf<EvaluatedFactResult>()
        for ((index, f) in expectedFacts.withIndex()) {
            val upperId = f.experienceId.uppercase()
            val params = parsedMap[upperId]
                ?: parsedMap.entries.find { it.key.contains(upperId) || upperId.contains(it.key) }?.value
                ?: parsedList.getOrNull(index)
                ?: FALLBACK_PARAMETERS

            val strength = MemoryDecayCalculator.calculateInitialStrength(
                params.importance,
                params.confidence,
                params.stability
            )
            results.add(
                EvaluatedFactResult(
                    experienceId = f.experienceId,
                    importance = params.importance,
                    confidence = params.confidence,
                    stability = params.stability,
                    strength = strength
                )
            )
        }
        return results
    }

    /**
     * Batch evaluates a list of facts with experience IDs using the 2nd LLM context.
     */
    fun evaluateBatch(context: Context, facts: List<FactForEvaluation>): List<EvaluatedFactResult> {
        if (facts.isEmpty()) return emptyList()

        val startTime = System.currentTimeMillis()
        try {
            val rawOutput = NoeonAiEngine.evaluateFactBatchParameters(context, facts)
            val results = parseAndValidateBatchResponse(rawOutput, facts)
            val durationMs = System.currentTimeMillis() - startTime
            ParameterStatsRepository.recordEvaluation(context, durationMs)
            runCatching { Log.d(TAG, "Batch evaluated ${facts.size} facts in ${durationMs}ms: $rawOutput") }
            return results
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Exception during batch fact parameter evaluation", e) }
        }

        // Fallback for all items
        return facts.map { f ->
            val strength = MemoryDecayCalculator.calculateInitialStrength(
                FALLBACK_PARAMETERS.importance,
                FALLBACK_PARAMETERS.confidence,
                FALLBACK_PARAMETERS.stability
            )
            EvaluatedFactResult(
                experienceId = f.experienceId,
                importance = FALLBACK_PARAMETERS.importance,
                confidence = FALLBACK_PARAMETERS.confidence,
                stability = FALLBACK_PARAMETERS.stability,
                strength = strength
            )
        }
    }

    fun buildSystemPrompt(): String {
        return buildBatchSystemPrompt()
    }

    fun buildUserPrompt(fact: String): String {
        return "Fact to evaluate:\n\"$fact\""
    }

    fun parseAndValidateResponse(rawOutput: String): MemoryParameters? {
        if (rawOutput.isBlank()) return null
        return try {
            val jsonStart = rawOutput.indexOf('{')
            val jsonEnd = rawOutput.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return null

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            if (!json.has("importance") || !json.has("confidence") || !json.has("stability")) {
                return null
            }

            val importance = json.getDouble("importance")
            val confidence = json.getDouble("confidence")
            val stability = json.getDouble("stability")

            if (importance.isNaN() || importance < 0.0 || importance > 1.0 ||
                confidence.isNaN() || confidence < 0.0 || confidence > 1.0 ||
                stability.isNaN() || stability < 0.0 || stability > 1.0) {
                return null
            }

            MemoryParameters(
                importance = importance,
                confidence = confidence,
                stability = stability
            )
        } catch (e: Exception) {
            null
        }
    }

    fun evaluate(context: Context, fact: String): MemoryParameters {
        val list = listOf(FactForEvaluation("TEMP", fact))
        val res = evaluateBatch(context, list).firstOrNull()
        return if (res != null) {
            MemoryParameters(res.importance, res.confidence, res.stability)
        } else {
            FALLBACK_PARAMETERS
        }
    }
}
