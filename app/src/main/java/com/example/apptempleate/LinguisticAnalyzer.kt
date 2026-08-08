package com.example.apptempleate

import org.json.JSONArray
import org.json.JSONObject

data class WordSynonymItem(
    val word: String,
    val synonyms: List<String>
)

object LinguisticAnalyzer {

    // Common non-content stopwords (pronouns, prepositions, conjunctions, auxiliary verbs, determiners)
    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
        "can", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
        "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers",
        "herself", "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if",
        "in", "into", "is", "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most",
        "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or",
        "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shan't", "she",
        "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that",
        "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these",
        "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too",
        "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've",
        "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while",
        "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd",
        "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves", "just", "now"
    )

    // Comprehensive content vocabulary synonym database
    private val SYNONYM_DATABASE = mapOf(
        "strategy" to listOf("plan", "masterplan", "approach", "scheme", "tactics"),
        "cognitive" to listOf("mental", "intellectual", "cerebral", "rational"),
        "notes" to listOf("memos", "records", "annotations", "jottings", "minutes"),
        "session" to listOf("meeting", "assembly", "gathering", "conference"),
        "neural" to listOf("synaptic", "nervous", "cerebral"),
        "architecture" to listOf("design", "structure", "framework", "layout", "blueprint"),
        "memory" to listOf("recollection", "remembrance", "retention", "mind"),
        "retrieval" to listOf("recovery", "fetching", "extraction", "recall"),
        "benchmarks" to listOf("standards", "criteria", "baselines", "touchstones"),
        "flow" to listOf("stream", "current", "movement", "progression"),
        "ideas" to listOf("concepts", "thoughts", "notions", "impressions"),
        "explored" to listOf("investigated", "examined", "researched", "probed", "surveyed"),
        "agentic" to listOf("autonomous", "self-directed", "proactive"),
        "workflow" to listOf("process", "procedure", "pipeline", "sequence"),
        "delegation" to listOf("assignment", "deputation", "empowerment", "devolution"),
        "patterns" to listOf("models", "templates", "designs", "archetypes"),
        "autonomous" to listOf("independent", "self-governing", "sovereign", "uncontrolled"),
        "contextual" to listOf("situational", "conditional", "environmental"),
        "search" to listOf("hunt", "quest", "lookup", "inquiry", "exploration"),
        "tactile" to listOf("tangible", "physical", "touchable", "palpable"),
        "background" to listOf("backdrop", "setting", "environment", "context"),
        "rendering" to listOf("depiction", "portrayal", "representation", "translation"),
        "product" to listOf("item", "creation", "output", "commodity"),
        "roadmap" to listOf("plan", "timeline", "guide", "blueprint"),
        "polish" to listOf("refine", "perfect", "buff", "enhance", "glaze"),
        "transcript" to listOf("record", "text", "copy", "manuscript", "document"),
        "voice" to listOf("speech", "vocalization", "tone", "expression"),
        "conversation" to listOf("dialogue", "chat", "discourse", "talk", "discussion"),
        "discussing" to listOf("debating", "conversing", "deliberating", "exchanging"),
        "smooth" to listOf("sleek", "seamless", "fluid", "polished", "silky"),
        "swipe" to listOf("slide", "glide", "sweep"),
        "gestures" to listOf("signals", "motions", "indications", "movements"),
        "sidebar" to listOf("panel", "margin", "drawer"),
        "navigation" to listOf("steering", "routing", "guidance", "traversal"),
        "clean" to listOf("spotless", "neat", "tidy", "pure", "immaculate"),
        "white" to listOf("ivory", "pale", "milky", "snowy"),
        "interface" to listOf("boundary", "connection", "link", "medium"),
        "styling" to listOf("designing", "fashioning", "formatting", "customizing"),
        "quick" to listOf("fast", "rapid", "swift", "speedy", "brisk"),
        "beautiful" to listOf("gorgeous", "stunning", "attractive", "lovely", "exquisite"),
        "analyze" to listOf("examine", "inspect", "evaluate", "scrutinize", "study"),
        "workspace" to listOf("office", "studio", "environment", "workstation"),
        "chat" to listOf("talk", "conversation", "discourse", "dialogue"),
        "create" to listOf("generate", "build", "produce", "make", "construct"),
        "happy" to listOf("joyful", "cheerful", "delighted", "content"),
        "thought" to listOf("idea", "concept", "notion", "reflection"),
        "code" to listOf("script", "program", "syntax", "source"),
        "system" to listOf("structure", "framework", "network", "mechanism"),
        "smart" to listOf("intelligent", "clever", "bright", "sharp"),
        "help" to listOf("assist", "aid", "support", "guide"),
        "quantum" to listOf("atomic", "molecular", "particle"),
        "computing" to listOf("calculation", "processing", "reckoning"),
        "learn" to listOf("master", "grasp", "absorb", "comprehend"),
        "future" to listOf("destiny", "prospects", "tomorrow", "ahead"),
        "deep" to listOf("profound", "intense", "thorough", "bottomless"),
        "vision" to listOf("insight", "sight", "perception", "foresight"),
        "speech" to listOf("oratory", "talk", "utterance", "address"),
        "speak" to listOf("talk", "converse", "articulate", "voice"),
        "listen" to listOf("hear", "attend", "hearken", "heed"),
        "hear" to listOf("listen", "perceive", "catch"),
        "remember" to listOf("recall", "recollect", "retain", "mind"),
        "recall" to listOf("remember", "recollect", "retrieve"),
        "message" to listOf("note", "dispatch", "communication", "memo"),
        "send" to listOf("dispatch", "transmit", "forward", "convey"),
        "read" to listOf("scan", "peruse", "study", "examine"),
        "write" to listOf("compose", "draft", "inscribe", "record"),
        "build" to listOf("construct", "assemble", "erect", "create"),
        "design" to listOf("layout", "draft", "pattern", "plan", "format"),
        "fast" to listOf("quick", "rapid", "swift", "brisk"),
        "slow" to listOf("unhurried", "leisurely", "sluggish", "gradual"),
        "bright" to listOf("luminous", "radiant", "vivid", "brilliant"),
        "dark" to listOf("dim", "somber", "shadowy", "gloomy"),
        "strong" to listOf("robust", "sturdy", "powerful", "firm"),
        "weak" to listOf("frail", "delicate", "feeble", "flimsy"),
        "big" to listOf("large", "huge", "massive", "sizeable"),
        "small" to listOf("tiny", "compact", "miniature", "little"),
        "good" to listOf("excellent", "superb", "fine", "great"),
        "great" to listOf("grand", "outstanding", "remarkable", "terrific"),
        "new" to listOf("fresh", "novel", "modern", "recent"),
        "old" to listOf("ancient", "aged", "veteran", "vintage"),
        "first" to listOf("initial", "primary", "premier", "lead"),
        "last" to listOf("final", "ultimate", "concluding", "terminal")
    )

    fun extractWordsAndSynonyms(text: String): List<WordSynonymItem> {
        if (text.isBlank()) return emptyList()

        // Extract tokens (alphabetic words)
        val tokens = text.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { token ->
                token.length >= 3 && !STOP_WORDS.contains(token)
            }

        // Distinct content words in order of appearance
        val distinctWords = tokens.distinct()

        val results = mutableListOf<WordSynonymItem>()

        for (word in distinctWords) {
            val synonyms = lookupSynonyms(word)
            // Store word and its synonyms (even if empty or 1..5)
            results.add(WordSynonymItem(word = word, synonyms = synonyms))
        }

        return results
    }

    private fun lookupSynonyms(word: String): List<String> {
        // Direct match in DB
        val direct = SYNONYM_DATABASE[word]
        if (direct != null) {
            return direct.take(5)
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
            return stemMatch.take(5)
        }

        // If no synonyms found in database for this specific word, return empty list (DO NOT invent fake filler words!)
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
        if (jsonStr.isNull_or_Empty()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<WordSynonymItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val word = obj.getString("word")
                val synArray = obj.getJSONArray("synonyms")
                val synonyms = mutableListOf<String>()
                for (j in 0 until synArray.length()) {
                    synonyms.add(synArray.getString(j))
                }
                list.add(WordSynonymItem(word, synonyms))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String?.isNull_or_Empty(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
