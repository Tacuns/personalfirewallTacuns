package com.sentinel.core.rules

import com.sentinel.core.logs.LogLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfProtectionTest {

    private val ownUid = 10175
    private val ownPkg = "com.tacu.nsfwzerotrust"

    @Test fun `a block on the firewall itself is removed`() {
        val cleaned = SelfProtection.sanitize(AppPolicy(ownUid, ownPkg, true, true), ownUid, ownPkg)
        assertFalse(cleaned.wifiBlocked)
        assertFalse(cleaned.cellBlocked)
    }

    @Test fun `the firewall is recognised by package too, as on a work profile uid`() {
        val workProfile = AppPolicy(1010175, ownPkg, true, false)
        assertTrue(SelfProtection.isOwn(workProfile, ownUid, ownPkg))
        assertFalse(SelfProtection.sanitize(workProfile, ownUid, ownPkg).wifiBlocked)
    }

    @Test fun `other apps keep exactly the rule the user chose`() {
        val chrome = AppPolicy(10136, "com.android.chrome", true, false)
        assertEquals(chrome, SelfProtection.sanitize(chrome, ownUid, ownPkg))
        assertFalse(SelfProtection.isOwn(chrome, ownUid, ownPkg))
    }

    @Test fun `an app-only block is labelled so the reason is visible`() {
        assertEquals(LogLabels.APP_BLOCKED, LogLabels.displayLabel("BLOCKED", ""))
    }

    @Test fun `existing labels and allowed rows are left alone`() {
        listOf("AD/TRACKER", "USER BLOCK", "SCHEDULE", "LOOK-ALIKE").forEach {
            assertEquals(it, LogLabels.displayLabel("BLOCKED", it))
        }
        assertEquals("WOULD BLOCK", LogLabels.displayLabel("ALLOWED", "WOULD BLOCK"))
        assertEquals("", LogLabels.displayLabel("ALLOWED", ""))
    }
}
