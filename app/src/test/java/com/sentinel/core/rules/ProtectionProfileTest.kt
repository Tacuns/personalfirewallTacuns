package com.sentinel.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionProfileTest {

    private val base = FirewallOptions(dnsMain = "cloudflare", logRetentionHours = 24)

    @Test fun `each level maps to exactly the documented switches`() {
        val n = base.withProfile(ProtectionProfile.NORMAL)
        assertTrue(n.blockLookalikes); assertFalse(n.safeSearch); assertFalse(n.watchOnly)

        val s = base.withProfile(ProtectionProfile.STRICT)
        assertTrue(s.blockLookalikes); assertTrue(s.safeSearch); assertFalse(s.safeSearchYouTube); assertFalse(s.watchOnly)

        val k = base.withProfile(ProtectionProfile.KIDS)
        assertTrue(k.blockLookalikes); assertTrue(k.safeSearch); assertTrue(k.safeSearchYouTube); assertFalse(k.watchOnly)

        ProtectionProfile.entries.forEach { assertTrue("${it.key} keeps the ad list on", it.adList) }
    }

    @Test fun `a level never leaves watch-only on`() {
        val watching = base.copy(watchOnly = true)
        ProtectionProfile.entries.forEach { assertFalse(watching.withProfile(it).watchOnly) }
    }

    @Test fun `a level keeps everything it does not cover`() {
        ProtectionProfile.entries.forEach {
            val out = base.withProfile(it)
            assertEquals("cloudflare", out.dnsMain)
            assertEquals(24, out.logRetentionHours)
        }
    }

    @Test fun `Normal leaves the YouTube choice as the user set it`() {
        assertFalse(base.copy(safeSearchYouTube = false).withProfile(ProtectionProfile.NORMAL).safeSearchYouTube)
        assertTrue(base.copy(safeSearchYouTube = true).withProfile(ProtectionProfile.NORMAL).safeSearchYouTube)
    }

    @Test fun `applying a level is detected as that level`() {
        ProtectionProfile.entries.forEach { p ->
            assertEquals(p, detectProfile(base.withProfile(p), adListOn = true))
        }
    }

    @Test fun `changing any switch afterwards shows Custom`() {
        val strict = base.withProfile(ProtectionProfile.STRICT)
        assertNull(detectProfile(strict.copy(blockLookalikes = false), true))
        assertNull(detectProfile(strict.copy(watchOnly = true), true))
        assertNull(detectProfile(strict, adListOn = false))
        assertEquals(ProtectionProfile.KIDS, detectProfile(strict.copy(safeSearchYouTube = true), true))
    }

    @Test fun `the app's first-run settings are Custom, not a level it never chose`() =
        assertNull(detectProfile(FirewallOptions(), adListOn = true))   // look-alike is off by default

    @Test fun `stored keys round-trip`() {
        ProtectionProfile.entries.forEach { assertEquals(it, ProtectionProfile.fromKey(it.key)) }
        assertNull(ProtectionProfile.fromKey(ProtectionProfile.CUSTOM_KEY))
        assertNull(ProtectionProfile.fromKey(null))
    }
}
