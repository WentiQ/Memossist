package com.example.apptempleate

import android.content.Context
import java.io.InputStream

/**
 * MODEL 2: Non-Reminder Intent Classifier
 * Evaluates non-reminder sentences.
 * Output:
 *  0 = ASKING
 *  1 = TELLING
 *  2 = MIXED
 */
object NonReminderIntentClassifier {
    private const val ASSET_PATH = "models/intent_classifier.json"
    private var instance: LocalTextClassifier? = null

    @Synchronized
    fun init(context: Context) {
        if (instance == null) {
            instance = LocalTextClassifier.fromAsset(context, ASSET_PATH)
        }
    }

    @Synchronized
    fun initFromStream(stream: InputStream) {
        if (instance == null) {
            instance = LocalTextClassifier.fromInputStream(stream)
        }
    }

    /**
     * Classifies non-reminder text. Supports multi-clause/multi-sentence evaluation.
     * @return Pair of intent name ("ASKING", "TELLING", "MIXED") and top probability confidence
     */
    fun classifyIntent(context: Context?, text: String): Pair<String, Float> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Pair("TELLING", 1.0f)

        val clauses = trimmed.split(Regex("(?<=[.!?])\\s+|[\r\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (clauses.size > 1) {
            val hasQuestion = clauses.any { isQuestionClause(it) }
            val hasStatement = clauses.any { !isQuestionClause(it) }

            if (hasQuestion && hasStatement) {
                return Pair("MIXED", 0.95f)
            } else if (hasQuestion) {
                return Pair("ASKING", 0.95f)
            } else {
                return Pair("TELLING", 0.95f)
            }
        }

        return classifySingleText(context, trimmed)
    }

    fun classifySingleText(context: Context?, text: String): Pair<String, Float> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Pair("TELLING", 1.0f)

        val isQ = isQuestionClause(trimmed)
        if (!isQ) {
            // Pure statement without question cues is definitely TELLING
            return Pair("TELLING", 0.95f)
        }

        // It has question cues: check if it's mixed with a declarative sub-clause
        // e.g. "I'm going to Paris, what should I pack?"
        val subClauses = trimmed.split(Regex("[,;]|\\b(and|also|plus)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length >= 3 }
        if (subClauses.size > 1 && subClauses.any { isQuestionClause(it) } && subClauses.any { !isQuestionClause(it) }) {
            return Pair("MIXED", 0.90f)
        }

        val classifier = getClassifier(context)
        if (classifier != null) {
            val result = classifier.predictMulticlass(trimmed)
            val intentName = when (result.label) {
                0 -> "ASKING"
                1 -> "TELLING"
                2 -> "MIXED"
                else -> "ASKING"
            }
            val maxProb = result.probabilities.maxOrNull() ?: 0.90f
            return Pair(if (intentName == "TELLING" && isQ) "ASKING" else intentName, maxProb)
        }

        return Pair("ASKING", 0.95f)
    }

    @Synchronized
    private fun getClassifier(context: Context?): LocalTextClassifier? {
        if (instance != null) return instance
        if (context != null) {
            try {
                instance = LocalTextClassifier.fromAsset(context, ASSET_PATH)
            } catch (e: Exception) {
                android.util.Log.e("IntentClassifier", "Failed to load intent model asset", e)
            }
        }
        return instance
    }

    fun isQuestionClause(t: String): Boolean {
        val trimmed = t.trim()
        if (trimmed.endsWith("?")) return true
        val lower = trimmed.lowercase()
        val questionKeywords = listOf(
            "what ", "who ", "where ", "when ", "why ", "how ",
            "can you", "could you", "do you", "tell me", "is there", "are there",
            "which ", "would you", "will you", "show me", "please tell", "explain "
        )
        return questionKeywords.any { lower.startsWith(it) || lower.contains(" $it") }
    }
}
