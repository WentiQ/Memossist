package com.example.apptempleate

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        val btnClose: View = dialogView.findViewById(R.id.btnDetailClose)

        tvExpId.text = memory.id
        tvTag.text = memory.tag
        tvTimestamp.text = memory.timestamp
        tvLocation.text = memory.location
        tvFullMessage.text = memory.message

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
