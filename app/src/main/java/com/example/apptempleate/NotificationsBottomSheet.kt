package com.example.apptempleate

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NotificationsBottomSheet(
    private val onDismissCallback: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private lateinit var tvNotifSubHeader: TextView
    private lateinit var btnMarkAllRead: TextView
    private lateinit var rvNotificationsList: RecyclerView
    private lateinit var llEmptyNotifications: LinearLayout
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_notifications_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNotifSubHeader = view.findViewById(R.id.tvNotifSubHeader)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        rvNotificationsList = view.findViewById(R.id.rvNotificationsList)
        llEmptyNotifications = view.findViewById(R.id.llEmptyNotifications)

        adapter = NotificationsAdapter { item ->
            context?.let { ctx ->
                NotificationHistoryRepository.markAsRead(ctx, item.id)

                val matchedConvId = findTargetConversationId(ctx, item)
                val matchedReminderId = findTargetReminderId(ctx, item)

                val isReminder = !item.reminderId.isNullOrEmpty() ||
                        matchedReminderId != null ||
                        item.type in listOf("ONE_DAY_BEFORE", "MORNING_OF_DAY", "ONE_HOUR_BEFORE", "TEN_MIN_BEFORE", "POST_EVENT_CHECK") ||
                        item.title.contains("Reminder", ignoreCase = true) ||
                        item.title.contains("Alarm", ignoreCase = true)

                val isChat = item.type == "CHAT_ANSWER" ||
                        !item.conversationId.isNullOrEmpty() ||
                        matchedConvId != null ||
                        item.title.contains("Memossist Answer", ignoreCase = true)

                if (isChat) {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        if (matchedConvId != null) {
                            putExtra("OPEN_CONVERSATION_ID", matchedConvId)
                        }
                    }
                    startActivity(intent)
                } else if (isReminder) {
                    val intent = Intent(ctx, RemindersActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        if (matchedReminderId != null) {
                            putExtra("HIGHLIGHT_REMINDER_ID", matchedReminderId)
                        }
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                }
            }
            dismiss()
        }

        rvNotificationsList.layoutManager = LinearLayoutManager(requireContext())
        rvNotificationsList.adapter = adapter

        // Swipe Left or Right ItemTouchHelper
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItem(position) ?: return
                val ctx = context ?: return

                if (!item.isRead) {
                    // Unread: Mark as READ -> Dim card, DO NOT REMOVE
                    NotificationHistoryRepository.markAsRead(ctx, item.id)
                    item.isRead = true
                    adapter.notifyItemChanged(position)
                    android.widget.Toast.makeText(ctx, "Marked as read (Swipe again to remove)", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Already read: Remove/delete notification card
                    NotificationHistoryRepository.deleteNotification(ctx, item.id)
                    adapter.removeItem(position)
                    android.widget.Toast.makeText(ctx, "Notification removed", android.widget.Toast.LENGTH_SHORT).show()
                    if (adapter.itemCount == 0) {
                        llEmptyNotifications.visibility = View.VISIBLE
                        rvNotificationsList.visibility = View.GONE
                    }
                }
                updateUnreadSubHeader()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rvNotificationsList)

        btnMarkAllRead.setOnClickListener {
            context?.let { ctx ->
                NotificationHistoryRepository.markAllAsRead(ctx)
                loadNotifications()
                onDismissCallback?.invoke()
            }
        }

        loadNotifications()
    }

    private fun findTargetConversationId(context: android.content.Context, item: NotificationItem): String? {
        if (!item.conversationId.isNullOrEmpty()) {
            return item.conversationId
        }

        val conversations = ChatRepository.loadAllConversations(context)
        if (conversations.isEmpty()) return null

        val notifClean = item.message.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim().lowercase()
        val stopWords = setOf("memossist", "answer", "ready", "the", "a", "an", "is", "are", "to", "of", "and", "in", "on", "for", "with")
        val notifWords = notifClean.split("\\s+".toRegex()).filter { it.length >= 3 && it !in stopWords }

        if (notifWords.isNotEmpty()) {
            var bestConvId: String? = null
            var highestScore = 0

            for (conv in conversations) {
                var convMaxScore = 0
                for (msg in conv.messages) {
                    val msgClean = msg.text.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim().lowercase()
                    val matchCount = notifWords.count { word -> msgClean.contains(word) }
                    if (matchCount > convMaxScore) {
                        convMaxScore = matchCount
                    }
                }
                if (convMaxScore > highestScore) {
                    highestScore = convMaxScore
                    bestConvId = conv.id
                }
            }

            if (highestScore >= 1) {
                return bestConvId
            }
        }

        return null
    }

    private fun findTargetReminderId(context: android.content.Context, item: NotificationItem): String? {
        if (!item.reminderId.isNullOrEmpty()) {
            return item.reminderId
        }

        val reminders = ReminderRepository.loadAllReminders(context)
        if (reminders.isEmpty()) return null

        val cleanTitle = item.title.trim().lowercase()
        val cleanMsg = item.message.trim().lowercase()

        for (reminder in reminders) {
            val rTitle = reminder.title.trim().lowercase()
            val rDesc = reminder.description.trim().lowercase()

            if ((rTitle.isNotEmpty() && (cleanTitle.contains(rTitle) || cleanMsg.contains(rTitle))) ||
                (rDesc.isNotEmpty() && (cleanMsg.contains(rDesc) || rDesc.contains(cleanMsg)))) {
                return reminder.id
            }
        }
        return null
    }

    private fun getFilteredNotifications(ctx: android.content.Context): List<NotificationItem> {
        val list = NotificationHistoryRepository.loadLast30DaysNotifications(ctx)
        val activeConvId = MainActivity.activeConversationId
        return if (!activeConvId.isNullOrEmpty()) {
            list.filter { item ->
                val targetConvId = findTargetConversationId(ctx, item)
                !(item.type == "CHAT_ANSWER" && targetConvId == activeConvId)
            }
        } else {
            list
        }
    }

    private fun updateUnreadSubHeader() {
        val ctx = context ?: return
        val list = getFilteredNotifications(ctx)
        val unreadCount = list.count { !it.isRead }
        tvNotifSubHeader.text = if (unreadCount > 0) {
            "$unreadCount unread • Showing last 30 days"
        } else {
            "All notifications read • Showing last 30 days"
        }
    }

    private fun loadNotifications() {
        val ctx = context ?: return
        val list = getFilteredNotifications(ctx)

        updateUnreadSubHeader()

        adapter.setNotifications(list)

        if (list.isEmpty()) {
            llEmptyNotifications.visibility = View.VISIBLE
            rvNotificationsList.visibility = View.GONE
        } else {
            llEmptyNotifications.visibility = View.GONE
            rvNotificationsList.visibility = View.VISIBLE
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
}
