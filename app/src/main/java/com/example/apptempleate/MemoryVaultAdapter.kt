package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MemoryVaultAdapter(
    private var items: List<MemoryItem>,
    private val onItemClick: (MemoryItem) -> Unit
) : RecyclerView.Adapter<MemoryVaultAdapter.MemoryViewHolder>() {

    class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTag: TextView = itemView.findViewById(R.id.tvMemoryTag)
        val tvTime: TextView = itemView.findViewById(R.id.tvMemoryTime)
        val tvTitle: TextView = itemView.findViewById(R.id.tvMemoryTitle)
        val tvSnippet: TextView = itemView.findViewById(R.id.tvMemorySnippet)
        val rvMemoryAttachments: RecyclerView = itemView.findViewById(R.id.rvMemoryAttachments)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory_vault, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvTag.text = item.tag.uppercase()
        holder.tvTime.text = item.timeAgo
        holder.tvTitle.text = item.title
        holder.tvSnippet.text = item.snippet

        if (item.attachments.isNotEmpty()) {
            holder.rvMemoryAttachments.visibility = View.VISIBLE
            holder.rvMemoryAttachments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context, RecyclerView.HORIZONTAL, false)
            holder.rvMemoryAttachments.adapter = MediaAttachmentAdapter(item.attachments)
        } else {
            holder.rvMemoryAttachments.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<MemoryItem>) {
        items = newList
        notifyDataSetChanged()
    }
}
