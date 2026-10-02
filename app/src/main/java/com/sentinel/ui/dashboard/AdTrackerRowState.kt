package com.sentinel.ui.dashboard

/**
 * What the Home "Ad & Tracker Blocking" row shows.
 *
 * It used to say ACTIVE with "Loading…" whenever no blocklist domains were stored. Turning
 * every list off in Manage Blocklists deletes their domains, so that state never ended, and
 * it also said ACTIVE while the firewall itself was off. The row now follows what is actually
 * enforced: the stored domains are used whenever any exist, and only while the firewall runs.
 */
enum class AdTrackerRowState { ACTIVE, FIREWALL_OFF, LISTS_OFF, NOT_DOWNLOADED }

fun adTrackerRowState(storedDomains: Int, enabledLists: Int, firewallRunning: Boolean): AdTrackerRowState = when {
    storedDomains > 0 -> if (firewallRunning) AdTrackerRowState.ACTIVE else AdTrackerRowState.FIREWALL_OFF
    enabledLists == 0 -> AdTrackerRowState.LISTS_OFF
    else              -> AdTrackerRowState.NOT_DOWNLOADED
}
