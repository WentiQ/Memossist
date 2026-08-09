package com.example.apptempleate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AttachmentStorageHelper {

    fun saveUriToInternalStorage(context: Context, uri: Uri): MediaAttachment? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "attachment_${System.currentTimeMillis()}"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            if (mimeType == "application/octet-stream") {
                val ext = fileName.substringAfterLast('.', "").lowercase()
                mimeType = when (ext) {
                    "jpg", "jpeg", "png", "webp", "gif" -> "image/$ext"
                    "mp4", "mkv", "webm", "avi" -> "video/$ext"
                    "mp3", "wav", "m4a", "ogg" -> "audio/$ext"
                    "pdf" -> "application/pdf"
                    "txt", "csv", "json" -> "text/plain"
                    "doc", "docx" -> "application/msword"
                    else -> mimeType
                }
            }

            val attachmentsDir = File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
            val destFile = File(attachmentsDir, "${System.currentTimeMillis()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (fileSize == 0L) {
                fileSize = destFile.length()
            }

            MediaAttachment(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                filePath = destFile.absolutePath,
                mimeType = mimeType,
                fileSize = fileSize,
                formattedSize = formatFileSize(fileSize)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openAttachment(context: Context, attachment: MediaAttachment) {
        try {
            val file = File(attachment.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "Attachment file not found", Toast.LENGTH_SHORT).show()
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, attachment.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open ${attachment.fileName}"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
