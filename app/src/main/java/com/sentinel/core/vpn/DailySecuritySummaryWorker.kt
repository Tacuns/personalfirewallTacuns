package com.sentinel.core.vpn

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sentinel.core.logs.LogDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Posts one "Your phone was protected today" notification every 24 hours — the
 * habit-forming half of the Look-Alike Domain Shield engagement design. Follows
 * the exact same shape as [com.sentinel.core.vpn.BlocklistSyncWorker] / the
 * schedule check worker: isMainProcess() guard, companion schedule() function,
 * a CoroutineWorker reading data that's already queried elsewhere.
 */
class DailySecuritySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailySecuritySummary"
        private const val WORK_NAME = "daily_security_summary"

        private fun isMainProcess(context: Context): Boolean {
            val pid = Process.myPid()
            val am = context.getSystemService(ActivityManager::class.java) ?: return false
            return am.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName == context.packageName
        }

        fun schedule(context: Context) {
            if (!isMainProcess(context)) {
                Log.w(TAG, "schedule() called from non-main process — skipped")
                return
            }
            val request = PeriodicWorkRequestBuilder<DailySecuritySummaryWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val startOfDay = run {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val blockedToday = LogDatabase.getInstance(applicationContext)
                .packetLogDao()
                .countByStatusSince("BLOCKED", startOfDay)

            SecurityAlertNotifier.notifyDailySummary(applicationContext, blockedToday)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Daily summary failed: ${e.message}")
            Result.success() // never retry-loop a notification failure
        }
    }
}
