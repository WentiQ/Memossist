package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences
import com.arm.aichat.internal.InferenceEngineImpl
import org.json.JSONArray

data class LlmPipelineResult(
    val cleanHumanoidAnswer: String,
    val relevantExperienceIds: List<String>,
    val extractedInformativeFacts: List<String>,
    val extractedReminderTag: String?,
    val intent: String,
    val modelName: String,
    val messageType: MessageType = MessageType.TELLING,
    val classificationResult: ClassificationResult? = null,
    val systemPromptUsed: String = "",
    val promptTokenCount: Int = 0,
    val legacyTokenCount: Int = 0,
    val promptBuildTimeMs: Long = 0L,
    val inferenceTimeMs: Long = 0L,
    val totalPipelineTimeMs: Long = 0L
)

object NoeonAiEngine {
    private const val PREFS_NAME = "MemossistPrefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    private const val KEY_DOWNLOADED_MODELS = "downloaded_models"
    const val DEFAULT_MODEL_ID = "qwen3.5_4b"
    private var cachedActiveModel: AiModel? = null
    private var chatEngine: InferenceEngineImpl? = null
    private var paramEngine: InferenceEngineImpl? = null

    fun getSelectedModel(context: Context): AiModel {
        val selectedId = getPrefs(context).getString(KEY_SELECTED_MODEL_ID, null)
        if (!selectedId.isNullOrEmpty()) {
            val candidate = ModelCatalog.getModelById(selectedId)
            if (isModelDownloaded(context, candidate.id)) {
                return candidate
            }
        }

        // If explicitly selected model is not downloaded or not set, find ANY downloaded model
        val anyDownloaded = ModelCatalog.models.firstOrNull { isModelDownloaded(context, it.id) }
        if (anyDownloaded != null) {
            getPrefs(context).edit().putString(KEY_SELECTED_MODEL_ID, anyDownloaded.id).apply()
            return anyDownloaded
        }

        // Fallback to selected preference or default if no models are downloaded yet
        val fallbackId = selectedId ?: DEFAULT_MODEL_ID
        return ModelCatalog.getModelById(fallbackId)
    }

    fun setSelectedModel(context: Context, modelId: String) {
        cachedActiveModel = ModelCatalog.getModelById(modelId)
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
        markModelAsDownloaded(context, modelId)
        
        // Reset cached native engines so the new model's weights and context are loaded seamlessly
        try {
            chatEngine?.release()
        } catch (e: Exception) {}
        chatEngine = null

        try {
            paramEngine?.release()
        } catch (e: Exception) {}
        paramEngine = null
    }

    fun isModelDownloaded(context: Context, modelId: String) =
        RealModelDownloader.isModelFileDownloaded(context, ModelCatalog.getModelById(modelId))

