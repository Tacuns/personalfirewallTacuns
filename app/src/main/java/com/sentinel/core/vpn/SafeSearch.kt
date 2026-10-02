package com.sentinel.core.vpn

import java.io.ByteArrayOutputStream

/**
 * Safe search by DNS, following each provider's published network method: the search
 * site's name is answered as an alias (CNAME) of the provider's safe-search address.
 *
 *   Google     www.google.<country> -> forcesafesearch.google.com
 *   YouTube    the five hosts Google lists -> restrictmoderate.youtube.com
 *   Bing       www.bing.com, edgeservices.bing.com -> strict.bing.com
 *   DuckDuckGo duckduckgo.com -> safe.duckduckgo.com
 *
 * Only A and AAAA questions get addresses. Every other question type for these names
 * (for example HTTPS records, which can carry the normal addresses as hints) gets an
 * empty answer, so nothing leads around the safe address. If the safe address cannot be
 * looked up, no answer is sent at all rather than falling back to normal results.
 */
object SafeSearch {

    private const val TYPE_A     = 1
    private const val TYPE_CNAME = 5
    private const val TYPE_AAAA  = 28
    private const val CLASS_IN   = 1
    private const val MAX_TTL    = DnsTtl.MAX_TTL_SECONDS   // same short cache time as DnsTtl

    const val GOOGLE_TARGET     = "forcesafesearch.google.com"
    const val YOUTUBE_TARGET    = "restrictmoderate.youtube.com"
    const val BING_TARGET       = "strict.bing.com"
    const val DUCKDUCKGO_TARGET = "safe.duckduckgo.com"

    private val YOUTUBE_HOSTS = setOf(
        "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com",
        "youtube.googleapis.com", "www.youtube-nocookie.com"
    )
    private val BING_HOSTS = setOf("www.bing.com", "edgeservices.bing.com")

    // Endings from google.com/supported_domains ("google." removed).
    private val GOOGLE_ENDINGS = """
        com ad ae com.af com.ag al am co.ao com.ar as at com.au az ba com.bd be bf bg com.bh bi bj
        com.bn com.bo com.br bs bt co.bw by com.bz ca cd cf cg ch ci co.ck cl cm cn com.co co.cr
        com.cu cv com.cy cz de dj dk dm com.do dz com.ec ee com.eg es com.et fi com.fj fm fr ga ge
        gg com.gh com.gi gl gm gr com.gt gy com.hk hn hr ht hu co.id ie co.il im co.in iq is it je
        com.jm jo co.jp co.ke com.kh ki kg co.kr com.kw kz la com.lb li lk co.ls lt lu lv com.ly
        co.ma md me mg mk ml com.mm mn com.mt mu mv mw com.mx com.my co.mz com.na com.ng com.ni ne
        nl no com.np nr nu co.nz com.om com.pa com.pe com.pg com.ph com.pk pl pn com.pr ps pt
        com.py com.qa ro ru rw com.sa com.sb sc se com.sg sh si sk com.sl sn so sm sr st com.sv td
        tg co.th com.tj tl tm tn to com.tr tt com.tw co.tz com.ua co.ug co.uk com.uy co.uz com.vc
        co.ve co.vi com.vn vu ws rs co.za co.zm co.zw cat
    """.trim().split(Regex("\\s+")).toSet()

    /**
     * Whether YouTube is restricted along with the search sites. Set from the user's settings
     * by RuleEngine.reloadCache(); on unless the user turned it off.
     */
    @Volatile var includeYouTube: Boolean = true

    /** The safe-search name for [domain], or null when it is not a search site. */
    fun targetFor(domain: String, youTube: Boolean = includeYouTube): String? {
        val d = domain.lowercase().trimEnd('.')
        return when {
            d in YOUTUBE_HOSTS -> if (youTube) YOUTUBE_TARGET else null
            d in BING_HOSTS    -> BING_TARGET
            d == "duckduckgo.com" -> DUCKDUCKGO_TARGET
            d.startsWith("www.google.") && d.removePrefix("www.google.") in GOOGLE_ENDINGS -> GOOGLE_TARGET
            else -> null
        }
    }

