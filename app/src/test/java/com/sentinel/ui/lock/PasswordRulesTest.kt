package com.sentinel.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {

    private val samples = listOf(
        "", "abc", "Aa1!aaa", "Aa1!aaaa", "aa1!aaaa", "AA1!AAAA", "Aa!aaaaa", "Aa1aaaaa",
        "Aa1 aaaaa", "Str0ng!Pass", "Ünïcødé1!x", "A".repeat(63) + "a1!", "Aa1!" + "a".repeat(60),
        "Aa1!" + "a".repeat(61)
    )

    @Test fun checklistAlwaysAgreesWithTheRealPasswordRule() {
        samples.forEach { p ->
            assertEquals("for \"$p\"", AppLockCrypto.isValid(p), passwordRules(p).all)
        }
    }

    @Test fun eachRuleIsReportedSeparately() {
        val r = passwordRules("abcdefgh")
        assertTrue(r.length)
        assertTrue(r.lower)
        assertFalse(r.upper)
        assertFalse(r.digit)
        assertFalse(r.symbol)
    }

    @Test fun spaceDoesNotCountAsASymbol() {
        assertFalse(passwordRules("Aa1 aaaa").symbol)
    }
}
