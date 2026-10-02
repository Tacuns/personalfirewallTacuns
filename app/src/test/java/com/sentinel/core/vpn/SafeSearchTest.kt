package com.sentinel.core.vpn

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SafeSearchTest {

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun name(n: String): ByteArray {
        val out = ByteArrayOutputStream()
        n.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }
        out.write(0)
        return out.toByteArray()
    }

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    private fun query(host: String, qtype: Int, id: Int = 0xBEEF): ByteArray =
        u16(id) + byteArrayOf(0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0) + name(host) + u16(qtype) + u16(1)

    /** Upstream reply for [host]: a CNAME hop then A records, like strict.bing.com returns. */
    private fun upstream(host: String, vararg ips: ByteArray, rcode: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u16(0)); out.write(0x81); out.write(0x80 or rcode)
        out.write(u16(1)); out.write(u16(1 + ips.size)); out.write(u16(0)); out.write(u16(0))
        out.write(name(host)); out.write(u16(1)); out.write(u16(1))
        val alias = name("edge.example.net")
        out.write(u16(0xC00C)); out.write(u16(5)); out.write(u16(1)); out.write(byteArrayOf(0, 0, 0x0E, 0x10))
        out.write(u16(alias.size)); val aliasOffset = out.size(); out.write(alias)
        ips.forEach {
            out.write(u16(0xC000 or aliasOffset)); out.write(u16(1)); out.write(u16(1))
            out.write(byteArrayOf(0, 0, 0x0E, 0x10)); out.write(u16(it.size)); out.write(it)
        }
        return out.toByteArray()
    }

    private fun r16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun readName(b: ByteArray, start: Int): Pair<String, Int> {
        val labels = ArrayList<String>(); var i = start; var end = -1
        while (true) {
            val len = b[i].toInt() and 0xFF
            if (len == 0) { if (end < 0) end = i + 1; break }
            if (len and 0xC0 == 0xC0) { if (end < 0) end = i + 2; i = r16(b, i) and 0x3FFF; continue }
            labels += String(b, i + 1, len); i += 1 + len
        }
        return labels.joinToString(".") to end
    }

    private data class Rr(val name: String, val type: Int, val ttl: Long, val data: ByteArray, val dataOffset: Int)

    private fun answers(b: ByteArray): List<Rr> {
        var i = readName(b, 12).second + 4
        return (0 until r16(b, 6)).map {
            val (n, e) = readName(b, i)
            val ttl = (r16(b, e + 4).toLong() shl 16) or r16(b, e + 6).toLong()
            val len = r16(b, e + 8)
            Rr(n, r16(b, e), ttl, b.copyOfRange(e + 10, e + 10 + len), e + 10).also { i = e + 10 + len }
        }
    }

    // ── which names are rewritten ──────────────────────────────────────────

    @Test fun searchSitesMapToTheirOfficialSafeNames() {
        assertEquals(SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor("www.google.com"))
        assertEquals(SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor("WWW.Google.co.in."))
        assertEquals(SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor("www.google.com.au"))
        assertEquals(SafeSearch.YOUTUBE_TARGET, SafeSearch.targetFor("m.youtube.com"))
        assertEquals(SafeSearch.YOUTUBE_TARGET, SafeSearch.targetFor("youtubei.googleapis.com"))
        assertEquals(SafeSearch.BING_TARGET, SafeSearch.targetFor("www.bing.com"))
        assertEquals(SafeSearch.DUCKDUCKGO_TARGET, SafeSearch.targetFor("duckduckgo.com"))
    }

    @Test fun otherNamesAreLeftAlone() {
        listOf(
            "google.com", "mail.google.com", "www.google.evil.com", "www.google.xyz", "youtube.com",
            "s.ytimg.com", "youtu.be", "googleapis.com", "bing.com", "example.com", ""
        ).forEach { assertNull(it, SafeSearch.targetFor(it)) }
    }

    // ── the reply sent to the phone ────────────────────────────────────────

    @Test fun addressQuestionGetsAliasAndSafeAddresses() = runBlocking {
        val q = query("www.bing.com", 1)
        var asked: ByteArray? = null
        val ip1 = byteArrayOf(204.toByte(), 79, (197).toByte(), 220.toByte())
        val ip2 = byteArrayOf(13, 107, 21, 200.toByte())
        val reply = SafeSearch.answer(q, SafeSearch.BING_TARGET) { asked = it; upstream(SafeSearch.BING_TARGET, ip1, ip2) }!!

        assertEquals(SafeSearch.BING_TARGET, readName(asked!!, 12).first)
        assertEquals(0xBEEF, r16(reply, 0))
        assertEquals(0x81, reply[2].toInt() and 0xFF)
        assertEquals(0x80, reply[3].toInt() and 0xFF)
        assertEquals("www.bing.com", readName(reply, 12).first)

        val rrs = answers(reply)
        assertEquals(3, rrs.size)
        assertEquals(5, rrs[0].type)
        assertEquals("www.bing.com", rrs[0].name)
        assertEquals(SafeSearch.BING_TARGET, readName(reply, rrs[0].dataOffset).first)
        assertEquals(listOf(1, 1), rrs.drop(1).map { it.type })
        assertEquals(listOf(SafeSearch.BING_TARGET, SafeSearch.BING_TARGET), rrs.drop(1).map { it.name })
        assertArrayEquals(ip1, rrs[1].data)
        assertArrayEquals(ip2, rrs[2].data)
        assertTrue(rrs.all { it.ttl <= DnsTtl.MAX_TTL_SECONDS })
    }

    @Test fun otherQuestionTypesGetAnEmptyAnswerWithoutLookingUp() = runBlocking {
        var called = false
        val reply = SafeSearch.answer(query("www.google.com", 65), SafeSearch.GOOGLE_TARGET) { called = true; null }!!
        assertEquals(false, called)
        assertEquals(0, r16(reply, 6))
        assertEquals(0x80, reply[3].toInt() and 0xFF)
        assertEquals("www.google.com", readName(reply, 12).first)
    }

    @Test fun failedLookupSendsNothing() = runBlocking {
        assertNull(SafeSearch.answer(query("www.google.com", 1), SafeSearch.GOOGLE_TARGET) { null })
        assertNull(SafeSearch.answer(byteArrayOf(1, 2, 3), SafeSearch.GOOGLE_TARGET) { null })
    }

    @Test fun upstreamErrorIsPassedOnWithoutAddresses() = runBlocking {
        val reply = SafeSearch.answer(query("www.google.com", 28), SafeSearch.GOOGLE_TARGET) {
            upstream(SafeSearch.GOOGLE_TARGET, rcode = 2)
        }!!
        assertEquals(2, reply[3].toInt() and 0x0F)
        assertEquals(0, r16(reply, 6))
    }
}
