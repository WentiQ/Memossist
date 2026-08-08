package com.example.apptempleate

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ConnectionsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvConnectionsCount: TextView
    private lateinit var llDagEdgesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_connections)

        btnBack = findViewById(R.id.btnBack)
        tvConnectionsCount = findViewById(R.id.tvConnectionsCount)
        llDagEdgesContainer = findViewById(R.id.llDagEdgesContainer)

        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }

        renderDagGraphConnections()
    }

    override fun onResume() {
        super.onResume()
        renderDagGraphConnections()
    }

    private fun renderDagGraphConnections() {
        val edges = ExperienceDagRepository.loadAllEdges(this)
        tvConnectionsCount.text = "${edges.size} Active Semantic Edges in DAG Graph"

        llDagEdgesContainer.removeAllViews()

        if (edges.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No active DAG connections yet. Ask questions in Chat to construct semantic edges across your experiences!"
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 13f
                setPadding(0, 16, 0, 16)
            }
            llDagEdgesContainer.addView(emptyTv)
            return
        }

        for (edge in edges) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 28, 32, 28)
                setBackgroundResource(R.drawable.bg_metallic_card)

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 20
                }
                layoutParams = lp

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    val tvPair = TextView(context).apply {
                        text = "${edge.title1} ↔ ${edge.title2}"
                        setTextColor(Color.parseColor("#111827"))
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    }

                    val strengthFormatted = String.format("%.3f", edge.strength)
                    val tvBadge = TextView(context).apply {
                        text = "S_ij = $strengthFormatted"
                        setTextColor(Color.parseColor("#2563EB"))
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                    }

                    addView(tvPair)
                    addView(tvBadge)
                }

                val tvDetails = TextView(context).apply {
                    text = "Formula S_ij = S_old + (C×t)/N • Co-used in ${edge.usageCount} responses"
                    setTextColor(Color.parseColor("#4B5563"))
                    textSize = 12f
                    setPadding(0, 8, 0, 0)
                }

                val sharedTermsText = if (edge.sharedTerms.isNotEmpty()) {
                    "Shared Vocabulary Q ∩ N1 ∩ N2: " + edge.sharedTerms.take(5).joinToString(", ")
                } else {
                    "Shared Terms: Semantic context overlap"
                }

                val tvTerms = TextView(context).apply {
                    text = sharedTermsText
                    setTextColor(Color.parseColor("#6B7280"))
                    textSize = 11f
                    setPadding(0, 4, 0, 0)
                }

                addView(headerRow)
                addView(tvDetails)
                addView(tvTerms)
            }

            llDagEdgesContainer.addView(cardView)
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
