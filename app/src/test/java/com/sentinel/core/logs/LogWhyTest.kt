package com.sentinel.core.logs

import com.sentinel.core.rules.DomainCheck
import com.sentinel.core.rules.FirewallRule
import com.sentinel.core.schedule.ScheduleCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogWhyTest {

    private fun rule(domain: String, source: String) =
        FirewallRule(domain = domain, uid = -1, packageName = "", isBlocked = source != DomainCheck.SOURCE_ALLOW, source = source)

    private val names = mapOf("BLOCKLIST_STEVENBLACK" to "StevenBlack", "BLOCKLIST_CUSTOM_1" to "My list")

    private fun now(site: String, vararg rules: FirewallRule) = LogWhy.currentRule(site, rules.toList(), names)

    // ── Which rule decides the site today ──────────────────────────────────────

    @Test fun blocklistIsNamedByItsShownName() {
        assertEquals(CurrentRule.OnList("StevenBlack"), now("ad.example.com", rule("ad.example.com", "BLOCKLIST_STEVENBLACK")))
        assertEquals(CurrentRule.OnList("My list"), now("x.example.com", rule("x.example.com", "BLOCKLIST_CUSTOM_1")))
    }

    @Test fun blocklistMatchesOnlyTheExactName() {
        // Same rule as the firewall: a list entry does not cover sub-names.
        assertEquals(CurrentRule.None, now("a.ad.example.com", rule("ad.example.com", "BLOCKLIST_STEVENBLACK")))
    }

    @Test fun unknownListIsNotNamed() {
        assertEquals(CurrentRule.None, now("ad.example.com", rule("ad.example.com", "BLOCKLIST_GONE")))
    }

    @Test fun ownRuleOnTheSiteOrAParent() {
        assertEquals(CurrentRule.OwnRule("tumblr.com"), now("tumblr.com", rule("tumblr.com", "USER")))
        assertEquals(CurrentRule.OwnRule("tumblr.com"), now("www.tumblr.com", rule("tumblr.com", "USER")))
    }

    @Test fun countryRuleIsTheBareEnding() {
        assertEquals(CurrentRule.Country("cn"), now("shop.example.cn", rule("cn", "USER")))
    }

    @Test fun scheduleRuleKnowsItsGroup() {
        assertEquals(CurrentRule.Schedule(ScheduleCategories.SOCIAL), now("old.reddit.com", rule("reddit.com", "SCHEDULE")))
        assertEquals(CurrentRule.Schedule(null), now("x.unknown.org", rule("unknown.org", "SCHEDULE")))
    }

    @Test fun alwaysAllowWinsOverAList() {
        assertEquals(CurrentRule.Allowed("doubleclick.net"),
            now("ad.doubleclick.net", rule("ad.doubleclick.net", "BLOCKLIST_STEVENBLACK"), rule("doubleclick.net", DomainCheck.SOURCE_ALLOW)))
    }

    @Test fun nothingDecides() {
        assertEquals(CurrentRule.None, now("example.org"))
    }

    // ── What the sheet says ────────────────────────────────────────────────────

    @Test fun sameKindTodayIsNamed() {
        assertEquals(WhyExplanation(true, WhyNote.None), LogWhy.explain(LogReason.AD_LIST, CurrentRule.OnList("StevenBlack")))
        assertEquals(WhyExplanation(true, WhyNote.None), LogWhy.explain(LogReason.USER_RULE, CurrentRule.OwnRule("tumblr.com")))
        assertEquals(WhyExplanation(true, WhyNote.None), LogWhy.explain(LogReason.USER_RULE, CurrentRule.Country("cn")))
        assertEquals(WhyExplanation(true, WhyNote.None), LogWhy.explain(LogReason.SCHEDULE, CurrentRule.Schedule(ScheduleCategories.NEWS)))
    }

    @Test fun ruleRemovedSinceIsSaidHonestly() {
        assertEquals(WhyExplanation(false, WhyNote.RuleGone), LogWhy.explain(LogReason.AD_LIST, CurrentRule.None))
        assertEquals(WhyExplanation(false, WhyNote.RuleGone), LogWhy.explain(LogReason.SCHEDULE, CurrentRule.None))
    }

    @Test fun differentKindTodayIsNotPassedOffAsTheReason() {
        // Blocked by a list then; the user has blocked it by hand since — don't claim the rule blocked it.
        assertEquals(WhyExplanation(false, WhyNote.Changed), LogWhy.explain(LogReason.AD_LIST, CurrentRule.OwnRule("x.com")))
        assertEquals(WhyExplanation(false, WhyNote.Changed), LogWhy.explain(LogReason.USER_RULE, CurrentRule.OnList("StevenBlack")))
    }

    @Test fun allowedSinceNamesTheAllowRule() {
        assertEquals(WhyExplanation(false, WhyNote.AllowedSince("doubleclick.net")),
            LogWhy.explain(LogReason.AD_LIST, CurrentRule.Allowed("doubleclick.net")))
    }

    @Test fun scheduleWithUnknownGroupKeepsTheGeneralLine() {
        assertEquals(WhyExplanation(false, WhyNote.None), LogWhy.explain(LogReason.SCHEDULE, CurrentRule.Schedule(null)))
    }

    @Test fun watchOnlyNamesWhatWouldHaveBlocked() {
        assertEquals(true, LogWhy.explain(LogReason.WATCH_ONLY, CurrentRule.OnList("StevenBlack")).specific)
        assertEquals(false, LogWhy.explain(LogReason.WATCH_ONLY, CurrentRule.None).specific)
    }

    @Test fun allowedEntryNamesTheAllowListOnlyWhenItApplies() {
        assertEquals(true, LogWhy.explain(LogReason.ALLOWED, CurrentRule.Allowed("example.org")).specific)
        assertEquals(false, LogWhy.explain(LogReason.ALLOWED, CurrentRule.None).specific)
    }

    @Test fun lookalikeAndAppBlockKeepTheirOwnWording() {
        assertEquals(WhyExplanation(false, WhyNote.None), LogWhy.explain(LogReason.LOOKALIKE, CurrentRule.None))
        assertEquals(WhyExplanation(false, WhyNote.None), LogWhy.explain(LogReason.APP_BLOCKED, CurrentRule.OnList("StevenBlack")))
    }

    // ── Whole-app block today ──────────────────────────────────────────────────

    @Test fun appBlockCombinesAllRowsOfThePackage() {
        val policies = listOf("com.android.chrome" to (true to false), "com.android.chrome" to (false to true), "other" to (true to true))
        assertEquals(AppBlockNow(wifi = true, mobile = true), LogWhy.appBlockNow("com.android.chrome", policies))
        assertEquals(AppBlockNow(wifi = false, mobile = false), LogWhy.appBlockNow("com.unblocked", policies))
        assertNull(LogWhy.appBlockNow("", policies))
    }
}
