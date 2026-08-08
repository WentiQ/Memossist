package com.example.apptempleate

import org.json.JSONArray
import org.json.JSONObject

data class WordSynonymItem(
    val word: String,
    val synonyms: List<String>
)

object LinguisticAnalyzer {

    // Non-content function words to exclude (pronouns, prepositions, conjunctions, determiners, auxiliary verbs, quantifiers, fillers)
    private val NON_CONTENT_WORDS = setOf(
        // Pronouns
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
        "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their",
        "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "whose", "whatever",
        "whoever", "whomever", "whichever", "anything", "something", "nothing", "everything", "anyone", "someone",
        "everyone", "nobody", "noone", "anybody", "somebody", "everybody",

        // Prepositions
        "about", "above", "across", "after", "against", "along", "among", "around", "at", "before", "behind",
        "below", "beneath", "beside", "between", "beyond", "by", "down", "during", "except", "for", "from",
        "in", "inside", "into", "near", "of", "off", "on", "onto", "out", "outside", "over", "past", "since",
        "through", "throughout", "to", "toward", "towards", "under", "underneath", "until", "unto", "up", "upon",
        "with", "within", "without",

        // Conjunctions
        "and", "but", "or", "nor", "so", "for", "yet", "because", "if", "although", "unless", "since", "while",
        "whereas", "wherever", "whenever", "whether", "than",

        // Determiners & Articles & Quantifiers
        "a", "an", "the", "each", "every", "either", "neither", "some", "any", "no", "much", "many", "more",
        "most", "few", "little", "less", "least", "several", "both", "all", "other", "another", "such",

        // Auxiliary Verbs & Modal Verbs
        "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
        "did", "doing", "done", "can", "could", "shall", "should", "will", "would", "may", "might", "must",
        "ought", "let", "let's", "isn't", "aren't", "wasn't", "weren't", "hasn't", "haven't", "hadn't",
        "doesn't", "don't", "didn't", "can't", "cannot", "couldn't", "won't", "wouldn't", "shouldn't",

        // Fillers, Interjections & Conversational Starters
        "just", "now", "then", "there", "here", "here's", "there's", "how", "how's", "why", "why's", "when",
        "when's", "where", "where's", "what's", "who's", "that's", "very", "too", "also", "well", "oh", "ah",
        "hey", "hello", "hi", "please", "okay", "ok", "etc", "yeah", "yes", "nope", "maybe"
    )

    // Comprehensive content vocabulary database (Nouns, Verbs, Adjectives, Adverbs)
    private val SYNONYM_DATABASE = mapOf(
        // Nouns
        "strategy" to listOf("plan", "masterplan", "approach", "scheme", "tactics"),
        "notes" to listOf("memos", "records", "annotations", "jottings", "minutes"),
        "session" to listOf("meeting", "assembly", "gathering", "conference"),
        "architecture" to listOf("design", "structure", "framework", "layout", "blueprint"),
        "memory" to listOf("recollection", "remembrance", "retention", "mind"),
        "retrieval" to listOf("recovery", "fetching", "extraction", "recall"),
        "benchmarks" to listOf("standards", "criteria", "baselines", "touchstones"),
        "flow" to listOf("stream", "current", "movement", "progression"),
        "ideas" to listOf("concepts", "thoughts", "notions", "impressions"),
        "workflow" to listOf("process", "procedure", "pipeline", "sequence"),
        "delegation" to listOf("assignment", "deputation", "empowerment", "devolution"),
        "patterns" to listOf("models", "templates", "designs", "archetypes"),
        "search" to listOf("hunt", "quest", "lookup", "inquiry", "exploration"),
        "background" to listOf("backdrop", "setting", "environment", "context"),
        "product" to listOf("item", "creation", "output", "commodity"),
        "roadmap" to listOf("plan", "timeline", "guide", "blueprint"),
        "transcript" to listOf("record", "text", "copy", "manuscript", "document"),
        "voice" to listOf("speech", "vocalization", "tone", "expression"),
        "conversation" to listOf("dialogue", "chat", "discourse", "talk", "discussion"),
        "gestures" to listOf("signals", "motions", "indications", "movements"),
        "sidebar" to listOf("panel", "margin", "drawer"),
        "navigation" to listOf("steering", "routing", "guidance", "traversal"),
        "interface" to listOf("boundary", "connection", "link", "medium"),
        "workspace" to listOf("office", "studio", "environment", "workstation"),
        "thought" to listOf("idea", "concept", "notion", "reflection"),
        "code" to listOf("script", "program", "syntax", "source"),
        "system" to listOf("structure", "framework", "network", "mechanism"),
        "future" to listOf("destiny", "prospects", "tomorrow", "ahead"),
        "vision" to listOf("insight", "sight", "perception", "foresight"),
        "speech" to listOf("oratory", "talk", "utterance", "address"),
        "message" to listOf("note", "dispatch", "communication", "memo"),

        // Verbs
        "explored" to listOf("investigated", "examined", "researched", "probed", "surveyed"),
        "rendering" to listOf("depiction", "portrayal", "representation", "translation"),
        "polish" to listOf("refine", "perfect", "buff", "enhance", "glaze"),
        "discussing" to listOf("debating", "conversing", "deliberating", "exchanging"),
        "styling" to listOf("designing", "fashioning", "formatting", "customizing"),
        "analyze" to listOf("examine", "inspect", "evaluate", "scrutinize", "study"),
        "chat" to listOf("talk", "converse", "speak", "dialogue"),
        "create" to listOf("generate", "build", "produce", "make", "construct"),
        "learn" to listOf("master", "grasp", "absorb", "comprehend"),
        "speak" to listOf("talk", "converse", "articulate", "voice"),
        "listen" to listOf("hear", "attend", "hearken", "heed"),
        "remember" to listOf("recall", "recollect", "retain", "mind"),
        "recall" to listOf("remember", "recollect", "retrieve"),
        "send" to listOf("dispatch", "transmit", "forward", "convey"),
        "read" to listOf("scan", "peruse", "study", "examine"),
        "write" to listOf("compose", "draft", "inscribe", "record"),
        "build" to listOf("construct", "assemble", "erect", "create"),
        "design" to listOf("layout", "draft", "pattern", "plan"),

        // Adjectives
        "cognitive" to listOf("mental", "intellectual", "cerebral", "rational"),
        "neural" to listOf("synaptic", "nervous", "cerebral"),
        "agentic" to listOf("autonomous", "self-directed", "proactive"),
        "autonomous" to listOf("independent", "self-governing", "sovereign"),
        "contextual" to listOf("situational", "conditional", "environmental"),
        "tactile" to listOf("tangible", "physical", "touchable", "palpable"),
        "smooth" to listOf("sleek", "seamless", "fluid", "polished"),
        "clean" to listOf("spotless", "neat", "tidy", "pure"),
        "white" to listOf("ivory", "pale", "milky", "snowy"),
        "quick" to listOf("fast", "rapid", "swift", "speedy"),
        "fast" to listOf("quick", "rapid", "swift", "brisk"),
        "beautiful" to listOf("gorgeous", "stunning", "attractive", "lovely"),
        "happy" to listOf("joyful", "cheerful", "delighted", "content"),
        "smart" to listOf("intelligent", "clever", "bright", "sharp"),
        "deep" to listOf("profound", "intense", "thorough"),
        "quantum" to listOf("atomic", "molecular", "particle"),
        "bright" to listOf("luminous", "radiant", "vivid", "brilliant"),
        "strong" to listOf("robust", "sturdy", "powerful", "firm"),
        "weak" to listOf("frail", "delicate", "feeble", "flimsy"),
        "good" to listOf("excellent", "superb", "fine", "great"),
        "great" to listOf("grand", "outstanding", "remarkable", "terrific"),

        // Adverbs
        "swiftly" to listOf("rapidly", "promptly", "quickly", "speedily"),
        "smoothly" to listOf("flawlessly", "seamlessly", "fluidly"),
        "deeply" to listOf("profoundly", "intensely", "thoroughly"),
        "dynamically" to listOf("actively", "energetically", "vividly"),
        "offline" to listOf("locally", "disconnectedly", "autonomously")
    )

