package com.sentinel.core.vpn

/**
 * A single DNS question the firewall sends itself after the DNS settings change.
 *
 * The DNS screen shows the server that last answered a lookup, and that is only written
 * when some app looks something up. Right after the user picked another server the screen
 * therefore kept showing the old one until the next lookup (reported by the owner: it only
 * updated after a Chrome search). Asking one question through the forwarder straight away
 * makes the status reflect the new choice within seconds.
 *
 * The question is a standard query (RFC 1035 §4.1): header with RD set and one question,
 * type A, class IN. "example.com" is reserved for exactly this kind of use (RFC 2606).
 */
object DnsProbe {

    const val NAME = "example.com"

    fun query(name: String = NAME, txId: Int = 0x7a7a): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(txId ushr 8 and 0xFF); out.write(txId and 0xFF)
        out.write(0x01); out.write(0x00)          // flags: standard query, recursion desired
        out.write(0x00); out.write(0x01)          // QDCOUNT = 1
        repeat(6) { out.write(0x00) }             // ANCOUNT, NSCOUNT, ARCOUNT = 0
        for (label in name.trim('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "bad label" }
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0x00)                           // end of name
        out.write(0x00); out.write(0x01)          // QTYPE = A
        out.write(0x00); out.write(0x01)          // QCLASS = IN
        return out.toByteArray()
    }
}
