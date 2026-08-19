package com.example.apptempleate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MemoryCleanupAdapter(
    private var allItems: List<MemoryStorageManager.RankedMemoryItem>,
    private val onSelectionChanged: (selectedCount: Int, selectedBytes: Long) -> Unit,
    private val onItemClick: (MemoryStorageManager.RankedMemoryItem) -> Unit
) : RecyclerView.Adapter<MemoryCleanupAdapter.CleanupViewHolder>() {

    private var displayedItems = allItems.toList()
    private val selectedIds = mutableSetOf<String>()

    fun updateData(newList: List<MemoryStorageManager.RankedMemoryItem>) {
        allItems = newList
        displayedItems = newList
        // Clean up any selected IDs that no longer exist
        val existingIds = newList.map { it.memory.id }.toSet()
        selectedIds.retainAll(existingIds)
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        displayedItems = if (q.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.memory.title.lowercase().contains(q) ||
                it.memory.snippet.lowercase().contains(q) ||
                it.memory.message.lowercase().contains(q) ||
                it.memory.tag.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun selectAll() {
        for (item in displayedItems) {
            selectedIds.add(item.memory.id)
        }
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun selectSpecificItems(itemsToSelect: List<MemoryItem>) {
        selectedIds.clear()
        for (mem in itemsToSelect) {
            selectedIds.add(mem.id)
        }
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    private fun notifySelectionUpdate() {
        var bytes = 0L
        for (item in allItems) {
            if (selectedIds.contains(item.memory.id)) {
                bytes += item.sizeBytes
            }
        }
        onSelectionChanged(selectedIds.size, bytes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CleanupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_cleanup, parent, false)
        return CleanupViewHolder(view)
    }

    override fun onBindViewHolder(holder: CleanupViewHolder, position: Int) {
        holder.bind(displayedItems[position])
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class CleanupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: LinearLayout = itemView.findViewById(R.id.llCleanupItemRoot)
        private val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelectMemory)
        private val tvRankBadge: TextView = itemView.findViewById(R.id.tvCleanupRankBadge)
        private val tvStrengthBadge: TextView = itemView.findViewById(R.id.tvCleanupStrengthBadge)
        private val tvSize: TextView = itemView.findViewById(R.id.tvCleanupSize)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvCleanupTitle)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvCleanupSnippet)
        private val tvTag: TextView = itemView.findViewById(R.id.tvCleanupTag)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvCleanupTimestamp)
        private val tvAttachmentsBadge: TextView = itemView.findViewById(R.id.tvCleanupAttachmentsBadge)

        fun bind(item: MemoryStorageManager.RankedMemoryItem) {
            val isSelected = selectedIds.contains(item.memory.id)
            cbSelect.isChecked = isSelected

            tvRankBadge.text = "#${item.rank} Rank"
            tvStrengthBadge.text = "Strength: ${item.strengthFormatted}"
            tvSize.text = item.sizeFormatted
            tvTitle.text = if (item.memory.title.isNotBlank()) item.memory.title else "Memory #${item.rank}"
            tvSnippet.text = item.memory.snippet
            tvTag.text = item.memory.tag
            tvTimestamp.text = "${item.memory.timestamp} • ${item.memory.timeAgo}"

            // Strength Badge Color Coding
            when {
                item.currentStrength < 0.15 -> {
                    tvStrengthBadge.setBackgroundResource(R.drawable.bg_tag_rounded)
                    tvStrengthBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    tvStrengthBadge.setTextColor(Color.parseColor("#DC2626"))
                    tvStrengthBadge.text = "Decayed: ${item.strengthFormatted}"
                }
                item.currentStrength < 0.35 -> {
                    tvStrengthBadge.setBackgroundResource(R.drawable.bg_tag_rounded)
                    tvStrengthBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    tvStrengthBadge.setTextColor(Color.parseColor("#D97706"))
                    tvStrengthBadge.text = "Weak: ${item.strengthFormatted}"
                }
                else -> {
                    tvStrengthBadge.setBackgroundResource(R.drawable.bg_tag_rounded)
                    tvStrengthBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0F2FE"))
                    tvStrengthBadge.setTextColor(Color.parseColor("#0284C7"))
                    tvStrengthBadge.text = "Strength: ${item.strengthFormatted}"
                }
            }

            // Attachments indicator
            if (item.memory.attachments.isNotEmpty()) {
                tvAttachmentsBadge.visibility = View.VISIBLE
                val count = item.memory.attachments.size
                tvAttachmentsBadge.text = if (count == 1) "📎 1 Attachment" else "📎 $count Attachments"
            } else {
                tvAttachmentsBadge.visibility = View.GONE
            }

            rootLayout.setOnClickListener {
                if (selectedIds.contains(item.memory.id)) {
                    selectedIds.remove(item.memory.id)
                } else {
                    selectedIds.add(item.memory.id)
                }
                cbSelect.isChecked = selectedIds.contains(item.memory.id)
                notifySelectionUpdate()
            }

            rootLayout.setOnLongClickListener {
                onItemClick(item)
                true
            }
        }
    }
}
