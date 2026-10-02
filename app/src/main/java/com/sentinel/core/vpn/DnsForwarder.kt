package com.sentinel.core.vpn

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-socket DNS forwarder with TTL-based response cache.
 *
 * Architecture:
 *   - One DatagramSocket, protected once at startup. All queries share it.
 *   - Responses matched to callers by DNS Transaction ID (ConcurrentHashMap).
 *   - Callers SUSPEND (not block) while awaiting responses — IO threads: exactly 2.
 *   - TTL cache: repeated queries served from memory, zero network round-trip.
 *
 * Cache design (RFC 1035 / RFC 2308 compliant):
 *   - Key: "domain|QTYPE" — A and AAAA records cached separately.
 *   - Only NOERROR (RCODE=0) responses with ≥1 answer are cached.
 *   - Expiry = minimum TTL across all Answer records, capped at 1 hour.
 *   - TX ID always rewritten when serving from cache (standard practice).
 *   - In-memory only, cleared on close() — no disk persistence.
 */
class DnsForwarder(
    scope: CoroutineScope,
    protectSocket: (DatagramSocket) -> Unit
) {
    companion object {
        private const val TAG = "DnsForwarder"
        private const val DNS_PORT = 53
        // After the main server fails to answer, go to the backup first for this long so each
        // lookup does not wait out the main server's timeout again; then try the main again.
        private const val PREFER_BACKUP_MS = 30_000L
        // At most this many of the main servers are tried for one lookup, to bound the wait.
        private const val MAX_MAIN_TRIES = 2
        private const val TIMEOUT_MS = 1500L
        private const val MAX_CACHE_ENTRIES = 500
        private const val MAX_TTL_SECONDS = 3600L
    }

    private class DnsCacheEntry(
        val response: ByteArray,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    }

    /** A lookup waiting for its answer, and the one server that answer must come from. */
    private class Pending(val deferred: CompletableDeferred<ByteArray>, val server: InetAddress)

    private val socket = DatagramSocket()
    private val pending = ConcurrentHashMap<Short, Pending>()

    @Volatile private var planKey: String? = null
    @Volatile private var preferBackupUntil = 0L
    private val secureRandom = SecureRandom()
    private val cache = ConcurrentHashMap<String, DnsCacheEntry>()

    init {
        protectSocket(socket)
        // The socket is left UNCONNECTED on purpose: a connected UDP socket caches the route
        // it had at connect() time, so after the phone changes network (Wi-Fi reconnect,
        // Wi-Fi<->mobile) that route can go dead and every lookup fails with ENETUNREACH until
        // the tunnel is rebuilt. An unconnected socket picks the current route on each send.
        // Forged answers are instead kept out by checking each reply's source in the receiver.
        startReceiver(scope)
    }

    /**
     * Receiver loop: one coroutine reading all responses from the shared socket.
     * Dispatches each response to the correct suspended coroutine via its CompletableDeferred.
     */
    private fun startReceiver(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1500)
            val pkt = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    pkt.length = buf.size            // reset capacity; receive() shrinks it each time
                    socket.receive(pkt)
                    // Only accept an answer from the exact server that lookup was sent to — the
                    // same protection a connected socket gives, without freezing the route.
                    // Drops off-path forgeries that merely guessed the transaction ID, and a
                    // mismatch never removes the waiting lookup, so a forgery cannot cancel it.
                    val ourTxId = readTxId(buf)
                    val waiting = pending[ourTxId] ?: continue
                    if (pkt.address != waiting.server || pkt.port != DNS_PORT) continue
                    if (pending.remove(ourTxId, waiting)) waiting.deferred.complete(buf.copyOf(pkt.length))
                } catch (e: Exception) {
                    if (socket.isClosed) break
                    Log.w(TAG, "Receiver error: ${e.message}")
                }
            }
        }
    }

    /**
     * Forward one DNS query. Checks the TTL cache first — if hit, returns instantly.
     * On cache miss, suspends the calling coroutine (no thread held) until the response
     * arrives from the user's main DNS server, or from their chosen backup when the main
     * server does not answer (see DnsResolverChoice), then caches and returns it.
     *
     * TX ID is rewritten on the wire to our own unique value to prevent collisions,
     * then restored to the original before returning (cache hits also get TX ID rewritten).
     */
    suspend fun forward(dnsPayload: ByteArray): ByteArray? {
        if (dnsPayload.size < 2) return null

        val originalTxId = readTxId(dnsPayload)

        // A different server can give different answers (a family filter, for one), so the
        // cache never carries answers over from a previous choice.
        val plan = DnsResolverChoice.currentPlan()
        if (plan.key != planKey) {
            planKey = plan.key
            cache.clear()
            preferBackupUntil = 0L
        }

        // Cache hit — serve instantly with zero network work
        val cacheKey = extractCacheKey(dnsPayload)
        if (cacheKey != null) {
            val cached = cache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                val hit = cached.response.clone()
                writeTxId(hit, originalTxId)
                return hit
            }
        }

        // Cache miss — main server first, then the user's backup (if they chose one). Only a
        // server that does not answer at all counts as failed: an answer such as "no such
        // domain" or a filter's block reply is a real answer and is passed on as it is.
        val preferBackup = plan.backup.isNotEmpty() && System.currentTimeMillis() < preferBackupUntil
        // Bounded wait: at most two of the main servers (System DNS can list several) and one
        // backup, so a chosen backup is never crowded out.
        val mainTry = plan.main.take(MAX_MAIN_TRIES)
        val backupTry = plan.backup.take(1)
        val attempts = if (preferBackup) backupTry + mainTry else mainTry + backupTry
        for (server in attempts) {
            val response = ask(server.address, dnsPayload, originalTxId) ?: continue
            if (server in plan.backup) {
                preferBackupUntil = System.currentTimeMillis() + PREFER_BACKUP_MS
                DnsResolverChoice.report(DnsResolverChoice.STATE_BACKUP, server.address)
            } else {
                preferBackupUntil = 0L
                DnsResolverChoice.report(DnsResolverChoice.STATE_MAIN, server.address)
            }

            // Store in cache if response is a valid NOERROR with answers
            if (cacheKey != null && shouldCache(response)) {
                val ttl = extractMinTtlSeconds(response).coerceIn(1L, MAX_TTL_SECONDS)
                evictIfNeeded()
                cache[cacheKey] = DnsCacheEntry(
                    response = response.clone(),
                    expiresAt = System.currentTimeMillis() + ttl * 1000L
                )
            }
            return response
        }
        DnsResolverChoice.report(DnsResolverChoice.STATE_FAILING, null)
        return null
    }

    /** One try against one server, with a fresh random transaction ID (see DnsTxId). */
    private suspend fun ask(server: InetAddress, dnsPayload: ByteArray, originalTxId: Short): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        val waiting = Pending(deferred, server)
        var ourTxId: Short
        do {
            ourTxId = DnsTxId.next(secureRandom, pending.keys)
        } while (pending.putIfAbsent(ourTxId, waiting) != null)

        val wire = dnsPayload.clone()
        writeTxId(wire, ourTxId)

        return try {
            socket.send(DatagramPacket(wire, wire.size, server, DNS_PORT))
            val response = withTimeout(TIMEOUT_MS) { deferred.await() }
            writeTxId(response, originalTxId)
            response
        } catch (e: Exception) {
            Log.w(TAG, "Forward failed: ${e.javaClass.simpleName}")
            null
        } finally {
            pending.remove(ourTxId, waiting)
        }
    }

    fun close() {
        runCatching { socket.close() }
        pending.values.forEach { it.deferred.cancel() }
        pending.clear()
        cache.clear()
    }

    // Only cache NOERROR (RCODE=0) responses that have at least one answer record.
    // NXDOMAIN, SERVFAIL, and empty responses are never cached.
    private fun shouldCache(response: ByteArray): Boolean {
        if (response.size < 12) return false
        val rcode = response[3].toInt() and 0x0F
        val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        return rcode == 0 && anCount > 0
    }

    // Build cache key "domain.name|QTYPE" from DNS query payload.
    // A-records (QTYPE=1) and AAAA-records (QTYPE=28) get separate entries per RFC 2308.
    // Returns null for malformed queries — they are forwarded but never cached.
    private fun extractCacheKey(payload: ByteArray): String? {
        if (payload.size < 17) return null
        var cursor = 12
        val name = StringBuilder()
        while (cursor < payload.size) {
            val labelLen = payload[cursor].toInt() and 0xFF
            if (labelLen == 0) { cursor++; break }
            if (labelLen and 0xC0 == 0xC0) return null  // Compression pointer in query — unexpected, skip cache
            cursor++
            if (cursor + labelLen > payload.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(payload, cursor, labelLen, Charsets.US_ASCII))
            cursor += labelLen
        }
        if (cursor + 2 > payload.size) return null
        val qtype = ((payload[cursor].toInt() and 0xFF) shl 8) or (payload[cursor + 1].toInt() and 0xFF)
        return "${name}|${qtype}"
    }

    // Parse the DNS response Answer section and return the minimum TTL in seconds.
    // All bounds are checked — returns 0 if the response cannot be parsed safely.
    private fun extractMinTtlSeconds(response: ByteArray): Long {
        if (response.size < 12) return 0
        val qdCount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
        val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (anCount == 0) return 0

        var cursor = 12

        // Skip question section
        repeat(qdCount) {
            while (cursor < response.size) {
                val len = response[cursor].toInt() and 0xFF
                if (len == 0) { cursor++; break }
                if (len and 0xC0 == 0xC0) { cursor += 2; break }
                cursor += len + 1
            }
            cursor += 4  // QTYPE + QCLASS
        }

        // Collect minimum TTL across all answer records
        var minTtl = Long.MAX_VALUE
        repeat(anCount) {
            if (cursor >= response.size) return@repeat
            // Skip NAME field (label sequence or compression pointer)
            while (cursor < response.size) {
                val len = response[cursor].toInt() and 0xFF
                if (len == 0) { cursor++; break }
                if (len and 0xC0 == 0xC0) { cursor += 2; break }
                cursor += len + 1
            }
            if (cursor + 10 > response.size) return@repeat
            cursor += 4  // TYPE + CLASS
            val ttl = ((response[cursor].toLong() and 0xFF) shl 24) or
                      ((response[cursor + 1].toLong() and 0xFF) shl 16) or
                      ((response[cursor + 2].toLong() and 0xFF) shl 8) or
                       (response[cursor + 3].toLong() and 0xFF)
            cursor += 4
            val rdLen = ((response[cursor].toInt() and 0xFF) shl 8) or (response[cursor + 1].toInt() and 0xFF)
            cursor += 2 + rdLen
            if (ttl > 0) minTtl = minOf(minTtl, ttl)
        }

        return if (minTtl == Long.MAX_VALUE) 0 else minTtl
    }

    // Remove expired entries when at capacity. If still full after sweep, evict one arbitrary entry.
    private fun evictIfNeeded() {
        if (cache.size < MAX_CACHE_ENTRIES) return
        cache.entries.filter { it.value.isExpired() }.forEach { cache.remove(it.key) }
        if (cache.size >= MAX_CACHE_ENTRIES) cache.keys.firstOrNull()?.let { cache.remove(it) }
    }

    private fun readTxId(buf: ByteArray): Short =
        (((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)).toShort()

    private fun writeTxId(buf: ByteArray, txId: Short) {
        buf[0] = ((txId.toInt() ushr 8) and 0xFF).toByte()
        buf[1] = (txId.toInt() and 0xFF).toByte()
    }
}
