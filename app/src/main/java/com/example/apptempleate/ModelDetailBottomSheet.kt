package com.example.apptempleate

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModelDetailBottomSheet(
    private val model: AiModel,
    private val onModelSelected: (AiModel) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var btnDetailAction: TextView
    private lateinit var btnDeleteModel: TextView

    private val downloadListener: (ModelDownloadProgress) -> Unit = { progress ->
        if (progress.modelId == model.id && isAdded) {
            updateUIState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_model_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        val tvDetailIcon: TextView = view.findViewById(R.id.tvDetailIcon)
        val tvDetailName: TextView = view.findViewById(R.id.tvDetailName)
        val tvDetailBadge: TextView = view.findViewById(R.id.tvDetailBadge)
        val tvDetailTagline: TextView = view.findViewById(R.id.tvDetailTagline)
        val btnCloseDetail: ImageButton = view.findViewById(R.id.btnCloseDetail)
        val tvDetailDescription: TextView = view.findViewById(R.id.tvDetailDescription)

        val pbIntelligence: ProgressBar = view.findViewById(R.id.pbIntelligence)
        val tvIntelligenceScore: TextView = view.findViewById(R.id.tvIntelligenceScore)

        val pbSpeed: ProgressBar = view.findViewById(R.id.pbSpeed)
        val tvSpeedScore: TextView = view.findViewById(R.id.tvSpeedScore)

        val pbMemory: ProgressBar = view.findViewById(R.id.pbMemory)
        val tvMemoryScore: TextView = view.findViewById(R.id.tvMemoryScore)

        val pbReasoning: ProgressBar = view.findViewById(R.id.pbReasoning)
        val tvReasoningScore: TextView = view.findViewById(R.id.tvReasoningScore)

        val pbCoding: ProgressBar = view.findViewById(R.id.pbCoding)
        val tvCodingScore: TextView = view.findViewById(R.id.tvCodingScore)

        val pbLongContext: ProgressBar = view.findViewById(R.id.pbLongContext)
        val tvLongContextScore: TextView = view.findViewById(R.id.tvLongContextScore)

        val llBestForContainer: LinearLayout = view.findViewById(R.id.llBestForContainer)
        val llNotIdealForContainer: LinearLayout = view.findViewById(R.id.llNotIdealForContainer)

        val tvDetailDownloadSize: TextView = view.findViewById(R.id.tvDetailDownloadSize)
        val tvDetailRamReq: TextView = view.findViewById(R.id.tvDetailRamReq)

        btnDetailAction = view.findViewById(R.id.btnDetailAction)
        btnDeleteModel = view.findViewById(R.id.btnDeleteModel)

        // Populate Metadata
        tvDetailIcon.text = model.icon
        tvDetailName.text = model.name
        tvDetailBadge.text = model.badge
        tvDetailTagline.text = model.tagline
        tvDetailDescription.text = model.description

        // Populate Progress Meters
        val intelProgress = model.intelligenceRating * 2
        pbIntelligence.progress = intelProgress
        tvIntelligenceScore.text = "$intelProgress/10"

        val speedProgress = model.speedRating * 2
        pbSpeed.progress = speedProgress
        tvSpeedScore.text = "$speedProgress/10"

        pbMemory.progress = model.memoryRating
        tvMemoryScore.text = "${model.memoryRating}/10"

        pbReasoning.progress = model.reasoningRating
        tvReasoningScore.text = "${model.reasoningRating}/10"

        pbCoding.progress = model.codingRating
        tvCodingScore.text = "${model.codingRating}/10"

        pbLongContext.progress = model.longContextRating
        tvLongContextScore.text = "${model.longContextRating}/10"

        // Populate Best For List
        llBestForContainer.removeAllViews()
        for (item in model.recommendedFor) {
            val tv = TextView(context).apply {
                text = "✓ $item"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setPadding(0, 4, 0, 4)
            }
            llBestForContainer.addView(tv)
        }

        // Populate Not Ideal For List
        llNotIdealForContainer.removeAllViews()
        if (model.notIdealFor.isEmpty()) {
            val tv = TextView(context).apply {
                text = "None noted"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
            }
            llNotIdealForContainer.addView(tv)
        } else {
            for (item in model.notIdealFor) {
                val tv = TextView(context).apply {
                    text = "× $item"
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, 4, 0, 4)
                }
                llNotIdealForContainer.addView(tv)
            }
        }

        tvDetailDownloadSize.text = "${model.downloadSizeMb} MB"
        tvDetailRamReq.text = "${model.ramRequiredGb} GB"

        // Register listener for background downloads
        BackgroundModelDownloadManager.registerListener(downloadListener)

        updateUIState()

        btnCloseDetail.setOnClickListener {
            dismiss()
        }

        btnDetailAction.setOnClickListener {
            val isDownloading = BackgroundModelDownloadManager.isModelDownloading(model.id)
            if (isDownloading) {
                Toast.makeText(context, "Model download is running in background...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentIsSelected = NoeonAiEngine.getSelectedModel(context).id == model.id
            val currentIsDownloaded = NoeonAiEngine.isModelDownloaded(context, model.id)

            if (!currentIsDownloaded) {
                // Model file is NOT downloaded on disk -> Select target and start download
                NoeonAiEngine.setSelectedModel(context, model.id)
                BackgroundModelDownloadManager.startDownload(context, model)
                Toast.makeText(context, "Started background download for ${model.name} (${model.downloadSizeMb} MB)", Toast.LENGTH_LONG).show()
                updateUIState()
            } else if (currentIsSelected) {
                // Model IS downloaded and already selected
                Toast.makeText(context, "${model.name} is currently active", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                // Model IS downloaded but NOT selected -> Switch active model
                NoeonAiEngine.setSelectedModel(context, model.id)
                onModelSelected(model)
                Toast.makeText(context, "Switched AI Engine to ${model.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }

        // Delete Model File Action
        btnDeleteModel.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun updateUIState() {
        if (!isAdded) return
        val context = requireContext()

        val isDownloading = BackgroundModelDownloadManager.isModelDownloading(model.id)
        val downloadProgress = BackgroundModelDownloadManager.getDownloadProgress(model.id)

        val currentIsSelected = NoeonAiEngine.getSelectedModel(context).id == model.id
        val currentIsDownloaded = NoeonAiEngine.isModelDownloaded(context, model.id)

        if (isDownloading && downloadProgress != null) {
            btnDetailAction.text = downloadProgress.statusMessage.ifEmpty { "DOWNLOADING (${downloadProgress.percentage}%)" }
            btnDetailAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4F46E5"))
            btnDetailAction.setTextColor(Color.WHITE)
            btnDeleteModel.visibility = View.GONE
        } else if (currentIsSelected && currentIsDownloaded) {
            btnDetailAction.text = "🟢 CURRENTLY ACTIVE MODEL"
            btnDetailAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            btnDetailAction.setTextColor(Color.WHITE)
            btnDeleteModel.visibility = View.VISIBLE
        } else if (currentIsDownloaded) {
            btnDetailAction.text = "SELECT & USE THIS MODEL"
            btnDetailAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_utility_text))
            btnDetailAction.setTextColor(ContextCompat.getColor(context, R.color.app_window_background))
            btnDeleteModel.visibility = View.VISIBLE
        } else {
            btnDetailAction.text = "DOWNLOAD REAL GGUF (${model.downloadSizeMb} MB)"
            btnDetailAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4F46E5"))
            btnDetailAction.setTextColor(Color.WHITE)
            btnDeleteModel.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmationDialog() {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle("Delete Model File")
            .setMessage("Are you sure you want to delete ${model.name} (${model.fileName}) from device storage?\n\nThis will free up ~${model.downloadSizeMb} MB.")
            .setPositiveButton("Delete") { dialog, _ ->
                val deleted = BackgroundModelDownloadManager.deleteDownloadedModel(context, model)
                if (deleted || !NoeonAiEngine.isModelDownloaded(context, model.id)) {
                    Toast.makeText(context, "Deleted ${model.fileName} from device storage", Toast.LENGTH_SHORT).show()
                    onModelSelected(model)
                    updateUIState()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        BackgroundModelDownloadManager.unregisterListener(downloadListener)
    }
}
