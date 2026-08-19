package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WorkspaceRemindersAdapter(
    private val onItemClick: (ReminderItem) -> Unit
) : RecyclerView.Adapter<WorkspaceRemindersAdapter.WorkspaceReminderViewHolder>() {

    private val reminderList: MutableList<ReminderItem> = mutableListOf()
    private var userName: String = "Dinesh"

    fun setReminders(newList: List<ReminderItem>, user: String = "Dinesh") {
        reminderList.clear()
        reminderList.addAll(newList)
        userName = user
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkspaceReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workspace_reminder, parent, false)
        return WorkspaceReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkspaceReminderViewHolder, position: Int) {
        holder.bind(reminderList[position])
    }

    override fun getItemCount(): Int = reminderList.size

    inner class WorkspaceReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWsReminderStatement: TextView = itemView.findViewById(R.id.tvWsReminderStatement)

        fun bind(item: ReminderItem) {
            tvWsReminderStatement.text = item.getHumanoidWorkspaceStatement(userName)

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
