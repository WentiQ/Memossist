package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationsAdapter(
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    private var notificationsList: List<NotificationItem> = emptyList()

    fun setNotifications(newList: List<NotificationItem>) {
        notificationsList = newList
        notifyDataSetChanged()
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
            tvNotifIcon.text = item.getTypeIconText()
            tvNotifTitle.text = item.title
            tvNotifTime.text = item.getFormattedTime()
            tvNotifMessage.text = item.message

            vUnreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
