package com.example.apptempleate

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModelPickerBottomSheet(
    private val onModelChanged: (AiModel) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_chat_model_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        val btnClosePicker: ImageButton = view.findViewById(R.id.btnClosePicker)
        val llPickerModelsContainer: LinearLayout = view.findViewById(R.id.llPickerModelsContainer)
        val btnOpenMarketplaceFromPicker: LinearLayout = view.findViewById(R.id.btnOpenMarketplaceFromPicker)

        btnClosePicker.setOnClickListener {
            dismiss()
        }

        val currentModel = NoeonAiEngine.getSelectedModel(context)
        val downloadedModels = ModelCatalog.models.filter { model -> NoeonAiEngine.isModelDownloaded(context, model.id) }

        llPickerModelsContainer.removeAllViews()

        if (downloadedModels.isEmpty()) {
            // Show empty state card if no offline GGUF models exist
            val emptyCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_metallic_card)
                setPadding(24, 24, 24, 24)

                val tvIcon = TextView(context).apply {
                    text = "📦"
                    textSize = 32f
                    gravity = android.view.Gravity.CENTER
                }
                val tvTitle = TextView(context).apply {
                    text = "No Local Models Downloaded Yet"
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 8, 0, 4)
                }
                val tvDesc = TextView(context).apply {
                    text = "Download local GGUF models from the marketplace to run ultra-fast offline AI inference."
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                }
                val btnGoMarketplace = TextView(context).apply {
                    text = "Explore Model Marketplace →"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_chip_selected)
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_utility_text))
                    setPadding(24, 16, 24, 16)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 16
                    }
                    layoutParams = lp
                    setOnClickListener {
                        dismiss()
                        val intent = Intent(context, ModelMarketplaceActivity::class.java)
                        startActivity(intent)
                    }
                }

                addView(tvIcon)
                addView(tvTitle)
                addView(tvDesc)
                addView(btnGoMarketplace)
            }
            llPickerModelsContainer.addView(emptyCard)
        } else {
            // Populate list of downloaded models
            for (model in downloadedModels) {
                val isSelected = model.id == currentModel.id

                val rowView = LayoutInflater.from(context).inflate(R.layout.item_model_card, llPickerModelsContainer, false) as LinearLayout
                
                val params = rowView.layoutParams as LinearLayout.LayoutParams
                params.setMargins(0, 0, 0, 12)
                rowView.layoutParams = params

                val tvIcon: TextView = rowView.findViewById(R.id.tvModelIcon)
                val tvName: TextView = rowView.findViewById(R.id.tvModelName)
                val tvBadge: TextView = rowView.findViewById(R.id.tvModelBadge)
                val tvTagline: TextView = rowView.findViewById(R.id.tvModelTagline)
                val btnAction: TextView = rowView.findViewById(R.id.btnSelectModelCard)
                val tvIntel: TextView = rowView.findViewById(R.id.tvIntelligenceStars)
                val tvSpeed: TextView = rowView.findViewById(R.id.tvSpeedStars)
                val tvDesc: TextView = rowView.findViewById(R.id.tvModelDescription)
                val tvParams: TextView = rowView.findViewById(R.id.tvModelParams)
                val tvContext: TextView = rowView.findViewById(R.id.tvModelContext)

                tvIcon.text = model.icon
                tvName.text = model.name
                tvBadge.text = model.badge
                tvTagline.text = model.tagline
                tvDesc.text = model.description
                tvParams.text = "${model.parameters} params"
                tvContext.text = model.contextWindow

                val intelStars = "★".repeat(model.intelligenceRating) + "☆".repeat(5 - model.intelligenceRating)
                tvIntel.text = "$intelStars Intelligence"

                val speedBolts = "⚡".repeat(model.speedRating) + "☆".repeat(5 - model.speedRating)
                tvSpeed.text = "$speedBolts Speed"

                if (isSelected) {
                    btnAction.text = "🟢 ACTIVE"
                    btnAction.setBackgroundResource(R.drawable.bg_chip_selected)
                    btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                    btnAction.setTextColor(Color.WHITE)
                } else {
                    btnAction.text = "SELECT"
                    btnAction.setBackgroundResource(R.drawable.bg_chip_selected)
                    btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_utility_text))
                    btnAction.setTextColor(Color.WHITE)
                }

                rowView.setOnClickListener {
                    if (isSelected) {
                        dismiss()
                    } else {
                        NoeonAiEngine.setSelectedModel(context, model.id)
                        onModelChanged(model)
                        Toast.makeText(context, "Switched chat model to ${model.name}", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }

                llPickerModelsContainer.addView(rowView)
            }
        }

        btnOpenMarketplaceFromPicker.setOnClickListener {
            dismiss()
            val intent = Intent(context, ModelMarketplaceActivity::class.java)
            startActivity(intent)
        }
    }
}