    /**
     * Strictly verifies whether a word is a valid content word (Noun, Verb, Adjective, or Adverb).
     * Excludes pronouns, prepositions, conjunctions, determiners, auxiliary verbs, numbers, and stop words.
     */
    fun isContentPosWord(word: String): Boolean {
        val w = word.lowercase().trim()
        if (w.length < 3) return false
        if (!w.all { it.isLetter() }) return false
        if (NON_CONTENT_WORDS.contains(w)) return false
        return true
    }

    /**
     * Extracts ONLY Nouns, Verbs, Adjectives, and Adverbs and their synonyms from text.
     */
    fun extractWordsAndSynonyms(text: String): List<WordSynonymItem> {
        if (text.isBlank()) return emptyList()

        // Extract tokens (alphabetic content words only)
        val tokens = text.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { token ->
                isContentPosWord(token)
            }

        val distinctWords = tokens.distinct()
        val results = mutableListOf<WordSynonymItem>()

        for (word in distinctWords) {
            val synonyms = lookupSynonyms(word).filter { isContentPosWord(it) }
            results.add(WordSynonymItem(word = word, synonyms = synonyms))
        }

        return results
    }

    private fun lookupSynonyms(word: String): List<String> {
        val direct = SYNONYM_DATABASE[word]
        if (direct != null) {
            return direct.filter { isContentPosWord(it) }.take(5)
        }

        // Stem matching (try removing 's', 'ed', 'ing', 'ly')
        val stem = when {
            word.endsWith("ing") && word.length > 5 -> word.substring(0, word.length - 3)
            word.endsWith("ed") && word.length > 4 -> word.substring(0, word.length - 2)
            word.endsWith("es") && word.length > 4 -> word.substring(0, word.length - 2)
            word.endsWith("s") && word.length > 3 -> word.substring(0, word.length - 1)
            word.endsWith("ly") && word.length > 4 -> word.substring(0, word.length - 2)
            else -> word
        }

        val stemMatch = SYNONYM_DATABASE[stem]
        if (stemMatch != null) {
            return stemMatch.filter { isContentPosWord(it) }.take(5)
        }

        return emptyList()
    }

    fun toJsonString(items: List<WordSynonymItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("word", item.word)
            val synArray = JSONArray()
            item.synonyms.forEach { synArray.put(it) }
            obj.put("synonyms", synArray)
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJsonString(jsonStr: String?): List<WordSynonymItem> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<WordSynonymItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val word = obj.getString("word")
                val synArray = obj.getJSONArray("synonyms")
                val synonyms = mutableListOf<String>()
                for (j in 0 until synArray.length()) {
                    val syn = synArray.getString(j)
                    if (isContentPosWord(syn)) synonyms.add(syn)
                }
                if (isContentPosWord(word)) {
                    list.add(WordSynonymItem(word, synonyms))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
