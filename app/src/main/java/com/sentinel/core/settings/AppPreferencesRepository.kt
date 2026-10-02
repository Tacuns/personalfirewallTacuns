package com.sentinel.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sentinel_settings")

class AppPreferencesRepository(private val context: Context) {

    private val BOOT_ON_START_KEY         = booleanPreferencesKey("boot_on_start")
    private val ONBOARDING_DONE_KEY       = booleanPreferencesKey("onboarding_done")
    private val MAP_MOVED_NOTE_SEEN_KEY   = booleanPreferencesKey("map_moved_note_seen")
    // Legacy single-record key, written by the pre-multi-user build. Kept so existing
    // installs keep working; it is migrated into APP_LOCK_USERS_KEY on first successful
    // sign-in (that is the only moment the username is known, since it was never stored).
    private val APP_LOCK_HASH_KEY         = stringPreferencesKey("app_lock_hash")
    private val APP_LOCK_ENABLED_KEY      = booleanPreferencesKey("app_lock_enabled")
    private val APP_LOCK_USERS_KEY        = stringPreferencesKey("app_lock_users")
    private val APP_LOCK_QUESTION_KEY     = stringPreferencesKey("app_lock_question")
    private val APP_LOCK_ANSWER_KEY       = stringPreferencesKey("app_lock_answer_hash")
    private val APP_LOCK_RECOVERY_KEY     = stringPreferencesKey("app_lock_recovery_hash")
    // Wrong-try counter. Stored, so closing the app does not reset the wait.
    private val APP_LOCK_FAILS_KEY        = intPreferencesKey("app_lock_failed_attempts")
    private val APP_LOCK_UNTIL_KEY        = longPreferencesKey("app_lock_locked_until")
    // The same deadline measured from phone start-up, which the user cannot change, and the
    // start-up it belongs to. Moving the clock forward can then no longer skip the wait.
    private val APP_LOCK_UNTIL_ELAPSED_KEY = longPreferencesKey("app_lock_locked_until_elapsed")
    private val APP_LOCK_UNTIL_BOOT_KEY    = intPreferencesKey("app_lock_locked_until_boot")
    private val IMPORT_EXPORT_COUNT_KEY    = intPreferencesKey("import_export_count")
    private val IMPORT_EXPORT_RESET_AT_KEY = longPreferencesKey("import_export_reset_at")

    // Scheduled blocking — 1 schedule included in free tier
    private val SCHEDULE_ENABLED_KEY   = booleanPreferencesKey("schedule_enabled")
    private val SCHEDULE_CATEGORY_KEY  = stringPreferencesKey("schedule_category")
    private val SCHEDULE_START_HOUR_KEY = intPreferencesKey("schedule_start_hour")
    private val SCHEDULE_START_MIN_KEY  = intPreferencesKey("schedule_start_min")
    private val SCHEDULE_END_HOUR_KEY   = intPreferencesKey("schedule_end_hour")
    private val SCHEDULE_END_MIN_KEY    = intPreferencesKey("schedule_end_min")
    private val SCHEDULE_DAYS_KEY       = stringPreferencesKey("schedule_days")

