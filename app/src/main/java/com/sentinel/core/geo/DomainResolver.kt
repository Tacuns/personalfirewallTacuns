package com.sentinel.core.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up the address of a website for the map (country only, never a blocking decision).
 *
 * Privacy: the look-up goes through the phone's normal resolver. While protection is on, that
 * is this firewall, which sends it to the DNS server the user chose in Settings > DNS — the same
 * path as every other look-up, as the Data safety screen says. It used to go around the
 * firewall on the Wi-Fi or mobile network, so these names reached that network's DNS server
 * instead of the user's choice. That detour was only needed for blocked websites, and the map
 * no longer looks those up (MapViewModel: only ALLOWED websites get a country).
 *
 * All resolution runs on Dispatchers.IO — never blocks the main thread.
 * Results cached in memory with a 500-entry soft cap.
 */
object DomainResolver {

    private val cache = ConcurrentHashMap<String, String>(256)

    /**
     * Returns a cached IP immediately; otherwise resolves on IO thread.
     * Returns null if the look-up fails or takes longer than 3 s.
     */
    suspend fun resolve(domain: String): String? {
        cache[domain]?.let { return it }

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(3_000L) {
                try {
                    val ip = runCatching { resolveViaSystemDns(domain) }.getOrNull()
                    if (ip != null) {
                        if (cache.size >= 500) cache.clear()
                        cache[domain] = ip
                    }
                    ip
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun cached(domain: String): String? = cache[domain]
    fun clearCache() = cache.clear()

    // ──────────────────────────────────────────────────────────────────────────

    /** The phone's normal resolver: through this firewall when protection is on. */
    private fun resolveViaSystemDns(domain: String): String? =
        (InetAddress.getAllByName(domain).firstOrNull { it is Inet4Address }
            ?: InetAddress.getAllByName(domain).firstOrNull())
            ?.hostAddress
}
