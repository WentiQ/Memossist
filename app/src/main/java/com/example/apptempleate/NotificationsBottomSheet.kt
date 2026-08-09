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
                val intent = Intent(ctx, RemindersActivity::class.java).apply {
                    putExtra("HIGHLIGHT_REMINDER_ID", item.reminderId)
                }
                startActivity(intent)
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

    private fun updateUnreadSubHeader() {
        val ctx = context ?: return
        val list = NotificationHistoryRepository.loadLast30DaysNotifications(ctx)
        val unreadCount = list.count { !it.isRead }
        tvNotifSubHeader.text = if (unreadCount > 0) {
            "$unreadCount unread • Showing last 30 days"
        } else {
            "All notifications read • Showing last 30 days"
        }
    }

    private fun loadNotifications() {
        val ctx = context ?: return
        val list = NotificationHistoryRepository.loadLast30DaysNotifications(ctx)

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
