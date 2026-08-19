package com.example.apptempleate

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MemoryCleanupActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvCleanupVaultSummary: TextView
    private lateinit var rgCleanupTargetMode: RadioGroup
    private lateinit var rbTargetBySize: RadioButton
    private lateinit var rbTargetByCount: RadioButton
    private lateinit var rbTargetNone: RadioButton
    private lateinit var llTargetInputRow: LinearLayout
    private lateinit var etTargetAmount: EditText
    private lateinit var spTargetUnit: Spinner
    private lateinit var tvTargetRecommendationStatus: TextView
    private lateinit var btnAutoDeleteWeakest: LinearLayout
    private lateinit var tvAutoDeleteBtnText: TextView
    private lateinit var btnSelectRecommended: LinearLayout
    private lateinit var tvSelectRecommendedBtnText: TextView
    private lateinit var etSearchCleanup: EditText
    private lateinit var btnSelectAll: TextView
    private lateinit var btnClearSelection: TextView
    private lateinit var rvCleanupMemories: RecyclerView
    private lateinit var tvEmptyCleanup: TextView
    private lateinit var tvBottomSelectedSummary: TextView
    private lateinit var tvBottomFreedEstimate: TextView
    private lateinit var btnDeleteSelected: TextView

    private lateinit var adapter: MemoryCleanupAdapter
    private var rankedMemoriesList: List<MemoryStorageManager.RankedMemoryItem> = emptyList()
    private var currentRecommendedMemories: List<MemoryItem> = emptyList()

    private var targetBytesToFree: Long = 0L
    private var targetNewLimitBytes: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_memory_cleanup)

        initViews()
        setupUnitSpinner()
        setupRecyclerView()
        setupListeners()

        // Handle incoming intent targets if launched from limit reduction workflow
        handleIncomingIntent(intent)

        loadMemoriesAndRefresh()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvCleanupVaultSummary = findViewById(R.id.tvCleanupVaultSummary)
        rgCleanupTargetMode = findViewById(R.id.rgCleanupTargetMode)
        rbTargetBySize = findViewById(R.id.rbTargetBySize)
        rbTargetByCount = findViewById(R.id.rbTargetByCount)
        rbTargetNone = findViewById(R.id.rbTargetNone)
        llTargetInputRow = findViewById(R.id.llTargetInputRow)
        etTargetAmount = findViewById(R.id.etTargetAmount)
        spTargetUnit = findViewById(R.id.spTargetUnit)
        tvTargetRecommendationStatus = findViewById(R.id.tvTargetRecommendationStatus)
        btnAutoDeleteWeakest = findViewById(R.id.btnAutoDeleteWeakest)
        tvAutoDeleteBtnText = findViewById(R.id.tvAutoDeleteBtnText)
        btnSelectRecommended = findViewById(R.id.btnSelectRecommended)
        tvSelectRecommendedBtnText = findViewById(R.id.tvSelectRecommendedBtnText)
        etSearchCleanup = findViewById(R.id.etSearchCleanup)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        rvCleanupMemories = findViewById(R.id.rvCleanupMemories)
        tvEmptyCleanup = findViewById(R.id.tvEmptyCleanup)
        tvBottomSelectedSummary = findViewById(R.id.tvBottomSelectedSummary)
        tvBottomFreedEstimate = findViewById(R.id.tvBottomFreedEstimate)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
    }

    private fun setupUnitSpinner() {
        val units = arrayOf(MemoryStorageManager.UNIT_MB, MemoryStorageManager.UNIT_GB, MemoryStorageManager.UNIT_KB, MemoryStorageManager.UNIT_B)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        spTargetUnit.adapter = spinnerAdapter
    }

    private fun setupRecyclerView() {
        adapter = MemoryCleanupAdapter(
            allItems = emptyList(),
            onSelectionChanged = { count, bytes ->
                updateBottomSelectionBar(count, bytes)
            },
            onItemClick = { rankedItem ->
                showMemoryDetailPreviewDialog(rankedItem.memory)
            }
        )
        rvCleanupMemories.layoutManager = LinearLayoutManager(this)
        rvCleanupMemories.adapter = adapter
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val incomingTargetBytes = intent.getLongExtra(EXTRA_TARGET_BYTES_TO_FREE, 0L)
        targetNewLimitBytes = intent.getLongExtra(EXTRA_TARGET_NEW_LIMIT, -1L)

        if (incomingTargetBytes > 0L) {
            rbTargetBySize.isChecked = true
            llTargetInputRow.visibility = View.VISIBLE
            // Convert to MB for convenience
            val mbValue = incomingTargetBytes.toDouble() / (1024.0 * 1024.0)
            etTargetAmount.setText(String.format("%.1f", mbValue))
            spTargetUnit.setSelection(0) // MB
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        rgCleanupTargetMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTargetBySize -> {
                    llTargetInputRow.visibility = View.VISIBLE
                    spTargetUnit.visibility = View.VISIBLE
                    etTargetAmount.hint = "e.g. 50"
                }
                R.id.rbTargetByCount -> {
                    llTargetInputRow.visibility = View.VISIBLE
                    spTargetUnit.visibility = View.GONE
                    etTargetAmount.hint = "e.g. 20 memories"
                }
                R.id.rbTargetNone -> {
                    llTargetInputRow.visibility = View.GONE
                }
            }
            recalculateTargetAndRecommendations()
        }

        etTargetAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recalculateTargetAndRecommendations()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        spTargetUnit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                recalculateTargetAndRecommendations()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnAutoDeleteWeakest.setOnClickListener {
            if (currentRecommendedMemories.isEmpty()) {
                Toast.makeText(this, "Please specify a valid cleanup target first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            promptAutoDeleteConfirmation(currentRecommendedMemories)
        }

        btnSelectRecommended.setOnClickListener {
            if (currentRecommendedMemories.isNotEmpty()) {
                adapter.selectSpecificItems(currentRecommendedMemories)
                Toast.makeText(this, "Selected ${currentRecommendedMemories.size} recommended weakest memories", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No recommendations available. Set a cleanup target.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnClearSelection.setOnClickListener {
            adapter.clearSelection()
        }

        etSearchCleanup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnDeleteSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "No memories selected for deletion.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            promptDeleteSelectedConfirmation(selectedIds)
        }
    }

    private fun loadMemoriesAndRefresh() {
        rankedMemoriesList = MemoryStorageManager.getRankedMemoriesWithDetails(this)
        adapter.updateData(rankedMemoriesList)

        val status = MemoryStorageManager.getStorageStatus(this)
        val count = rankedMemoriesList.size
        tvCleanupVaultSummary.text = "${status.usedFormatted} used across $count memories (Phone Free: ${status.deviceFreeFormatted})"

        if (rankedMemoriesList.isEmpty()) {
            tvEmptyCleanup.visibility = View.VISIBLE
            rvCleanupMemories.visibility = View.GONE
        } else {
            tvEmptyCleanup.visibility = View.GONE
            rvCleanupMemories.visibility = View.VISIBLE
        }

        recalculateTargetAndRecommendations()
    }

    private fun recalculateTargetAndRecommendations() {
        if (rankedMemoriesList.isEmpty()) {
            disableActionButtons()
            tvTargetRecommendationStatus.text = "No memories available to delete."
            return
        }

        val checkedId = rgCleanupTargetMode.checkedRadioButtonId
        if (checkedId == R.id.rbTargetNone) {
            currentRecommendedMemories = emptyList()
            disableActionButtons()
            tvTargetRecommendationStatus.text = "Browsing all memories. Select items manually below or choose a target mode."
            return
        }

        val inputStr = etTargetAmount.text.toString().trim()
        val numValue = inputStr.toDoubleOrNull()
        if (numValue == null || numValue <= 0.0) {
            currentRecommendedMemories = emptyList()
            disableActionButtons()
            tvTargetRecommendationStatus.text = "Enter a positive number to set a cleanup target."
            return
        }

        if (checkedId == R.id.rbTargetBySize) {
            val selectedUnit = spTargetUnit.selectedItem?.toString() ?: MemoryStorageManager.UNIT_MB
            val multiplier = when (selectedUnit) {
                MemoryStorageManager.UNIT_B -> 1L
                MemoryStorageManager.UNIT_KB -> 1024L
                MemoryStorageManager.UNIT_MB -> 1024L * 1024L
                MemoryStorageManager.UNIT_GB -> 1024L * 1024L * 1024L
                else -> 1024L * 1024L
            }
            val targetBytes = (numValue * multiplier).toLong()
            targetBytesToFree = targetBytes
            currentRecommendedMemories = MemoryStorageManager.findRecommendedMemoriesToFreeBytes(this, targetBytes)

            val estFreed = currentRecommendedMemories.sumOf { MemoryStorageManager.calculateMemoryItemSizeBytes(it) }
            val count = currentRecommendedMemories.size

            enableActionButtons(count, estFreed)
            tvTargetRecommendationStatus.text = "Target: Free ${MemoryStorageManager.formatBytes(targetBytes)} • Recommends $count weakest memories (~${MemoryStorageManager.formatBytes(estFreed)})"
        } else {
            val targetCount = numValue.toInt().coerceAtLeast(1)
            currentRecommendedMemories = MemoryStorageManager.findRecommendedMemoriesByCount(this, targetCount)
            val estFreed = currentRecommendedMemories.sumOf { MemoryStorageManager.calculateMemoryItemSizeBytes(it) }
            val count = currentRecommendedMemories.size

            enableActionButtons(count, estFreed)
            tvTargetRecommendationStatus.text = "Target: Clear $targetCount memories • Recommends $count weakest memories (~${MemoryStorageManager.formatBytes(estFreed)})"
        }
    }

    private fun enableActionButtons(count: Int, estFreed: Long) {
        btnAutoDeleteWeakest.alpha = 1.0f
        btnAutoDeleteWeakest.isClickable = true
        tvAutoDeleteBtnText.text = "Auto-Delete $count Memories"

        btnSelectRecommended.alpha = 1.0f
        btnSelectRecommended.isClickable = true
        tvSelectRecommendedBtnText.text = "Select Recommended ($count)"
    }

    private fun disableActionButtons() {
        btnAutoDeleteWeakest.alpha = 0.45f
        btnAutoDeleteWeakest.isClickable = false
        tvAutoDeleteBtnText.text = "Auto-Delete Target"

        btnSelectRecommended.alpha = 0.45f
        btnSelectRecommended.isClickable = false
        tvSelectRecommendedBtnText.text = "Select Recommended"
    }

    private fun updateBottomSelectionBar(count: Int, bytes: Long) {
        tvBottomSelectedSummary.text = if (count == 1) "1 memory selected" else "$count memories selected"
        tvBottomFreedEstimate.text = "${MemoryStorageManager.formatBytes(bytes)} to be reclaimed"

        if (count > 0) {
            btnDeleteSelected.alpha = 1.0f
            btnDeleteSelected.isClickable = true
        } else {
            btnDeleteSelected.alpha = 0.5f
            btnDeleteSelected.isClickable = false
        }
    }

    private fun promptAutoDeleteConfirmation(memoriesToPrune: List<MemoryItem>) {
        val totalBytes = memoriesToPrune.sumOf { MemoryStorageManager.calculateMemoryItemSizeBytes(it) }
        AlertDialog.Builder(this)
            .setTitle("Confirm Auto-Deletion")
            .setMessage("Are you sure you want to automatically delete ${memoriesToPrune.size} lowest-strength memories to reclaim ~${MemoryStorageManager.formatBytes(totalBytes)}?")
            .setPositiveButton("Delete Memories") { dialog, _ ->
                val freed = MemoryStorageManager.executePruning(this, memoriesToPrune)
                Toast.makeText(this, "Pruned ${memoriesToPrune.size} memories (Freed ${MemoryStorageManager.formatBytes(freed)})", Toast.LENGTH_SHORT).show()
                dialog.dismiss()

                // If launched from limit reduction workflow, also apply the new limit
                if (targetNewLimitBytes > 0L) {
                    val unit = MemoryStorageManager.getLimitUnit(this)
                    val mult = MemoryStorageManager.getUnitMultiplier(unit)
                    val rawValue = targetNewLimitBytes.toDouble() / mult.toDouble()
                    MemoryStorageManager.setMemoryLimit(this, rawValue, unit)
                }

                loadMemoriesAndRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDeleteSelectedConfirmation(selectedIds: Set<String>) {
        val count = selectedIds.size
        var totalBytes = 0L
        for (item in rankedMemoriesList) {
            if (selectedIds.contains(item.memory.id)) {
                totalBytes += item.sizeBytes
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Selected Memories")
            .setMessage("Are you sure you want to permanently delete $count selected memories (~${MemoryStorageManager.formatBytes(totalBytes)})?")
            .setPositiveButton("Delete") { dialog, _ ->
                val freed = MemoryStorageManager.deleteMemoriesBatch(this, selectedIds)
                Toast.makeText(this, "Deleted $count memories (Freed ${MemoryStorageManager.formatBytes(freed)})", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadMemoriesAndRefresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMemoryDetailPreviewDialog(memory: MemoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_memory_detail, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvExpId: TextView = dialogView.findViewById(R.id.tvDetailExpId)
        val tvTag: TextView = dialogView.findViewById(R.id.tvDetailTag)
        val tvTimestamp: TextView = dialogView.findViewById(R.id.tvDetailTimestamp)
        val tvLocation: TextView = dialogView.findViewById(R.id.tvDetailLocation)
        val tvFullMessage: TextView = dialogView.findViewById(R.id.tvDetailFullMessage)
        val tvStrengthBadge: TextView = dialogView.findViewById(R.id.tvDetailStrengthBadge)

        tvExpId.text = memory.id
        tvTag.text = memory.tag
        tvTimestamp.text = memory.timestamp
        tvLocation.text = memory.location
        tvFullMessage.text = memory.message
        tvStrengthBadge.text = String.format("Strength: %.2f", memory.strength)

        dialog.show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    companion object {
        const val EXTRA_TARGET_BYTES_TO_FREE = "extra_target_bytes_to_free"
        const val EXTRA_TARGET_NEW_LIMIT = "extra_target_new_limit"
    }
}
