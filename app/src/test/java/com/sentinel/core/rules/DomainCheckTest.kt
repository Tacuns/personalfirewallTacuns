package com.sentinel.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainCheckTest {

    private fun rule(domain: String, source: String = "USER", blocked: Boolean = true) =
        FirewallRule(domain, -1, "", blocked, source)

    // ── cleaning input ──────────────────────────────────────────────────────

    @Test fun normalizeCleansPastedAddresses() {
        assertEquals("www.youtube.com", DomainCheck.normalize("https://www.YouTube.com/watch?v=1"))
        assertEquals("ads.example.com", DomainCheck.normalize("http://ads.example.com:8080/x"))
        assertEquals("example.com", DomainCheck.normalize("  EXAMPLE.com  "))
        assertEquals("tumblr.com", DomainCheck.normalize("*.tumblr.com"))
        assertEquals("example.com", DomainCheck.normalize("example.com."))
    }

    @Test fun normalizeRejectsThingsThatAreNotDomains() {
        assertNull(DomainCheck.normalize(""))
        assertNull(DomainCheck.normalize("localhost"))
        assertNull(DomainCheck.normalize("bad site.com"))
        assertNull(DomainCheck.normalize("ex*ample.com"))
    }

    @Test fun candidatesListTheDomainThenEveryParent() {
        assertEquals(
            listOf("a.b.tumblr.com", "b.tumblr.com", "tumblr.com", "com"),
            DomainCheck.candidates("a.b.tumblr.com")
        )
    }

    // ── matching, same as the firewall ──────────────────────────────────────

    @Test fun noRulesMeansNotBlocked() {
        assertNull(DomainCheck.findMatch("example.com", emptyList()))
    }

    @Test fun exactUserRuleMatches() {
        assertEquals("example.com", DomainCheck.findMatch("example.com", listOf(rule("example.com")))?.domain)
    }

    @Test fun userRuleCoversSubdomains() {
        assertEquals("tumblr.com", DomainCheck.findMatch("www.tumblr.com", listOf(rule("tumblr.com")))?.domain)
    }

    @Test fun countryRuleCoversTheWholeCountryEnding() {
        assertEquals("ru", DomainCheck.findMatch("news.example.ru", listOf(rule("ru")))?.domain)
    }

    @Test fun scheduleRuleCoversSubdomains() {
        val match = DomainCheck.findMatch("m.facebook.com", listOf(rule("facebook.com", "SCHEDULE")))
        assertEquals("SCHEDULE", match?.source)
    }

    @Test fun blocklistMatchesTheExactDomainOnly() {
        val list = listOf(rule("ads.com", "BLOCKLIST_TEST"))
        assertEquals("ads.com", DomainCheck.findMatch("ads.com", list)?.domain)
        assertNull(DomainCheck.findMatch("cdn.ads.com", list))
    }

    @Test fun wwwFormListedOnABlocklistIsFound() {
        // Hosts-style blocklists often list the www. name exactly.
        val host = DomainCheck.normalize("https://www.doubleclick.net/ad")!!
        val list = listOf(rule("www.doubleclick.net", "BLOCKLIST_TEST"))
        assertEquals("www.doubleclick.net", DomainCheck.findMatch(host, list)?.domain)
    }

    @Test fun wwwAddressIsStillCoveredByYourRule() {
        val host = DomainCheck.normalize("www.tumblr.com")!!
        assertEquals("tumblr.com", DomainCheck.findMatch(host, listOf(rule("tumblr.com")))?.domain)
    }

    @Test fun exactMatchWinsOverParent() {
        val rules = listOf(rule("tumblr.com"), rule("www.tumblr.com", "BLOCKLIST_TEST"))
        assertEquals("www.tumblr.com", DomainCheck.findMatch("www.tumblr.com", rules)?.domain)
    }

    @Test fun closestParentWins() {
        val rules = listOf(rule("tumblr.com"), rule("b.tumblr.com"))
        assertEquals("b.tumblr.com", DomainCheck.findMatch("a.b.tumblr.com", rules)?.domain)
    }

    @Test fun rowsThatAreNotBlockingAreIgnored() {
        assertNull(DomainCheck.findMatch("example.com", listOf(rule("example.com", blocked = false))))
    }

    // ── Always allow list ───────────────────────────────────────────────────

    private fun allow(domain: String) = rule(domain, DomainCheck.SOURCE_ALLOW, blocked = false)

    @Test fun decideAgreesWithFindMatchWhenNothingIsAllowed() {
        val rules = listOf(rule("tumblr.com"), rule("www.tumblr.com", "BLOCKLIST_TEST"), rule("ads.com", "BLOCKLIST_TEST"),
            rule("ru"), rule("facebook.com", "SCHEDULE"))
        listOf("www.tumblr.com", "a.tumblr.com", "ads.com", "cdn.ads.com", "news.x.ru", "m.facebook.com", "example.org")
            .forEach { assertEquals(it, DomainCheck.findMatch(it, rules), DomainCheck.decide(it, rules)) }
    }

    @Test fun allowBeatsABlockOnTheSameName() {
        // The table keys rows by domain, so the allow row replaces the blocklist row.
        val rules = listOf(allow("ads.com"), rule("cdn.ads.com", "BLOCKLIST_TEST"))
        assertEquals(DomainCheck.SOURCE_ALLOW, DomainCheck.decide("ads.com", rules)?.source)
        assertEquals(DomainCheck.SOURCE_ALLOW, DomainCheck.decide("cdn.ads.com", rules)?.source)
        assertEquals(false, DomainCheck.isBlocked("ads.com", emptySet(), setOf("ads.com"), setOf("ads.com")))
    }

    @Test fun allowedParentCoversSubdomainsButCloserBlockWins() {
        val rules = listOf(allow("example.com"), rule("ads.example.com"))
        assertEquals(DomainCheck.SOURCE_ALLOW, DomainCheck.decide("www.example.com", rules)?.source)
        assertEquals("ads.example.com", DomainCheck.decide("x.ads.example.com", rules)?.domain)
        val user = setOf("ads.example.com"); val allowSet = setOf("example.com")
        assertEquals(false, DomainCheck.isBlocked("www.example.com", user, allowSet, emptySet()))
        assertEquals(true, DomainCheck.isBlocked("x.ads.example.com", user, allowSet, emptySet()))
        assertEquals(true, DomainCheck.isAllowed("www.example.com", user, allowSet))
        assertEquals(false, DomainCheck.isAllowed("x.ads.example.com", user, allowSet))
    }

    @Test fun allowedSiteCoversSubdomainsTheBlocklistNamesExactly() {
        // Phone test: doubleclick.net was allowed but Chrome still hit www./ad. subdomains
        // that StevenBlack lists one by one.
        val list = setOf("www.doubleclick.net", "ad.doubleclick.net", "stats.g.doubleclick.net", "ads.com")
        val allowSet = setOf("doubleclick.net")
        listOf("doubleclick.net", "www.doubleclick.net", "ad.doubleclick.net", "stats.g.doubleclick.net")
            .forEach { assertEquals(it, false, DomainCheck.isBlocked(it, emptySet(), allowSet, list)) }
        assertEquals(true, DomainCheck.isBlocked("ads.com", emptySet(), allowSet, list))
        val rules = listOf(allow("doubleclick.net"), rule("ad.doubleclick.net", "BLOCKLIST_TEST"))
        assertEquals(DomainCheck.SOURCE_ALLOW, DomainCheck.decide("ad.doubleclick.net", rules)?.source)
    }

    @Test fun ownRuleOnASubdomainStillBeatsAnAllowedParent() {
        val user = setOf("ad.doubleclick.net"); val allowSet = setOf("doubleclick.net")
        assertEquals(true, DomainCheck.isBlocked("x.ad.doubleclick.net", user, allowSet, emptySet()))
        assertEquals(false, DomainCheck.isBlocked("www.doubleclick.net", user, allowSet, setOf("www.doubleclick.net")))
    }

    @Test fun closerAllowBeatsBlockedParent() {
        val user = setOf("example.com"); val allowSet = setOf("www.example.com")
        assertEquals(false, DomainCheck.isBlocked("www.example.com", user, allowSet, emptySet()))
        assertEquals(true, DomainCheck.isBlocked("mail.example.com", user, allowSet, emptySet()))
    }

    @Test fun isBlockedMatchesTheOldFirewallRulesWithoutAllowEntries() {
        val user = setOf("tumblr.com", "ru"); val list = setOf("ads.com", "www.doubleclick.net")
        assertEquals(true, DomainCheck.isBlocked("a.b.tumblr.com", user, emptySet(), list))
        assertEquals(true, DomainCheck.isBlocked("news.example.ru", user, emptySet(), list))
        assertEquals(true, DomainCheck.isBlocked("ads.com", user, emptySet(), list))
        assertEquals(false, DomainCheck.isBlocked("cdn.ads.com", user, emptySet(), list))
        assertEquals(false, DomainCheck.isBlocked("example.org", user, emptySet(), list))
        assertEquals(false, DomainCheck.isBlocked("", user, emptySet(), list))
    }
}
