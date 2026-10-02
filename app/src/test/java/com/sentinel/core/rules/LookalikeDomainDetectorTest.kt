package com.sentinel.core.rules

import com.sentinel.core.rules.LookalikeDomainDetector.MatchReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LookalikeDomainDetectorTest {

    private val trusted = setOf("paypal.com", "amazon.com", "facebook.com")

    private fun check(domain: String) = LookalikeDomainDetector.findLookalike(domain, trusted)

    // ── level-squatting (new) ───────────────────────────────────────────────

    @Test fun levelSquatIsDetected() {
        // Real brand as a leading label, attacker owns the apex.
        val m = check("paypal.com.secure-login.net")
        assertNotNull(m)
        assertEquals(MatchReason.BRAND_AS_SUBDOMAIN, m!!.reason)
        assertEquals("paypal.com", m.matchedTrustedDomain)
    }

    @Test fun levelSquatDetectedMidDomain() {
        val m = check("login.paypal.com.attacker.io")
        assertNotNull(m)
        assertEquals(MatchReason.BRAND_AS_SUBDOMAIN, m!!.reason)
    }

    @Test fun levelSquatWithDifferentTld() {
        val m = check("amazon.com.verify-account.co")
        assertNotNull(m)
        assertEquals("amazon.com", m!!.matchedTrustedDomain)
    }

    // ── legitimate traffic must NOT be flagged ──────────────────────────────

    @Test fun realTrustedDomainIsNotFlagged() {
        assertNull(check("paypal.com"))
        assertNull(check("www.paypal.com"))
    }

    @Test fun genuineSubdomainOfTrustedIsNotFlagged() {
        assertNull(check("login.paypal.com"))
        assertNull(check("api.checkout.paypal.com"))
    }

    @Test fun brandInfrastructureStillNotFlagged() {
        // Regression guard for the previously-fixed false positive: these are
        // genuine Amazon-owned domains and must stay unflagged.
        assertNull(check("m.media-amazon.com"))
        assertNull(check("images-na.ssl-images-amazon.com"))
        assertNull(check("s.amazon-adsystem.com"))
    }

    @Test fun unrelatedDomainIsNotFlagged() {
        assertNull(check("example.com"))
        assertNull(check("kernel.org"))
    }

    // ── existing detection paths must still work ────────────────────────────

    @Test fun confusableCharacterStillDetected() {
        val m = check("paypa1.com")
        assertNotNull(m)
        assertEquals(MatchReason.CONFUSABLE_CHARACTERS, m!!.reason)
    }

    @Test fun combosquatStillDetected() {
        val m = check("paypal-secure-login.com")
        assertNotNull(m)
        assertEquals(MatchReason.BRAND_NAME_WITH_EXTRA_TOKENS, m!!.reason)
    }

    @Test fun typoStillDetected() {
        val m = check("facebok.com")
        assertNotNull(m)
        assertEquals(MatchReason.EDIT_DISTANCE_TYPO, m!!.reason)
    }

    // ── input guards ────────────────────────────────────────────────────────

    @Test fun blankAndInvalidInputReturnNull() {
        assertNull(check(""))
        assertNull(check("   "))
        assertNull(check("nodot"))
    }

    @Test fun emptyTrustedSetMatchesNothing() {
        assertNull(LookalikeDomainDetector.findLookalike("paypal.com.evil.net", emptySet()))
    }

    @Test fun fakeSitesVisitedOftenAreNeverTrusted() {
        // Emulator: opening paypa1.com and arnazon.com twice put them in the frequent list,
        // which made them trusted and silenced the check for them.
        val trusted = TrustedBrandDomains.buildTrustedSet(
            listOf("paypa1.com", "arnazon.com", "g00gle.com", "example.org", "googleads.g.doubleclick.net"))
        listOf("paypa1.com", "arnazon.com", "g00gle.com").forEach {
            assertEquals(it, false, it in trusted)
            assertNotNull(it, LookalikeDomainDetector.findLookalike(it, trusted))
        }
        assertEquals(true, "example.org" in trusted)
        assertNull(LookalikeDomainDetector.findLookalike("update.googleapis.com", trusted))
    }

    @Test fun frequentFullHostNamesAreComparedByTheirMainSite() {
        // Emulator: "googleads.g.doubleclick.net" came from the logs as a trusted name, and
        // Google's own update.googleapis.com was flagged as a typo of "googleads".
        val fromLogs = setOf("googleads.g.doubleclick.net", "play.google.com", "paypal.com")
        listOf("update.googleapis.com", "www.googleapis.com", "fonts.googleapis.com", "stats.g.doubleclick.net")
            .forEach { assertNull(it, LookalikeDomainDetector.findLookalike(it, fromLogs)) }
        // Real look-alikes are still caught when the trusted name is a full host name.
        assertNotNull(LookalikeDomainDetector.findLookalike("g00gle.com", fromLogs))
        assertNotNull(LookalikeDomainDetector.findLookalike("doub1eclick.net", fromLogs))
    }
}
