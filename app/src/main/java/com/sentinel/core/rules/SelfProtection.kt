package com.sentinel.core.rules

import android.content.Context

/**
 * The firewall must never block itself.
 *
 * Blocking TacU-NS in the Apps tab did two bad things. Its own lookups were refused, so
 * blocklist downloads failed with "Unable to resolve host". Worse, one blocked app switches
 * the tunnel to full-tunnel mode for the blocked apps only, so with just the firewall itself
 * blocked every other app left the tunnel entirely: no filtering and no logging, while Home
 * still said the firewall was active (seen on the emulator: VPN UIDs 10175 only, Chrome on
 * wlan0).
 *
 * Checked by package name as well as UID, because the same app has a different UID in a
 * work profile or on a second user.
 */
object SelfProtection {

    fun isOwn(policy: AppPolicy, ownUid: Int, ownPackage: String): Boolean =
        policy.uid == ownUid || policy.packageName == ownPackage

    /** The same rule, with any block on the firewall's own app removed. */
    fun sanitize(policy: AppPolicy, ownUid: Int, ownPackage: String): AppPolicy =
        if (isOwn(policy, ownUid, ownPackage) && (policy.wifiBlocked || policy.cellBlocked))
            policy.copy(wifiBlocked = false, cellBlocked = false)
        else policy

    fun sanitize(context: Context, policy: AppPolicy): AppPolicy =
        sanitize(policy, context.applicationInfo.uid, context.packageName)

    fun isOwnPackage(context: Context, packageName: String): Boolean =
        packageName == context.packageName

    /**
     * Clears a self-block saved by an older version or restored from an old backup.
     * The rule engine already ignores such a row, so this only keeps the stored rules and
     * the "N blocked" counts honest.
     */
    suspend fun removeStoredSelfBlock(context: Context, dao: RuleDao) {
        dao.getAllPolicies()
            .filter { isOwn(it, context.applicationInfo.uid, context.packageName) }
            .filter { it.wifiBlocked || it.cellBlocked }
            .forEach { dao.updatePolicy(it.copy(wifiBlocked = false, cellBlocked = false)) }
    }
}
