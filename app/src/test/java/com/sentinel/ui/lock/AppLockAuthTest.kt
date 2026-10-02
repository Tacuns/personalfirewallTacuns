package com.sentinel.ui.lock

import com.sentinel.core.settings.AppPreferencesRepository.AppLockConfig
import com.sentinel.core.settings.AppPreferencesRepository.AppLockUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockAuthTest {

    private fun cfg(
        users: List<AppLockUser> = emptyList(),
        legacy: String? = null,
        enabled: Boolean = true
    ) = AppLockConfig(enabled, users, legacy, null, null, null)

    private fun user(name: String, pass: String) = AppLockAuth.newUser(name, pass)

    // ── multi-user sign-in ──────────────────────────────────────────────────

    @Test fun correctUserAmongManyAuthenticates() {
        val users = listOf(
            user("alice", "Aa1!aaaa"),
            user("bob",   "Bb2@bbbb"),
            user("carol", "Cc3#cccc")
        )
        assertEquals("bob", AppLockAuth.authenticate(cfg(users), "bob", "Bb2@bbbb"))
        assertEquals("carol", AppLockAuth.authenticate(cfg(users), "carol", "Cc3#cccc"))
    }

    @Test fun wrongPasswordForExistingUserFails() {
        val users = listOf(user("alice", "Aa1!aaaa"), user("bob", "Bb2@bbbb"))
        assertNull(AppLockAuth.authenticate(cfg(users), "bob", "Aa1!aaaa"))
    }

    @Test fun unknownUsernameFails() {
        val users = listOf(user("alice", "Aa1!aaaa"))
        assertNull(AppLockAuth.authenticate(cfg(users), "mallory", "Aa1!aaaa"))
    }

    @Test fun usernameMatchIsCaseAndSpaceInsensitive() {
        val users = listOf(user("Alice", "Aa1!aaaa"))
        assertEquals("Alice", AppLockAuth.authenticate(cfg(users), "  alice ", "Aa1!aaaa"))
    }

    @Test fun oneUserCannotUseAnothersPassword() {
        // Passwords are bound to their username, so swapping them must fail both ways.
        val users = listOf(user("alice", "Aa1!aaaa"), user("bob", "Bb2@bbbb"))
        assertNull(AppLockAuth.authenticate(cfg(users), "alice", "Bb2@bbbb"))
        assertNull(AppLockAuth.authenticate(cfg(users), "bob", "Aa1!aaaa"))
    }

    @Test fun emptyConfigAuthenticatesNobody() {
        assertNull(AppLockAuth.authenticate(cfg(), "alice", "Aa1!aaaa"))
    }

    // ── legacy single-record migration path ─────────────────────────────────

    @Test fun legacyRecordStillAuthenticates() {
        // Pre-multi-user installs stored one record with the username folded in.
        val legacy = AppLockCrypto.create(AppLockCrypto.combine("alice", "Str0ng!Pass"))
        assertEquals("alice", AppLockAuth.authenticate(cfg(legacy = legacy), "alice", "Str0ng!Pass"))
    }

    @Test fun legacyRecordRejectsWrongCredentials() {
        val legacy = AppLockCrypto.create(AppLockCrypto.combine("alice", "Str0ng!Pass"))
        assertNull(AppLockAuth.authenticate(cfg(legacy = legacy), "alice", "Wr0ng!Pass"))
        assertNull(AppLockAuth.authenticate(cfg(legacy = legacy), "someone", "Str0ng!Pass"))
    }

    @Test fun legacyIsIgnoredOnceUsersExist() {
        // After migration the account list is authoritative; a stale legacy record
        // must not keep working as a second way in.
        val legacy = AppLockCrypto.create(AppLockCrypto.combine("old", "Old1!pass"))
        val users  = listOf(user("new", "New1!pass"))
        assertNull(AppLockAuth.authenticate(cfg(users, legacy), "old", "Old1!pass"))
        assertEquals("new", AppLockAuth.authenticate(cfg(users, legacy), "new", "New1!pass"))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    @Test fun isTakenIsCaseInsensitive() {
        val users = listOf(user("Alice", "Aa1!aaaa"))
        assertTrue(AppLockAuth.isTaken(cfg(users), "alice"))
        assertTrue(AppLockAuth.isTaken(cfg(users), "  ALICE  "))
        assertFalse(AppLockAuth.isTaken(cfg(users), "bob"))
    }

    @Test fun newUserPreservesTypedCasingButMatchesInsensitively() {
        val u = user("  Alice  ", "Aa1!aaaa")
        assertEquals("Alice", u.username)
        assertEquals("Alice", AppLockAuth.authenticate(cfg(listOf(u)), "alice", "Aa1!aaaa"))
    }

    // ── config state ────────────────────────────────────────────────────────

    @Test fun lockIsInactiveWithoutCredentials() {
        assertFalse(cfg(enabled = true).isLocked)
        assertFalse(cfg(enabled = true).hasCredentials)
    }

    @Test fun lockIsInactiveWhenFlagOff() {
        val users = listOf(user("alice", "Aa1!aaaa"))
        assertTrue(cfg(users, enabled = false).hasCredentials)
        assertFalse(cfg(users, enabled = false).isLocked)
    }

    @Test fun lockIsActiveWhenFlagOnAndUserExists() {
        assertTrue(cfg(listOf(user("alice", "Aa1!aaaa")), enabled = true).isLocked)
    }

    @Test fun maxUsersIsFive() {
        assertEquals(5, AppLockAuth.MAX_USERS)
    }

    // ── view-only accounts ──────────────────────────────────────────────────

    @Test fun newAccountsCanMakeChanges() {
        assertFalse(user("alice", "Aa1!aaaa").readOnly)
    }

    @Test fun viewOnlyFlagIsFoundCaseInsensitively() {
        val users = listOf(user("Alice", "Aa1!aaaa").copy(readOnly = true), user("bob", "Bb2@bbbb"))
        assertTrue(AppLockAuth.isReadOnly(cfg(users), " alice "))
        assertFalse(AppLockAuth.isReadOnly(cfg(users), "bob"))
        assertFalse(AppLockAuth.isReadOnly(cfg(users), "nobody"))
    }

    @Test fun legacySignInIsNeverViewOnly() {
        val legacy = AppLockCrypto.create(AppLockCrypto.combine("alice", "Str0ng!Pass"))
        assertFalse(AppLockAuth.isReadOnly(cfg(legacy = legacy), "alice"))
    }

    @Test fun lastAccountThatCanMakeChangesIsProtected() {
        val alice = user("alice", "Aa1!aaaa")                       // can make changes
        val bob   = user("bob", "Bb2@bbbb").copy(readOnly = true)
        assertFalse(AppLockAuth.keepsAFullAccount(listOf(alice, bob), "alice"))
        assertTrue(AppLockAuth.keepsAFullAccount(listOf(alice, bob), "bob"))
    }

    @Test fun anotherFullAccountAllowsTheChange() {
        val users = listOf(user("alice", "Aa1!aaaa"), user("carol", "Cc3#cccc"))
        assertTrue(AppLockAuth.keepsAFullAccount(users, "alice"))
    }
}
