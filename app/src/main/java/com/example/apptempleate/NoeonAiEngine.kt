package com.example.apptempleate

import android.content.Context
import android.content.SharedPreferences

object NoeonAiEngine {

    private const val PREFS_NAME = "MemossistPrefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    private const val KEY_DOWNLOADED_MODELS = "downloaded_models"

    const val DEFAULT_MODEL_ID = "qwen3.5_4b"

    private var cachedActiveModel: AiModel? = null

    fun getSelectedModel(context: Context): AiModel {
        if (cachedActiveModel != null) return cachedActiveModel!!

        val prefs = getPrefs(context)
        val selectedId = prefs.getString(KEY_SELECTED_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        val model = ModelCatalog.getModelById(selectedId)
        cachedActiveModel = model
        return model
    }

    fun setSelectedModel(context: Context, modelId: String) {
        val model = ModelCatalog.getModelById(modelId)
        cachedActiveModel = model
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
        markModelAsDownloaded(context, modelId)
    }

    /**
     * Strictly verifies whether the model's GGUF file exists on disk
     */
    fun isModelDownloaded(context: Context, modelId: String): Boolean {
        val model = ModelCatalog.getModelById(modelId)
        return RealModelDownloader.isModelFileDownloaded(context, model)
    }

    fun markModelAsDownloaded(context: Context, modelId: String) {
        val prefs = getPrefs(context)
        val downloadedSet = prefs.getStringSet(KEY_DOWNLOADED_MODELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        downloadedSet.add(modelId)
        prefs.edit().putStringSet(KEY_DOWNLOADED_MODELS, downloadedSet).apply()
    }

    fun getActiveModelFilePath(context: Context): String {
        val activeModel = getSelectedModel(context)
        val file = RealModelDownloader.getModelFile(context, activeModel)
        return if (file.exists() && file.length() > 0) file.absolutePath else "Built-in System Engine"
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
