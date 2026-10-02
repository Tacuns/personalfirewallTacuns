package com.sentinel.core.geo

import android.content.Context
import android.net.InetAddresses
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps IP addresses to ISO 3166-1 alpha-2 country codes for the Map tab, fully offline.
 *
 * The table is assets/country.mmdb, built by tools/build_geoip.py from the public-domain
 * (PDDL 1.0) "server-country" dataset. It replaced the MaxMind GeoLite2 file, whose licence
 * requires written consent to hand the database to third parties and deletion of old copies
 * within 30 days of every MaxMind release - neither of which an app shipped through a store
 * can honour.
 *
 * The file is copied to internal storage because an asset stream is not seekable and the
 * reader needs random access. The copy is named after the install time, so a new app version
 * carrying a refreshed table replaces it instead of reusing a stale copy.
 *
 * Used by the map only. It never affects a blocking decision.
 */
object CountryLookup {

    private const val ASSET = "country.mmdb"
    private const val COPY_PREFIX = "country-"
    private const val COPY_SUFFIX = ".mmdb"

    // Files an older version of the app copied out of its assets. The MaxMind one in
    // particular must go: its licence asks for old copies to be destroyed.
    private val RETIRED_FILES = listOf("GeoLite2-Country.mmdb")

    @Volatile private var resolver: CountryResolver? = null
    @Volatile private var initStarted = false

    private val readySignal = CompletableDeferred<Unit>()
    private val cache       = ConcurrentHashMap<String, String>(256)

    /** Call once from MapViewModel.init{}. Safe to call again; later calls do nothing. */
    fun init(context: Context) {
        if (initStarted) return
        initStarted = true
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                resolver = MmdbCountryResolver(prepareCopy(appCtx))
            } catch (e: Exception) {
                // Missing or unreadable table: the map still works, countries show as unknown.
                android.util.Log.w("CountryLookup", "country table unavailable: ${e.javaClass.simpleName}")
            } finally {
                readySignal.complete(Unit)
            }
        }
    }

    private fun prepareCopy(ctx: Context): File {
        val stamp = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        } catch (e: Exception) {
            0L
        }
        val dest = File(ctx.filesDir, "$COPY_PREFIX$stamp$COPY_SUFFIX")

        // Clear anything left by an earlier install, then copy this version's table once.
        ctx.filesDir.listFiles()?.forEach { f ->
            val stale = f.name in RETIRED_FILES ||
                (f.name.startsWith(COPY_PREFIX) && f.name.endsWith(COPY_SUFFIX) && f.name != dest.name)
            if (stale) f.delete()
        }
        if (!dest.exists()) {
            val tmp = File(ctx.filesDir, dest.name + ".tmp")
            ctx.assets.open(ASSET).use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                error("could not place country table")
            }
        }
        return dest
    }

    /** Suspends until the table has loaded (or failed to). Returns immediately afterwards. */
    suspend fun awaitReady() = readySignal.await()

    /**
     * Country for a literal IPv4 or IPv6 address, e.g. "142.250.80.46" -> "US".
     * Returns null when the table is not loaded, the address is private or unknown, or the
     * text is not an IP literal. A hostname is refused rather than resolved, so this can
     * never trigger a DNS lookup of its own.
     */
    fun lookup(ip: String): String? {
        val r = resolver ?: return null
        cache[ip]?.let { return it }
        if (!InetAddresses.isNumericAddress(ip)) return null
        return try {
            val code = r.countryOf(InetAddresses.parseNumericAddress(ip))
            if (code != null && cache.size < 500) cache[ip] = code
            code
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() = cache.clear()
}
