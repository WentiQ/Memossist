package com.example.apptempleate

import android.app.ActivityManager
import android.content.Context

object ModelCatalog {

    val models = listOf(
        AiModel(
            id = "qwen3.5_0.8b",
            name = "Qwen3.5 0.8B",
            tagline = "Ultra Fast",
            badge = "ULTRA FAST",
            isRecommended = false,
            icon = "🪶",
            description = "A tiny AI designed for speed and efficiency. Great for quick questions, text processing, classification, simple summaries and everyday commands.",
            parameters = "~0.8B",
            contextWindow = "Large Context",
            speedRating = 5,
            intelligenceRating = 3,
            memoryRating = 2,
            reasoningRating = 4,
            codingRating = 3,
            longContextRating = 6,
            categoryTags = listOf("Fastest", "Low Memory"),
            strengths = listOf(
                "⚡ Extremely fast response times",
                "🔋 Low battery usage",
                "📱 Works smoothly on lower-spec phones",
                "💾 Very small storage footprint",
                "📴 Completely offline operation"
            ),
            weaknesses = listOf(
                "Less capable at complex multi-step reasoning",
                "Less reliable for difficult coding or math",
                "Shorter effective conversational complexity"
            ),
            recommendedFor = listOf(
                "Low-end devices",
                "Quick voice & text commands",
                "Background AI processing",
                "Simple note summaries"
            ),
            notIdealFor = listOf(
                "Complex programming or engineering",
                "Deep mathematical proofs",
                "Large multi-document analysis"
            ),
            downloadSizeMb = 469,
            ramRequiredGb = 1.2f,
            minDeviceRamGb = 2,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen3.5_0.8b.gguf"
        ),
        AiModel(
            id = "qwen3.5_2b",
            name = "Qwen3.5 2B",
            tagline = "Everyday AI",
            badge = "FAST & BALANCED",
            isRecommended = false,
            icon = "⚡",
            description = "A balanced lightweight model for everyday conversations, summarization, rewriting, knowledge extraction and general assistance.",
            parameters = "~2B",
            contextWindow = "Large Context",
            speedRating = 5,
            intelligenceRating = 4,
            memoryRating = 4,
            reasoningRating = 7,
            codingRating = 6,
            longContextRating = 7,
            categoryTags = listOf("Fastest", "Low Memory"),
            strengths = listOf(
                "⚡ Very fast generation speed",
                "💾 Low memory requirements",
                "🧠 Better reasoning than 0.8B model",
                "📝 Good general-purpose capability",
                "📱 Excellent optimization for smartphones"
            ),
            weaknesses = listOf(
                "May struggle with advanced technical code",
                "Slightly lower depth than 4B parameters"
            ),
            recommendedFor = listOf(
                "Daily conversations & chat",
                "Note summarization & rewriting",
                "Knowledge extraction",
                "Personal daily assistant"
            ),
            notIdealFor = listOf(
                "Heavy full-stack software development",
                "Complex scientific problem solving"
            ),
            downloadSizeMb = 1066,
            ramRequiredGb = 2.5f,
            minDeviceRamGb = 4,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen3.5_2b.gguf"
        ),
        AiModel(
            id = "qwen3.5_4b",
            name = "Qwen3.5 4B",
            tagline = "Best Overall",
            badge = "🏆 RECOMMENDED",
            isRecommended = true,
            icon = "🧠",
            description = "A powerful compact AI that provides an excellent balance between intelligence, speed and memory usage. Recommended for users who want a capable general-purpose local assistant.",
            parameters = "~4B",
            contextWindow = "Large Context",
            speedRating = 4,
            intelligenceRating = 5,
            memoryRating = 6,
            reasoningRating = 9,
            codingRating = 9,
            longContextRating = 9,
            categoryTags = listOf("Smartest", "Coding", "Best Overall"),
            strengths = listOf(
                "🧠 Strong reasoning & problem solving",
                "💻 Good coding and script generation",
                "📚 Good document analysis & synthesis",
                "✍️ Strong creative and formal writing",
                "⚡ Practical speed on modern smartphones",
                "🔒 100% offline & private"
            ),
            weaknesses = listOf(
                "Requires more RAM than 0.8B/2B",
                "Slower token generation speed than 0.8B",
                "Higher battery consumption during long tasks"
            ),
            recommendedFor = listOf(
                "General AI assistance",
                "Coding & technical tasks",
                "Studying & research assistance",
                "Document analysis",
                "Personal memory vault companion"
            ),
            notIdealFor = listOf(
                "Very low-end budget smartphones",
                "Users seeking absolute minimum battery usage"
            ),
            downloadSizeMb = 2008,
            ramRequiredGb = 4.5f,
            minDeviceRamGb = 6,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "qwen3.5_4b.gguf"
        ),
        AiModel(
            id = "gemma_3n_e2b",
            name = "Gemma 3n E2B",
            tagline = "Mobile Multimodal",
            badge = "MULTIMODAL",
            isRecommended = false,
            icon = "👁️",
            description = "A mobile-optimized AI designed by Google for efficient on-device interaction with text, visual inputs, and audio information.",
            parameters = "~2B active",
            contextWindow = "Large Context",
            speedRating = 5,
            intelligenceRating = 4,
            memoryRating = 5,
            reasoningRating = 7,
            codingRating = 6,
            longContextRating = 7,
            categoryTags = listOf("Multimodal", "Fastest"),
            strengths = listOf(
                "📱 Custom-built for mobile silicon",
                "👁️ Native multimodal visual & voice processing",
                "⚡ Highly efficient execution engine",
                "🔋 Lower resource & battery footprint",
                "📴 Offline image & speech comprehension"
            ),
            weaknesses = listOf(
                "Lower text-only reasoning than 4B class models",
                "Requires image preprocessing pipeline"
            ),
            recommendedFor = listOf(
                "Camera & visual understanding AI",
                "Voice assistant & speech tasks",
                "Mobile memories with photos",
                "Fast multimodal mobile workflows"
            ),
            notIdealFor = listOf(
                "Deep text-only mathematical logic"
            ),
            downloadSizeMb = 1630,
            ramRequiredGb = 2.8f,
            minDeviceRamGb = 4,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            fileName = "gemma_3n_e2b.gguf"
        ),
        AiModel(
            id = "gemma_3n_e4b",
            name = "Gemma 3n E4B",
            tagline = "Advanced Multimodal",
            badge = "ADVANCED MULTIMODAL",
            isRecommended = false,
            icon = "👁️🧠",
            description = "A more capable mobile AI for users who want stronger reasoning together with multimodal visual and voice interaction.",
            parameters = "~4B active",
            contextWindow = "Large Context",
            speedRating = 4,
            intelligenceRating = 5,
            memoryRating = 7,
            reasoningRating = 9,
            codingRating = 8,
            longContextRating = 8,
            categoryTags = listOf("Multimodal", "Smartest"),
            strengths = listOf(
                "🖼️ High-precision image & document understanding",
                "🎙️ Seamless voice & audio context interaction",
                "🧠 Superior multimodal reasoning capacity",
                "📚 Comprehensive document analysis"
            ),
            weaknesses = listOf(
                "Requires at least 6 GB device RAM",
                "Moderate battery drain on active generation"
            ),
            recommendedFor = listOf(
                "Image & screenshot understanding",
                "Interactive voice assistant",
                "Document OCR & diagram analysis",
                "Complex multimodal conversations"
            ),
            notIdealFor = listOf(
                "Devices with less than 6 GB RAM"
            ),
            downloadSizeMb = 2052,
            ramRequiredGb = 5.0f,
            minDeviceRamGb = 8,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q6_K.gguf",
            fileName = "gemma_3n_e4b.gguf"
        ),
        AiModel(
            id = "phi_4_mini",
            name = "Phi-4-mini",
            tagline = "Reasoning Specialist",
            badge = "REASONING",
            isRecommended = false,
            icon = "🧮",
            description = "A compact reasoning-focused AI designed for mathematics, logic, structured tasks, coding and complex instructions.",
            parameters = "3.8B",
            contextWindow = "128K Context",
            speedRating = 4,
            intelligenceRating = 5,
            memoryRating = 6,
            reasoningRating = 10,
            codingRating = 10,
            longContextRating = 9,
            categoryTags = listOf("Reasoning", "Coding", "Smartest"),
            strengths = listOf(
                "🧮 Advanced mathematical accuracy",
                "🧠 High-density logical reasoning",
                "💻 Superior code generation & debugging",
                "📊 Structured JSON & schema output",
                "📚 Massive 128K context window capacity",
                "📱 Mobile CPU/ONNX optimized variant"
            ),
            weaknesses = listOf(
                "Requires careful prompt formatting",
                "Focuses heavily on logic over casual chit-chat"
            ),
            recommendedFor = listOf(
                "Engineering & software development",
                "Mathematics & scientific formulas",
                "Complex logic problem solving",
                "Technical instruction execution"
            ),
            notIdealFor = listOf(
                "Casual light conversation",
                "Low-RAM phones (< 6 GB)"
            ),
            downloadSizeMb = 2283,
            ramRequiredGb = 4.2f,
            minDeviceRamGb = 6,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            fileName = "phi_4_mini.gguf"
        ),
        AiModel(
            id = "smollm3_3b",
            name = "SmolLM3 3B",
            tagline = "Long Context",
            badge = "LONG CONTEXT",
            isRecommended = false,
            icon = "📚",
            description = "A compact reasoning model designed to handle very large conversations and documents while maintaining a relatively small memory footprint.",
            parameters = "3B",
            contextWindow = "128K Context",
            speedRating = 4,
            intelligenceRating = 4,
            memoryRating = 5,
            reasoningRating = 8,
            codingRating = 8,
            longContextRating = 10,
            categoryTags = listOf("Long Context", "Reasoning"),
            strengths = listOf(
                "📚 Massive 128K context window capacity",
                "🧠 Built-in hybrid reasoning mode",
                "⚡ Small 3B parameter memory efficiency",
                "🔎 Deep long-document analysis & retrieval",
                "🧩 Efficient Grouped Query Attention (GQA)"
            ),
            weaknesses = listOf(
                "Slightly slower when filling high context lengths"
            ),
            recommendedFor = listOf(
                "Large PDF & book analysis",
                "Academic research & long notes",
                "Extensive conversational history",
                "Deep memory vault indexing"
            ),
            notIdealFor = listOf(
                "Short 1-liner voice commands"
            ),
            downloadSizeMb = 1007,
            ramRequiredGb = 3.8f,
            minDeviceRamGb = 6,
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            fileName = "smollm3_3b.gguf"
        ),
        AiModel(
            id = "lfm2",
            name = "LFM2",
            tagline = "Speed Specialist",
            badge = "LIGHTNING FAST",
            isRecommended = false,
            icon = "🚀",
            description = "A compact architecture optimized for low-latency local inference. Ideal when response speed matters more than maximum reasoning ability.",
            parameters = "1.2–2.6B",
            contextWindow = "Moderate",
            speedRating = 5,
            intelligenceRating = 4,
            memoryRating = 3,
            reasoningRating = 6,
            codingRating = 5,
            longContextRating = 5,
            categoryTags = listOf("Fastest", "Low Memory"),
            strengths = listOf(
                "🚀 Instantaneous response latency",
                "🎙️ Perfect for real-time voice conversations",
                "⚡ Ultra-low power CPU usage",
                "📱 Fluid operation on budget Android devices"
            ),
            weaknesses = listOf(
                "Moderate reasoning on complex coding challenges",
                "Shorter context window"
            ),
            recommendedFor = listOf(
                "Instant real-time responses",
                "Live voice assistant loops",
                "Autocomplete & smart reply",
                "Background data processing"
            ),
            notIdealFor = listOf(
                "Large document synthesis",
                "Advanced mathematical proofs"
            ),
            downloadSizeMb = 771,
            ramRequiredGb = 1.8f,
            minDeviceRamGb = 3,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "lfm2.gguf"
        )
    )

    fun getModelById(id: String): AiModel {
        return models.find { it.id == id } ?: models[2] // Default to Qwen3.5 4B
    }

    /**
     * Reads total physical RAM of the device in GB
     */
    fun getTotalDeviceRamGb(context: Context): Float {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalBytes = memInfo.totalMem
            (totalBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
        } catch (e: Exception) {
            8.0f // Fallback standard
        }
    }

    /**
     * Computes automatic model recommendation based on hardware RAM
     */
    fun getRecommendedModelForDevice(context: Context): AiModel {
        val ramGb = getTotalDeviceRamGb(context)
        return when {
            ramGb < 3.5f -> models.find { it.id == "qwen3.5_0.8b" } ?: models[0]
            ramGb in 3.5f..5.5f -> models.find { it.id == "qwen3.5_2b" } ?: models[1]
            ramGb in 5.5f..9.5f -> models.find { it.id == "qwen3.5_4b" } ?: models[2]
            else -> models.find { it.id == "gemma_3n_e4b" } ?: models[4]
        }
    }
}
