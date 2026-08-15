package com.example.apptempleate

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * WorkManager worker that guarantees background LLM evaluation for memory parameters
 * (importance, confidence, stability) and initial strength calculations,
 * ensuring execution even if the user closes or kills the app.
 */
class MemoryParameterWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val memoryId = inputData.getString(KEY_MEMORY_ID)
        val fact = inputData.getString(KEY_FACT)

        if (memoryId.isNullOrBlank() || fact.isNullOrBlank()) {
            Log.w(TAG, "Missing memoryId or fact in MemoryParameterWorker input data")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Running background MemoryParameterWorker for $memoryId: \"$fact\"")
            val params = MemoryParameterEvaluator.evaluate(applicationContext, fact)
            val evaluatedStrength = MemoryDecayCalculator.calculateInitialStrength(
                params.importance,
                params.confidence,
                params.stability
            )

            val existing = MemoryVaultRepository.getMemoryById(applicationContext, memoryId)
            if (existing != null) {
                val updated = existing.copy(
                    importance = params.importance,
                    confidence = params.confidence,
                    stability = params.stability,
                    baseStrength = evaluatedStrength,
                    strength = evaluatedStrength
                )
                MemoryVaultRepository.updateMemory(applicationContext, updated)
                MemoryDecayCalculator.logDebugInfo(updated, System.currentTimeMillis(), "Background Worker Parameter Evaluation")
                Log.d(TAG, "Successfully evaluated and updated memory $memoryId in background")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed background parameter evaluation for $memoryId", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "MemoryParameterWorker"
        const val KEY_MEMORY_ID = "KEY_MEMORY_ID"
        const val KEY_FACT = "KEY_FACT"

        fun enqueue(context: Context, memoryId: String, fact: String) {
            try {
                val inputData = Data.Builder()
                    .putString(KEY_MEMORY_ID, memoryId)
                    .putString(KEY_FACT, fact)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<MemoryParameterWorker>()
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
                Log.d(TAG, "Enqueued MemoryParameterWorker for $memoryId to run reliably in background")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue MemoryParameterWorker for $memoryId", e)
            }
        }
    }
}
