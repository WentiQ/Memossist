package com.example.apptempleate

import java.util.UUID

data class MediaAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String, // Internal persistent app storage path
    val mimeType: String,
    val fileSize: Long,
    val formattedSize: String
) {
    fun isImage(): Boolean = mimeType.startsWith("image/")
    fun isVideo(): Boolean = mimeType.startsWith("video/")
    fun isAudio(): Boolean = mimeType.startsWith("audio/")
    fun isPdf(): Boolean = mimeType.contains("pdf")
    
    fun getFileExtension(): String {
        return fileName.substringAfterLast('.', "").uppercase()
    }

    fun getTypeIconText(): String {
        return when {
            isImage() -> "🖼️"
            isVideo() -> "🎥"
            isAudio() -> "🎵"
            isPdf() -> "📄"
            else -> "📁"
        }
    }
}
