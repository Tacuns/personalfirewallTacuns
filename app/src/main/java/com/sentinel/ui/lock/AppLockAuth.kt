package com.sentinel.ui.lock

import com.sentinel.core.settings.AppPreferencesRepository.AppLockConfig
import com.sentinel.core.settings.AppPreferencesRepository.AppLockUser

/**
 * Sign-in logic for App Lock.
 *
 * Usernames are stored readable so the matching record is selected FIRST and only then
 * hashed. That keeps sign-in at exactly ONE PBKDF2 derivation no matter how many accounts
 * exist — trying every record would cost one 600k-iteration derivation per account.
 *
 * Every function here is CPU-bound and must be called from Dispatchers.Default.
 */
object AppLockAuth {

    /** Maximum accounts. Small on purpose: this is a household lock, not a user directory. */
    const val MAX_USERS = 5

    fun normalizeUsername(name: String): String = name.trim().lowercase()

    /**
     * Verifies credentials.
     *
     * @return the stored username on success (useful for migrating the legacy record), or
     *         null on failure. Callers must show one generic error for every failure —
     *         never reveal whether the username or the password was the wrong half.
     */
    fun authenticate(config: AppLockConfig, username: String, password: String): String? {
        val typed    = normalizeUsername(username)
        val combined = AppLockCrypto.combine(username, password)

        val match = config.users.firstOrNull { normalizeUsername(it.username) == typed }
        if (match != null) {
            return if (AppLockCrypto.verify(combined, match.hash)) match.username else null
        }

        // Pre-multi-user install: one record, username never stored. The typed username is
        // still part of the hash, so the same check works — and the caller can then migrate
        // it into the account list, because at this point the username IS known.
        if (config.users.isEmpty() && config.legacyHash != null) {
            return if (AppLockCrypto.verify(combined, config.legacyHash)) username.trim() else null
        }
        return null
    }

    /** True when [username] is already taken (case-insensitive). */
    fun isTaken(config: AppLockConfig, username: String): Boolean =
        config.users.any { normalizeUsername(it.username) == normalizeUsername(username) }

    /** Builds a new account record. Call from Dispatchers.Default — this hashes. */
    fun newUser(username: String, password: String): AppLockUser =
        AppLockUser(
            username = username.trim(),
            hash     = AppLockCrypto.create(AppLockCrypto.combine(username, password))
        )

    /** True when [username] is a view-only account. The legacy single record never is. */
    fun isReadOnly(config: AppLockConfig, username: String): Boolean =
        config.users.firstOrNull { normalizeUsername(it.username) == normalizeUsername(username) }
            ?.readOnly ?: false

    /**
     * True when making [username] view-only, or removing it, still leaves at least one
     * account that can make changes — otherwise nobody could manage the app any more.
     */
    fun keepsAFullAccount(users: List<AppLockUser>, username: String): Boolean =
        users.any { !it.readOnly && normalizeUsername(it.username) != normalizeUsername(username) }
}
