package com.sentinel.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlocklistParserTest {

    /** The parser the worker used before streaming, kept verbatim as the reference. */
    private fun legacyParse(text: String, format: String): List<String> {
        val result = ArrayList<String>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) continue
            val domain = when (format) {
                "hosts" -> {
                    val parts = line.split(Regex("\\s+"), limit = 3)
                    val ip = parts.getOrNull(0) ?: continue
                    if (ip != "0.0.0.0" && ip != "127.0.0.1" && ip != "::1") continue
                    parts.getOrNull(1)?.substringBefore('#')?.trim() ?: continue
                }
                "abp" -> {
                    if (!line.startsWith("||") || line.startsWith("@@")) continue
                    line.removePrefix("||").substringBefore('^').substringBefore('/').substringBefore('$').trim()
                }
                else -> line.substringBefore('#').trim()
            }
            if (domain.isNotEmpty() && domain != "0.0.0.0" && !domain.startsWith('.')
                && domain.contains('.') && !domain.contains('*')) {
                result.add(domain.lowercase())
            }
        }
        return result.distinct()
    }

    private fun newParse(text: String, format: String): List<String> =
        text.lines().mapNotNull { BlocklistParser.parseLine(it, format) }.distinct()

    private val sample = """
        # comment
        ! abp comment
        [Adblock Plus 2.0]
        0.0.0.0 Ads.Example.com
        127.0.0.1 localhost
        127.0.0.1	tracker.example.net # trailing comment
        ::1 six.example.org
        0.0.0.0 0.0.0.0
        1.2.3.4 other-ip.example.com
        0.0.0.0
        ||adserver.example.com^
        ||cdn.example.com^${'$'}third-party
        ||path.example.com/banner
        @@||allowed.example.com^
        ||*.wild.example.com^
        plain.example.com
        .leading-dot.example.com
        nodot
        spaced entry.example.com
        ads.example.com
        ads.example.com
    """.trimIndent()

    @Test fun builtInFormatsBehaveExactlyAsBefore() {
        for (format in listOf("hosts", "abp", "domains", "unknown")) {
            assertEquals("format $format", legacyParse(sample, format), newParse(sample, format))
        }
    }

    @Test fun autoReadsEveryCommonFormat() {
        assertEquals("ads.example.com", BlocklistParser.parseLine("0.0.0.0 ads.example.com", "auto"))
        assertEquals("tracker.example.net", BlocklistParser.parseLine("127.0.0.1\ttracker.example.net", "auto"))
        assertEquals("adserver.example.com", BlocklistParser.parseLine("||adserver.example.com^", "auto"))
        assertEquals("plain.example.com", BlocklistParser.parseLine("Plain.Example.com", "auto"))
    }

    @Test fun autoRejectsJunkFromUnknownLinks() {
        listOf(
            "<html>", "<title>Not found</title>", "spaced entry.example.com", "1.2.3.4 other.example.com",
            "@@||allowed.example.com^", "||*.wild.example.com^", "nodot", "0.0.0.0", "bad!chars.example.com",
            "a".repeat(250) + ".com"
        ).forEach { assertNull(it, BlocklistParser.parseLine(it, "auto")) }
    }

    @Test fun linksAreCleanedAndChecked() {
        assertEquals("https://example.com/list.txt", CustomBlocklist.normalizeLink("  https://example.com/list.txt "))
        assertEquals("https://example.com/hosts", CustomBlocklist.normalizeLink("example.com/hosts"))
        assertEquals("https://example.com/a.txt", CustomBlocklist.normalizeLink("http://example.com/a.txt"))
        assertEquals("https://example.com/b.txt", CustomBlocklist.normalizeLink("HTTP://example.com/b.txt"))
        listOf(
            "", "ftp://example.com/x", "file:///sdcard/list.txt", "javascript:alert(1)", "https://localhost/x",
            "https://example .com/x", "content://com.example/x", "https://.example.com/x"
        ).forEach { assertNull(it, CustomBlocklist.normalizeLink(it)) }
    }

    @Test fun newSourceIsFreeSwitchedOffAndNamed() {
        val s = CustomBlocklist.newSource("BLOCKLIST_CUSTOM_1", "  ", "https://lists.example.com/hosts.txt")
        assertEquals("lists.example.com", s.displayName)
        assertEquals("auto", s.format)
        assertEquals(true, s.isDefault)
        assertEquals(false, s.enabled)
        assertEquals(true, CustomBlocklist.isCustom(s.sourceKey))
        assertEquals(false, CustomBlocklist.isCustom("BLOCKLIST_STEVENBLACK"))
        assertNotNull(CustomBlocklist.newKey().takeIf { it.startsWith("BLOCKLIST") })
    }
}
