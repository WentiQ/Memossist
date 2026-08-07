package com.example.apptempleate

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
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

        // Initialize Sample Memories Data
        loadSampleMemories()

        // Setup RecyclerView
        adapter = MemoryVaultAdapter(allMemories) { memory ->
            Toast.makeText(this, "Opened: ${memory.title}", Toast.LENGTH_SHORT).show()
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

    private fun loadSampleMemories() {
        allMemories.add(
            MemoryItem(
                id = "1",
                title = "Quarterly Strategy & Cognitive Notes",
                snippet = "Key takeaways from the strategy session covering neural architecture designs, memory retrieval benchmarks, and UI state flows.",
                tag = "Audio",
                timeAgo = "4 mins ago",
                isPinned = true
            )
        )
        allMemories.add(
            MemoryItem(
                id = "2",
                title = "AI Agentic Workflow Ideas",
                snippet = "Explored multi-agent delegation patterns for autonomous contextual search and live tactile background rendering.",
                tag = "Idea",
                timeAgo = "2 hours ago",
                isPinned = true
            )
        )
        allMemories.add(
            MemoryItem(
                id = "3",
                title = "Product Roadmap & UI Polish Transcript",
                snippet = "Transcript of live voice conversation discussing smooth swipe gestures, sidebar navigation, and clean white interface styling.",
                tag = "Document",
                timeAgo = "Yesterday"
            )
        )
        allMemories.add(
            MemoryItem(
                id = "4",
                title = "Voice Note on Neural Memory Retention",
                snippet = "Recorded thoughts on long-term cognitive indexing and instant context recall algorithms.",
                tag = "Audio",
                timeAgo = "2 days ago"
            )
        )
        allMemories.add(
            MemoryItem(
                id = "5",
                title = "Design System Color Tokens",
                snippet = "Monochrome light palette specification with dark slate typography and subtle card stroke borders.",
                tag = "Idea",
                timeAgo = "3 days ago"
            )
        )
    }

    private fun filterMemories(query: String) {
        val filtered = if (query.isEmpty()) {
            allMemories
        } else {
            allMemories.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.snippet.contains(query, ignoreCase = true) ||
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
