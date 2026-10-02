package com.sentinel.ui.viewmodel

import com.sentinel.ui.rules.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the production crash "Key <package> was already used ... LazyColumn",
 * reproduced on the emulator by tapping the SECOND of two apps that share a UID
 * (Download Manager + Downloads, UID 10070; on the user's phone Samsung Cloud +
 * Samsung Cloud Assistant).
 */
class AppPolicyRowsTest {

    private fun rows() = mutableListOf(
        AppRule("Download Manager", "com.android.providers.downloads", 10070),
        AppRule("Downloads", "com.android.providers.downloads.ui", 10070),
        AppRule("Chrome", "com.android.chrome", 10136)
    )

    private fun keysUnique(list: List<AppRule>) = list.map { it.packageName }.toSet().size == list.size

    /** The code that shipped, kept here only to prove this scenario really broke the invariant. */
    private fun oldBehaviour(list: MutableList<AppRule>, tapped: AppRule, wifi: Boolean, data: Boolean) {
        val index = list.indexOfFirst { it.uid == tapped.uid }
        if (index != -1) list[index] = tapped.copy(isWifiBlocked = wifi, isDataBlocked = data)
    }

    @Test fun `the crash scenario broke the unique-key rule with the old code`() {
        val list = rows()
        oldBehaviour(list, list[1], wifi = true, data = false)   // tap the SECOND shared-UID app
        assertFalse("old code must reproduce the duplicate key", keysUnique(list))
    }

    @Test fun `tapping the second app of a shared UID keeps every list key unique`() {
        val list = rows()
        applyPolicyToRows(list, uid = 10070, wifiBlocked = true, dataBlocked = false)
        assertTrue(keysUnique(list))
        assertEquals(listOf("com.android.providers.downloads", "com.android.providers.downloads.ui",
            "com.android.chrome"), list.map { it.packageName })
    }

    @Test fun `every app sharing the UID shows the rule, because the VPN applies it per UID`() {
        val list = rows()
        applyPolicyToRows(list, uid = 10070, wifiBlocked = true, dataBlocked = true)
        assertTrue(list.filter { it.uid == 10070 }.all { it.isWifiBlocked && it.isDataBlocked })
        assertEquals("Download Manager", list[0].name)   // rows keep their own identity
        assertEquals("Downloads", list[1].name)
    }

    @Test fun `apps with other UIDs are untouched`() {
        val list = rows()
        applyPolicyToRows(list, uid = 10070, wifiBlocked = true, dataBlocked = true)
        assertFalse(list[2].isWifiBlocked || list[2].isDataBlocked)
    }

    @Test fun `unblocking clears the rule on every shared row`() {
        val list = rows()
        applyPolicyToRows(list, uid = 10070, wifiBlocked = true, dataBlocked = true)
        applyPolicyToRows(list, uid = 10070, wifiBlocked = false, dataBlocked = false)
        assertTrue(list.none { it.isWifiBlocked || it.isDataBlocked })
        assertTrue(keysUnique(list))
    }

    @Test fun `rapid repeated taps on both shared apps never create a duplicate key`() {
        val list = rows()
        repeat(200) { i ->
            applyPolicyToRows(list, 10070, wifiBlocked = i % 2 == 0, dataBlocked = i % 3 == 0)
            assertTrue("iteration $i", keysUnique(list))
        }
    }

    @Test fun `a UID not in the list changes nothing`() {
        val list = rows()
        val before = list.toList()
        applyPolicyToRows(list, uid = 99999, wifiBlocked = true, dataBlocked = true)
        assertEquals(before, list)
    }
}
