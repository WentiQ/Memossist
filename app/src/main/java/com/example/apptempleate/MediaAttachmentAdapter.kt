package com.example.apptempleate

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MediaAttachmentAdapter(
    private val attachments: List<MediaAttachment>
) : RecyclerView.Adapter<MediaAttachmentAdapter.AttachmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_attachment, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(attachments[position])
    }

    override fun getItemCount(): Int = attachments.size

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAttachmentImage: ImageView = itemView.findViewById(R.id.ivAttachmentImage)
        private val tvAttachmentIcon: TextView = itemView.findViewById(R.id.tvAttachmentIcon)
        private val tvAttachmentName: TextView = itemView.findViewById(R.id.tvAttachmentName)
        private val tvAttachmentSize: TextView = itemView.findViewById(R.id.tvAttachmentSize)

        fun bind(item: MediaAttachment) {
            tvAttachmentName.text = item.fileName
            tvAttachmentSize.text = "${item.formattedSize} • Tap to open"

            if (item.isImage()) {
                val file = File(item.filePath)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        ivAttachmentImage.setImageBitmap(bmp)
                        ivAttachmentImage.visibility = View.VISIBLE
                        tvAttachmentIcon.visibility = View.GONE
                    } else {
                        showIcon(item)
                    }
                } else {
                    showIcon(item)
                }
            } else {
                showIcon(item)
            }

            itemView.setOnClickListener {
                AttachmentStorageHelper.openAttachment(itemView.context, item)
            }
        }

        private fun showIcon(item: MediaAttachment) {
            ivAttachmentImage.visibility = View.GONE
            tvAttachmentIcon.visibility = View.VISIBLE
            tvAttachmentIcon.text = item.getTypeIconText()
        }
    }
}
