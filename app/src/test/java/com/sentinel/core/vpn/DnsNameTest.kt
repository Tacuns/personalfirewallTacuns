package com.sentinel.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DnsNameTest {

    /** A DNS query payload (12-byte header + question) for [name]. */
    private fun query(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(ByteArray(12))
        name.split('.').forEach { l -> out.write(l.length); out.write(l.toByteArray(Charsets.US_ASCII)) }
        out.write(0); out.write(byteArrayOf(0, 1, 0, 1))
        return out.toByteArray()
    }

    private fun parse(b: ByteArray, len: Int = b.size) = DnsName.fromQuery(b, 0, len)

    @Test fun lowercaseNameUnchanged() = assertEquals("ad.doubleclick.net", parse(query("ad.doubleclick.net")))

    @Test fun mixedCaseIsLowercased() {
        assertEquals("stats.g.doubleclick.net", parse(query("Stats.G.DoubleClick.NET")))
        assertEquals("ads.yahoo.com", parse(query("ADS.YAHOO.COM")))
    }

    @Test fun turkishLocaleDoesNotChangeLetters() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("ist.example.io", parse(query("IST.EXAMPLE.IO")))
        } finally { Locale.setDefault(old) }
    }

    @Test fun truncatedLabelIsRejected() {
        val q = query("example.com")
        assertNull(parse(q, 12 + 4))      // cut inside the first label
    }

    @Test fun tooShortOrEmptyGivesNull() {
        assertNull(parse(ByteArray(12)))
        assertNull(parse(ByteArray(13)))  // header + root label only
    }

    @Test fun offsetIsRespected() {
        val q = query("Example.ORG")
        val withPrefix = ByteArray(28) + q          // 20 IP + 8 UDP header bytes in front
        assertEquals("example.org", DnsName.fromQuery(withPrefix, 28, withPrefix.size))
    }
}