    fun markModelAsDownloaded(context: Context, modelId: String) {
        val downloaded = getPrefs(context).getStringSet(KEY_DOWNLOADED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        downloaded.add(modelId)
        getPrefs(context).edit().putStringSet(KEY_DOWNLOADED_MODELS, downloaded).apply()
    }

    fun clearModelDownloaded(context: Context, modelId: String) {
        val downloaded = getPrefs(context).getStringSet(KEY_DOWNLOADED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        downloaded.remove(modelId)
        getPrefs(context).edit().putStringSet(KEY_DOWNLOADED_MODELS, downloaded).apply()
    }

    fun getActiveModelFilePath(context: Context): String {
        val file = RealModelDownloader.getModelFile(context, getSelectedModel(context))
        return if (file.exists() && file.length() > 0) file.absolutePath else "No model file available"
    }

    fun buildSystemPrompt(candidateExperiences: List<MemoryItem>): String =
        MemossistPromptBuilder.buildFallbackFullPrompt(candidateExperiences)

    fun processMessagePipeline(
        context: Context,
        userMessage: String,
        candidateExperiences: List<MemoryItem>,
        classificationResult: ClassificationResult? = null,
        onTokenGenerated: ((String) -> Unit)? = null
    ): LlmPipelineResult {
        val overallStartTime = System.currentTimeMillis()
        val classification = classificationResult ?: MessageAnalyzer.analyze(context, userMessage)

        val promptStartTime = System.currentTimeMillis()
        val systemPromptStr = MemossistPromptBuilder.build(classification, candidateExperiences)
        val promptBuildTime = System.currentTimeMillis() - promptStartTime

        val legacyPrompt = MemossistPromptBuilder.buildFallbackFullPrompt(candidateExperiences)
        val promptTokenCount = MemossistPromptBuilder.estimateTokenCount(systemPromptStr)
        val legacyTokenCount = MemossistPromptBuilder.estimateTokenCount(legacyPrompt)

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
                extractedReminderTag = null,
                intent = classification.intent,
                modelName = model.name,
                messageType = classification.messageType,
                classificationResult = classification,
                systemPromptUsed = systemPromptStr,
                promptTokenCount = promptTokenCount,
                legacyTokenCount = legacyTokenCount,
                promptBuildTimeMs = promptBuildTime,
                inferenceTimeMs = 0L,
                totalPipelineTimeMs = System.currentTimeMillis() - overallStartTime
            )
        }

        val inferenceStartTime = System.currentTimeMillis()
        val raw = try {
            val engine = chatEngine ?: InferenceEngineImpl(context.applicationContext).also { chatEngine = it }
            engine.generate(
                modelPath = modelFile.absolutePath,
                systemPrompt = systemPromptStr,
                userMessage = userMessage,
                onTokenGenerated = onTokenGenerated
            )
        } catch (e: Exception) {
            android.util.Log.e("NoeonAiEngine", "Offline GGUF inference failed", e)
            "[LLM Engine Exception: ${e.message ?: e.toString()}]"
        }
        val inferenceDuration = System.currentTimeMillis() - inferenceStartTime

        val candidateIdsByCanonicalValue = candidateExperiences.associateBy { it.id.trim().lowercase() }
        val candidateIds = candidateIdsByCanonicalValue.keys
        val usedExperienceTag = parseTagValue(raw, "USED_EXPERIENCES")?.trim()
        val modelExplicitlyUsedNoExperiences = usedExperienceTag.equals("NONE", ignoreCase = true)
        var usedIds = (usedExperienceTag ?: "")
            .split(",")
            .mapNotNull { candidateIdsByCanonicalValue[it.trim().lowercase()]?.id }
            .distinct()

        if (usedIds.isEmpty() && !modelExplicitlyUsedNoExperiences && candidateIds.isNotEmpty()) {
            usedIds = candidateExperiences
                .filter { raw.contains(it.id, ignoreCase = true) }
                .map { it.id }
        }

        val cleanAnswer = parseAnswer(raw)
        // Recover attribution only from a malformed or missing header. Never
        // override the model's explicit NONE decision.
        if (usedIds.isEmpty() && !modelExplicitlyUsedNoExperiences && candidateExperiences.isNotEmpty()) {
            usedIds = inferUsedExperienceIdsFromAnswer(cleanAnswer, candidateExperiences)
        }
        // Validate against the complete vault so a paraphrased or repeated
        // statement cannot create another experience just because it was not
        // among the small set of retrieval candidates.
        val existingExperiences = MemoryVaultRepository.loadAllMemories(context)
        val extractedFacts = parseFacts(parseTagValue(raw, "EXTRACTED_FACTS"), userMessage, existingExperiences)
        val extractedReminderTag = parseTagValue(raw, "EXTRACTED_REMINDERS")
        val finalHumanoidAnswer = if (cleanAnswer.isNotBlank()) {
            cleanAnswer
        } else {
            when (classification.messageType) {
                MessageType.REMINDER_ONLY -> "I've noted that and scheduled your reminder!"
                MessageType.REMINDER_AND_TELLING -> "I've noted that down and scheduled your reminder."
                MessageType.REMINDER_AND_ASKING, MessageType.REMINDER_AND_MIXED -> "I've set your reminder and noted your message."
                MessageType.TELLING -> "Got it, I've noted that down for you!"
                else -> "I understand and have noted your message."
            }
        }
        val intent = (parseTagValue(raw, "INTENT") ?: classification.intent).uppercase()

        return LlmPipelineResult(
            cleanHumanoidAnswer = finalHumanoidAnswer,
            relevantExperienceIds = usedIds,
            extractedInformativeFacts = extractedFacts,
            extractedReminderTag = extractedReminderTag,
            intent = intent,
            modelName = model.name,
            messageType = classification.messageType,
            classificationResult = classification,
            systemPromptUsed = systemPromptStr,
            promptTokenCount = promptTokenCount,
            legacyTokenCount = legacyTokenCount,
            promptBuildTimeMs = promptBuildTime,
            inferenceTimeMs = inferenceDuration,
            totalPipelineTimeMs = System.currentTimeMillis() - overallStartTime
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
        val startMarker = "[$tagName:"
        val startIndex = content.indexOf(startMarker, ignoreCase = true)
        if (startIndex == -1) return null

        val tagContentStart = startIndex + startMarker.length
        val remaining = content.substring(tagContentStart)

        // Find boundary of this tag (before subsequent known tags or newline)
        val nextTagRegex = Regex("(?:\\[(?:HUMANOID_ANSWER|USED_EXPERIENCES|EXTRACTED_FACTS|EXTRACTED_REMINDERS|INTENT))", RegexOption.IGNORE_CASE)
        val nextTagMatch = nextTagRegex.find(remaining)
        val rawTagBody = if (nextTagMatch != null) {
            remaining.substring(0, nextTagMatch.range.first).trim()
        } else {
            val endLineIdx = remaining.indexOf('\n')
            if (endLineIdx != -1) remaining.substring(0, endLineIdx).trim() else remaining.trim()
        }

        val trimmed = rawTagBody.trim()
        return if (trimmed.endsWith("]")) {
            // Check if it's an array e.g. [{"title":"..."}] followed by the outer tag bracket ']'
            if (trimmed.startsWith("[") && trimmed.count { it == '[' } < trimmed.count { it == ']' }) {
                trimmed.removeSuffix("]").trim()
            } else if (!trimmed.startsWith("[")) {
                trimmed.removeSuffix("]").trim()
            } else {
                trimmed
            }
        } else {
            trimmed
        }
    }

    private fun parseAnswer(content: String): String {
        if (content.isBlank()) return ""
        val marker = Regex("\\[HUMANOID_ANSWER\\]", RegexOption.IGNORE_CASE).find(content)
        if (marker != null) {
            return content.substring(marker.range.last + 1)
                .trim()
                .trimStart(']', '[', ':', '|', ',', ';')
                .trim()
        }
        // If [HUMANOID_ANSWER] tag was missing, strip metadata tags from anywhere in the response body
        return content
            .replace(Regex("\\[INTENT\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[EXTRACTED_REMINDERS\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[EXTRACTED_FACTS\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[USED_EXPERIENCES\\s*:[^\\]]*\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[HUMANOID_ANSWER\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^[\\s\\]\\[|,:;.!?`*_~-]+"), "")
            .trim()
    }

    /**
     * Recovers used-memory attribution from a malformed model response. This is
     * intentionally a fallback: well-formed [USED_EXPERIENCES] metadata always
     * takes priority.
     */
    private fun inferUsedExperienceIdsFromAnswer(answer: String, candidates: List<MemoryItem>): List<String> {
        val answerWords = meaningfulWords(answer)
        if (answerWords.isEmpty()) return emptyList()

        return candidates.filter { candidate ->
            val candidateWords = meaningfulWords("${candidate.title} ${candidate.message}")
            val overlap = answerWords.intersect(candidateWords)
            overlap.size >= 2 || candidate.title.length >= 8 && answer.contains(candidate.title, ignoreCase = true)
        }.map { it.id }
    }

    private fun meaningfulWords(text: String): Set<String> {
        val ignoredWords = setOf(
            "about", "additional", "also", "and", "are", "been", "being", "but", "for", "from",
            "have", "here", "information", "into", "just", "that", "the", "there", "this", "they",
            "today", "tomorrow", "with", "will", "your", "you", "does", "what", "when", "where"
        )
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in ignoredWords }
            .toSet()
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

    private fun isUserDeclaredFact(fact: String, userMessage: String): Boolean {
        if (userMessage.isBlank() || fact.isBlank()) return false

        val normUser = userMessage.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val normFact = fact.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")

        val userWords = normUser.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        val factWords = normFact.split("\\s+".toRegex()).filter { it.length > 1 }

        if (factWords.isEmpty()) return false

        val stopWords = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "am",
            "i", "me", "my", "myself", "we", "our", "ours", "you", "your", "yours", "he", "him", "his", "she", "her", "they", "them", "their", "user", "im",
            "to", "of", "for", "in", "on", "at", "by", "with", "about", "against",
            "between", "into", "through", "during", "before", "after", "above", "below",
            "from", "up", "down", "out", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all",
            "any", "both", "each", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "s", "t", "can", "will", "just", "don", "should", "now", "stands", "means"
        )

        val significantFactWords = factWords.filter { word ->
            word !in stopWords && (word.length >= 3 || word.any { it.isDigit() })
        }

        if (significantFactWords.isEmpty()) {
            return normUser.contains(normFact)
        }

        val matchedCount = significantFactWords.count { factWord ->
            userWords.any { userWord ->
                userWord == factWord || userWord.contains(factWord) || factWord.contains(userWord)
            }
        }

        val matchRatio = matchedCount.toDouble() / significantFactWords.size.toDouble()
        return matchRatio >= 0.75
    }

