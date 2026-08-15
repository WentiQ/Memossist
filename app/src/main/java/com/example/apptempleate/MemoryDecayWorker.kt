package com.example.apptempleate

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for executing periodic memory decay and pruning.
 * Loads all memories, recalculates their decayed strength based on time passed,
 * removes memories falling below the FORGET_THRESHOLD (0.15), and updates retained ones.
 */
class MemoryDecayWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic memory decay background worker...")
            MemoryVaultRepository.recalculateAndPruneMemories(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing periodic memory decay worker", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "MemoryDecayWorker"
        const val WORK_NAME = "memossist_periodic_memory_decay"
    }
}

/**
 * Helper manager to configure and initialize periodic decay operations via WorkManager and on app startup.
 */
object MemoryDecayManager {
    private const val TAG = "MemoryDecayManager"

    fun schedulePeriodicDecay(context: Context) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<MemoryDecayWorker>(12, TimeUnit.HOURS)
                .addTag(MemoryDecayWorker.TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                MemoryDecayWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Periodic memory decay worker successfully scheduled (12-hour interval).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic memory decay worker", e)
        }
    }

    fun runImmediateDecayAsync(context: Context) {
        Thread {
            try {
                Log.d(TAG, "Running immediate startup memory decay pass...")
                MemoryVaultRepository.recalculateAndPruneMemories(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run immediate memory decay pass", e)
            }
        }.start()
    }
}
