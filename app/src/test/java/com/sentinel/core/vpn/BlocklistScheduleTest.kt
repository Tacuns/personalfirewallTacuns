package com.sentinel.core.vpn

import com.sentinel.core.rules.BlocklistSource
import com.sentinel.ui.settings.NextUpdate
import com.sentinel.ui.settings.nextUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistScheduleTest {

    private val hour = 3_600_000L
    private val now  = 1_000_000 * hour

    private fun list(updatedAgoHours: Long, domains: Int = 75_000) = BlocklistSource(
        sourceKey = "BLOCKLIST_STEVENBLACK", displayName = "StevenBlack", url = "https://x",
        format = "hosts", description = "", isDefault = true, enabled = true,
        domainCount = domains, lastUpdatedMs = if (updatedAgoHours < 0) 0L else now - updatedAgoHours * hour
    )

    @Test fun `default is weekly, which is what every list used before`() =
        assertEquals(24 * 7, BlocklistSchedule.DEFAULT_HOURS)

    @Test fun `unknown saved values fall back to weekly`() {
        assertEquals(BlocklistSchedule.DEFAULT_HOURS, BlocklistSchedule.sanitize(0))
        assertEquals(BlocklistSchedule.DEFAULT_HOURS, BlocklistSchedule.sanitize(1))
        assertEquals(BlocklistSchedule.DEFAULT_HOURS, BlocklistSchedule.sanitize(-5))
        assertEquals(6, BlocklistSchedule.sanitize(6))
    }

    @Test fun `a list is due only after its own interval has passed`() {
        assertFalse(BlocklistSchedule.isDue(now - 5 * hour, 10, 6, now))
        assertTrue(BlocklistSchedule.isDue(now - 6 * hour, 10, 6, now))
        assertFalse(BlocklistSchedule.isDue(now - 100 * hour, 10, 24 * 7, now))
        assertTrue(BlocklistSchedule.isDue(now - 168 * hour, 10, 24 * 7, now))
    }

    @Test fun `a list never downloaded, or empty, is always due`() {
        assertTrue(BlocklistSchedule.isDue(0L, 0, 24 * 7, now))
        assertTrue(BlocklistSchedule.isDue(now - 1 * hour, 0, 24 * 7, now))
    }

    @Test fun `the worker wakes often enough for the shortest choice`() =
        assertTrue(BlocklistSchedule.CHECK_EVERY_HOURS <= BlocklistSchedule.CHOICES.min())

    @Test fun `next update is shown in hours, then days, rounded up`() {
        assertEquals(NextUpdate.InHours(1), nextUpdate(list(5), 6, now - 30 * 60_000L + 30 * 60_000L))
        assertEquals(NextUpdate.InHours(24), nextUpdate(list(0), 24, now))
        assertEquals(NextUpdate.InDays(7), nextUpdate(list(0), 24 * 7, now))
        assertEquals(NextUpdate.InDays(3), nextUpdate(list(100), 24 * 7, now))   // 68 h left
    }

    @Test fun `a due list says it updates when online`() {
        assertEquals(NextUpdate.WhenOnline, nextUpdate(list(200), 24 * 7, now))
        assertEquals(NextUpdate.WhenOnline, nextUpdate(list(-1, domains = 0), 24, now))
    }
}
