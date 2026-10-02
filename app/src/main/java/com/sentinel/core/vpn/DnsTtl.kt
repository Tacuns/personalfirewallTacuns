package com.sentinel.core.vpn

/**
 * Shortens how long apps and Android may remember a DNS answer that the firewall allowed.
 *
 * Android keeps each answer for its full TTL (often 5 minutes or more) and does not ask the
 * firewall again until it expires, so a site blocked in the meantime kept opening. Capping
 * the TTL handed back to the phone makes a new block apply much sooner. This is the same
 * idea as dnsmasq's `--max-ttl`. The forwarder's own cache still uses the real TTL, so this
 * adds no extra traffic to the upstream DNS server.
 *
 * Pure function over the DNS wire format (RFC 1035 section 4.1). Any reply it cannot fully
 * read is returned unchanged, so a parsing problem can never break a lookup.
 */
object DnsTtl {

    // 60 s, not 20 s: 20 s made the phone ask the firewall 2.3–2.6× more often (measured on
    // the emulator) and raised VPN CPU use. Chrome caches answers for 1 minute anyway.
    const val MAX_TTL_SECONDS = 60L

    private const val TYPE_OPT = 41   // EDNS pseudo-record: its "TTL" field holds flags, not a time

    fun cap(response: ByteArray, maxSeconds: Long = MAX_TTL_SECONDS): ByteArray {
        if (response.size < 12) return response
        val out = response.copyOf()
        val qd = u16(out, 4)
        val records = u16(out, 6) + u16(out, 8) + u16(out, 10)   // answer + authority + additional

        var cursor = 12
        repeat(qd) {
            cursor = skipName(out, cursor) ?: return response
            cursor += 4                                           // QTYPE + QCLASS
            if (cursor > out.size) return response
        }
        repeat(records) {
            cursor = skipName(out, cursor) ?: return response
            if (cursor + 10 > out.size) return response
            val type  = u16(out, cursor)
            val ttl   = u32(out, cursor + 4)
            val rdLen = u16(out, cursor + 8)
            if (type != TYPE_OPT && ttl > maxSeconds) writeU32(out, cursor + 4, maxSeconds)
            cursor += 10 + rdLen
            if (cursor > out.size) return response
        }
        return out
    }

    /** Position just after a (possibly compressed) domain name, or null if it runs off the end. */
    private fun skipName(buf: ByteArray, start: Int): Int? {
        var cursor = start
        while (cursor < buf.size) {
            val len = buf[cursor].toInt() and 0xFF
            when {
                len == 0            -> return cursor + 1
                len and 0xC0 == 0xC0 -> return if (cursor + 2 <= buf.size) cursor + 2 else null
                len and 0xC0 != 0   -> return null                // reserved label type
                else                -> cursor += 1 + len
            }
        }
        return null
    }

    private fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
        ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    private fun writeU32(b: ByteArray, i: Int, v: Long) {
        b[i]     = ((v ushr 24) and 0xFF).toByte()
        b[i + 1] = ((v ushr 16) and 0xFF).toByte()
        b[i + 2] = ((v ushr 8) and 0xFF).toByte()
        b[i + 3] = (v and 0xFF).toByte()
    }
}
