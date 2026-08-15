package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf(),
    private val onMessageLongClick: ((ChatMessage) -> Unit)? = null,
    private val onUserMessageLongClick: ((ChatMessage) -> Unit)? = null,
    private val onChangeTypeClicked: ((ChatMessage) -> Unit)? = null,
    private val onConfirmationTypeSelected: ((ChatMessage, MessageType) -> Unit)? = null
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

    fun updateThinkingStep(messageId: String = "", stepText: String) {
        val idx = if (messageId.isNotEmpty()) {
            messages.indexOfFirst { it.id == messageId }
        } else {
            messages.indexOfLast { it.isThinking }
        }
        if (idx != -1) {
            messages[idx].thinkingStatus = stepText
            notifyItemChanged(idx, "PAYLOAD_STEP_UPDATE")
        }
    }

    fun updateStreamingText(messageId: String = "", partialText: String) {
        val idx = if (messageId.isNotEmpty()) {
            messages.indexOfFirst { it.id == messageId }
        } else {
            messages.indexOfLast { it.isThinking }
        }
        if (idx != -1) {
            messages[idx].text = partialText
            notifyItemChanged(idx, "PAYLOAD_STEP_UPDATE")
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

    fun updateParamEvaluation(messageId: String, status: String?, text: String?) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            messages[idx].paramEvaluationStatus = status
            messages[idx].paramEvaluationText = text
            notifyItemChanged(idx)
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
                val statusText = message.thinkingStatus ?: "Thinking..."
                holder.tvThinkingStep.text = statusText
                if (statusText.startsWith("⏳ In queue")) {
                    holder.typingDotsView.stopAnimation()
                    holder.typingDotsView.visibility = View.GONE
                    holder.btnChangeMessageType.visibility = View.GONE
                    holder.tvThinkingStep.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                } else {
                    holder.typingDotsView.visibility = View.VISIBLE
                    holder.btnChangeMessageType.visibility = View.VISIBLE
                    holder.typingDotsView.startAnimation()
                    holder.tvThinkingStep.setTextColor(android.graphics.Color.parseColor("#4F46E5"))
                }
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

            // Long click to edit last user message
            holder.itemView.setOnLongClickListener {
                val lastUserMsg = messages.findLast { it.isUser }
                if (message.id == lastUserMsg?.id) {
                    onUserMessageLongClick?.invoke(message)
                    true
                } else {
                    false
                }
            }
        } else if (holder is AiViewHolder) {
            if (message.awaitingTypeConfirmation) {
                holder.llThinkingContainer.visibility = View.GONE
                holder.typingDotsView.stopAnimation()
                holder.tvAiMsg.visibility = View.GONE
                holder.rvAiAttachments.visibility = View.GONE
                holder.llParamProgressContainer.visibility = View.GONE
                holder.llConfirmationContainer.visibility = View.VISIBLE

                val detectedName = message.detectedMessageType?.displayName ?: "Detected Intent"
                val confPct = (message.classificationConfidence * 100).toInt()
                holder.tvConfirmationTitle.text = "Detected $detectedName ($confPct% confidence). Select type to proceed:"

                holder.llConfirmChipsContainer.removeAllViews()
                val context = holder.itemView.context
                val density = context.resources.displayMetrics.density

                for (type in MessageType.values()) {
                    val chip = TextView(context).apply {
                        text = "${type.iconEmoji} ${type.displayName}"
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
                        setBackgroundResource(R.drawable.bg_button_cancel_pill)
                        setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                        isClickable = true
                        isFocusable = true
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, (8 * density).toInt(), 0)
                        }
                        layoutParams = lp

                        setOnClickListener {
                            onConfirmationTypeSelected?.invoke(message, type)
                        }
                    }
                    holder.llConfirmChipsContainer.addView(chip)
                }
            } else if (message.isThinking) {
                holder.llConfirmationContainer.visibility = View.GONE
                holder.llThinkingContainer.visibility = View.VISIBLE
                val statusText = message.thinkingStatus ?: "Thinking..."
                holder.tvThinkingStep.text = statusText

                if (statusText.startsWith("⏳ In queue")) {
                    holder.typingDotsView.stopAnimation()
                    holder.typingDotsView.visibility = View.GONE
                    holder.btnChangeMessageType.visibility = View.GONE
                    holder.tvThinkingStep.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                } else {
                    holder.typingDotsView.visibility = View.VISIBLE
                    holder.btnChangeMessageType.visibility = View.VISIBLE
                    holder.typingDotsView.startAnimation()
                    holder.tvThinkingStep.setTextColor(android.graphics.Color.parseColor("#4F46E5"))
                }

                if (message.text.isNotEmpty()) {
                    holder.tvAiMsg.visibility = View.VISIBLE
                    holder.tvAiMsg.text = message.text
                } else {
                    holder.tvAiMsg.visibility = View.GONE
                }
                holder.rvAiAttachments.visibility = View.GONE
                holder.llParamProgressContainer.visibility = View.GONE

                holder.btnChangeMessageType.setOnClickListener {
                    onChangeTypeClicked?.invoke(message)
                }
            } else {
                holder.llConfirmationContainer.visibility = View.GONE
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

                // Render 2nd LLM Parameter Evaluation Progress / Completion Container
                val paramText = message.paramEvaluationText
                if (!paramText.isNullOrBlank()) {
                    holder.llParamProgressContainer.visibility = View.VISIBLE
                    holder.tvParamProgressText.text = paramText
                    if (message.paramEvaluationStatus == "DONE") {
                        holder.pbParamProgress.visibility = View.GONE
                    } else {
                        holder.pbParamProgress.visibility = View.VISIBLE
                    }
                } else {
                    holder.llParamProgressContainer.visibility = View.GONE
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
        val btnChangeMessageType: TextView = itemView.findViewById(R.id.btnChangeMessageType)
        val llConfirmationContainer: LinearLayout = itemView.findViewById(R.id.llConfirmationContainer)
        val tvConfirmationTitle: TextView = itemView.findViewById(R.id.tvConfirmationTitle)
        val llConfirmChipsContainer: LinearLayout = itemView.findViewById(R.id.llConfirmChipsContainer)
        val tvAiMsg: TextView = itemView.findViewById(R.id.tvAiMsg)
        val rvAiAttachments: RecyclerView = itemView.findViewById(R.id.rvAiAttachments)
        val llParamProgressContainer: LinearLayout = itemView.findViewById(R.id.llParamProgressContainer)
        val pbParamProgress: ProgressBar = itemView.findViewById(R.id.pbParamProgress)
        val tvParamProgressText: TextView = itemView.findViewById(R.id.tvParamProgressText)
    }
}