    val bootOnStart: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[BOOT_ON_START_KEY] ?: true
    }

    val onboardingDone: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ONBOARDING_DONE_KEY] ?: false
    }

    // How many times Import/Export was used in the current 24h window (max 2)
    val importExportCount: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[IMPORT_EXPORT_COUNT_KEY] ?: 0
    }

    // Unix ms when the current 24h window started (0 = never used)
    val importExportResetAt: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[IMPORT_EXPORT_RESET_AT_KEY] ?: 0L
    }

    // App Lock — gates the UI only. The VPN service and BootReceiver never consult
    // this, so protection keeps running (and still auto-starts) while the app is locked.
    //
    // Stores only a salted PBKDF2 record, never the password itself. "Enabled" is
    // derived from the record's presence rather than kept as a separate flag, so it is
    // structurally impossible to end up locked with no password set.
    /**
     * One App Lock account. The username is stored in READABLE form so the right record
     * can be selected before hashing — otherwise signing in with N accounts would cost N
     * PBKDF2 derivations per attempt. The password is never stored in any form; [hash] is
     * a salted PBKDF2 record derived from username+password together.
     */
    data class AppLockUser(val username: String, val hash: String, val readOnly: Boolean = false)

    /**
     * Everything App Lock needs, read as one value.
     *
     * `enabled` is now a real flag, separate from whether accounts exist. Turning the lock
     * off therefore keeps the accounts, so turning it back on does not re-run setup.
     * `question` is the only other field kept readable — it must be shown on the reset
     * screen, and the question is not the secret; the answer is.
     */
    data class AppLockConfig(
        val enabled:      Boolean,
        val users:        List<AppLockUser>,
        val legacyHash:   String?,
        val question:     String?,
        val answerHash:   String?,
        val recoveryHash: String?,
        val failedAttempts: Int  = 0,
        val lockedUntilMs:  Long = 0L,
        val lockedUntilElapsedMs: Long = 0L,
        val lockBoot: Int = -1
    ) {
        /** When the current wait ends (see AppLockThrottle.remainingMs). */
        val lockDeadline: com.sentinel.ui.lock.AppLockThrottle.Deadline
            get() = com.sentinel.ui.lock.AppLockThrottle.Deadline(lockedUntilMs, lockedUntilElapsedMs, lockBoot)

        /** True when at least one way to sign in exists (new format or legacy record). */
        val hasCredentials: Boolean get() = users.isNotEmpty() || legacyHash != null

        /** The gate only locks when the flag is on AND something can actually unlock it. */
        val isLocked: Boolean get() = enabled && hasCredentials
    }

    val appLockConfig: Flow<AppLockConfig> = context.settingsDataStore.data.map { prefs ->
        AppLockConfig(
            enabled      = prefs[APP_LOCK_ENABLED_KEY] ?: false,
            users        = decodeUsers(prefs[APP_LOCK_USERS_KEY]),
            legacyHash   = prefs[APP_LOCK_HASH_KEY],
            question     = prefs[APP_LOCK_QUESTION_KEY],
            answerHash   = prefs[APP_LOCK_ANSWER_KEY],
            recoveryHash   = prefs[APP_LOCK_RECOVERY_KEY],
            failedAttempts = prefs[APP_LOCK_FAILS_KEY] ?: 0,
            lockedUntilMs  = prefs[APP_LOCK_UNTIL_KEY] ?: 0L,
            lockedUntilElapsedMs = prefs[APP_LOCK_UNTIL_ELAPSED_KEY] ?: 0L,
            lockBoot       = prefs[APP_LOCK_UNTIL_BOOT_KEY] ?: -1
        )
    }

    /** Whether the lock is actually active — flag on and credentials present. */
    val appLockEnabled: Flow<Boolean> = appLockConfig.map { it.isLocked }

    /** Whether any account exists, regardless of the on/off flag. */
    val appLockHasCredentials: Flow<Boolean> = appLockConfig.map { it.hasCredentials }

    /**
     * Turns the lock on or off WITHOUT touching stored accounts.
     * Enabling is refused when no account exists, which preserves the guarantee that the
     * app can never be locked with no way back in.
     */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val hasAny = !decodeUsers(prefs[APP_LOCK_USERS_KEY]).isEmpty() ||
                         prefs[APP_LOCK_HASH_KEY] != null
            prefs[APP_LOCK_ENABLED_KEY] = enabled && hasAny
        }
    }

    /**
     * Replaces the account list. Writing a non-empty list also drops the legacy record,
     * since it has been superseded.
     */
    suspend fun setAppLockUsers(users: List<AppLockUser>) {
        context.settingsDataStore.edit { prefs ->
            if (users.isEmpty()) {
                prefs.remove(APP_LOCK_USERS_KEY)
                prefs[APP_LOCK_ENABLED_KEY] = false
            } else {
                prefs[APP_LOCK_USERS_KEY] = encodeUsers(users)
                prefs.remove(APP_LOCK_HASH_KEY)
            }
        }
    }

    /** Stores the shared recovery question/answer and one-time code. */
    suspend fun setAppLockRecovery(question: String, answerHash: String, recoveryHash: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[APP_LOCK_QUESTION_KEY] = question
            prefs[APP_LOCK_ANSWER_KEY]   = answerHash
            prefs[APP_LOCK_RECOVERY_KEY] = recoveryHash
        }
    }

    /**
     * Records one password or recovery check. Success clears the counter; a failure adds
     * one and, once [waitAfter] returns a wait, stores when the next try is allowed.
     * One edit, so two quick attempts can never lose a count.
     */
    suspend fun recordAppLockAttempt(success: Boolean, waitAfter: (failures: Int) -> Long) {
        context.settingsDataStore.edit { prefs ->
            if (success) {
                prefs.remove(APP_LOCK_FAILS_KEY)
                prefs.remove(APP_LOCK_UNTIL_KEY)
                prefs.remove(APP_LOCK_UNTIL_ELAPSED_KEY)
                prefs.remove(APP_LOCK_UNTIL_BOOT_KEY)
            } else {
                val failures = (prefs[APP_LOCK_FAILS_KEY] ?: 0) + 1
                prefs[APP_LOCK_FAILS_KEY] = failures
                val wait = waitAfter(failures)
                if (wait > 0) {
                    prefs[APP_LOCK_UNTIL_KEY] = System.currentTimeMillis() + wait
                    prefs[APP_LOCK_UNTIL_ELAPSED_KEY] = android.os.SystemClock.elapsedRealtime() + wait
                    prefs[APP_LOCK_UNTIL_BOOT_KEY] = com.sentinel.ui.lock.AppLockClock.bootCount(context)
                }
            }
        }
    }

    private fun encodeUsers(users: List<AppLockUser>): String =
        org.json.JSONArray().apply {
            users.forEach { u ->
                put(org.json.JSONObject().apply {
                    put("username", u.username)
                    put("hash", u.hash)
                    if (u.readOnly) put("readOnly", true)
                })
            }
        }.toString()

    private fun decodeUsers(raw: String?): List<AppLockUser> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("username")
                val hash = o.optString("hash")
                if (name.isBlank() || hash.isBlank()) null else AppLockUser(name, hash, o.optBoolean("readOnly"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun setBootOnStart(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[BOOT_ON_START_KEY] = enabled
        }
    }

    /** The one-time "the map is now in Activity" note on the Security tab. */
    val mapMovedNoteSeen: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[MAP_MOVED_NOTE_SEEN_KEY] ?: false
    }

    /** Whether a screen's "More tools" section is open; [screen] is "protect", "home" or "apps". */
    fun moreToolsOpen(screen: String): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[booleanPreferencesKey("more_tools_open_$screen")] ?: false
    }

    suspend fun setMoreToolsOpen(screen: String, open: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("more_tools_open_$screen")] = open
        }
    }

    suspend fun setMapMovedNoteSeen() {
        context.settingsDataStore.edit { prefs ->
            prefs[MAP_MOVED_NOTE_SEEN_KEY] = true
        }
    }

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { prefs ->
            prefs[ONBOARDING_DONE_KEY] = true
        }
    }

    suspend fun setImportExportUsage(count: Int, resetAt: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[IMPORT_EXPORT_COUNT_KEY]    = count
            prefs[IMPORT_EXPORT_RESET_AT_KEY] = resetAt
        }
    }

    val scheduleEnabled:   Flow<Boolean> = context.settingsDataStore.data.map { it[SCHEDULE_ENABLED_KEY]    ?: false }
    val scheduleCategory:  Flow<String>  = context.settingsDataStore.data.map { it[SCHEDULE_CATEGORY_KEY]   ?: "SOCIAL" }
    val scheduleStartHour: Flow<Int>     = context.settingsDataStore.data.map { it[SCHEDULE_START_HOUR_KEY] ?: 22 }
    val scheduleStartMin:  Flow<Int>     = context.settingsDataStore.data.map { it[SCHEDULE_START_MIN_KEY]  ?: 0 }
    val scheduleEndHour:   Flow<Int>     = context.settingsDataStore.data.map { it[SCHEDULE_END_HOUR_KEY]   ?: 6 }
    val scheduleEndMin:    Flow<Int>     = context.settingsDataStore.data.map { it[SCHEDULE_END_MIN_KEY]    ?: 0 }
    // "1111100" = Mon–Fri active, Sat+Sun off  (index 0=Mon … 6=Sun)
    val scheduleDays:      Flow<String>  = context.settingsDataStore.data.map { it[SCHEDULE_DAYS_KEY]       ?: "1111100" }

    suspend fun setSchedule(
        enabled:   Boolean,
        category:  String,
        startHour: Int,
        startMin:  Int,
        endHour:   Int,
        endMin:    Int,
        days:      String
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[SCHEDULE_ENABLED_KEY]    = enabled
            prefs[SCHEDULE_CATEGORY_KEY]   = category
            prefs[SCHEDULE_START_HOUR_KEY] = startHour
            prefs[SCHEDULE_START_MIN_KEY]  = startMin
            prefs[SCHEDULE_END_HOUR_KEY]   = endHour
            prefs[SCHEDULE_END_MIN_KEY]    = endMin
            prefs[SCHEDULE_DAYS_KEY]       = days
        }
    }
}
