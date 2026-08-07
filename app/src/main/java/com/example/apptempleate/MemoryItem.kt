package com.example.apptempleate

data class MemoryItem(
    val id: String,
    val title: String,
    val snippet: String,
    val message: String,
    val timestamp: String,
    val location: String,
    val tag: String = "Chat",
    val timeAgo: String = "Just now",
    val isPinned: Boolean = false
)