    /**
     * A high word overlap alone is not enough to make a memory. This gate
     * rejects keyword fragments such as "auto immune disease" while allowing
     * explicit, complete first-person statements.
     */
    private fun isCompletePersonalStatement(fact: String): Boolean {
        val words = fact.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (words.size < 3) return false

        val hasPersonalSubject = words.any {
            it in setOf("i", "i'm", "im", "i've", "ive", "i'll", "ill", "my", "we", "we're", "were", "we've", "weve", "our", "you", "your")
        }
        if (!hasPersonalSubject) return false

        val finiteVerbs = setOf(
            "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "can", "could", "will", "would", "should", "must", "might",
            "feel", "feels", "felt", "think", "thinks", "believe", "believes", "know", "knows",
            "like", "likes", "love", "loves", "prefer", "prefers", "enjoy", "enjoys", "want", "wants",
            "need", "needs", "work", "works", "study", "studies", "live", "lives", "use", "uses",
            "own", "owns", "attend", "attends", "suffer", "suffers", "suffered", "experience", "experiences",
            "get", "gets", "got", "take", "takes", "took", "make", "makes", "made", "go", "goes", "went",
            "born", "diagnosed", "allergic", "married", "graduated", "joined", "moved", "started", "became"
        )
        return words.any { it in finiteVerbs || it.endsWith("ing") }
    }

