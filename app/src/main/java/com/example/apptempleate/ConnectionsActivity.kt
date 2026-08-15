package com.example.apptempleate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ConnectionsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnResetView: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var tvConnectionsCount: TextView
    private lateinit var dagGraph2DView: DagGraph2DView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applySavedTheme(this)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_connections)

        btnBack = findViewById(R.id.btnBack)
        btnResetView = findViewById(R.id.btnResetView)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        tvConnectionsCount = findViewById(R.id.tvConnectionsCount)
        dagGraph2DView = findViewById(R.id.dagGraph2DView)

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        btnResetView.setOnClickListener {
            dagGraph2DView.resetView()
        }

        btnZoomIn.setOnClickListener {
            dagGraph2DView.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            dagGraph2DView.zoomOut()
        }

        dagGraph2DView.onNodeSelectedListener = { memory, connectedEdges ->
            showNodeDetailsDialog(memory, connectedEdges)
        }

        render2DGraphPlane()
    }

    override fun onResume() {
        super.onResume()
        render2DGraphPlane()
    }

    private fun render2DGraphPlane() {
        val memories = MemoryVaultRepository.loadAllMemories(this)
        val allEdges = ExperienceDagRepository.loadAllEdges(this)

        if (allEdges.isEmpty()) {
            tvConnectionsCount.text = "0 Connections • Formed when memories are co-used in answers"
        } else {
            tvConnectionsCount.text = "${allEdges.size} Active Connection(s) in 2D Plane"
        }

        dagGraph2DView.setData(memories, allEdges)
    }

    private fun showNodeDetailsDialog(memory: MemoryItem, connectedEdges: List<DagEdge>) {
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

        tvTitle.text = "${memory.id}: ${memory.title}"

        if (connectedEdges.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            container.visibility = View.GONE
            tvSubHeader.text = "No non-zero strength connections for ${memory.id} yet."
        } else {
            tvEmpty.visibility = View.GONE
            container.visibility = View.VISIBLE
            container.removeAllViews()

            tvSubHeader.text = "${connectedEdges.size} non-zero connection(s) • S_ij > 0.0"

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
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            textSize = 14f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                        }

                        val strengthFormatted = String.format("%.3f", edge.strength)
                        val tvBadge = TextView(context).apply {
                            text = "S_ij = $strengthFormatted"
                            setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }

                        addView(tvConnectedId)
                        addView(tvBadge)
                    }

                    val strengthFormatted = String.format("%.3f", edge.strength)
                    val tvDetails = TextView(context).apply {
                        text = "Co-used Count (t): ${edge.usageCount} • Strength Weight: $strengthFormatted"
                        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
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
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
