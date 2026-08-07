package com.example.apptempleate

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
