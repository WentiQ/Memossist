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

        btnMarkAllRead.setOnClickListener {
            context?.let { ctx ->
                NotificationHistoryRepository.markAllAsRead(ctx)
                loadNotifications()
                onDismissCallback?.invoke()
            }
        }

        loadNotifications()
    }

    private fun loadNotifications() {
        val ctx = context ?: return
        val list = NotificationHistoryRepository.loadLast30DaysNotifications(ctx)

        val unreadCount = list.count { !it.isRead }
        tvNotifSubHeader.text = if (unreadCount > 0) {
            "$unreadCount unread • Showing last 30 days"
        } else {
            "All notifications read • Showing last 30 days"
        }

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
