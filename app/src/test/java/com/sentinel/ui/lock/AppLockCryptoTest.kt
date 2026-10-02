package com.sentinel.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockCryptoTest {

    @Test fun correctPasswordVerifies() {
        val record = AppLockCrypto.create("Str0ng!Pass")
        assertTrue(AppLockCrypto.verify("Str0ng!Pass", record))
    }

    @Test fun wrongPasswordRejected() {
        val record = AppLockCrypto.create("Str0ng!Pass")
        assertFalse(AppLockCrypto.verify("Str0ng!Pasx", record))
        assertFalse(AppLockCrypto.verify("", record))
        assertFalse(AppLockCrypto.verify("str0ng!pass", record))   // case matters
    }

    @Test fun saltIsUniquePerRecord() {
        // Same password must never produce the same stored record twice.
        assertNotEquals(AppLockCrypto.create("Str0ng!Pass"), AppLockCrypto.create("Str0ng!Pass"))
    }

    @Test fun bothRecordsStillVerify() {
        val a = AppLockCrypto.create("Str0ng!Pass")
        val b = AppLockCrypto.create("Str0ng!Pass")
        assertTrue(AppLockCrypto.verify("Str0ng!Pass", a))
        assertTrue(AppLockCrypto.verify("Str0ng!Pass", b))
    }

    @Test fun malformedRecordDoesNotCrash() {
        assertFalse(AppLockCrypto.verify("x", ""))
        assertFalse(AppLockCrypto.verify("x", "garbage"))
        assertFalse(AppLockCrypto.verify("x", "not:base64!!"))
        assertFalse(AppLockCrypto.verify("x", "a:b:c"))
    }

    @Test fun rulesAcceptValidPasswords() {
        assertTrue(AppLockCrypto.isValid("Str0ng!Pass"))
        assertTrue(AppLockCrypto.isValid("Aa1!aaaa"))          // exactly 8
        assertTrue(AppLockCrypto.isValid("Aa1@" + "x".repeat(60)))  // exactly 64
    }

    // ── username + password combined ────────────────────────────────────────

    @Test fun usernameIsActuallyRequired() {
        val record = AppLockCrypto.create(AppLockCrypto.combine("alice", "Str0ng!Pass"))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.combine("alice", "Str0ng!Pass"), record))
        // Right password, wrong username must fail.
        assertFalse(AppLockCrypto.verify(AppLockCrypto.combine("someone", "Str0ng!Pass"), record))
        // Right username, wrong password must fail.
        assertFalse(AppLockCrypto.verify(AppLockCrypto.combine("alice", "Str0ng!Pasx"), record))
    }

    @Test fun usernameIsCaseAndSpaceInsensitive() {
        val record = AppLockCrypto.create(AppLockCrypto.combine("Alice", "Str0ng!Pass"))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.combine("  alice ", "Str0ng!Pass"), record))
    }

    @Test fun separatorPreventsFieldCollision() {
        // "ab"+"c" must not hash the same as "a"+"bc".
        assertNotEquals(AppLockCrypto.combine("ab", "c"), AppLockCrypto.combine("a", "bc"))
    }

    // ── recovery ────────────────────────────────────────────────────────────

    @Test fun recoveryCodeHasExpectedShape() {
        val code = AppLockCrypto.generateRecoveryCode()
        assertTrue(code.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")))
        // Ambiguous characters must never appear.
        assertFalse(code.any { it in "OIL01" })
    }

    @Test fun recoveryCodesAreUnique() {
        val codes = (1..50).map { AppLockCrypto.generateRecoveryCode() }.toSet()
        assertEquals(50, codes.size)
    }

    @Test fun recoveryCodeVerifiesIgnoringCaseAndDashes() {
        val code   = AppLockCrypto.generateRecoveryCode()
        val record = AppLockCrypto.create(AppLockCrypto.normalizeCode(code))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.normalizeCode(code.lowercase()), record))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.normalizeCode(code.replace("-", "")), record))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.normalizeCode(" $code "), record))
        assertFalse(AppLockCrypto.verify(AppLockCrypto.normalizeCode("AAAA-AAAA-AAAA-AAAA"), record))
    }

    @Test fun securityAnswerIsForgiving() {
        val record = AppLockCrypto.create(AppLockCrypto.normalizeAnswer("My First Dog"))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.normalizeAnswer("my first dog"), record))
        assertTrue(AppLockCrypto.verify(AppLockCrypto.normalizeAnswer("  My  First   Dog  "), record))
        assertFalse(AppLockCrypto.verify(AppLockCrypto.normalizeAnswer("my second dog"), record))
    }

    @Test fun rulesRejectInvalidPasswords() {
        assertFalse(AppLockCrypto.isValid("Aa1!aaa"))          // 7 — too short
        assertFalse(AppLockCrypto.isValid("Aa1@" + "x".repeat(61)))  // 65 — too long
        assertFalse(AppLockCrypto.isValid("abcd1234!"))        // no uppercase
        assertFalse(AppLockCrypto.isValid("ABCD1234!"))        // no lowercase
        assertFalse(AppLockCrypto.isValid("Abcdefg!"))         // no digit
        assertFalse(AppLockCrypto.isValid("Abcd1234"))         // no symbol
        assertFalse(AppLockCrypto.isValid(""))
    }
}
