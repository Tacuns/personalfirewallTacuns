package com.sentinel.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleActionsTest {

    @Test fun blockTargetMatchesTheProtectTab() {
        // The button must show exactly what gets blocked: "www." is dropped, so the whole site is.
        assertEquals("tumblr.com", RuleActions.blockTarget("www.tumblr.com"))
        assertEquals("securepubads.g.doubleclick.net", RuleActions.blockTarget("securepubads.g.doubleclick.net"))
        assertEquals("example.com", RuleActions.blockTarget("  Example.COM "))
    }

    @Test fun blockTargetRejectsUnusableNames() {
        assertNull(RuleActions.blockTarget("localhost"))
        assertNull(RuleActions.blockTarget(""))
        assertNull(RuleActions.blockTarget("bad name.com"))
    }

    @Test fun allowTargetDropsWwwLikeTheProtectTab() {
        assertEquals("tumblr.com", RuleActions.allowTarget("www.tumblr.com"))
        assertEquals("ad.doubleclick.net", RuleActions.allowTarget("ad.doubleclick.net"))
    }

    @Test fun allowTargetRejectsUnusableNames() {
        assertNull(RuleActions.allowTarget("unknown"))
        assertNull(RuleActions.allowTarget(""))
    }
}
