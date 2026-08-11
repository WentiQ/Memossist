package com.example.apptempleate

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ModelMarketplaceActivity : AppCompatActivity() {

    private lateinit var btnBackMarketplace: ImageButton
    private lateinit var tvDeviceRamInfo: TextView
    private lateinit var tvRecommendedModelText: TextView
    private lateinit var llFilterCategoryContainer: LinearLayout
    private lateinit var tvCatalogCountHeader: TextView
    private lateinit var rvModelCatalog: RecyclerView

    private lateinit var adapter: ModelMarketplaceAdapter
    private var selectedCategory: String = "All"

    private val categories = listOf("All", "⚡ Fastest", "🧠 Smartest", "👁️ Multimodal", "📚 Long Context", "💻 Coding", "📱 Low Memory")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_model_marketplace)

        btnBackMarketplace = findViewById(R.id.btnBackMarketplace)
        tvDeviceRamInfo = findViewById(R.id.tvDeviceRamInfo)
        tvRecommendedModelText = findViewById(R.id.tvRecommendedModelText)
        llFilterCategoryContainer = findViewById(R.id.llFilterCategoryContainer)
        tvCatalogCountHeader = findViewById(R.id.tvCatalogCountHeader)
        rvModelCatalog = findViewById(R.id.rvModelCatalog)

        btnBackMarketplace.setOnClickListener {
            finishWithSmoothAnimation()
        }

        // Setup Hardware Recommendation Banner
        setupHardwareAnalysis()

        // Setup Category Filter Chips
        setupFilterChips()

        // Setup RecyclerView
        rvModelCatalog.layoutManager = LinearLayoutManager(this)
        adapter = ModelMarketplaceAdapter(
            context = this,
            modelsList = ModelCatalog.models,
            onModelClick = { model ->
                showModelDetailSheet(model)
            },
            onSelectClick = { model ->
                val isDownloaded = NoeonAiEngine.isModelDownloaded(this, model.id)
                val isDownloading = BackgroundModelDownloadManager.isModelDownloading(model.id)
                if (!isDownloaded && !isDownloading) {
                    BackgroundModelDownloadManager.startDownload(this, model)
                    Toast.makeText(this, "Started background download for ${model.name} (${model.downloadSizeMb} MB)", Toast.LENGTH_LONG).show()
                    refreshCatalogList()
                } else {
                    showModelDetailSheet(model)
                }
            }
        )
        rvModelCatalog.adapter = adapter

        refreshCatalogList()

        BackgroundModelDownloadManager.registerListener(downloadListener)
    }

    private val downloadListener: (ModelDownloadProgress) -> Unit = { _ ->
        refreshCatalogList()
    }

    override fun onResume() {
        super.onResume()
        refreshCatalogList()
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundModelDownloadManager.unregisterListener(downloadListener)
    }

    private fun setupHardwareAnalysis() {
        val totalRamGb = ModelCatalog.getTotalDeviceRamGb(this)
        val recommendedModel = ModelCatalog.getRecommendedModelForDevice(this)

        val ramFormatted = String.format("%.1f", totalRamGb)
        tvDeviceRamInfo.text = "Your device has approximately $ramFormatted GB RAM."
        tvRecommendedModelText.text = "We recommend ${recommendedModel.name} for the best balance between intelligence and speed on your phone."
    }

    private fun setupFilterChips() {
        llFilterCategoryContainer.removeAllViews()

        for (cat in categories) {
            val isSelected = cat == selectedCategory

            val chipTv = TextView(this).apply {
                text = cat
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(36, 18, 36, 18)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 16, 0)
                }
                layoutParams = params

                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_chip_selected)
                    setTextColor(ContextCompat.getColor(this@ModelMarketplaceActivity, R.color.app_window_background))
                } else {
                    setBackgroundResource(R.drawable.bg_chip_unselected)
                    setTextColor(ContextCompat.getColor(this@ModelMarketplaceActivity, R.color.text_secondary))
                }

                setOnClickListener {
                    selectedCategory = cat
                    setupFilterChips()
                    refreshCatalogList()
                }
            }
            llFilterCategoryContainer.addView(chipTv)
        }
    }

    private fun refreshCatalogList() {
        val filteredList = if (selectedCategory == "All") {
            ModelCatalog.models
        } else {
            val filterKey = selectedCategory.replace("⚡ ", "").replace("🧠 ", "").replace("👁️ ", "").replace("📚 ", "").replace("💻 ", "").replace("📱 ", "")
            ModelCatalog.models.filter { model ->
                model.categoryTags.any { tag -> tag.equals(filterKey, ignoreCase = true) }
            }
        }

        val activeModelId = NoeonAiEngine.getSelectedModel(this).id
        adapter.updateData(filteredList, activeModelId)
        tvCatalogCountHeader.text = "${selectedCategory} Models (${filteredList.size})"
    }

    private fun showModelDetailSheet(model: AiModel) {
        val detailSheet = ModelDetailBottomSheet(
            model = model,
            onModelSelected = { _ ->
                refreshCatalogList()
            }
        )
        detailSheet.show(supportFragmentManager, "ModelDetailBottomSheet")
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
