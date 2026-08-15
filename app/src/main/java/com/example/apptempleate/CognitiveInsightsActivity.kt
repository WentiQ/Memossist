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
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class CognitiveInsightsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvHumanoidAiInsightStatement: TextView

    // 8 Key Stats Grid
    private lateinit var tvStatTotalMemories: TextView
    private lateinit var tvStatDagEdges: TextView
    private lateinit var tvStatClusters: TextView
    private lateinit var tvStatOrphans: TextView
    private lateinit var tvStatAvgResponseTime: TextView
    private lateinit var tvStatTotalDataSize: TextView
    private lateinit var tvStatParamSpeed: TextView
    private lateinit var tvStatParamCount: TextView
    private lateinit var tvStatAvgStrength: TextView
    private lateinit var tvStatAvgHalfLife: TextView

    // DAG Topology Card
    private lateinit var tvTopologyStatus: TextView
    private lateinit var tvConnectedRatio: TextView
    private lateinit var pbConnectedRatio: ProgressBar
    private lateinit var tvClusterSummary: TextView

    // 2nd LLM Parameter Engine Card
    private lateinit var tvParamEngineStatus: TextView
    private lateinit var tvParamEngineDetails: TextView
    private lateinit var tvParamLastDuration: TextView
    private lateinit var tvParamTotalFacts: TextView
    private lateinit var tvParamDecayHealth: TextView

    private data class ClusterResult(
        val clustersCount: Int,
        val orphansCount: Int,
        val clusterDetails: List<List<MemoryItem>>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

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

    override fun onResume() {
        super.onResume()
        loadAndCalculateRealTimeInsights()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvHumanoidAiInsightStatement = findViewById(R.id.tvHumanoidAiInsightStatement)

        tvStatTotalMemories = findViewById(R.id.tvStatTotalMemories)
        tvStatDagEdges = findViewById(R.id.tvStatDagEdges)
        tvStatClusters = findViewById(R.id.tvStatClusters)
        tvStatOrphans = findViewById(R.id.tvStatOrphans)
        tvStatAvgResponseTime = findViewById(R.id.tvStatAvgResponseTime)
        tvStatTotalDataSize = findViewById(R.id.tvStatTotalDataSize)
        tvStatParamSpeed = findViewById(R.id.tvStatParamSpeed)
        tvStatParamCount = findViewById(R.id.tvStatParamCount)
        tvStatAvgStrength = findViewById(R.id.tvStatAvgStrength)
        tvStatAvgHalfLife = findViewById(R.id.tvStatAvgHalfLife)

        tvTopologyStatus = findViewById(R.id.tvTopologyStatus)
        tvConnectedRatio = findViewById(R.id.tvConnectedRatio)
        pbConnectedRatio = findViewById(R.id.pbConnectedRatio)
        tvClusterSummary = findViewById(R.id.tvClusterSummary)

        tvParamEngineStatus = findViewById(R.id.tvParamEngineStatus)
        tvParamEngineDetails = findViewById(R.id.tvParamEngineDetails)
        tvParamLastDuration = findViewById(R.id.tvParamLastDuration)
        tvParamTotalFacts = findViewById(R.id.tvParamTotalFacts)
        tvParamDecayHealth = findViewById(R.id.tvParamDecayHealth)
    }

    private fun loadAndCalculateRealTimeInsights() {
        val allMemories = MemoryVaultRepository.loadAllMemories(this)
        val dagEdges = ExperienceDagRepository.loadAllEdges(this)
        val (avgSpeedSec, _) = ResponseStatsRepository.getStats(this)
        val prefs = getSharedPreferences("MemossistPrefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Dinesh") ?: "Dinesh"

        val memoriesCount = allMemories.size
        val dagEdgesCount = dagEdges.size

        // 1. DAG Graph Clustering & Orphans Calculation
        val clusterResult = calculateDagClustersAndOrphans(allMemories, dagEdges)
        val clustersCount = clusterResult.clustersCount
        val orphansCount = clusterResult.orphansCount

        // 2. Estimated Response Time for Next Message in Chat
        val estNextSec = if (avgSpeedSec > 0f) {
            avgSpeedSec
        } else {
            ResponseStatsRepository.getLastDuration(this)
        }

        val avgResponseStr = if (estNextSec > 0f) {
            if (estNextSec < 1.0f) String.format(Locale.US, "%.2fs", estNextSec) else String.format(Locale.US, "%.1fs", estNextSec)
        } else {
            "--"
        }

        // 3. Memory Vault Total Data Added Calculation (Text + Attachments)
        val totalVaultBytes = calculateTotalMemoryVaultDataSize(allMemories)
        val formattedDataSize = formatBytes(totalVaultBytes)

        // 4. 2nd LLM Parameter Evaluation Statistics
        val avgParamSec = ParameterStatsRepository.getAvgDurationSeconds(this)
        val lastParamSec = ParameterStatsRepository.getLastDurationSeconds(this)
        val totalParamCount = ParameterStatsRepository.getTotalEvaluationsCount(this)
        val formattedParamAvgSpeed = ParameterStatsRepository.formatDuration(avgParamSec)
        val formattedParamLastSpeed = ParameterStatsRepository.formatDuration(lastParamSec)

        // 5. Memory Retention & Decay Metrics
        val avgStrength = if (allMemories.isNotEmpty()) {
            allMemories.map { MemoryDecayCalculator.calculateCurrentStrength(it) }.average()
        } else {
            0.0
        }
        val avgHalfLife = if (allMemories.isNotEmpty()) {
            allMemories.map { MemoryDecayCalculator.calculateHalfLifeDays(it.importance, it.stability) }.average()
        } else {
            0.0
        }
        val healthyMemoriesCount = allMemories.count { MemoryDecayCalculator.calculateCurrentStrength(it) >= 0.50 }

        // 6. Update Grid Cards
        tvStatTotalMemories.text = "$memoriesCount"
        tvStatDagEdges.text = "$dagEdgesCount"
        tvStatClusters.text = "$clustersCount"
        tvStatOrphans.text = "$orphansCount"
        tvStatAvgResponseTime.text = avgResponseStr
        tvStatTotalDataSize.text = formattedDataSize

        tvStatParamSpeed.text = formattedParamAvgSpeed
        tvStatParamCount.text = if (totalParamCount > 0) "#$totalParamCount facts scored" else "Avg parameter scoring"
        tvStatAvgStrength.text = String.format(Locale.US, "%.2f", avgStrength)
        tvStatAvgHalfLife.text = String.format(Locale.US, "Avg half-life: %.0fd", avgHalfLife)

        // 7. Update DAG Topology Analysis Card
        val connectedMemoriesCount = (memoriesCount - orphansCount).coerceAtLeast(0)
        val connectedPct = if (memoriesCount > 0) (connectedMemoriesCount * 100) / memoriesCount else 0

        tvConnectedRatio.text = "$connectedPct% Connected ($connectedMemoriesCount of $memoriesCount)"
        pbConnectedRatio.progress = connectedPct

        tvTopologyStatus.text = when {
            clustersCount >= 3 -> "High Network Density"
            clustersCount > 0 -> "Active Synaptic Clusters"
            memoriesCount > 0 -> "Initial Graph Mapping"
            else -> "Vault Empty"
        }

        tvClusterSummary.text = "Identified $clustersCount connected cluster(s) and $orphansCount orphan memory item(s) in your DAG graph."

        // 8. Update 2nd LLM Parameter Engine Card
        tvParamEngineStatus.text = if (totalParamCount > 0) "Active Parallel Context" else "Ready in Background"
        tvParamEngineDetails.text = "2nd LLM scores Importance, Confidence, and Stability dynamically per extracted fact in background. Average scoring time is $formattedParamAvgSpeed."
        tvParamLastDuration.text = "Last Latency: $formattedParamLastSpeed"
        tvParamTotalFacts.text = "Total Facts Scored: $totalParamCount"
        tvParamDecayHealth.text = "Retention Health: $healthyMemoriesCount of $memoriesCount memories in high-strength state. Vault half-life average is ${String.format(Locale.US, "%.0f", avgHalfLife)} days."

        // 9. Humanoid AI Insight Statement Synthesis
        tvHumanoidAiInsightStatement.text = "Hello $userName! Your Memory Vault holds $memoriesCount memories ($formattedDataSize) with an average strength of ${String.format(Locale.US, "%.2f", avgStrength)}. Graph topology reveals $dagEdgesCount synaptic edges across $clustersCount cluster(s). 2nd LLM scores fact parameters in $formattedParamAvgSpeed on average without delaying chat responses."
    }

    private fun calculateDagClustersAndOrphans(
        allMemories: List<MemoryItem>,
        dagEdges: List<DagEdge>
    ): ClusterResult {
        if (allMemories.isEmpty()) {
            return ClusterResult(0, 0, emptyList())
        }

        val memoryMap = allMemories.associateBy { it.id }
        val validMemoryIds = memoryMap.keys

        // 1. Build adjacency list of connected node IDs (only valid memory IDs in current vault)
        val adjacencyMap = mutableMapOf<String, MutableSet<String>>()
        for (mem in allMemories) {
            adjacencyMap[mem.id] = mutableSetOf()
        }

        for (edge in dagEdges) {
            val id1 = edge.experienceId1
            val id2 = edge.experienceId2
            if (edge.strength > 0.0 && validMemoryIds.contains(id1) && validMemoryIds.contains(id2) && id1 != id2) {
                adjacencyMap[id1]?.add(id2)
                adjacencyMap[id2]?.add(id1)
            }
        }

        // 2. Compute degrees and count orphans (nodes with degree == 0)
        var orphansCount = 0
        val nonOrphanIds = mutableListOf<String>()

        for (mem in allMemories) {
            val degree = adjacencyMap[mem.id]?.size ?: 0
            if (degree == 0) {
                orphansCount++
            } else {
                nonOrphanIds.add(mem.id)
            }
        }

        // 3. Graph traversal (BFS) to identify connected components with >= 2 nodes
        val visited = mutableSetOf<String>()
        val clustersList = mutableListOf<List<MemoryItem>>()

        for (id in nonOrphanIds) {
            if (visited.contains(id)) continue

            val componentMemoryItems = mutableListOf<MemoryItem>()
            val queue = ArrayDeque<String>()
            queue.add(id)
            visited.add(id)

            while (queue.isNotEmpty()) {
                val currId = queue.removeFirst()
                val mem = memoryMap[currId]
                if (mem != null) {
                    componentMemoryItems.add(mem)
                }
                val neighbors = adjacencyMap[currId] ?: emptySet()
                for (neighbor in neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }

            if (componentMemoryItems.size >= 2) {
                clustersList.add(componentMemoryItems)
            }
        }

        return ClusterResult(
            clustersCount = clustersList.size,
            orphansCount = orphansCount,
            clusterDetails = clustersList.sortedByDescending { it.size }
        )
    }

    private fun calculateTotalMemoryVaultDataSize(allMemories: List<MemoryItem>): Long {
        var totalBytes = 0L

        // 1. Serialized JSON storage file size for memory vault
        val vaultFile = File(filesDir, "memossist_vault_memories.json")
        if (vaultFile.exists()) {
            totalBytes += vaultFile.length()
        } else {
            for (mem in allMemories) {
                val textContent = "${mem.id}${mem.title}${mem.snippet}${mem.message}${mem.tag}${mem.location}${mem.timestamp}${mem.wordSynonymsJson.orEmpty()}"
                totalBytes += textContent.toByteArray(Charsets.UTF_8).size
            }
        }

        // 2. Media attachment files specifically attached to memory vault items
        val processedFilePaths = mutableSetOf<String>()
        for (mem in allMemories) {
            for (att in mem.attachments) {
                val path = att.filePath
                if (path.isNotBlank() && !processedFilePaths.contains(path)) {
                    processedFilePaths.add(path)
                    val f = File(path)
                    if (f.exists()) {
                        totalBytes += f.length()
                    } else if (att.fileSize > 0) {
                        totalBytes += att.fileSize
                    }
                }
            }
        }

        return totalBytes
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            else -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
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
