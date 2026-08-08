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
        append("Return exactly the following four sections and no other metadata:\n")
        append("[INTENT: ASKING | TELLING | MIXED]\n")
        append("[EXTRACTED_FACTS: [\"fact 1\", \"fact 2\"] or []]\n")
        append("[USED_EXPERIENCES: comma-separated candidate IDs actually used, or NONE]\n")
        append("[HUMANOID_ANSWER]\n<the natural answer only>\n\n")
        append("Fact Extraction Rules:\n")
        append("- Extract any concise, standalone facts or statements supplied by the user in their message into [EXTRACTED_FACTS].\n")
        append("- If the user states a personal fact (e.g. studies, location, role, creation, preference), format it as a string in a JSON array.\n")
        append("- If the message is purely a question without any user-supplied facts, use [EXTRACTED_FACTS: []].\n\n")
        append("Rules: Answer questions using the candidate memories below and general world knowledge when useful.\n")
        append("For telling messages, acknowledge naturally. For mixed messages, answer and extract only the user-supplied facts.\n")
        append("Use only IDs from the candidates below; use NONE if no memory was used.\n\n")
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
        var extractedFacts = parseFacts(parseTagValue(raw, "EXTRACTED_FACTS"))
        if (extractedFacts.isEmpty()) {
            extractedFacts = extractFallbackUserFacts(userMessage)
        }
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
        val lower = userMessage.trim().lowercase()
        val questionKeywords = listOf("what", "who", "where", "when", "why", "how", "?", "tell me", "do you know", "show")
        val isQuestion = questionKeywords.any { lower.contains(it) }
        return if (isQuestion) "ASKING" else "TELLING"
    }

    private fun parseTagValue(content: String, tagName: String): String? =
        Regex("\\[$tagName:\\s*(.*?)\\]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(content)?.groupValues?.get(1)?.trim()

    private fun parseAnswer(content: String): String {
        if (content.isBlank()) return ""
        val marker = Regex("\\[HUMANOID_ANSWER\\]", RegexOption.IGNORE_CASE).find(content)
        if (marker != null) {
            return content.substring(marker.range.last + 1).trim()
        }
        // If [HUMANOID_ANSWER] tag was missing, remove metadata tags and keep the response body
        return content
            .replace(Regex("\\[INTENT:.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[EXTRACTED_FACTS:.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[USED_EXPERIENCES:.*?\\]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun parseFacts(value: String?): List<String> {
        if (value.isNullOrBlank() || value.equals("NONE", true) || value == "[]" || value == "[\"\"]") return emptyList()
        val cleanedVal = value.trim()

        // 1. Try standard JSONArray parsing
        try {
            val array = JSONArray(cleanedVal)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i).trim().trim('"', '\'')
                if (item.isNotBlank() && !item.equals("NONE", true)) {
                    list.add(item)
                }
            }
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {}

        // 2. Try single-quoted JSON or bulleted/newline-separated list parsing
        val lines = cleanedVal
            .removeSurrounding("[", "]")
            .split(Regex("[\n\r]+|(?<=\"),|(?<='),|,"))
            .map { it.trim().trim('-', '•', '*', '"', '\'', ',') }
            .filter { it.isNotBlank() && !it.equals("NONE", true) && it != "[]" }

        if (lines.isNotEmpty()) return lines

        // 3. Fallback single string
        val single = cleanedVal.removeSurrounding("[", "]").trim().trim('"', '\'')
        return if (single.isNotBlank() && !single.equals("NONE", true)) listOf(single) else emptyList()
    }

    private fun extractFallbackUserFacts(userMessage: String): List<String> {
        val trimmed = userMessage.trim()
        val lower = trimmed.lowercase()
        // Skip pure question messages unless they also contain explicit fact statements
        val isPureQuestion = trimmed.endsWith("?") && !lower.contains("i am") && !lower.contains("i study") && !lower.contains("i live") && !lower.contains("i created") && !lower.contains("my ")
        if (isPureQuestion) return emptyList()

        val factPatterns = listOf(
            Regex("(?:i am|i'm)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:i study|i'm studying)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:i live in|i'm located in)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:i created|i built|i made)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:my name is|my favorite)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:i work at|i work as)\\s+(.+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in factPatterns) {
            if (pattern.containsMatchIn(trimmed)) {
                return listOf(trimmed)
            }
        }
        return emptyList()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
