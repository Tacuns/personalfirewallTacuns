package com.sentinel.core.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleHitsTest {

    private fun row(destination: String, count: Int) = StatRow(destination, count)

    @Test fun noRulesOrNoLogsMeansNoHits() {
        assertTrue(RuleHits.count(emptyList(), listOf(row("a.com", 3))).isEmpty())
        assertTrue(RuleHits.count(listOf("a.com"), emptyList()).isEmpty())
    }

    @Test fun exactDomainIsCredited() {
        assertEquals(mapOf("example.com" to 4), RuleHits.count(listOf("example.com"), listOf(row("example.com", 4))))
    }

    @Test fun subdomainsAreCreditedToTheirParentRule() {
        val hits = RuleHits.count(
            listOf("tumblr.com"),
            listOf(row("www.tumblr.com", 2), row("api.tumblr.com", 5), row("tumblr.com", 1))
        )
        assertEquals(mapOf("tumblr.com" to 8), hits)
    }

    @Test fun closestParentRuleGetsTheCredit() {
        val hits = RuleHits.count(listOf("tumblr.com", "api.tumblr.com"), listOf(row("v2.api.tumblr.com", 3)))
        assertEquals(mapOf("api.tumblr.com" to 3), hits)
    }

    @Test fun unrelatedWebsitesAreIgnored() {
        val hits = RuleHits.count(listOf("tumblr.com"), listOf(row("nottumblr.com", 9), row("example.org", 2)))
        assertTrue(hits.isEmpty())
    }

    @Test fun eachRuleGetsItsOwnCount() {
        val hits = RuleHits.count(
            listOf("a.com", "b.com"),
            listOf(row("x.a.com", 1), row("b.com", 2), row("y.b.com", 3))
        )
        assertEquals(mapOf("a.com" to 1, "b.com" to 5), hits)
    }
}
