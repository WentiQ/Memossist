package com.example.apptempleate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CognitiveInsightsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvHumanoidAiInsightStatement: TextView

    private lateinit var tvCognitiveScorePercent: TextView
    private lateinit var tvCognitiveScoreBadge: TextView
    private lateinit var pbCognitiveScore: ProgressBar

    private lateinit var tvStatTotalMemories: TextView
    private lateinit var tvStatDagEdges: TextView
    private lateinit var tvStatAvgSpeed: TextView
    private lateinit var tvStatTotalReminders: TextView

    private lateinit var tvDagDensityStatus: TextView
    private lateinit var tvDagNodesCount: TextView
    private lateinit var tvDagEdgesCount: TextView
    private lateinit var llTopHubNodesContainer: LinearLayout

    private lateinit var pbFactsCategory: ProgressBar
    private lateinit var tvFactsCategoryLabel: TextView
    private lateinit var pbRemindersCategory: ProgressBar
    private lateinit var tvRemindersCategoryLabel: TextView
    private lateinit var pbMediaCategory: ProgressBar
    private lateinit var tvMediaCategoryLabel: TextView

    private lateinit var tvRemindersCompletedCount: TextView
    private lateinit var pbRemindersCompletion: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_cognitive_insights)

        initViews()

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        loadAndCalculateRealTimeInsights()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvHumanoidAiInsightStatement = findViewById(R.id.tvHumanoidAiInsightStatement)

        tvCognitiveScorePercent = findViewById(R.id.tvCognitiveScorePercent)
        tvCognitiveScoreBadge = findViewById(R.id.tvCognitiveScoreBadge)
        pbCognitiveScore = findViewById(R.id.pbCognitiveScore)

        tvStatTotalMemories = findViewById(R.id.tvStatTotalMemories)
        tvStatDagEdges = findViewById(R.id.tvStatDagEdges)
        tvStatAvgSpeed = findViewById(R.id.tvStatAvgSpeed)
        tvStatTotalReminders = findViewById(R.id.tvStatTotalReminders)

        tvDagDensityStatus = findViewById(R.id.tvDagDensityStatus)
        tvDagNodesCount = findViewById(R.id.tvDagNodesCount)
        tvDagEdgesCount = findViewById(R.id.tvDagEdgesCount)
        llTopHubNodesContainer = findViewById(R.id.llTopHubNodesContainer)

        pbFactsCategory = findViewById(R.id.pbFactsCategory)
        tvFactsCategoryLabel = findViewById(R.id.tvFactsCategoryLabel)
        pbRemindersCategory = findViewById(R.id.pbRemindersCategory)
        tvRemindersCategoryLabel = findViewById(R.id.tvRemindersCategoryLabel)
        pbMediaCategory = findViewById(R.id.pbMediaCategory)
        tvMediaCategoryLabel = findViewById(R.id.tvMediaCategoryLabel)

        tvRemindersCompletedCount = findViewById(R.id.tvRemindersCompletedCount)
        pbRemindersCompletion = findViewById(R.id.pbRemindersCompletion)
    }

    private fun loadAndCalculateRealTimeInsights() {
        val allMemories = MemoryVaultRepository.loadAllMemories(this)
        val dagEdges = ExperienceDagRepository.loadAllEdges(this)
        val (avgSpeedSec, totalQueries) = ResponseStatsRepository.getStats(this)
        val allReminders = ReminderRepository.loadAllReminders(this)
        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"

        val memoriesCount = allMemories.size
        val dagEdgesCount = dagEdges.size
        val remindersCount = allReminders.size

        // 1. Calculate Real-Time Cognitive Health Score %
        val healthScore = if (memoriesCount == 0) {
            100
        } else {
            (60 + (memoriesCount * 2) + (dagEdgesCount * 1) + (totalQueries * 0.5)).toInt().coerceIn(65, 99)
        }

        tvCognitiveScorePercent.text = "$healthScore%"
        pbCognitiveScore.progress = healthScore

        tvCognitiveScoreBadge.text = when {
            healthScore >= 90 -> "Optimal Neural Density 🌟"
            healthScore >= 80 -> "High Synaptic Retention ⚡"
            else -> "Growing Knowledge Base 🧠"
        }

        // 2. Set Top 4 Quick Stats
        tvStatTotalMemories.text = "$memoriesCount"
        tvStatDagEdges.text = "$dagEdgesCount"
        tvStatAvgSpeed.text = if (avgSpeedSec > 0f) String.format("%.1fs", avgSpeedSec) else "--"
        tvStatTotalReminders.text = "$remindersCount"

        // 3. DAG Graph Network Analysis
        tvDagNodesCount.text = "• $memoriesCount Vault Nodes"
        tvDagEdgesCount.text = "• $dagEdgesCount Synaptic Connections"

        val edgeToNodeRatio = if (memoriesCount > 1) (dagEdgesCount.toFloat() / memoriesCount.toFloat()) else 0f
        tvDagDensityStatus.text = when {
            edgeToNodeRatio >= 1.5f -> "High Synaptic Density"
            edgeToNodeRatio >= 0.8f -> "Moderate Synaptic Coupling"
            else -> "Initial Synaptic Mapping"
        }

        // Compute Top Connected Memory Hubs
        populateTopHubNodes(allMemories, dagEdges)

        // 4. Vault Knowledge Distribution
        val chatFactsCount = allMemories.count { it.tag.contains("Fact", ignoreCase = true) || it.tag.contains("Chat", ignoreCase = true) }
        val remindersMemCount = allMemories.count { it.tag.contains("Reminder", ignoreCase = true) }
        val totalMediaAttachments = allMemories.sumOf { it.attachments.size }

        val factsPct = if (memoriesCount > 0) (chatFactsCount * 100) / memoriesCount else 0
        val remindersPct = if (memoriesCount > 0) (remindersMemCount * 100) / memoriesCount else 0
        val mediaPct = if (memoriesCount > 0) ((totalMediaAttachments.coerceAtMost(memoriesCount)) * 100) / memoriesCount else 0

        pbFactsCategory.progress = factsPct.coerceAtLeast(5)
        tvFactsCategoryLabel.text = "$chatFactsCount items ($factsPct%)"

        pbRemindersCategory.progress = remindersPct.coerceAtLeast(5)
        tvRemindersCategoryLabel.text = "$remindersMemCount items ($remindersPct%)"

        pbMediaCategory.progress = mediaPct.coerceAtLeast(5)
        tvMediaCategoryLabel.text = "$totalMediaAttachments files ($mediaPct%)"

        // 5. Reminder Completion Rate
        val completedCount = allReminders.count { it.isCompleted }
        val completionRatePct = if (remindersCount > 0) (completedCount * 100) / remindersCount else 100

        tvRemindersCompletedCount.text = "$completedCount of $remindersCount Completed ($completionRatePct%)"
        pbRemindersCompletion.progress = completionRatePct

        // 6. Humanoid AI Insight Statement Synthesis
        val speedStr = if (avgSpeedSec > 0f) String.format("%.1fs", avgSpeedSec) else "real-time"
        tvHumanoidAiInsightStatement.text = "Hello $userName! Your Memory Vault stores $memoriesCount memories linked across $dagEdgesCount DAG synaptic edges. Your AI response speed averages $speedStr across $totalQueries processed queries with $completedCount of $remindersCount reminders completed."
    }

    private fun populateTopHubNodes(allMemories: List<MemoryItem>, dagEdges: List<DagEdge>) {
        llTopHubNodesContainer.removeAllViews()

        if (allMemories.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No core memory hubs registered yet. Start chatting to build connections!"
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            llTopHubNodesContainer.addView(emptyTv)
            return
        }

        // Count degree connections per experience ID
        val degreeMap = mutableMapOf<String, Int>()
        for (edge in dagEdges) {
            degreeMap[edge.experienceId1] = (degreeMap[edge.experienceId1] ?: 0) + 1
            degreeMap[edge.experienceId2] = (degreeMap[edge.experienceId2] ?: 0) + 1
        }

        val topMemories = allMemories.sortedByDescending { degreeMap[it.id] ?: 0 }.take(3)

        for (mem in topMemories) {
            val connectionsCount = degreeMap[mem.id] ?: 0
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_workspace_reminder, llTopHubNodesContainer, false)

            val tvIcon: TextView = rowView.findViewById(R.id.tvWsReminderIcon)
            val tvStatement: TextView = rowView.findViewById(R.id.tvWsReminderStatement)

            tvIcon.text = "🧠"
            tvStatement.text = "${mem.title} — ($connectionsCount synaptic connections)\n${mem.snippet}"

            rowView.setOnClickListener {
                val intent = Intent(this, MemoryVaultActivity::class.java)
                startActivity(intent)
            }

            llTopHubNodesContainer.addView(rowView)
        }
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
