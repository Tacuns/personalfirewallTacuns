package com.sentinel.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home "Ad & Tracker Blocking" row used to say ACTIVE · Loading… forever at 0 domains. */
class AdTrackerRowStateTest {

    @Test fun `all lists turned off shows OFF, not Loading`() =
        assertEquals(AdTrackerRowState.LISTS_OFF, adTrackerRowState(0, 0, firewallRunning = true))

    @Test fun `a list is on but nothing is downloaded yet`() =
        assertEquals(AdTrackerRowState.NOT_DOWNLOADED, adTrackerRowState(0, 1, firewallRunning = true))

    @Test fun `domains stored and firewall running is active`() =
        assertEquals(AdTrackerRowState.ACTIVE, adTrackerRowState(75_900, 1, firewallRunning = true))

    @Test fun `domains stored but firewall off is not active`() =
        assertEquals(AdTrackerRowState.FIREWALL_OFF, adTrackerRowState(75_900, 1, firewallRunning = false))

    @Test fun `stored domains are still enforced even if no list is marked on`() =
        assertEquals(AdTrackerRowState.ACTIVE, adTrackerRowState(10, 0, firewallRunning = true))
}
