package com.sentinel.core.vpn

/**
 * Reads the question name from a DNS query.
 *
 * DNS names ignore letter case (RFC 4343), but every rule set is stored in lowercase.
 * A query written as "Stats.G.DoubleClick.NET" used to miss the blocklist and was allowed
 * (seen on the emulator), so the name is lowercased here, once, for all matching. The query
 * sent upstream is left exactly as the app wrote it.
 *
 * Pure function only, so it is unit-tested on the JVM.
 */
object DnsName {

    // QNAME starts 12 bytes into the DNS payload (after the DNS header).
    // Each label: [length byte][label chars] ... terminated by 0x00.
    // Example: 0x06 "google" 0x03 "com" 0x00 → "google.com"
    fun fromQuery(buffer: ByteArray, dnsOffset: Int, length: Int): String? {
        val qnameStart = dnsOffset + 12
        if (length < qnameStart + 1) return null

        var cursor = qnameStart
        val builder = StringBuilder()

        while (cursor < length) {
            val labelLen = buffer[cursor].toInt() and 0xFF
            if (labelLen == 0) break
            cursor++
            if (cursor + labelLen > length) return null
            if (builder.isNotEmpty()) builder.append('.')
            builder.append(String(buffer, cursor, labelLen, Charsets.UTF_8))
            cursor += labelLen
        }

        // lowercase() uses Locale.ROOT, so no locale (for example Turkish) changes the letters.
        return builder.takeIf { it.isNotEmpty() }?.toString()?.lowercase()
    }
}
