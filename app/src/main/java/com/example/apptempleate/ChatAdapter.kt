package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf(),
    private val onMessageLongClick: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessage(position: Int, message: ChatMessage) {
        if (position in 0 until messages.size) {
            messages[position] = message
            notifyItemChanged(position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_ai, parent, false)
            AiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.tvUserMsg.text = message.text
        } else if (holder is AiViewHolder) {
            if (message.isThinking) {
                holder.llThinkingContainer.visibility = View.VISIBLE
                holder.typingDotsView.startAnimation()
                holder.tvThinkingStep.text = message.thinkingStatus ?: "Thinking..."
                if (message.text.isNotEmpty()) {
                    holder.tvAiMsg.visibility = View.VISIBLE
                    holder.tvAiMsg.text = message.text
                } else {
                    holder.tvAiMsg.visibility = View.GONE
                }
            } else {
                holder.llThinkingContainer.visibility = View.GONE
                holder.typingDotsView.stopAnimation()
                holder.tvAiMsg.visibility = View.VISIBLE
                holder.tvAiMsg.text = message.text

                // Enable Long Press Inspection for AI response messages
                holder.itemView.setOnLongClickListener {
                    onMessageLongClick?.invoke(message)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserMsg: TextView = itemView.findViewById(R.id.tvUserMsg)
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llThinkingContainer: LinearLayout = itemView.findViewById(R.id.llThinkingContainer)
        val typingDotsView: TypingDotsView = itemView.findViewById(R.id.typingDotsView)
        val tvThinkingStep: TextView = itemView.findViewById(R.id.tvThinkingStep)
        val tvAiMsg: TextView = itemView.findViewById(R.id.tvAiMsg)
    }
}
