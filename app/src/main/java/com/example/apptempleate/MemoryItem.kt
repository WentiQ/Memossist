package com.example.apptempleate

data class MemoryItem(
    val id: String,
    val title: String,
    val snippet: String,
    val tag: String,
    val timeAgo: String,
    val isPinned: Boolean = false
)
