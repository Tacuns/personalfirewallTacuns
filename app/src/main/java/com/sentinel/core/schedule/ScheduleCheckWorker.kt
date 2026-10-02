package com.sentinel.core.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sentinel.core.rules.RuleEngine
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 minutes. Reads the saved schedule from DataStore, checks if the
 * current time + day falls within the window, then adds or removes category
 * domains in RuleEngine accordingly.
 *
 * Uses source='SCHEDULE' so it never overwrites or deletes USER-added rules.
 * No AlarmManager or special permissions required.
 */
class ScheduleCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG       = "ScheduleCheck"
        private const val WORK_NAME = "schedule_check"
        private const val NOW_WORK_NAME = "schedule_check_now"
        private const val EXACT_WORK_NAME = "schedule_check_exact"
        private const val KEY_EXACT = "exact_boundary_check"

        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "Schedule worker started")
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            cancelExactCheck(context)
            Log.i(TAG, "Schedule worker stopped")
        }

        /**
         * Checks the schedule once, right away. Used when the app starts and after a restore,
         * so rules always match the saved schedule — including clearing leftovers.
         */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ScheduleCheckWorker>().build()
            )
        }

        /**
         * Books one extra check for the next start or end minute, so the schedule switches
         * close to the chosen time instead of up to 15 minutes late. Android may still delay
         * it a little to save battery, so the 15-minute check stays as the safety net.
         *
         * [fromExactRun] must be true only when called from inside that exact check itself:
         * REPLACE would cancel the running check, so it appends the next one instead. Every
         * other caller replaces any waiting check with a freshly calculated one.
         */
        fun scheduleNextExactCheck(
            context: Context,
            startHour: Int, startMin: Int, endHour: Int, endMin: Int,
            fromExactRun: Boolean = false
        ) {
            val now   = Calendar.getInstance()
            val delay = ScheduleTiming.msUntilNextBoundary(
                now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                now.get(Calendar.SECOND),
                startHour * 60 + startMin,
                endHour * 60 + endMin
            )
            if (delay == null) { cancelExactCheck(context); return }
            val request = OneTimeWorkRequestBuilder<ScheduleCheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_EXACT to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                EXACT_WORK_NAME,
                if (fromExactRun) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelExactCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(EXACT_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val prefs      = AppPreferencesRepository(applicationContext)
        val ruleEngine = RuleEngine.getInstance(applicationContext)

        if (!prefs.scheduleEnabled.first()) {
            // A switched-off schedule must never keep blocking. Rules can be left behind by a
            // restore or an interrupted update, so they are cleared here as well.
            ruleEngine.removeAllScheduleRules()
            cancelExactCheck(applicationContext)
            notifyVpn()
            return Result.success()
        }

        val category  = prefs.scheduleCategory.first()
        val startHour = prefs.scheduleStartHour.first()
        val startMin  = prefs.scheduleStartMin.first()
        val endHour   = prefs.scheduleEndHour.first()
        val endMin    = prefs.scheduleEndMin.first()
        val days      = prefs.scheduleDays.first() // "1111100" Mon=0 … Sun=6

        val cal = Calendar.getInstance()
        // Calendar.DAY_OF_WEEK: Sun=1, Mon=2 … Sat=7 → convert to Mon=0 … Sun=6
        val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val fromExactRun = inputData.getBoolean(KEY_EXACT, false)

        if (dayIndex >= days.length || days[dayIndex] != '1') {
            ruleEngine.removeAllScheduleRules()
            scheduleNextExactCheck(applicationContext, startHour, startMin, endHour, endMin, fromExactRun)
            notifyVpn()
            return Result.success()
        }

        val nowMins   = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMin
        val endMins   = endHour   * 60 + endMin

        val inWindow = when {
            startMins == endMins -> false
            startMins < endMins  -> nowMins in startMins until endMins
            else                 -> nowMins >= startMins || nowMins < endMins  // overnight
        }

        if (inWindow) {
            ScheduleCategories.domainsFor(category).forEach { domain ->
                ruleEngine.addScheduleRule(domain)
            }
            Log.i(TAG, "Schedule ACTIVE: blocking ${ScheduleCategories.labelFor(category)}")
        } else {
            ruleEngine.removeAllScheduleRules()
            Log.i(TAG, "Schedule INACTIVE: rules cleared")
        }

        scheduleNextExactCheck(applicationContext, startHour, startMin, endHour, endMin, fromExactRun)
        notifyVpn()
        return Result.success()
    }

    // Only notify the VPN process if it is already running. startForegroundService
    // requires startForeground() within 5 s — the RELOAD_RULES branch in
    // SentinelVpnService does not call startForeground(), so starting it while VPN is
    // stopped would crash with ForegroundServiceDidNotStartInTimeException. If VPN is
    // off the rule update is unnecessary; fresh rules are read from DB on next VPN start.
    private fun notifyVpn() {
        if (applicationContext.vpnRunning()) {
            try {
                val intent = Intent(applicationContext, SentinelVpnService::class.java).apply {
                    action = SentinelVpnService.ACTION_RELOAD_RULES
                }
                ContextCompat.startForegroundService(applicationContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not notify VPN of schedule change: ${e.message}")
            }
        }
    }
}
