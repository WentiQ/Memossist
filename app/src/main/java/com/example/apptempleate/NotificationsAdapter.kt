package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class NotificationsAdapter(
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    private val notificationsList: MutableList<NotificationItem> = mutableListOf()

    fun setNotifications(newList: List<NotificationItem>) {
        notificationsList.clear()
        notificationsList.addAll(newList)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): NotificationItem? {
        return if (position in 0 until notificationsList.size) notificationsList[position] else null
    }

    fun removeItem(position: Int) {
        if (position in 0 until notificationsList.size) {
            notificationsList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_card, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notificationsList[position])
    }

    override fun getItemCount(): Int = notificationsList.size

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNotifIcon: TextView = itemView.findViewById(R.id.tvNotifIcon)
        private val tvNotifTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
        private val tvNotifTime: TextView = itemView.findViewById(R.id.tvNotifTime)
        private val tvNotifMessage: TextView = itemView.findViewById(R.id.tvNotifMessage)
        private val vUnreadDot: View = itemView.findViewById(R.id.vUnreadDot)

        fun bind(item: NotificationItem) {
            val context = itemView.context
            tvNotifIcon.text = item.getTypeIconText()
            tvNotifTitle.text = item.title
            tvNotifTime.text = item.getFormattedTime()
            tvNotifMessage.text = item.message

            vUnreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE

            // Dim item and adjust dynamic theme text colors based on read state
            if (item.isRead) {
                itemView.alpha = 0.55f
                tvNotifTitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                tvNotifMessage.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            } else {
                itemView.alpha = 1.0f
                tvNotifTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tvNotifMessage.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
