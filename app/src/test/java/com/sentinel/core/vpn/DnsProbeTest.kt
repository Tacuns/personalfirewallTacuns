package com.sentinel.core.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsProbeTest {

    @Test fun `builds a standard A query for example dot com`() {
        val q = DnsProbe.query("example.com", 0x1234)
        val expected = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0x00, 0x01, 0x00, 0x01
        )
        assertArrayEquals(expected, q)
    }

    @Test fun `the name read back from the query is the probe name`() =
        assertEquals(DnsProbe.NAME, DnsName.fromQuery(DnsProbe.query(), 0, DnsProbe.query().size))
}
