package com.sentinel.core.rules

import android.content.Context
import com.sentinel.core.logs.LogDatabase

/**
 * In-memory cache of trusted domains for [LookalikeDomainDetector], read on the
 * VPN's hot DNS-processing path. Mirrors [RuleEngine]'s own existing cache pattern
 * exactly: a plain volatile Set, populated by an explicit suspend reload call
 * triggered on VPN start, read synchronously and unsynchronized per packet.
 *
 * Never queries the database from the packet path — only [refresh] does I/O,
 * and it is only ever called from a coroutine at VPN start.
 */
object TrustedDomainsCache {

    @Volatile
    private var cache: Set<String> = TrustedBrandDomains.STATIC_LIST

    fun get(): Set<String> = cache

    suspend fun refresh(context: Context) {
        val sinceMs = System.currentTimeMillis() - 30L * 24 * 3_600_000
        val topDomains = try {
            LogDatabase.getInstance(context).packetLogDao()
                .topDestinations("ALLOWED", sinceMs, 40)
                .map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
        cache = TrustedBrandDomains.buildTrustedSet(topDomains)
    }
}
