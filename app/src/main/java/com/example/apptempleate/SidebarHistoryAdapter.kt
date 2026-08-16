package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SidebarHistoryAdapter(
    private val conversations: MutableList<Conversation> = mutableListOf(),
    private val onItemClick: (Conversation) -> Unit,
    private val onItemLongClick: (Conversation) -> Unit
) : RecyclerView.Adapter<SidebarHistoryAdapter.ViewHolder>() {

    fun setConversations(newList: List<Conversation>) {
        conversations.clear()
        conversations.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sidebar_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conv = conversations[position]
        holder.tvHistoryTitle.text = if (conv.isPinned) "📌 ${conv.title}" else conv.title
        holder.ivHistoryIcon.setImageResource(if (conv.isPinned) R.drawable.ic_pin else R.drawable.ic_chat_history)
        holder.vChatUnreadBadge.visibility = if (conv.hasUnread) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            onItemClick(conv)
        }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick(conv)
            true
        }
    }

    override fun getItemCount(): Int = conversations.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivHistoryIcon: ImageView = itemView.findViewById(R.id.ivHistoryIcon)
        val tvHistoryTitle: TextView = itemView.findViewById(R.id.tvHistoryTitle)
        val vChatUnreadBadge: View = itemView.findViewById(R.id.vChatUnreadBadge)
    }
}
