package com.example.apptempleate

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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MemoryVaultActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etSearchVault: EditText
    private lateinit var rvMemoryList: RecyclerView
    private lateinit var fabAddMemory: FloatingActionButton

    private lateinit var adapter: MemoryVaultAdapter
    private val allMemories = ArrayList<MemoryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_memory_vault)

        btnBack = findViewById(R.id.btnBack)
        etSearchVault = findViewById(R.id.etSearchVault)
        rvMemoryList = findViewById(R.id.rvMemoryList)
        fabAddMemory = findViewById(R.id.fabAddMemory)

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        fabAddMemory.setOnClickListener {
            Toast.makeText(this, "Add new memory feature", Toast.LENGTH_SHORT).show()
        }

        // Load Real & Sample Memories Data from Repository
        loadMemoriesFromRepository()

        // Setup RecyclerView with Click Handler to open Detail Dialog
        adapter = MemoryVaultAdapter(allMemories) { memory ->
            showMemoryDetailDialog(memory)
        }

        rvMemoryList.layoutManager = LinearLayoutManager(this)
        rvMemoryList.adapter = adapter

        // Real-time Search Filter
        etSearchVault.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMemories(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadMemoriesFromRepository() {
        allMemories.clear()
        val loadedList = MemoryVaultRepository.loadAllMemories(this)
        allMemories.addAll(loadedList)
    }

    private fun showMemoryDetailDialog(memory: MemoryItem) {
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
        val rvDetailAttachments: RecyclerView = dialogView.findViewById(R.id.rvDetailAttachments)
        val ibEdit: ImageButton = dialogView.findViewById(R.id.ibDetailEdit)
        val ibDelete: ImageButton = dialogView.findViewById(R.id.ibDetailDelete)
        val btnWordsSynonyms: View = dialogView.findViewById(R.id.btnDetailWordsSynonyms)
        val btnDagConnections: View = dialogView.findViewById(R.id.btnDetailDagConnections)
        val btnClose: View = dialogView.findViewById(R.id.btnDetailClose)

        tvExpId.text = memory.id
        tvTag.text = memory.tag
        tvTimestamp.text = memory.timestamp
        tvLocation.text = memory.location
        tvFullMessage.text = memory.message

        if (memory.attachments.isNotEmpty()) {
            rvDetailAttachments.visibility = View.VISIBLE
            rvDetailAttachments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            rvDetailAttachments.adapter = MediaAttachmentAdapter(memory.attachments)
        } else {
            rvDetailAttachments.visibility = View.GONE
        }

        ibEdit.setOnClickListener {
            dialog.dismiss()
            showEditMemoryDialog(memory)
        }

        ibDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteMemoryConfirmationDialog(memory)
        }

        btnWordsSynonyms.setOnClickListener {
            showWordsAndSynonymsDialog(memory)
        }

        btnDagConnections.setOnClickListener {
            showDagConnectionsDialog(memory)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDagConnectionsDialog(memory: MemoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dag_connections, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle: TextView = dialogView.findViewById(R.id.tvDagDialogTitle)
        val tvSubHeader: TextView = dialogView.findViewById(R.id.tvDagSubHeader)
        val ibClose: ImageButton = dialogView.findViewById(R.id.ibDagClose)
        val btnDone: TextView = dialogView.findViewById(R.id.btnDagDone)
        val container: LinearLayout = dialogView.findViewById(R.id.llDagConnectionsContainer)
        val tvEmpty: TextView = dialogView.findViewById(R.id.tvEmptyDagMessage)

        tvTitle.text = "${memory.id} DAG Connections"

        val allEdges = ExperienceDagRepository.loadAllEdges(this)
        val connectedEdges = allEdges.filter { edge ->
            (edge.experienceId1.equals(memory.id, ignoreCase = true) ||
             edge.experienceId2.equals(memory.id, ignoreCase = true)) &&
            edge.strength > 0.0
        }

        if (connectedEdges.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            container.visibility = View.GONE
            tvSubHeader.text = "No active connections in DAG graph for ${memory.id}"
        } else {
            tvEmpty.visibility = View.GONE
            container.visibility = View.VISIBLE
            container.removeAllViews()

            tvSubHeader.text = "${connectedEdges.size} connected experience(s) • Formula: S_ij = S_old + (C×t)/N"

            for (edge in connectedEdges) {
                val isExp1Self = edge.experienceId1.equals(memory.id, ignoreCase = true)
                val targetId = if (isExp1Self) edge.experienceId2 else edge.experienceId1
                val targetTitle = if (isExp1Self) edge.title2 else edge.title1

                val cardView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 24, 28, 24)
                    setBackgroundResource(R.drawable.bg_metallic_card)

                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 16
                    }
                    layoutParams = lp

                    val headerRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL

                        val tvConnectedId = TextView(context).apply {
                            text = "$targetId: $targetTitle"
                            setTextColor(ContextCompat.getColor(this@MemoryVaultActivity, R.color.text_primary))
                            textSize = 14f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                        }

                        val strengthFormatted = String.format("%.3f", edge.strength)
                        val tvBadge = TextView(context).apply {
                            text = "S_ij = $strengthFormatted"
                            setTextColor(android.graphics.Color.parseColor("#2563EB"))
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }

                        addView(tvConnectedId)
                        addView(tvBadge)
                    }

                    val strengthFormatted = String.format("%.3f", edge.strength)
                    val tvDetails = TextView(context).apply {
                        text = "Co-used Count (t): ${edge.usageCount} • Connection Strength: $strengthFormatted"
                        setTextColor(ContextCompat.getColor(this@MemoryVaultActivity, R.color.text_tertiary))
                        textSize = 12f
                        setPadding(0, 6, 0, 0)
                    }

                    val sharedTermsText = if (edge.sharedTerms.isNotEmpty()) {
                        "Shared Words: " + edge.sharedTerms.joinToString(", ")
                    } else {
                        "Shared Terms: Semantic context overlap"
                    }

                    val tvTerms = TextView(context).apply {
                        text = sharedTermsText
                        setTextColor(ContextCompat.getColor(this@MemoryVaultActivity, R.color.text_secondary))
                        textSize = 11f
                        setPadding(0, 4, 0, 0)
                    }

                    addView(headerRow)
                    addView(tvDetails)
                    addView(tvTerms)
                }

                container.addView(cardView)
            }
        }

        ibClose.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showWordsAndSynonymsDialog(memory: MemoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_word_synonyms, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle: TextView = dialogView.findViewById(R.id.tvWordsDialogTitle)
        val ibClose: ImageButton = dialogView.findViewById(R.id.ibWordsClose)
        val btnDone: TextView = dialogView.findViewById(R.id.btnWordsDone)
        val rvList: RecyclerView = dialogView.findViewById(R.id.rvWordsSynonyms)
        val tvEmpty: TextView = dialogView.findViewById(R.id.tvEmptyWordsMessage)

        tvTitle.text = "${memory.id} Vocabulary & Synonyms"

        val wordItems = if (!memory.wordSynonymsJson.isNullOrEmpty()) {
            LinguisticAnalyzer.fromJsonString(memory.wordSynonymsJson)
        } else {
            LinguisticAnalyzer.extractWordsAndSynonyms(memory.message)
        }

        if (wordItems.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvList.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvList.visibility = View.VISIBLE
            rvList.layoutManager = LinearLayoutManager(this)
            rvList.adapter = WordsSynonymsAdapter(wordItems)
        }

        ibClose.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showEditMemoryDialog(memory: MemoryItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_experience, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvDialogTitle: TextView = dialogView.findViewById(R.id.tvEditExpDialogTitle)
        val etMessage: EditText = dialogView.findViewById(R.id.etEditExpMessage)
        val btnCancel: View = dialogView.findViewById(R.id.btnEditExpCancel)
        val btnSave: View = dialogView.findViewById(R.id.btnEditExpSave)

        tvDialogTitle.text = "Edit ${memory.id}"
        etMessage.setText(memory.message)
        etMessage.setSelection(memory.message.length)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newMessage = etMessage.text.toString().trim()
            if (newMessage.isNotEmpty()) {
                val newTitle = if (newMessage.length > 32) newMessage.take(32) + "..." else newMessage
                val newSnippet = if (newMessage.length > 70) newMessage.take(70) + "..." else newMessage
                
                val updatedItem = memory.copy(
                    title = newTitle,
                    snippet = newSnippet,
                    message = newMessage
                )

                MemoryVaultRepository.updateMemory(this, updatedItem)
                loadMemoriesFromRepository()
                adapter.updateList(allMemories)
                Toast.makeText(this, "Experience updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteMemoryConfirmationDialog(memory: MemoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Experience")
            .setMessage("Are you sure you want to delete ${memory.id} from Memory Vault?")
            .setPositiveButton("Delete") { dialog, _ ->
                MemoryVaultRepository.deleteMemory(this, memory.id)
                loadMemoriesFromRepository()
                adapter.updateList(allMemories)
                Toast.makeText(this, "Experience deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun filterMemories(query: String) {
        val filtered = if (query.isEmpty()) {
            allMemories
        } else {
            allMemories.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.snippet.contains(query, ignoreCase = true) ||
                it.message.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true) ||
                it.tag.contains(query, ignoreCase = true)
            }
        }
        adapter.updateList(filtered)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
