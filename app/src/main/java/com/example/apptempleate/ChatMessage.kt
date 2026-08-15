package com.example.apptempleate

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    var text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var isThinking: Boolean = false,
    var thinkingStatus: String? = null,
    var debugLog: String? = null,
    var attachments: List<MediaAttachment> = emptyList(),
    var createdMemoryIds: List<String> = emptyList(),
    var createdReminderId: String? = null,
    var awaitingTypeConfirmation: Boolean = false,
    var detectedMessageType: MessageType? = null,
    var classificationConfidence: Float = 1.0f,
    var paramEvaluationStatus: String? = null,
    var paramEvaluationText: String? = null
)
