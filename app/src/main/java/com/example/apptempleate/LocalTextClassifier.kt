package com.example.apptempleate

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight, high-performance, offline TF-IDF + Logistic Regression evaluator.
 * Operates with zero native dependencies, achieves sub-millisecond inference,
 * and reproduces exact linear decision boundaries.
 */
class LocalTextClassifier private constructor(
    val type: String,
    val vocabulary: Map<String, Int>,
    val idf: FloatArray,
    val sublinearTf: Boolean = true,
    // Binary classification parameters
    val binaryCoef: FloatArray? = null,
    val binaryIntercept: Float = 0f,
    // Multiclass classification parameters
    val multiClasses: IntArray? = null,
    val multiCoef: Array<FloatArray>? = null,
    val multiIntercepts: FloatArray? = null
) {

    data class BinaryResult(val label: Int, val probability: Float)
    data class MultiResult(val label: Int, val probabilities: FloatArray)

    fun predictBinary(text: String): BinaryResult {
        val features = extractNormalizedTfidf(text)
        var score = binaryIntercept
        val coef = binaryCoef ?: return BinaryResult(0, 0f)

        for ((idx, value) in features) {
            if (idx < coef.size) {
                score += value * coef[idx]
            }
        }

        val prob = 1.0f / (1.0f + exp(-score))
        val label = if (prob >= 0.5f) 1 else 0
        return BinaryResult(label = label, probability = prob)
    }

    fun predictMulticlass(text: String): MultiResult {
        val classes = multiClasses ?: intArrayOf(0, 1, 2)
        val intercepts = multiIntercepts ?: FloatArray(classes.size)
        val coefs = multiCoef ?: Array(classes.size) { FloatArray(0) }

        val features = extractNormalizedTfidf(text)
        val scores = FloatArray(classes.size) { i -> intercepts.getOrElse(i) { 0f } }

        for (cIdx in scores.indices) {
            val classCoef = coefs.getOrNull(cIdx) ?: continue
            for ((idx, value) in features) {
                if (idx < classCoef.size) {
                    scores[cIdx] += value * classCoef[idx]
                }
            }
        }

        // Softmax
        var maxScore = Float.NEGATIVE_INFINITY
        for (s in scores) {
            if (s > maxScore) maxScore = s
        }

        val expScores = FloatArray(scores.size)
        var sumExp = 0f
        for (i in scores.indices) {
            expScores[i] = exp(scores[i] - maxScore)
            sumExp += expScores[i]
        }

        val probabilities = FloatArray(scores.size)
        var bestIdx = 0
        var bestProb = -1f
        for (i in scores.indices) {
            probabilities[i] = if (sumExp > 0f) expScores[i] / sumExp else 0f
            if (probabilities[i] > bestProb) {
                bestProb = probabilities[i]
                bestIdx = i
            }
        }

        val bestLabel = classes.getOrElse(bestIdx) { bestIdx }
        return MultiResult(label = bestLabel, probabilities = probabilities)
    }

    private fun extractNormalizedTfidf(text: String): List<Pair<Int, Float>> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()

        val tfMap = mutableMapOf<String, Int>()
        for (token in tokens) {
            tfMap[token] = (tfMap[token] ?: 0) + 1
        }

        val vec = mutableListOf<Pair<Int, Float>>()
        var sumSq = 0.0

        for ((token, count) in tfMap) {
            val idx = vocabulary[token] ?: continue
            val idfVal = idf.getOrElse(idx) { 1.0f }
            val tfVal = if (sublinearTf) (1.0 + ln(count.toDouble())).toFloat() else count.toFloat()
            val weight = tfVal * idfVal
            vec.add(Pair(idx, weight))
            sumSq += (weight * weight).toDouble()
        }

        if (sumSq > 0.0) {
            val norm = sqrt(sumSq).toFloat()
            return vec.map { Pair(it.first, it.second / norm) }
        }

        return vec
    }

    companion object {
        /**
         * Tokenizes text into unigrams and bigrams matching standard scikit-learn r'(?u)\b\w\w+\b' pattern.
         */
        fun tokenize(text: String): List<String> {
            val lower = text.lowercase()
            val regex = Regex("\\b\\w\\w+\\b")
            val words = regex.findAll(lower).map { it.value }.toList()
            if (words.isEmpty()) return emptyList()

            val tokens = ArrayList<String>(words.size * 2)
            tokens.addAll(words) // unigrams

            for (i in 0 until words.size - 1) {
                tokens.add("${words[i]} ${words[i + 1]}") // bigrams
            }

            return tokens
        }

        fun fromInputStream(stream: InputStream): LocalTextClassifier {
            val jsonStr = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            return fromJsonString(jsonStr)
        }

        fun fromJsonString(jsonStr: String): LocalTextClassifier {
            val obj = JSONObject(jsonStr)
            val type = obj.optString("type", "binary_logistic")
            val sublinearTf = obj.optBoolean("sublinear_tf", true)

            val vocabObj = obj.getJSONObject("vocabulary")
            val vocab = HashMap<String, Int>(vocabObj.length())
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = vocabObj.getInt(key)
            }

            val idfArr = obj.getJSONArray("idf")
            val idf = FloatArray(idfArr.length()) { i -> idfArr.getDouble(i).toFloat() }

            if (type == "binary_logistic") {
                val coefArr = obj.getJSONArray("coef")
                val binaryCoef = FloatArray(coefArr.length()) { i -> coefArr.getDouble(i).toFloat() }
                val binaryIntercept = obj.optDouble("intercept", 0.0).toFloat()

                return LocalTextClassifier(
                    type = type,
                    vocabulary = vocab,
                    idf = idf,
                    sublinearTf = sublinearTf,
                    binaryCoef = binaryCoef,
                    binaryIntercept = binaryIntercept
                )
            } else {
                val classesArr = obj.getJSONArray("classes")
                val multiClasses = IntArray(classesArr.length()) { i -> classesArr.getInt(i) }

                val interceptsArr = obj.getJSONArray("intercepts")
                val multiIntercepts = FloatArray(interceptsArr.length()) { i -> interceptsArr.getDouble(i).toFloat() }

                val coefArr2D = obj.getJSONArray("coef")
                val multiCoef = Array(coefArr2D.length()) { r ->
                    val rowArr = coefArr2D.getJSONArray(r)
                    FloatArray(rowArr.length()) { c -> rowArr.getDouble(c).toFloat() }
                }

                return LocalTextClassifier(
                    type = type,
                    vocabulary = vocab,
                    idf = idf,
                    sublinearTf = sublinearTf,
                    multiClasses = multiClasses,
                    multiCoef = multiCoef,
                    multiIntercepts = multiIntercepts
                )
            }
        }

        fun fromAsset(context: Context, assetPath: String): LocalTextClassifier {
            return context.assets.open(assetPath).use { fromInputStream(it) }
        }
    }
}