    private fun cleanSourceStatement(statement: String): String? {
        var cleaned = statement.trim().trim('"', '\'', ' ', '\t', '\n')
        cleaned = cleaned.replace(Regex("(?i)^(?:bro|hey|well|so),\\s+"), "")
        cleaned = cleaned.replace(
            Regex("(?i)^(?:please\\s+)?(?:remember|note|save|store)\\s+(?:this\\s*[:,]?\\s*)?(?:that\\s+)?"),
            ""
        )
        cleaned = cleaned.replace(
            Regex("(?i)^i\\s+want\\s+you\\s+to\\s+remember\\s+(?:that\\s+)?"),
            ""
        )
        cleaned = cleaned.trim().trim(',', ';', ':')
        if (cleaned.isBlank() || isQuestionText(cleaned) || !isCompletePersonalStatement(cleaned)) return null
        if (!cleaned.endsWith(".") && !cleaned.endsWith("!") && !cleaned.endsWith("?")) cleaned += "."
        return cleaned
    }

    /** Groups adjacent statements that describe one experience before saving. */
    private fun groupRelatedStatements(statements: List<String>): List<String> {
        if (statements.size < 2) return statements
        val groups = mutableListOf<String>()
        for (statement in statements) {
            val previous = groups.lastOrNull()
            if (previous != null && describeSameContext(previous, statement)) {
                groups[groups.lastIndex] = "$previous $statement"
            } else {
                groups.add(statement)
            }
        }
        return groups
    }

    private fun describeSameContext(first: String, second: String): Boolean {
        if (hasHealthContext(first) && hasHealthContext(second)) return true

        val firstWords = contextWords(first)
        val secondWords = contextWords(second)
        if (firstWords.intersect(secondWords).size >= 2) return true

        val isReaction = Regex(
            "(?i)^\\s*(?:i feel|i felt|it feels|this feels|that feels|it makes me|this makes me|that makes me|it helps me|this helps me|i find it)\\b"
        ).containsMatchIn(second)
        val firstDescribesActivity = Regex(
            "(?i)\\b(watch\\w*|view\\w*|listen\\w*|play\\w*|read\\w*|cook\\w*|dance\\w*|sing\\w*|visit\\w*|stud\\w*|work\\w*|habit)\\b"
        ).containsMatchIn(first)
        return isReaction && firstDescribesActivity
    }

