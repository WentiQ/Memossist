package com.example.apptempleate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class RemindersAdapter(
    private val onToggleActive: (ReminderItem) -> Unit,
    private val onComplete: (ReminderItem) -> Unit,
    private val onEdit: (ReminderItem) -> Unit,
    private val onDelete: (ReminderItem) -> Unit,
    private val onTestAlarm: (ReminderItem) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    private var reminderList: List<ReminderItem> = emptyList()

    fun setReminders(newList: List<ReminderItem>) {
        reminderList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder_card, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminderList[position]
        holder.bind(reminder)
    }

    override fun getItemCount(): Int = reminderList.size

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        private val tvReminderTitle: TextView = itemView.findViewById(R.id.tvReminderTitle)
        private val tvReminderTime: TextView = itemView.findViewById(R.id.tvReminderTime)
        private val tvTimeStatus: TextView = itemView.findViewById(R.id.tvTimeStatus)
        private val switchActive: SwitchCompat = itemView.findViewById(R.id.switchActive)
        private val tvReminderDescription: TextView = itemView.findViewById(R.id.tvReminderDescription)
        private val llTriggersContainer: LinearLayout = itemView.findViewById(R.id.llTriggersContainer)
        private val btnTestAlarm: LinearLayout = itemView.findViewById(R.id.btnTestAlarm)
        private val btnCompleteReminder: ImageButton = itemView.findViewById(R.id.btnCompleteReminder)
        private val btnEditReminder: ImageButton = itemView.findViewById(R.id.btnEditReminder)
        private val btnDeleteReminder: ImageButton = itemView.findViewById(R.id.btnDeleteReminder)

        fun bind(reminder: ReminderItem) {
            tvCategoryIcon.text = reminder.getCategoryIconText()
            tvReminderTitle.text = reminder.title
            tvReminderTime.text = reminder.getFormattedEventDateTime()

            if (reminder.description.isNotBlank()) {
                tvReminderDescription.visibility = View.VISIBLE
                tvReminderDescription.text = reminder.description
            } else {
                tvReminderDescription.visibility = View.GONE
            }

            // Status Tag & Colors
            tvTimeStatus.text = reminder.getTimeStatusLabel()
            if (reminder.isCompleted) {
                tvTimeStatus.setBackgroundResource(R.drawable.bg_tag_rounded)
                tvTimeStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                tvTimeStatus.setTextColor(Color.parseColor("#065F46"))
                btnCompleteReminder.setImageResource(R.drawable.ic_check_circle)
                btnCompleteReminder.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
            } else {
                tvTimeStatus.setBackgroundResource(R.drawable.bg_tag_rounded)
                tvTimeStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EFF6FF"))
                tvTimeStatus.setTextColor(Color.parseColor("#2563EB"))
                btnCompleteReminder.setImageResource(R.drawable.ic_check_circle)
                btnCompleteReminder.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
            }

            // Active Switch state listener
            switchActive.setOnCheckedChangeListener(null)
            switchActive.isChecked = reminder.isActive && !reminder.isCompleted
            switchActive.setOnCheckedChangeListener { _, _ ->
                onToggleActive(reminder)
            }

            // Populate Trigger Pills
            llTriggersContainer.removeAllViews()
            if (reminder.triggers.isEmpty()) {
                val tvEmpty = TextView(itemView.context).apply {
                    text = "1 Alert Scheduled"
                    textSize = 11f
                    setTextColor(Color.parseColor("#6B7280"))
                }
                llTriggersContainer.addView(tvEmpty)
            } else {
                for (trigger in reminder.triggers) {
                    val pill = TextView(itemView.context).apply {
                        text = "${trigger.getTypeBadge()} (${trigger.getFormattedTime()})"
                        textSize = 11f
                        setTextColor(Color.parseColor("#374151"))
                        setPadding(18, 8, 18, 8)
                        setBackgroundResource(R.drawable.bg_tag_rounded)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(
                            if (trigger.isTriggered) Color.parseColor("#E5E7EB") else Color.parseColor("#FEF3C7")
                        )
                        val layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 12, 0)
                        }
                        this.layoutParams = layoutParams
                    }
                    llTriggersContainer.addView(pill)
                }
            }

            btnTestAlarm.setOnClickListener { onTestAlarm(reminder) }
            btnCompleteReminder.setOnClickListener { onComplete(reminder) }
            btnEditReminder.setOnClickListener { onEdit(reminder) }
            btnDeleteReminder.setOnClickListener { onDelete(reminder) }
        }
    }
}
