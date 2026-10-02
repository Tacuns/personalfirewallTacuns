package com.sentinel.core.logs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sentinel.core.rules.FirewallOptionsStore
import java.util.concurrent.TimeUnit

/**
 * Deletes activity history that has fallen outside the window the user chose.
 *
 * The old code trimmed inside every insert, which meant a sub-select ran for every DNS
 * answer just to hold the table at 500 rows. Doing it on a timer instead lets the history
 * actually be useful while still bounding the file.
 */
class LogRetentionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val hours = FirewallOptionsStore.read(applicationContext).logRetentionHours
            val dao = LogDatabase.getInstance(applicationContext).packetLogDao()
            LogTrimmer.trim(dao, hours)
            Result.success()
        } catch (e: Exception) {
            // Never the name of a domain - only the exception type.
            android.util.Log.w(TAG, "trim failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LogRetentionWorker"
        private const val UNIQUE_NAME = "log_retention_trim"

        // Six hours is the shortest window offered, so a trim can never be a whole
        // window behind. KEEP so an existing schedule is not reset on every launch.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogRetentionWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
