package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
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
                tvTimeStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.reminder_completed_background))
                tvTimeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.reminder_completed_text))
                btnCompleteReminder.setImageResource(R.drawable.ic_check_circle)
                btnCompleteReminder.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.reminder_completed_text))
            } else {
                tvTimeStatus.setBackgroundResource(R.drawable.bg_tag_rounded)
                tvTimeStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.reminder_upcoming_background))
                tvTimeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.reminder_upcoming_text))
                btnCompleteReminder.setImageResource(R.drawable.ic_check_circle)
                btnCompleteReminder.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_hint))
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
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
                llTriggersContainer.addView(tvEmpty)
            } else {
                for (trigger in reminder.triggers) {
                    val pill = TextView(itemView.context).apply {
                        text = "${trigger.getTypeBadge()} (${trigger.getFormattedTime()})"
                        textSize = 11f
                        setTextColor(ContextCompat.getColor(itemView.context, R.color.text_tertiary))
                        setPadding(18, 8, 18, 8)
                        setBackgroundResource(R.drawable.bg_tag_rounded)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(itemView.context, if (trigger.isTriggered) R.color.reminder_alert_triggered_background else R.color.reminder_alert_pending_background)
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
