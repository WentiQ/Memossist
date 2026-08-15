package com.example.apptempleate

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class AiMediaAttachmentAdapter(
    private val attachments: List<MediaAttachment>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_VIDEO = 2
        private const val TYPE_AUDIO = 3
        private const val TYPE_DOCUMENT = 4
    }

    override fun getItemViewType(position: Int): Int {
        val item = attachments[position]
        return when {
            item.isImage() -> TYPE_IMAGE
            item.isVideo() -> TYPE_VIDEO
            item.isAudio() -> TYPE_AUDIO
            else -> TYPE_DOCUMENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> ImageViewViewHolder(inflater.inflate(R.layout.item_chat_ai_media_image, parent, false))
            TYPE_VIDEO -> VideoViewHolder(inflater.inflate(R.layout.item_chat_ai_media_video, parent, false))
            TYPE_AUDIO -> AudioViewHolder(inflater.inflate(R.layout.item_chat_ai_media_audio, parent, false))
            else -> DocumentViewHolder(inflater.inflate(R.layout.item_chat_ai_media_doc, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = attachments[position]
        when (holder) {
            is ImageViewViewHolder -> holder.bind(item)
            is VideoViewHolder -> holder.bind(item)
            is AudioViewHolder -> holder.bind(item)
            is DocumentViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = attachments.size

    inner class ImageViewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAiImagePreview: ImageView = itemView.findViewById(R.id.ivAiImagePreview)
        private val tvAiImageFallbackIcon: TextView = itemView.findViewById(R.id.tvAiImageFallbackIcon)
        private val tvAiImageFileName: TextView = itemView.findViewById(R.id.tvAiImageFileName)
        private val tvAiImageFileSize: TextView = itemView.findViewById(R.id.tvAiImageFileSize)

        fun bind(item: MediaAttachment) {
            tvAiImageFileName.text = item.fileName
            tvAiImageFileSize.text = "${item.formattedSize} • Photo"

            val file = File(item.filePath)
            if (file.exists()) {
                val bmp = decodeSampledBitmapFromFile(file.absolutePath, 800, 600)
                if (bmp != null) {
                    ivAiImagePreview.setImageBitmap(bmp)
                    ivAiImagePreview.visibility = View.VISIBLE
                    tvAiImageFallbackIcon.visibility = View.GONE
                } else {
                    ivAiImagePreview.visibility = View.GONE
                    tvAiImageFallbackIcon.visibility = View.VISIBLE
                }
            } else {
                ivAiImagePreview.visibility = View.GONE
                tvAiImageFallbackIcon.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                AttachmentStorageHelper.openAttachment(itemView.context, item)
            }
        }
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAiVideoThumbnail: ImageView = itemView.findViewById(R.id.ivAiVideoThumbnail)
        private val tvAiVideoFileName: TextView = itemView.findViewById(R.id.tvAiVideoFileName)
        private val tvAiVideoFileSize: TextView = itemView.findViewById(R.id.tvAiVideoFileSize)

        fun bind(item: MediaAttachment) {
            tvAiVideoFileName.text = item.fileName
            tvAiVideoFileSize.text = "${item.formattedSize} • Tap to play"

            val file = File(item.filePath)
            if (file.exists()) {
                val thumb = getVideoThumbnail(file)
                if (thumb != null) {
                    ivAiVideoThumbnail.setImageBitmap(thumb)
                    ivAiVideoThumbnail.visibility = View.VISIBLE
                } else {
                    ivAiVideoThumbnail.setImageResource(R.drawable.matte_metallic_bg)
                }
            } else {
                ivAiVideoThumbnail.setImageResource(R.drawable.matte_metallic_bg)
            }

            itemView.setOnClickListener {
                AttachmentStorageHelper.openAttachment(itemView.context, item)
            }
        }
    }

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAiAudioFileName: TextView = itemView.findViewById(R.id.tvAiAudioFileName)
        private val tvAiAudioFileSize: TextView = itemView.findViewById(R.id.tvAiAudioFileSize)

        fun bind(item: MediaAttachment) {
            tvAiAudioFileName.text = item.fileName
            tvAiAudioFileSize.text = "${item.formattedSize} • Audio Note"

            itemView.setOnClickListener {
                AttachmentStorageHelper.openAttachment(itemView.context, item)
            }
        }
    }

    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flAiDocBadgeContainer: FrameLayout = itemView.findViewById(R.id.flAiDocBadgeContainer)
        private val ivAiDocIcon: ImageView = itemView.findViewById(R.id.ivAiDocIcon)
        private val tvAiDocExtensionBadge: TextView = itemView.findViewById(R.id.tvAiDocExtensionBadge)
        private val tvAiDocFileName: TextView = itemView.findViewById(R.id.tvAiDocFileName)
        private val tvAiDocFileSize: TextView = itemView.findViewById(R.id.tvAiDocFileSize)

        fun bind(item: MediaAttachment) {
            val ext = item.getFileExtension().ifEmpty { "FILE" }
            tvAiDocFileName.text = item.fileName
            tvAiDocExtensionBadge.text = ext.take(4)
            tvAiDocFileSize.text = "${item.formattedSize} • $ext • Tap to open"

            // Customize WhatsApp document badge theme by file type
            val (bgTint, fgTint) = when (ext.lowercase()) {
                "pdf" -> Pair("#FEE2E2", "#DC2626") // Red
                "doc", "docx", "pages" -> Pair("#DBEAFE", "#2563EB") // Blue
                "xls", "xlsx", "csv", "numbers" -> Pair("#D1FAE5", "#059669") // Green
                "ppt", "pptx", "key" -> Pair("#FEF3C7", "#D97706") // Yellow/Amber
                "zip", "rar", "tar", "7z", "gz" -> Pair("#FFEDD5", "#EA580C") // Orange
                "txt", "md", "json", "xml", "kt", "java", "py" -> Pair("#E0E7FF", "#4F46E5") // Indigo
                else -> Pair("#F3F4F6", "#4B5563") // Slate
            }

            try {
                flAiDocBadgeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgTint))
                ivAiDocIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(fgTint))
                tvAiDocExtensionBadge.setTextColor(Color.parseColor(fgTint))
            } catch (e: Exception) {
                // Fallback to default
            }

            itemView.setOnClickListener {
                AttachmentStorageHelper.openAttachment(itemView.context, item)
            }
        }
    }

    private fun getVideoThumbnail(file: File): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(512, 384), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (e: Exception) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val frame = retriever.frameAtTime
                retriever.release()
                frame
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