    private fun hasHealthContext(text: String): Boolean = Regex(
        "\\b(health|disease|illness|pain|inflamm\\w*|symptom|medical|doctor|joint|autoimmune|medicine|injur\\w*)\\b",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(text)

    private fun contextWords(text: String): Set<String> {
        val generic = setOf("have", "has", "had", "feel", "felt", "every", "much", "many", "very", "often", "daily", "today", "yesterday", "tomorrow")
        return text.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in generic && it !in setOf("that", "this", "with", "from", "your", "about", "into", "when", "where", "what", "which", "while") }
            .toSet()
    }

    private fun splitExplicitContextClauses(sentence: String): List<String> {
        val boundary = Regex(
            "(?i)\\s+(?:and|but|while)\\s+(?=(?:i(?:'m|'ve|'ll)?|my|we(?:'re|'ve)?|our|you|your)\\b)"
        )
        return sentence.split(boundary).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun isMemoryInstruction(text: String): Boolean = Regex(
        "(?i)^\\s*(?:(?:please\\s+)?(?:remember|note|save|store|keep\\s+in\\s+mind)\\b|i\\s+want\\s+you\\s+to\\s+remember\\b)"
    ).containsMatchIn(text)

    /** A sentence that refers back to the immediately preceding context belongs with it. */
    private fun isContextContinuation(text: String): Boolean = Regex(
        "(?i)^\\s*(?:(?:and|so|because|which|while)\\b|(?:this|that|it|these|those)\\b)"
    ).containsMatchIn(text)

    /** Rebuilds list fragments under a context heading into one full experience. */
    private fun extractSourceExperiences(userMessage: String): List<String> {
        val segments = SentenceSegmenter.segment(userMessage)
        val experiences = mutableListOf<String>()
        var index = 0

        while (index < segments.size) {
            val rawHeading = segments[index].trim()
            val heading = cleanSourceStatement(rawHeading)
            if (rawHeading.endsWith(":") && heading != null) {
                val details = mutableListOf<String>()
                var cursor = index + 1
                var markerPending = false

                while (cursor < segments.size) {
                    val part = segments[cursor].trim()
                    if (part.isBlank() || isMemoryInstruction(part) || isQuestionText(part)) break

                    if (Regex("^\\d+\\s*[.)]$").matches(part)) {
                        markerPending = true
                        cursor++
                        continue
                    }

                    val prefixedItem = Regex("^(?:\\d+\\s*[.)]|[-*•])\\s*(.*)$").find(part)
                    if (prefixedItem != null) {
                        val content = prefixedItem.groupValues[1].trim()
                        if (content.isNotBlank()) details.add(content.trimEnd('.', '!', '?', ',', ';'))
                        markerPending = content.isBlank()
                        cursor++
                        continue
                    }

                    if (markerPending || !isCompletePersonalStatement(part)) {
                        details.add(part.trimStart('-', '*', '•').trim().trimEnd('.', '!', '?', ',', ';'))
                        markerPending = false
                        cursor++
                    } else {
                        break
                    }
                }

                if (details.isNotEmpty()) {
                    val context = heading.trimEnd('.', '!', '?')
                    experiences.add("$context: ${details.joinToString(", ")}.")
                    index = cursor
                    continue
                }
            }

            for (clause in splitExplicitContextClauses(rawHeading)) {
                val statement = cleanSourceStatement(clause)
                if (statement != null) {
                    experiences.add(statement)
                } else if (experiences.isNotEmpty() && isContextContinuation(clause)) {
                    // Preserve complete follow-up details even when the sentence uses a
                    // pronoun instead of repeating a first-person subject.
                    val continuation = clause.trim().trim('"', '\'', ' ', '\t', '\n')
                    if (continuation.isNotBlank() && !isQuestionText(continuation)) {
                        val completeContinuation = if (
                            continuation.endsWith(".") || continuation.endsWith("!") || continuation.endsWith("?")
                        ) continuation else "$continuation."
                        experiences[experiences.lastIndex] = "${experiences.last()} $completeContinuation"
                    }
                }
            }
            index++
        }

        return experiences.distinctBy { it.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() }
    }

    private fun parseFacts(
        value: String?,
        userMessage: String = "",
        candidateExperiences: List<MemoryItem> = emptyList()
    ): List<String> {
        val intent = inferIntent(userMessage)

        val clauses = userMessage.split(Regex("(?<=[.!?\\n])|\\b(and|also|plus)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim().trim(',', ';', '.', '!', '?') }
            .filter { it.isNotBlank() && it.length >= 4 }
        val hasStatement = clauses.any { !isQuestionText(it) }

        // Pure question with no user statements -> ZERO facts declared by user!
        if (intent == "ASKING" && !hasStatement) {
            return emptyList()
        }

        val rawList = mutableListOf<String>()

        if (!value.isNullOrBlank()) {
            val trimmedVal = value.trim()
            val cleanedVal = trimmedVal.removeSurrounding("[", "]").removeSurrounding("[", "]").trim()

            // 1. Try standard JSONArray parsing
            try {
                val array = JSONArray("[$cleanedVal]")
                for (i in 0 until array.length()) {
                    val item = array.optString(i).trim().trim('"', '\'')
                    if (item.isNotBlank() && !item.equals("NONE", true) && item != "[]") {
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

        // Filter out facts that:
        // 1. Are questions or invalid
        // 2. Were NOT declared by the user in userMessage
        // 3. Duplicate existing candidate experiences
        val validatedModelFacts = rawList.map { it.trim('"', '\'', '[', ']', ' ', '\t', '\n') }
            .mapNotNull { rawFact ->
                if (rawFact.length < 3 || rawFact.equals("NONE", true)) return@mapNotNull null
                if (isQuestionText(rawFact) || !isCompletePersonalStatement(rawFact)) return@mapNotNull null
                val fact = if (rawFact.endsWith(".") || rawFact.endsWith("!") || rawFact.endsWith("?")) rawFact else "$rawFact."
                if (!isUserDeclaredFact(fact, userMessage)) return@mapNotNull null
                if (isDuplicateExperience(fact, candidateExperiences)) return@mapNotNull null
                fact
            }

        // Use the user's own complete statements as the canonical memories.
        // This works even when a small local model returns [] or fragments,
        // and groups related source statements into one experience.
        val sourceFacts = groupRelatedStatements(extractSourceExperiences(userMessage))
            .filterNot { experience ->
                val componentStatements = SentenceSegmenter.segment(experience)
                isDuplicateExperience(experience, candidateExperiences) ||
                    (componentStatements.size > 1 && componentStatements.all {
                        isDuplicateExperience(it, candidateExperiences)
                    })
            }

        return sourceFacts.ifEmpty {
            validatedModelFacts.distinctBy { it.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() }
        }
    }

    fun evaluateFactBatchParameters(context: Context, facts: List<FactForEvaluation>): String {
        val model = getSelectedModel(context)
        val modelFile = RealModelDownloader.getModelFile(context, model)
        if (!modelFile.exists() || modelFile.length() < 10 * 1024 * 1024) {
            return ""
        }
        val systemPrompt = MemoryParameterEvaluator.buildBatchSystemPrompt()
        val userPrompt = MemoryParameterEvaluator.buildBatchUserPrompt(facts)
        return try {
            val engine = paramEngine ?: InferenceEngineImpl(context.applicationContext).also { paramEngine = it }
            engine.generate(
                modelPath = modelFile.absolutePath,
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                maxTokens = (facts.size * 65).coerceIn(80, 512),
                contextSize = 512,
                stopCondition = { text ->
                    text.contains("]") && text.count { it == '}' } >= facts.size
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("NoeonAiEngine", "Memory batch parameter inference failed", e)
            ""
        }
    }

    fun evaluateFactParameters(context: Context, fact: String): String {
        val model = getSelectedModel(context)
        val modelFile = RealModelDownloader.getModelFile(context, model)
        if (!modelFile.exists() || modelFile.length() < 10 * 1024 * 1024) {
            return ""
        }
        val systemPrompt = MemoryParameterEvaluator.buildSystemPrompt()
        val userPrompt = MemoryParameterEvaluator.buildUserPrompt(fact)
        return try {
            val engine = paramEngine ?: InferenceEngineImpl(context.applicationContext).also { paramEngine = it }
            engine.generate(
                modelPath = modelFile.absolutePath,
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                maxTokens = 40,
                contextSize = 512,
                stopPattern = Regex("\\}")
            )
        } catch (e: Exception) {
            android.util.Log.e("NoeonAiEngine", "Memory parameter inference failed", e)
            ""
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
