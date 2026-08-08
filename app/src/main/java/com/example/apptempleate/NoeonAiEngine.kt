package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import com.arm.aichat.internal.InferenceEngineImpl
import org.json.JSONArray

data class LlmPipelineResult(
    val cleanHumanoidAnswer: String,
    val relevantExperienceIds: List<String>,
    val extractedInformativeFacts: List<String>,
    val intent: String,
    val modelName: String
)

object NoeonAiEngine {
    private const val PREFS_NAME = "MemossistPrefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    private const val KEY_DOWNLOADED_MODELS = "downloaded_models"
    const val DEFAULT_MODEL_ID = "qwen3.5_4b"
    private var cachedActiveModel: AiModel? = null
    private var offlineEngine: InferenceEngineImpl? = null

    fun getSelectedModel(context: Context): AiModel {
        if (cachedActiveModel != null) return cachedActiveModel!!
        val selectedId = getPrefs(context).getString(KEY_SELECTED_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        return ModelCatalog.getModelById(selectedId).also { cachedActiveModel = it }
    }

    fun setSelectedModel(context: Context, modelId: String) {
        cachedActiveModel = ModelCatalog.getModelById(modelId)
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
        markModelAsDownloaded(context, modelId)
    }

    fun isModelDownloaded(context: Context, modelId: String) =
        RealModelDownloader.isModelFileDownloaded(context, ModelCatalog.getModelById(modelId))

    fun markModelAsDownloaded(context: Context, modelId: String) {
        val downloaded = getPrefs(context).getStringSet(KEY_DOWNLOADED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        downloaded.add(modelId)
        getPrefs(context).edit().putStringSet(KEY_DOWNLOADED_MODELS, downloaded).apply()
    }

    fun getActiveModelFilePath(context: Context): String {
        val file = RealModelDownloader.getModelFile(context, getSelectedModel(context))
        return if (file.exists() && file.length() > 0) file.absolutePath else "No model file available"
    }

    fun buildSystemPrompt(candidateExperiences: List<MemoryItem>): String = buildString {
        append("You are Memossist, an intelligent humanoid assistant with access to a Memory Vault.\n")
        append("Your output MUST begin with the four tag sections in exact sequence:\n")
        append("1. [INTENT: ASKING | TELLING | MIXED]\n")
        append("2. [EXTRACTED_FACTS: [\"fact 1\", \"fact 2\"] or []]\n")
        append("3. [USED_EXPERIENCES: EXP-ID1, EXP-ID2 or NONE]\n")
        append("4. [HUMANOID_ANSWER]\n")
        append("<your natural response answer>\n\n")
        append("Rules for INTENT:\n")
        append("- ASKING: The user is only asking a question or requesting information.\n")
        append("- TELLING: The user is stating personal facts, background details, roles, location, or preferences.\n")
        append("- MIXED: The user message contains BOTH personal facts AND a question.\n\n")
        append("Rules for EXTRACTED_FACTS:\n")
        append("- EXTRACTED_FACTS are ALL informative statements, declarations, facts, or knowledge expressed in the user's message (it can be ANY statement - not just personal details).\n")
        append("- Exclude ONLY questions or inquiry requests asked in the message.\n")
        append("- Do NOT extract or repeat facts that are ALREADY listed in the candidate experiences below.\n")
        append("- Format extracted facts as a JSON array of strings: [\"fact 1\", \"fact 2\"]. If no statement facts exist, output [EXTRACTED_FACTS: []].\n\n")
        append("Rules for USED_EXPERIENCES:\n")
        append("- Use only IDs from the candidate experiences below if actually used to answer, or NONE.\n\n")
        append("=== TOP 5 CANDIDATE EXPERIENCES ===\n")
        if (candidateExperiences.isEmpty()) {
            append("(No candidate experiences retrieved)\n")
        } else {
            candidateExperiences.forEachIndexed { index, exp ->
                append("${index + 1}. [ID: ${exp.id}] Title: ${exp.title}\nContent: ${exp.message}\n")
            }
        }
    }

    fun processMessagePipeline(
        context: Context,
        userMessage: String,
        candidateExperiences: List<MemoryItem>,
        onTokenGenerated: ((String) -> Unit)? = null
    ): LlmPipelineResult {
        val model = getSelectedModel(context)
        val modelFile = RealModelDownloader.getModelFile(context, model)

        if (!modelFile.exists() || modelFile.length() < 10 * 1024 * 1024) {
            val downloadedMb = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0L
            val notDownloadedMsg = if (downloadedMb > 0) {
                "The selected LLM model (${model.name}) is only partially downloaded ($downloadedMb MB of ${model.downloadSizeMb} MB). Please open Model Marketplace to download the complete ${model.fileName} file (${model.downloadSizeMb} MB)."
            } else {
                "The selected LLM model (${model.name}) is not downloaded on your device yet. Please open Model Marketplace from the top menu to download ${model.fileName} (${model.downloadSizeMb} MB) for offline AI generation."
            }
            return LlmPipelineResult(
                cleanHumanoidAnswer = notDownloadedMsg,
                relevantExperienceIds = emptyList(),
                extractedInformativeFacts = emptyList(),
                intent = "UNKNOWN",
                modelName = model.name
            )
        }

        val raw = try {
            val engine = offlineEngine ?: InferenceEngineImpl(context.applicationContext).also { offlineEngine = it }
            engine.generate(
                modelPath = modelFile.absolutePath,
                systemPrompt = buildSystemPrompt(candidateExperiences),
                userMessage = userMessage,
                onTokenGenerated = onTokenGenerated
            )
        } catch (e: Exception) {
            android.util.Log.e("NoeonAiEngine", "Offline GGUF inference failed", e)
            "[LLM Engine Exception: ${e.message ?: e.toString()}]"
        }

        val candidateIds = candidateExperiences.map { it.id }.toSet()
        var usedIds = (parseTagValue(raw, "USED_EXPERIENCES") ?: "")
            .split(",").map { it.trim() }.filter { it in candidateIds }.distinct()

        if (usedIds.isEmpty() && candidateIds.isNotEmpty()) {
            usedIds = candidateIds.filter { id -> raw.contains(id, ignoreCase = true) }
        }

        val cleanAnswer = parseAnswer(raw)
        val extractedFacts = parseFacts(parseTagValue(raw, "EXTRACTED_FACTS"), userMessage, candidateExperiences)
        val intent = (parseTagValue(raw, "INTENT") ?: inferIntent(userMessage)).uppercase()

        return LlmPipelineResult(
            cleanHumanoidAnswer = if (cleanAnswer.isNotBlank()) cleanAnswer else raw,
            relevantExperienceIds = usedIds,
            extractedInformativeFacts = extractedFacts,
            intent = intent,
            modelName = model.name
        )
    }

    private fun inferIntent(userMessage: String): String {
        val message = userMessage.trim()
        if (message.isBlank()) return "TELLING"

        val clauses = message.split(Regex("(?<=[.!?\\n])|\\b(and|also|plus)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim().trim(',', ';', '.', '!', '?') }
            .filter { it.isNotBlank() && it.length >= 4 }

        val hasQuestion = clauses.any { isQuestionText(it) } || isQuestionText(message)
        val hasStatement = clauses.any { !isQuestionText(it) }

        return when {
            hasQuestion && hasStatement -> "MIXED"
            hasQuestion -> "ASKING"
            else -> "TELLING"
        }
    }

    private fun parseTagValue(content: String, tagName: String): String? {
        val regex = Regex("\\[$tagName\\s*:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val match = regex.find(content)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        val fallbackRegex = Regex("\\[$tagName\\s*:\\s*(.*)", RegexOption.IGNORE_CASE)
        return fallbackRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun parseAnswer(content: String): String {
        if (content.isBlank()) return ""
        val marker = Regex("\\[HUMANOID_ANSWER\\]", RegexOption.IGNORE_CASE).find(content)
        if (marker != null) {
            return content.substring(marker.range.last + 1).trim()
        }
        // If [HUMANOID_ANSWER] tag was missing, strip metadata tags from anywhere in the response body
        return content
            .replace(Regex("\\[INTENT\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[EXTRACTED_FACTS\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[USED_EXPERIENCES\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun isQuestionText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return true
        val lower = trimmed.lowercase()
        val questionKeywords = listOf(
            "what ", "who ", "where ", "when ", "why ", "how ",
            "can you", "could you", "do you", "tell me", "is there", "are there",
            "which ", "would ", "will you", "show me", "please tell", "explain ",
            "what's", "where's", "who's", "how's", "what mess", "which mess",
            "do i", "do we", "is it", "are we"
        )
        return questionKeywords.any { lower.contains(it) }
    }

    private fun isDuplicateExperience(fact: String, candidateExperiences: List<MemoryItem>): Boolean {
        val lowerFact = fact.lowercase().trim()
        if (lowerFact.length < 4) return false

        val factWords = lowerFact.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (factWords.isEmpty()) return false

        for (cand in candidateExperiences) {
            val candText = "${cand.title} ${cand.message}".lowercase()
            if (candText.contains(lowerFact) || lowerFact.contains(candText)) {
                return true
            }
            val candWords = candText.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
            val commonWords = factWords.intersect(candWords)
            val overlapRatio = commonWords.size.toDouble() / factWords.size.toDouble()
            if (overlapRatio >= 0.75) {
                return true
            }
        }
        return false
    }

    private fun parseFacts(
        value: String?,
        userMessage: String = "",
        candidateExperiences: List<MemoryItem> = emptyList()
    ): List<String> {
        val rawList = mutableListOf<String>()

        if (!value.isNullOrBlank() && !value.equals("NONE", true) && value != "[]" && value != "[\"\"]") {
            val cleanedVal = value.trim().removeSurrounding("[", "]").removeSurrounding("[", "]").trim()

            // 1. Try standard JSONArray parsing
            try {
                val array = JSONArray("[$cleanedVal]")
                for (i in 0 until array.length()) {
                    val item = array.optString(i).trim().trim('"', '\'')
                    if (item.isNotBlank() && !item.equals("NONE", true)) {
                        rawList.add(item)
                    }
                }
            } catch (_: Exception) {
                // 2. Try single-quoted JSON or bulleted/newline-separated list parsing
                val lines = cleanedVal
                    .split(Regex("[\n\r]+|(?<=\"),|(?<='),|,"))
                    .map { it.trim().trim('-', '•', '*', '"', '\'', ',', '[', ']') }
                    .filter { it.isNotBlank() && !it.equals("NONE", true) && it != "[]" }
                rawList.addAll(lines)
            }
        }

        // Fallback: If rawList is empty, split userMessage into non-question statement clauses
        if (rawList.isEmpty() && userMessage.isNotBlank()) {
            val clauses = userMessage.split(Regex("(?<=[.!?\\n])|\\b(and|also|plus)\\b", RegexOption.IGNORE_CASE))
                .map { it.trim().trim(',', ';', '.', '!', '?') }
                .filter { it.isNotBlank() && it.length >= 4 }

            for (clause in clauses) {
                if (!isQuestionText(clause)) {
                    rawList.add(clause)
                }
            }
        }

        // Filter out facts that duplicate existing candidate experiences or are questions
        return rawList.map { it.trim('"', '\'', '[', ']').trim() }
            .filter { fact ->
                if (fact.length < 3 || fact.equals("NONE", true)) return@filter false
                if (isQuestionText(fact)) return@filter false // NEVER save questions as facts!
                !isDuplicateExperience(fact, candidateExperiences)
            }.distinct()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
