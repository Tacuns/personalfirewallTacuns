package com.sentinel.ui.viewmodel

import com.sentinel.core.rules.DomainCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreRulesTest {

    // ── Rule names ───────────────────────────────────────────────────────────

    @Test fun normalNamesAccepted() {
        listOf("example.com", "ad.doubleclick.net", "xn--mnchen-3ya.de", "my_host.example.org",
               "a-b.co.uk", "cn", "ru", "xyz").forEach { assertTrue(it, DomainCheck.isValidRuleName(it)) }
    }

    @Test fun junkFromHostileBackupRejected() {
        listOf("x", "<script>.com", "ümlaut.example", "bad domain.com", "", " ", "a..com",
               ".com", "com.", "abcd").forEach { assertFalse(it, DomainCheck.isValidRuleName(it)) }
    }

    @Test fun lengthLimits() {
        assertTrue(DomainCheck.isValidRuleName("a".repeat(63) + ".com"))
        assertFalse(DomainCheck.isValidRuleName("a".repeat(64) + ".com"))       // label > 63
        assertFalse(DomainCheck.isValidRuleName("a".repeat(300) + ".com"))      // the hostile 300-char name
        val at253 = List(4) { "a".repeat(61) }.joinToString(".") + ".abcdefg"    // 4*61 + 3 dots + 8 = 255
        assertFalse(DomainCheck.isValidRuleName(at253))
        val ok253 = List(4) { "a".repeat(61) }.joinToString(".") + ".abcde"      // 253
        assertTrue(DomainCheck.isValidRuleName(ok253))
    }

    // ── Schedule ─────────────────────────────────────────────────────────────

    @Test fun realScheduleAccepted() {
        assertTrue(RestoreRules.isValidSchedule("SOCIAL", 22, 0, 6, 0, "1111100"))
        assertTrue(RestoreRules.isValidSchedule("NEWS", 0, 0, 23, 59, "0000000"))
    }

    @Test fun hostileScheduleRejected() {
        assertFalse(RestoreRules.isValidSchedule("EVIL", 22, 0, 6, 0, "1111100"))    // unknown category
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 99, 0, 6, 0, "1111100"))  // hour 99
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, -5, 6, 0, "1111100")) // minute -5
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, 0, -1, 0, "1111100"))
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, 0, 6, 999, "1111100"))
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 24, 0, 6, 0, "1111100"))  // boundary 24
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, 60, 6, 0, "1111100")) // boundary 60
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, 0, 6, 0, "XYZ"))
        assertFalse(RestoreRules.isValidSchedule("SOCIAL", 22, 0, 6, 0, "11111000"))  // 8 days
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

    @Test fun logRules() {
        val now = 1_789_700_000_000L
        assertTrue(RestoreRules.isValidLog("BLOCKED", now - 1000, now))
        assertTrue(RestoreRules.isValidLog("ALLOWED", now + 5 * 60_000, now))     // small clock difference
        assertFalse(RestoreRules.isValidLog("BLOCKED", 4_102_444_800_000L, now))  // year 2100
        assertFalse(RestoreRules.isValidLog("BLOCKED", 0, now))
        assertFalse(RestoreRules.isValidLog("BLOCKED", -1, now))
        assertFalse(RestoreRules.isValidLog("HACKED", now - 1000, now))
        assertFalse(RestoreRules.isValidLog("", now - 1000, now))
    }

    // ── Apps and lists ───────────────────────────────────────────────────────

    @Test fun firewallNeverBlocksItself() {
        val own = "com.tacu.nsfwzerotrust"
        assertFalse(RestoreRules.isRestorableApp(own, own))
        assertFalse(RestoreRules.isRestorableApp("", own))
        assertTrue(RestoreRules.isRestorableApp("com.android.chrome", own))
    }

    @Test fun onlySwitchableListsRestored() {
        assertTrue(RestoreRules.isSwitchableList(isDefault = true))
        assertFalse(RestoreRules.isSwitchableList(isDefault = false))
    }
}
