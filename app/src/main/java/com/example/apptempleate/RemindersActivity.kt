package com.example.apptempleate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RemindersActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvActiveCount: TextView
    private lateinit var tvNextEvent: TextView
    private lateinit var etSearchReminders: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipUpcoming: TextView
    private lateinit var chipCompleted: TextView
    private lateinit var rvRemindersList: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var fabAddReminder: FloatingActionButton

    private lateinit var remindersAdapter: RemindersAdapter
    private var allReminders: MutableList<ReminderItem> = mutableListOf()
    private var currentFilter: String = "ALL" // ALL, UPCOMING, COMPLETED
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_reminders)

        btnBack = findViewById(R.id.btnBack)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvNextEvent = findViewById(R.id.tvNextEvent)
        etSearchReminders = findViewById(R.id.etSearchReminders)
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipCompleted = findViewById(R.id.chipCompleted)
        rvRemindersList = findViewById(R.id.rvRemindersList)
        llEmptyState = findViewById(R.id.llEmptyState)
        fabAddReminder = findViewById(R.id.fabAddReminder)

        remindersAdapter = RemindersAdapter(
            onToggleActive = { reminder ->
                ReminderRepository.toggleReminderActive(this, reminder.id)
                refreshRemindersList()
            },
            onComplete = { reminder ->
                ReminderRepository.toggleReminderCompleted(this, reminder.id)
                refreshRemindersList()
            },
            onEdit = { reminder ->
                showAddOrEditReminderDialog(reminder)
            },
            onDelete = { reminder ->
                showDeleteConfirmationDialog(reminder)
            },
            onTestAlarm = { reminder ->
                ReminderRepository.triggerTestAlarmImmediately(this, reminder)
                Toast.makeText(this, "Testing full-screen alarm alert...", Toast.LENGTH_SHORT).show()
            }
        )

        rvRemindersList.layoutManager = LinearLayoutManager(this)
        rvRemindersList.adapter = remindersAdapter

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        val btnTestPhoneNotification: ImageButton? = findViewById(R.id.btnTestPhoneNotification)
        btnTestPhoneNotification?.setOnClickListener {
            val testReminder = ReminderItem(
                id = "TEST-${System.currentTimeMillis()}",
                title = "Memossist Reminder",
                description = "Hey Dinesh, do you remember today you have an extra class at 2pm?",
                eventTimeMillis = System.currentTimeMillis() + 5000L,
                importance = "HIGH",
                category = "CLASS"
            )
            ReminderRepository.triggerTestAlarmImmediately(this, testReminder)
            Toast.makeText(this, "Testing status bar notification in 3 seconds...", Toast.LENGTH_LONG).show()
        }

        fabAddReminder.setOnClickListener {
            showAddOrEditReminderDialog(null)
        }

        setupSearchAndFilters()
        refreshRemindersList()
    }

    override fun onResume() {
        super.onResume()
        refreshRemindersList()
    }

    private fun refreshRemindersList() {
        allReminders = ReminderRepository.loadAllReminders(this)

        // Update stats
        val activeItems = allReminders.filter { it.isActive && !it.isCompleted }
        tvActiveCount.text = "${activeItems.size} Active"

        val now = System.currentTimeMillis()
        val nextUpcoming = activeItems.filter { it.eventTimeMillis > now }.minByOrNull { it.eventTimeMillis }
        if (nextUpcoming != null) {
            tvNextEvent.text = "${nextUpcoming.title} (${nextUpcoming.getTimeStatusLabel()})"
        } else {
            tvNextEvent.text = "None scheduled"
        }

        // Apply search & filter
        val filtered = allReminders.filter { item ->
            val matchesFilter = when (currentFilter) {
                "UPCOMING" -> !item.isCompleted && item.eventTimeMillis >= now
                "COMPLETED" -> item.isCompleted
                else -> true
            }

            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }

        remindersAdapter.setReminders(filtered)

        if (filtered.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            rvRemindersList.visibility = View.GONE
        } else {
            llEmptyState.visibility = View.GONE
            rvRemindersList.visibility = View.VISIBLE
        }
    }

    private fun setupSearchAndFilters() {
        etSearchReminders.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                refreshRemindersList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipAll.setOnClickListener {
            setFilter("ALL")
        }

        chipUpcoming.setOnClickListener {
            setFilter("UPCOMING")
        }

        chipCompleted.setOnClickListener {
            setFilter("COMPLETED")
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        updateFilterChipStyles()
        refreshRemindersList()
    }

    private fun updateFilterChipStyles() {
        val selectedBg = R.drawable.bg_chip_selected
        val unselectedBg = R.drawable.bg_chip_unselected

        chipAll.setBackgroundResource(if (currentFilter == "ALL") selectedBg else unselectedBg)
        chipAll.setTextColor(if (currentFilter == "ALL") Color.WHITE else Color.parseColor("#4B5563"))

        chipUpcoming.setBackgroundResource(if (currentFilter == "UPCOMING") selectedBg else unselectedBg)
        chipUpcoming.setTextColor(if (currentFilter == "UPCOMING") Color.WHITE else Color.parseColor("#4B5563"))

        chipCompleted.setBackgroundResource(if (currentFilter == "COMPLETED") selectedBg else unselectedBg)
        chipCompleted.setTextColor(if (currentFilter == "COMPLETED") Color.WHITE else Color.parseColor("#4B5563"))
    }

    private fun showAddOrEditReminderDialog(existingItem: ReminderItem?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_reminder, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvHeader: TextView = dialogView.findViewById(R.id.tvDialogHeader)
        val etTitle: EditText = dialogView.findViewById(R.id.etDialogReminderTitle)
        val etDesc: EditText = dialogView.findViewById(R.id.etDialogReminderDesc)
        val btnPickDate: LinearLayout = dialogView.findViewById(R.id.btnPickDate)
        val tvSelectedDate: TextView = dialogView.findViewById(R.id.tvSelectedDate)
        val btnPickTime: LinearLayout = dialogView.findViewById(R.id.btnPickTime)
        val tvSelectedTime: TextView = dialogView.findViewById(R.id.tvSelectedTime)
        val btnCancel: TextView = dialogView.findViewById(R.id.btnDialogCancel)
        val btnSave: TextView = dialogView.findViewById(R.id.btnDialogSave)

        val targetCal = Calendar.getInstance()
        if (existingItem != null) {
            tvHeader.text = "Edit Smart Reminder"
            etTitle.setText(existingItem.title)
            etDesc.setText(existingItem.description)
            targetCal.timeInMillis = existingItem.eventTimeMillis
        } else {
            tvHeader.text = "Add Smart Reminder"
            // Default to tomorrow 2:00 PM if creating fresh
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
            targetCal.set(Calendar.HOUR_OF_DAY, 14)
            targetCal.set(Calendar.MINUTE, 0)
        }

        fun updateDateTimeDisplay() {
            val sdfDate = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
            tvSelectedDate.text = sdfDate.format(targetCal.time)
            tvSelectedTime.text = sdfTime.format(targetCal.time)
        }

        updateDateTimeDisplay()

        btnPickDate.setOnClickListener {
            val dpd = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    targetCal.set(Calendar.YEAR, year)
                    targetCal.set(Calendar.MONTH, month)
                    targetCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    updateDateTimeDisplay()
                },
                targetCal.get(Calendar.YEAR),
                targetCal.get(Calendar.MONTH),
                targetCal.get(Calendar.DAY_OF_MONTH)
            )
            dpd.show()
        }

        btnPickTime.setOnClickListener {
            val tpd = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    targetCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    targetCal.set(Calendar.MINUTE, minute)
                    updateDateTimeDisplay()
                },
                targetCal.get(Calendar.HOUR_OF_DAY),
                targetCal.get(Calendar.MINUTE),
                false
            )
            tpd.show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a reminder title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetMillis = targetCal.timeInMillis

            val reminderToSave = if (existingItem != null) {
                existingItem.apply {
                    this.title = title
                    this.description = desc
                    this.eventTimeMillis = targetMillis
                }
            } else {
                val inputPrompt = "$title $desc on ${tvSelectedDate.text} at ${tvSelectedTime.text}"
                ReminderExtractor.extractAndCreateReminder(this, inputPrompt) ?: ReminderItem(
                    id = "REM_${System.currentTimeMillis()}",
                    title = title,
                    description = desc,
                    eventTimeMillis = targetMillis
                )
            }

            ReminderRepository.addOrUpdateReminder(this, reminderToSave)
            refreshRemindersList()
            Toast.makeText(this, "Smart Reminder saved & scheduled!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(reminder: ReminderItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Reminder")
            .setMessage("Are you sure you want to delete '${reminder.title}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                ReminderRepository.deleteReminder(this, reminder.id)
                refreshRemindersList()
                Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }
}
