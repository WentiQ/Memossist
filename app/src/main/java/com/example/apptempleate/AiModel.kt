package com.example.apptempleate

data class AiModel(
    val id: String,
    val name: String,
    val tagline: String,
    val badge: String,
    val isRecommended: Boolean = false,
    val icon: String,
    val description: String,
    val parameters: String,
    val contextWindow: String,
    val speedRating: Int, // 1 to 5
    val intelligenceRating: Int, // 1 to 5
    val memoryRating: Int, // 1 to 10 scale (higher = requires more RAM)
    val reasoningRating: Int, // 1 to 10
    val codingRating: Int, // 1 to 10
    val longContextRating: Int, // 1 to 10
    val categoryTags: List<String>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendedFor: List<String>,
    val notIdealFor: List<String>,
    val downloadSizeMb: Int,
    val ramRequiredGb: Float,
    val minDeviceRamGb: Int,
    val downloadUrl: String,
    val fileName: String
)
