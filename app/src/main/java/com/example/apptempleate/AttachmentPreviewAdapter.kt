package com.example.apptempleate

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class AttachmentPreviewAdapter(
    private val onRemoveClick: (MediaAttachment) -> Unit
) : RecyclerView.Adapter<AttachmentPreviewAdapter.PreviewViewHolder>() {

    private var attachmentsList: List<MediaAttachment> = emptyList()

    fun setAttachments(newList: List<MediaAttachment>) {
        attachmentsList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attachment_preview, parent, false)
        return PreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        holder.bind(attachmentsList[position])
    }

    override fun getItemCount(): Int = attachmentsList.size

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPreviewThumbnail: ImageView = itemView.findViewById(R.id.ivPreviewThumbnail)
        private val tvPreviewIcon: TextView = itemView.findViewById(R.id.tvPreviewIcon)
        private val tvPreviewName: TextView = itemView.findViewById(R.id.tvPreviewName)
        private val tvPreviewSize: TextView = itemView.findViewById(R.id.tvPreviewSize)
        private val btnRemoveAttachment: ImageButton = itemView.findViewById(R.id.btnRemoveAttachment)

        fun bind(item: MediaAttachment) {
            tvPreviewName.text = item.fileName
            tvPreviewSize.text = item.formattedSize

            if (item.isImage()) {
                val file = File(item.filePath)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        ivPreviewThumbnail.setImageBitmap(bmp)
                        ivPreviewThumbnail.visibility = View.VISIBLE
                        tvPreviewIcon.visibility = View.GONE
                    } else {
                        showIcon(item)
                    }
                } else {
                    showIcon(item)
                }
            } else {
                showIcon(item)
            }

            btnRemoveAttachment.setOnClickListener {
                onRemoveClick(item)
            }
        }

        private fun showIcon(item: MediaAttachment) {
            ivPreviewThumbnail.visibility = View.GONE
            tvPreviewIcon.visibility = View.VISIBLE
            tvPreviewIcon.text = item.getTypeIconText()
        }
    }
}
