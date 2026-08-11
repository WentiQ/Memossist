package com.example.apptempleate

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ModelMarketplaceAdapter(
    private val context: Context,
    private var modelsList: List<AiModel>,
    private val onModelClick: (AiModel) -> Unit,
    private val onSelectClick: (AiModel) -> Unit
) : RecyclerView.Adapter<ModelMarketplaceAdapter.ViewHolder>() {

    private var activeModelId: String = NoeonAiEngine.getSelectedModel(context).id

    fun updateData(newList: List<AiModel>, activeId: String) {
        this.modelsList = newList
        this.activeModelId = activeId
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModelIcon: TextView = view.findViewById(R.id.tvModelIcon)
        val tvModelName: TextView = view.findViewById(R.id.tvModelName)
        val tvModelBadge: TextView = view.findViewById(R.id.tvModelBadge)
        val tvModelTagline: TextView = view.findViewById(R.id.tvModelTagline)
        val btnSelectModelCard: TextView = view.findViewById(R.id.btnSelectModelCard)
        val tvIntelligenceStars: TextView = view.findViewById(R.id.tvIntelligenceStars)
        val tvSpeedStars: TextView = view.findViewById(R.id.tvSpeedStars)
        val tvModelDescription: TextView = view.findViewById(R.id.tvModelDescription)
        val tvModelParams: TextView = view.findViewById(R.id.tvModelParams)
        val tvModelContext: TextView = view.findViewById(R.id.tvModelContext)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = modelsList[position]
        
        // A model is downloaded ONLY if its real GGUF file exists on disk
        val isDownloadedOnDisk = NoeonAiEngine.isModelDownloaded(context, model.id)
        val isSelectedAndDownloaded = (model.id == activeModelId) && isDownloadedOnDisk

        holder.tvModelIcon.text = model.icon
        holder.tvModelName.text = model.name
        holder.tvModelBadge.text = model.badge
        holder.tvModelTagline.text = model.tagline
        holder.tvModelDescription.text = model.description
        holder.tvModelParams.text = "${model.parameters} params"
        holder.tvModelContext.text = model.contextWindow

        // Intelligence Star String
        val intelStars = "★".repeat(model.intelligenceRating) + "☆".repeat(5 - model.intelligenceRating)
        holder.tvIntelligenceStars.text = "$intelStars Intelligence"

        // Speed Star/Bolt String
        val speedBolts = "⚡".repeat(model.speedRating) + "☆".repeat(5 - model.speedRating)
        holder.tvSpeedStars.text = "$speedBolts Speed"

        // Button state logic strictly tied to physical disk file presence & active background download
        val isDownloading = BackgroundModelDownloadManager.isModelDownloading(model.id)
        val downloadProgress = BackgroundModelDownloadManager.getDownloadProgress(model.id)

        if (isDownloading && downloadProgress != null) {
            holder.btnSelectModelCard.text = "DOWNLOADING ${downloadProgress.percentage}%"
            holder.btnSelectModelCard.setBackgroundResource(R.drawable.bg_chip_selected)
            holder.btnSelectModelCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4F46E5"))
            holder.btnSelectModelCard.setTextColor(Color.WHITE)
        } else if (isSelectedAndDownloaded) {
            holder.btnSelectModelCard.text = "🟢 ACTIVE"
            holder.btnSelectModelCard.setBackgroundResource(R.drawable.bg_chip_selected)
            holder.btnSelectModelCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981")) // Emerald Green
            holder.btnSelectModelCard.setTextColor(Color.WHITE)
        } else if (isDownloadedOnDisk) {
            holder.btnSelectModelCard.text = "SELECT"
            holder.btnSelectModelCard.setBackgroundResource(R.drawable.bg_chip_selected)
            holder.btnSelectModelCard.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_utility_text))
            holder.btnSelectModelCard.setTextColor(ContextCompat.getColor(context, R.color.app_window_background))
        } else {
            holder.btnSelectModelCard.text = "DOWNLOAD"
            holder.btnSelectModelCard.setBackgroundResource(R.drawable.bg_chip_unselected)
            holder.btnSelectModelCard.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.app_icon_button_background))
            holder.btnSelectModelCard.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }

        holder.itemView.setOnClickListener {
            onModelClick(model)
        }

        holder.btnSelectModelCard.setOnClickListener {
            onSelectClick(model)
        }
    }

    override fun getItemCount(): Int = modelsList.size
}