    /**
     * Builds the reply to [query] (a DNS message asking about a search site).
     * [forward] looks up the safe-search name upstream. Returns null when no reply
     * should be sent.
     */
    suspend fun answer(query: ByteArray, target: String, forward: suspend (ByteArray) -> ByteArray?): ByteArray? {
        if (query.size < 12 || u16(query, 4) != 1) return null
        val questionEnd = questionEnd(query) ?: return null
        val qtype = u16(query, questionEnd - 4)
        if (qtype != TYPE_A && qtype != TYPE_AAAA) {
            return reply(query, questionEnd, rcode = 0, target = null, addresses = emptyList(), qtype = qtype)
        }
        val upstream = forward(buildQuery(target, qtype)) ?: return null
        if (upstream.size < 12) return null
        val rcode = upstream[3].toInt() and 0x0F
        if (rcode != 0) {
            return reply(query, questionEnd, rcode, target = null, addresses = emptyList(), qtype = qtype)
        }
        val addresses = addressRecords(upstream, qtype) ?: return null
        return reply(query, questionEnd, 0, target, addresses, qtype)
    }

    // ── DNS wire format (RFC 1035 section 4.1) ─────────────────────────────

    /** Index just after the question (name + type + class), or null if malformed. */
    private fun questionEnd(msg: ByteArray): Int? {
        var i = 12
        while (i < msg.size) {
            val len = msg[i].toInt() and 0xFF
            if (len == 0) return (i + 5).takeIf { it <= msg.size }
            if (len and 0xC0 != 0) return null          // no compression expected in a question
            i += 1 + len
        }
        return null
    }

    private fun encodeName(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun buildQuery(name: String, qtype: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))   // RD set, one question
        out.write(encodeName(name))
        put16(out, qtype)
        put16(out, CLASS_IN)
        return out.toByteArray()
    }

    /** (ttl, rdata) of every answer record of [qtype], or null if the reply is malformed. */
    private fun addressRecords(msg: ByteArray, qtype: Int): List<Pair<Long, ByteArray>>? {
        val qd = u16(msg, 4)
        val an = u16(msg, 6)
        var i = 12
        repeat(qd) {
            i = skipName(msg, i) ?: return null
            i += 4
        }
        val out = ArrayList<Pair<Long, ByteArray>>()
        repeat(an) {
            i = skipName(msg, i) ?: return null
            if (i + 10 > msg.size) return null
            val type  = u16(msg, i)
            val clazz = u16(msg, i + 2)
            val ttl   = u32(msg, i + 4)
            val rdLen = u16(msg, i + 8)
            val start = i + 10
            if (start + rdLen > msg.size) return null
            val expected = if (qtype == TYPE_A) 4 else 16
            if (type == qtype && clazz == CLASS_IN && rdLen == expected) {
                out += ttl to msg.copyOfRange(start, start + rdLen)
            }
            i = start + rdLen
        }
        return out
    }

    private fun reply(
        query: ByteArray, questionEnd: Int, rcode: Int,
        target: String?, addresses: List<Pair<Long, ByteArray>>, qtype: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(query[0].toInt()); out.write(query[1].toInt())                  // same transaction ID
        out.write(0x80 or (query[2].toInt() and 0x01))                            // response, keep RD
        out.write(0x80 or (rcode and 0x0F))                                       // RA + result code
        put16(out, 1)
        put16(out, if (target == null) 0 else 1 + addresses.size)
        put16(out, 0); put16(out, 0)
        out.write(query, 12, questionEnd - 12)                                    // original question
        if (target != null) {
            val name = encodeName(target)
            put16(out, 0xC00C)                                                    // owner: the question name
            put16(out, TYPE_CNAME); put16(out, CLASS_IN); put32(out, MAX_TTL)
            put16(out, name.size)
            val targetOffset = out.size()
            out.write(name)
            for ((ttl, rdata) in addresses) {
                put16(out, 0xC000 or targetOffset)                                // owner: the safe name
                put16(out, qtype); put16(out, CLASS_IN); put32(out, minOf(ttl, MAX_TTL))
                put16(out, rdata.size)
                out.write(rdata)
            }
        }
        return out.toByteArray()
    }

    private fun skipName(msg: ByteArray, start: Int): Int? {
        var i = start
        while (i < msg.size) {
            val len = msg[i].toInt() and 0xFF
            when {
                len == 0 -> return i + 1
                len and 0xC0 == 0xC0 -> return (i + 2).takeIf { it <= msg.size }
                len and 0xC0 != 0 -> return null
                else -> i += 1 + len
            }
        }
        return null
    }

    private fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
        ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    private fun put16(out: ByteArrayOutputStream, v: Int) { out.write(v ushr 8 and 0xFF); out.write(v and 0xFF) }

    private fun put32(out: ByteArrayOutputStream, v: Long) { put16(out, (v ushr 16).toInt() and 0xFFFF); put16(out, v.toInt() and 0xFFFF) }
}
