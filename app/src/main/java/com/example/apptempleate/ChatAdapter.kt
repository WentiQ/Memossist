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
        if (messages.size == newMessages.size && messages.isNotEmpty()) {
            val lastIdx = messages.lastIndex
            messages.clear()
            messages.addAll(newMessages)
            notifyItemChanged(lastIdx, "PAYLOAD_STEP_UPDATE")
        } else {
            messages.clear()
            messages.addAll(newMessages)
            notifyDataSetChanged()
        }
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && holder is AiViewHolder) {
            val message = messages[position]
            if (message.isThinking) {
                holder.tvThinkingStep.text = message.thinkingStatus ?: "Thinking..."
                if (message.text.isNotEmpty()) {
                    holder.tvAiMsg.visibility = View.VISIBLE
                    holder.tvAiMsg.text = message.text
                }
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.tvUserMsg.text = message.text
            if (message.attachments.isNotEmpty()) {
                holder.rvUserAttachments.visibility = View.VISIBLE
                holder.rvUserAttachments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context, RecyclerView.HORIZONTAL, false)
                holder.rvUserAttachments.adapter = MediaAttachmentAdapter(message.attachments)
            } else {
                holder.rvUserAttachments.visibility = View.GONE
            }
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
                holder.rvAiAttachments.visibility = View.GONE
            } else {
                holder.llThinkingContainer.visibility = View.GONE
                holder.typingDotsView.stopAnimation()
                holder.tvAiMsg.visibility = View.VISIBLE
                holder.tvAiMsg.text = message.text

                // Render attached media/files from used experiences
                if (message.attachments.isNotEmpty()) {
                    holder.rvAiAttachments.visibility = View.VISIBLE
                    holder.rvAiAttachments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(holder.itemView.context, RecyclerView.HORIZONTAL, false)
                    holder.rvAiAttachments.adapter = MediaAttachmentAdapter(message.attachments)
                } else {
                    holder.rvAiAttachments.visibility = View.GONE
                }

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
        val rvUserAttachments: RecyclerView = itemView.findViewById(R.id.rvUserAttachments)
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llThinkingContainer: LinearLayout = itemView.findViewById(R.id.llThinkingContainer)
        val typingDotsView: TypingDotsView = itemView.findViewById(R.id.typingDotsView)
        val tvThinkingStep: TextView = itemView.findViewById(R.id.tvThinkingStep)
        val tvAiMsg: TextView = itemView.findViewById(R.id.tvAiMsg)
        val rvAiAttachments: RecyclerView = itemView.findViewById(R.id.rvAiAttachments)
    }
}
