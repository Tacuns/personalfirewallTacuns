package com.sentinel.core.vpn

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import androidx.room.withTransaction
import com.sentinel.core.rules.BlocklistSource
import com.sentinel.core.rules.FirewallRule
import com.sentinel.core.rules.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads community blocklists and stores domains in Room.
 *
 * Line formats are handled by [BlocklistParser] (hosts, plain domains, AdBlock syntax,
 * and auto for lists the user adds).
 *
 * Attribution:
 *   StevenBlack Unified Hosts — MIT License https://github.com/StevenBlack/hosts
 *   OISD — https://oisd.nl
 *   HaGeZi DNS Blocklists — https://github.com/hagezi/dns-blocklists
 *   AdGuard DNS Filter — https://github.com/AdguardTeam/AdguardSDNSFilter
 *   Dan Pollock Hosts — https://someonewhocares.org/hosts/
 */
class BlocklistSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG           = "BlocklistSync"
        private const val WORK_PERIODIC = "blocklist_sync"
        private const val WORK_ONCE     = "blocklist_sync_init"
        const val KEY_SOURCE            = "source_key"
        // Set only on the periodic request: download just the lists whose interval has passed.
        private const val KEY_DUE_ONLY  = "due_only"

        // A list is read line by line, never held whole in memory. These caps stop a huge
        // or endless download (easy to hit with a link the user typed) from exhausting memory.
        // The largest built-in list is ~400k domains, well inside both limits.
        private const val MAX_BYTES     = 64L * 1024 * 1024
        private const val MAX_DOMAINS   = 1_000_000
        private const val MAX_REDIRECTS = 3
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS    = 30_000

        // WorkManager is only initialized in the main process.
        // The VPN service runs in :vpn process — calling WorkManager there crashes the service.
        // This guard makes any accidental call from the wrong process a no-op instead of a crash.
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
            // Wakes every few hours and downloads only the lists that are due (each list has its
            // own interval, see BlocklistSchedule). UPDATE moves installs that still hold the old
            // weekly request onto this one without cancelling a run in progress (WorkManager
            // 2.9 ExistingPeriodicWorkPolicy.UPDATE).
            val request = PeriodicWorkRequestBuilder<BlocklistSyncWorker>(
                    BlocklistSchedule.CHECK_EVERY_HOURS, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_DUE_ONLY to true))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context, sourceKey: String? = null) {
            if (!isMainProcess(context)) {
                Log.w(TAG, "runNow() called from non-main process — skipped")
                return
            }
            val data = if (sourceKey != null) workDataOf(KEY_SOURCE to sourceKey) else Data.EMPTY
            val name = if (sourceKey != null) "${WORK_ONCE}_$sourceKey" else WORK_ONCE
            val request = OneTimeWorkRequestBuilder<BlocklistSyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** The link answered, but the list cannot be used. Retrying later would not help. */
    private class ListProblem(val code: String) : IOException(code)

    override suspend fun doWork(): Result {
        val ruleEngine  = RuleEngine.getInstance(applicationContext)
        val dao         = ruleEngine.db.ruleDao()
        val sourceDao   = ruleEngine.db.blocklistSourceDao()
        val targetKey   = inputData.getString(KEY_SOURCE)

        val dueOnly     = inputData.getBoolean(KEY_DUE_ONLY, false)

        val now = System.currentTimeMillis()
        val sources = when {
            targetKey != null -> listOfNotNull(sourceDao.getByKey(targetKey))
            dueOnly -> sourceDao.getEnabled().filter {
                BlocklistSchedule.isDue(it.lastUpdatedMs, it.domainCount,
                    BlocklistSchedule.intervalHours(applicationContext, it.sourceKey), now)
            }
            else -> sourceDao.getEnabled()
        }

        if (sources.isEmpty()) {
            Log.i(TAG, if (dueOnly) "No blocklist is due for an update" else "No active blocklist sources to sync")
            return Result.success()
        }

        var anyFailed = false
        for (source in sources) {
            try {
                Log.i(TAG, "Downloading ${source.displayName} (${source.format})…")
                val domains = withContext(Dispatchers.IO) { download(source) }
                // An empty result is never stored: a captive portal or error page must not
                // silently wipe a list that was protecting the user.
                if (domains.isEmpty()) throw ListProblem(BlocklistSyncStatus.EMPTY)
                Log.i(TAG, "${source.displayName}: ${domains.size} domains parsed")

                // Replace this source's existing entries atomically.
                // withTransaction ensures delete + all inserts + stats update are one atomic
                // operation. If anything fails mid-batch, the old entries are preserved and
                // protection continues uninterrupted until the next successful retry.
                val stored = ruleEngine.db.withTransaction {
                    // Removed or switched off while downloading: storing now would leave
                    // rules behind that nothing on screen can turn off.
                    val current = sourceDao.getByKey(source.sourceKey)
                    if (current == null || !current.enabled) return@withTransaction false
                    dao.deleteBlocklistBySource(source.sourceKey)
                    for (chunk in domains.asSequence().chunked(500)) {
                        dao.insertBlocklistBatch(chunk.map { domain ->
                            FirewallRule(
                                domain      = domain,
                                uid         = -1,
                                packageName = "",
                                isBlocked   = true,
                                source      = source.sourceKey
                            )
                        })
                    }
                    sourceDao.updateStats(source.sourceKey, domains.size, System.currentTimeMillis())
                    true
                }
                BlocklistSyncStatus.clear(applicationContext, source.sourceKey)
                Log.i(TAG, if (stored) "${source.displayName}: stored ${domains.size} domains"
                           else "${source.displayName}: removed or switched off during download, not stored")
            } catch (e: ListProblem) {
                Log.w(TAG, "${source.displayName} not usable: ${e.code}")
                reportError(sourceDao, source, e.code)
            } catch (e: Exception) {
                Log.w(TAG, "${source.displayName} sync failed: ${e.message}")
                reportError(sourceDao, source, BlocklistSyncStatus.NETWORK)
                anyFailed = true
            }
        }

        ruleEngine.reloadBlocklist()

        // Only notify the VPN process if it is already running. startForegroundService
        // requires startForeground() to be called within 5 s — the RELOAD_RULES branch in
        // SentinelVpnService does not call startForeground(), so starting it while VPN is
        // stopped would crash with ForegroundServiceDidNotStartInTimeException. If VPN is
        // off the cache reload is unnecessary anyway; the VPN reads fresh rules from DB on
        // next start.
        if (applicationContext.vpnRunning()) {
            try {
                val intent = Intent(applicationContext, SentinelVpnService::class.java).apply {
                    action = SentinelVpnService.ACTION_RELOAD_RULES
                }
                ContextCompat.startForegroundService(applicationContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not notify VPN of blocklist update: ${e.message}")
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    /** Saves the reason and pokes the row, so an open Blocklist screen shows it straight away. */
    private suspend fun reportError(sourceDao: com.sentinel.core.rules.BlocklistSourceDao, source: BlocklistSource, code: String) {
        BlocklistSyncStatus.setError(applicationContext, source.sourceKey, code)
        // runAttemptCount is 0 on the first try and counts WorkManager's automatic retries.
        if (com.sentinel.core.alerts.AlertRules.alertForBlocklistFailure(code, runAttemptCount)) {
            com.sentinel.core.alerts.SecurityAlerts.record(applicationContext, com.sentinel.core.alerts.AlertType.BLOCKLIST_FAILED,
                com.sentinel.core.alerts.AlertSeverity.MEDIUM, target = source.sourceKey, extra = code)
        }
        try { sourceDao.touch(source.sourceKey) } catch (_: Exception) { }
    }

    /** Streams the list and returns its unique domains in file order. */
    private fun download(source: BlocklistSource): Set<String> {
        var url = try { URL(source.url) } catch (e: Exception) { throw ListProblem(BlocklistSyncStatus.LINK) }
        var redirects = 0
        while (true) {
            if (url.protocol != "https" && url.protocol != "http") throw ListProblem(BlocklistSyncStatus.LINK)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout    = READ_TIMEOUT_MS
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    // HttpURLConnection never follows http <-> https redirects by itself.
                    val location = conn.getHeaderField("Location") ?: throw IOException("HTTP $code")
                    if (++redirects > MAX_REDIRECTS) throw ListProblem(BlocklistSyncStatus.HTTP)
                    url = URL(url, location)
                    // Android blocks plain-text HTTP for this app, so follow as https.
                    if (url.protocol == "http") url = URL("https", url.host, url.port, url.file)
                    continue
                }
                if (code in 400..499) throw ListProblem(BlocklistSyncStatus.HTTP)
                if (code !in 200..299) throw IOException("HTTP $code")
                if (conn.contentLengthLong > MAX_BYTES) throw ListProblem(BlocklistSyncStatus.TOO_LARGE)

                val result = LinkedHashSet<String>(8192)
                CappedStream(conn.inputStream, MAX_BYTES).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val domain = BlocklistParser.parseLine(line, source.format) ?: continue
                        if (result.add(domain) && result.size > MAX_DOMAINS) {
                            throw ListProblem(BlocklistSyncStatus.TOO_LARGE)
                        }
                    }
                }
                return result
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Fails the read once more than [limit] bytes have arrived. */
    private class CappedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0 && ++count > limit) throw ListProblem(BlocklistSyncStatus.TOO_LARGE)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                count += n
                if (count > limit) throw ListProblem(BlocklistSyncStatus.TOO_LARGE)
            }
            return n
        }
    }
}
