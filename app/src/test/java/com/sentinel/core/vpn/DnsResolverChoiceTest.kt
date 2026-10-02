package com.sentinel.core.vpn

import com.sentinel.core.vpn.DnsResolverChoice.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsResolverChoiceTest {

    // JVM stand-ins for android.net.InetAddresses: literal IPs only, never a DNS lookup.
    private val numeric = Regex("^[0-9.]+$|^[0-9a-fA-F:]+$")
    private fun isNumeric(s: String) = numeric.matches(s) && (s.count { it == '.' } == 3 || ':' in s)
    private fun custom(text: String) = DnsResolverChoice.parseCustom(text, ::isNumeric) { InetAddress.getByName(it) }

    private val router = InetAddress.getByName("192.168.1.1")
    private fun addrs(plan: List<DnsResolverChoice.Server>) = plan.map { it.address.hostAddress }

    @Test fun `defaults keep today's behaviour - Google first, Cloudflare as backup`() {
        val plan = DnsResolverChoice.buildPlan(Settings(), emptyList(), ::custom)
        assertEquals(listOf("8.8.8.8"), addrs(plan.main))
        assertEquals(listOf("1.1.1.1"), addrs(plan.backup))
    }

    @Test fun `system dns uses the network's own servers`() {
        val plan = DnsResolverChoice.buildPlan(
            Settings(main = DnsResolverChoice.SYSTEM, backup = DnsResolverChoice.NONE),
            listOf(router), ::custom)
        assertEquals(listOf("192.168.1.1"), addrs(plan.main))
    }

    @Test fun `no backup chosen means the firewall never falls back`() {
        val plan = DnsResolverChoice.buildPlan(
            Settings(main = DnsResolverChoice.SYSTEM, backup = DnsResolverChoice.NONE),
            listOf(router), ::custom)
        assertTrue("a family filter must never be bypassed silently", plan.backup.isEmpty())
    }

    @Test fun `a backup equal to the main server is not a backup`() {
        val plan = DnsResolverChoice.buildPlan(
            Settings(main = DnsResolverChoice.GOOGLE, backup = DnsResolverChoice.GOOGLE),
            emptyList(), ::custom)
        assertTrue(plan.backup.isEmpty())
    }

    @Test fun `our own tunnel address is never used as a system server`() {
        val plan = DnsResolverChoice.buildPlan(
            Settings(main = DnsResolverChoice.SYSTEM, backup = DnsResolverChoice.NONE),
            listOf(InetAddress.getByName("10.0.0.1"), router), ::custom)
        assertEquals(listOf("192.168.1.1"), addrs(plan.main))
    }

    @Test fun `custom server must be a usable literal address`() {
        assertNotNull(custom("192.168.1.2"))
        assertNotNull(custom("9.9.9.9"))
        assertNotNull(custom("2606:4700:4700::1111"))
        listOf("", "   ", "dns.google", "10.0.0.1", "10.0.0.2", "127.0.0.1", "0.0.0.0", "224.0.0.1", "::1", "999.1.1.1")
            .forEach { assertNull("'$it' must be refused", custom(it)) }
    }

    @Test fun `an invalid custom main server gives an empty main list, never a guess`() {
        val plan = DnsResolverChoice.buildPlan(
            Settings(main = DnsResolverChoice.CUSTOM, mainCustom = "not-an-ip", backup = DnsResolverChoice.NONE),
            emptyList(), ::custom)
        assertTrue(plan.main.isEmpty())
    }

    @Test fun `unknown saved values fall back to the defaults`() {
        assertEquals(DnsResolverChoice.DEFAULT_MAIN, DnsResolverChoice.sanitizeMain("quad9"))
        assertEquals(DnsResolverChoice.DEFAULT_MAIN, DnsResolverChoice.sanitizeMain(null))
        assertEquals(DnsResolverChoice.DEFAULT_BACKUP, DnsResolverChoice.sanitizeBackup("whatever"))
        assertEquals(DnsResolverChoice.NONE, DnsResolverChoice.sanitizeBackup(DnsResolverChoice.NONE))
    }

    @Test fun `the plan key changes when the servers change, so stale answers are dropped`() {
        val a = DnsResolverChoice.buildPlan(Settings(), emptyList(), ::custom).key
        val b = DnsResolverChoice.buildPlan(Settings(main = DnsResolverChoice.CLOUDFLARE), emptyList(), ::custom).key
        assertTrue(a != b)
    }

    @Test fun `youtube follows its own switch and search sites do not`() {
        assertEquals(SafeSearch.YOUTUBE_TARGET, SafeSearch.targetFor("www.youtube.com", youTube = true))
        assertNull(SafeSearch.targetFor("www.youtube.com", youTube = false))
        assertNull(SafeSearch.targetFor("youtubei.googleapis.com", youTube = false))
        assertEquals(SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor("www.google.com", youTube = false))
        assertEquals(SafeSearch.BING_TARGET, SafeSearch.targetFor("www.bing.com", youTube = false))
        assertEquals(SafeSearch.DUCKDUCKGO_TARGET, SafeSearch.targetFor("duckduckgo.com", youTube = false))
    }
}
