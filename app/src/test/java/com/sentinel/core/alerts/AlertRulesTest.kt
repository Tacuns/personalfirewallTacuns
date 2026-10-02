package com.sentinel.core.alerts

import com.sentinel.core.logs.LogRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val hour = 3_600_000L
    private val now = 1_000_000 * hour

    @Test fun `alerts follow the history setting, capped at 30 days`() {
        assertEquals(0, AlertRules.keepHours(LogRetention.OFF))
        assertEquals(6, AlertRules.keepHours(LogRetention.HOURS_6))
        assertEquals(24 * 7, AlertRules.keepHours(LogRetention.DAYS_7))
        assertEquals(24 * 30, AlertRules.keepHours(LogRetention.DAYS_30))
        assertEquals(24 * 30, AlertRules.keepHours(24 * 365))
    }

    @Test fun `history off means nothing is kept`() =
        assertEquals(now, AlertRules.cutoffMs(LogRetention.OFF, now))

    @Test fun `the same look-alike site is recorded once a day`() {
        assertTrue(AlertRules.isDuplicate(now - 23 * hour, AlertType.LOOKALIKE_BLOCKED, now))
        assertFalse(AlertRules.isDuplicate(now - 25 * hour, AlertType.LOOKALIKE_BLOCKED, now))
        assertFalse(AlertRules.isDuplicate(null, AlertType.LOOKALIKE_BLOCKED, now))
    }

    @Test fun `protection stopping is recorded again after ten minutes`() {
        assertTrue(AlertRules.isDuplicate(now - 5 * 60_000L, AlertType.PROTECTION_STOPPED, now))
        assertFalse(AlertRules.isDuplicate(now - 11 * 60_000L, AlertType.PROTECTION_STOPPED, now))
    }

    @Test fun `a network failure is alerted only when the retry also fails`() {
        assertFalse(AlertRules.alertForBlocklistFailure(com.sentinel.core.vpn.BlocklistSyncStatus.NETWORK, 0))
        assertTrue(AlertRules.alertForBlocklistFailure(com.sentinel.core.vpn.BlocklistSyncStatus.NETWORK, 1))
    }

    @Test fun `a broken link or bad list is alerted at once`() {
        listOf(com.sentinel.core.vpn.BlocklistSyncStatus.HTTP, com.sentinel.core.vpn.BlocklistSyncStatus.EMPTY,
               com.sentinel.core.vpn.BlocklistSyncStatus.TOO_LARGE, com.sentinel.core.vpn.BlocklistSyncStatus.LINK)
            .forEach { assertTrue(it, AlertRules.alertForBlocklistFailure(it, 0)) }
    }

    @Test fun `lost rules are never treated as a duplicate`() =
        assertFalse(AlertRules.isDuplicate(now - 1L, AlertType.RULES_LOST, now))
}
