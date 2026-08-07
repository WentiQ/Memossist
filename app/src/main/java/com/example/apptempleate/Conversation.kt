package com.example.apptempleate

import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var lastUpdated: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val messages: MutableList<ChatMessage> = mutableListOf()
)
