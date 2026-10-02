package com.sentinel.core.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsTtlTest {

    // ── Tiny DNS message builder ────────────────────────────────────────────

    private class Msg {
        private val body = ByteArrayOutputStream()
        private var qd = 0; private var an = 0; private var ns = 0; private var ar = 0

        private fun u16(v: Int) { body.write(v ushr 8 and 0xFF); body.write(v and 0xFF) }
        private fun u32(v: Long) { u16((v ushr 16).toInt() and 0xFFFF); u16(v.toInt() and 0xFFFF) }
        private fun name(n: String) {
            n.split('.').forEach { body.write(it.length); body.write(it.toByteArray()) }
            body.write(0)
        }

        fun question(n: String) = apply { name(n); u16(1); u16(1); qd++ }

        /** Answer that points back at the question name (offset 12) with compression. */
        fun answer(ttl: Long, type: Int = 1) = apply {
            u16(0xC00C); u16(type); u16(1); u32(ttl); u16(4); body.write(byteArrayOf(1, 2, 3, 4)); an++
        }

        fun authority(ttl: Long) = apply {
            u16(0xC00C); u16(6); u16(1); u32(ttl); u16(2); body.write(byteArrayOf(0, 0)); ns++
        }

        /** EDNS OPT record: root name, type 41, and a "TTL" that is really flags. */
        fun opt(flags: Long) = apply { body.write(0); u16(41); u16(1232); u32(flags); u16(0); ar++ }

        fun bytes(): ByteArray {
            val head = ByteArrayOutputStream()
            fun h(v: Int) { head.write(v ushr 8 and 0xFF); head.write(v and 0xFF) }
            h(0x1234); h(0x8180); h(qd); h(an); h(ns); h(ar)
            return head.toByteArray() + body.toByteArray()
        }
    }

    /** TTL of the Nth resource record (answer, then authority, then additional). */
    private fun ttlOf(msg: ByteArray, index: Int): Long {
        var c = 12
        while (msg[c].toInt() != 0) c += 1 + msg[c]
        c += 1 + 4
        repeat(index) {
            c = if (msg[c].toInt() and 0xC0 == 0xC0) c + 2 else c + 1
            c += 10 + (((msg[c + 8].toInt() and 0xFF) shl 8) or (msg[c + 9].toInt() and 0xFF))
        }
        c = if (msg[c].toInt() and 0xC0 == 0xC0) c + 2 else c + 1
        return ((msg[c + 4].toLong() and 0xFF) shl 24) or ((msg[c + 5].toLong() and 0xFF) shl 16) or
               ((msg[c + 6].toLong() and 0xFF) shl 8) or (msg[c + 7].toLong() and 0xFF)
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test fun longTtlIsShortened() {
        val out = DnsTtl.cap(Msg().question("apnews.com").answer(300).bytes())
        assertEquals(DnsTtl.MAX_TTL_SECONDS, ttlOf(out, 0))
    }

    @Test fun shortTtlIsKept() {
        val out = DnsTtl.cap(Msg().question("example.com").answer(5).bytes())
        assertEquals(5L, ttlOf(out, 0))
    }

    @Test fun everyAnswerAndAuthorityRecordIsCapped() {
        val out = DnsTtl.cap(Msg().question("a.example.com").answer(300).answer(3600).authority(900).bytes())
        assertEquals(DnsTtl.MAX_TTL_SECONDS, ttlOf(out, 0))
        assertEquals(DnsTtl.MAX_TTL_SECONDS, ttlOf(out, 1))
        assertEquals(DnsTtl.MAX_TTL_SECONDS, ttlOf(out, 2))
    }

    @Test fun ednsOptFlagsAreNeverChanged() {
        val flags = 0x00008000L   // DNSSEC OK bit — looks like a large TTL but is not one
        val out = DnsTtl.cap(Msg().question("example.com").answer(300).opt(flags).bytes())
        assertEquals(DnsTtl.MAX_TTL_SECONDS, ttlOf(out, 0))
        assertEquals(flags, ttlOf(out, 1))
    }

    @Test fun onlyTtlBytesChange() {
        val input = Msg().question("example.com").answer(300).bytes()
        val out = DnsTtl.cap(input)
        assertEquals(input.size, out.size)
        val diff = input.indices.filter { input[it] != out[it] }
        assertEquals(true, diff.isNotEmpty() && diff.all { it in (input.size - 10)..(input.size - 7) })
    }

    @Test fun originalArrayIsNotModified() {
        val input = Msg().question("example.com").answer(300).bytes()
        val copy = input.copyOf()
        val out = DnsTtl.cap(input)
        assertArrayEquals(copy, input)
        assertNotSame(input, out)
    }

    @Test fun truncatedReplyIsReturnedUnchanged() {
        val full = Msg().question("example.com").answer(300).bytes()
        val cut = full.copyOf(full.size - 6)
        assertArrayEquals(cut, DnsTtl.cap(cut))
    }

    @Test fun tooShortOrNoRecordsIsUnchanged() {
        val tiny = byteArrayOf(1, 2, 3)
        assertArrayEquals(tiny, DnsTtl.cap(tiny))
        val noAnswers = Msg().question("example.com").bytes()
        assertArrayEquals(noAnswers, DnsTtl.cap(noAnswers))
    }
}
