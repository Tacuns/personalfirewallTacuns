package com.sentinel.ui.viewmodel

import com.sentinel.core.rules.AppPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Apps screen's "N blocked" number. A user with 10+ blocked apps saw "1 blocked" because
 * the old number counted only the rows on screen (current tab and search text).
 */
class BlockedAppCountTest {

    private val own = 10175
    private val ownPkg = "com.tacu.nsfwzerotrust"

    // Chrome 10136 (Installed tab), Downloads pair 10070 (System tab, two apps), Maps 10150.
    private val visible = listOf(10136, 10070, 10070, 10150, own)

    @Test fun `apps blocked in both tabs are counted, whatever is on screen`() {
        val policies = listOf(
            AppPolicy(10136, "com.android.chrome", wifiBlocked = true, cellBlocked = true),
            AppPolicy(10150, "com.google.android.apps.maps", wifiBlocked = false, cellBlocked = true)
        )
        assertEquals(2, countBlockedApps(visible, policies, own, ownPkg))
    }

    @Test fun `both apps of a shared UID count, because both are blocked`() {
        val policies = listOf(AppPolicy(10070, "com.android.providers.downloads.ui", true, false))
        assertEquals(2, countBlockedApps(visible, policies, own, ownPkg))
    }

    @Test fun `an allowed rule is not counted`() {
        val policies = listOf(AppPolicy(10136, "com.android.chrome", false, false))
        assertEquals(0, countBlockedApps(visible, policies, own, ownPkg))
    }

    @Test fun `a rule for an app that is no longer installed is not counted`() {
        val policies = listOf(AppPolicy(10999, "gone.app", true, true))
        assertEquals(0, countBlockedApps(visible, policies, own, ownPkg))
    }

    @Test fun `the firewall's own app never counts`() {
        val policies = listOf(AppPolicy(own, ownPkg, true, true))
        assertEquals(0, countBlockedApps(visible, policies, own, ownPkg))
    }
}
