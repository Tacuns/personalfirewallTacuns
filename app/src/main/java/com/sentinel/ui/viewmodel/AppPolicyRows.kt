package com.sentinel.ui.viewmodel

import com.sentinel.core.rules.AppPolicy
import com.sentinel.core.rules.SelfProtection
import com.sentinel.ui.rules.AppRule

/**
 * Applies a new Wi-Fi / mobile-data rule to the rows of the Apps list after it is saved.
 *
 * Android runs some apps under one shared UID (for example two Samsung Cloud apps, or
 * Download Manager and Downloads), and the VPN applies app rules per UID: the framework turns
 * each package name into its UID before building the tunnel's UID ranges (Vpn.java,
 * addUserToRanges -> getAppsUids). So one saved rule always covers every app with that UID,
 * and every one of their rows has to show it.
 *
 * Each row keeps its own name and package. The package is the list's item key, and the
 * LazyColumn contract is that "using the same key for multiple items in the list is not
 * allowed". The old code overwrote the first row with the tapped app's copy, which put the
 * tapped package into the list twice and crashed the app when the second app of a shared-UID
 * pair was tapped.
 */
fun applyPolicyToRows(rows: MutableList<AppRule>, uid: Int, wifiBlocked: Boolean, dataBlocked: Boolean) {
    for (i in rows.indices) {
        val row = rows[i]
        if (row.uid == uid && (row.isWifiBlocked != wifiBlocked || row.isDataBlocked != dataBlocked)) {
            rows[i] = row.copy(isWifiBlocked = wifiBlocked, isDataBlocked = dataBlocked)
        }
    }
}

/**
 * How many apps the firewall is blocking, across the Installed and System lists together.
 *
 * The Apps screen used to count only the rows on screen, so the number changed with the tab
 * and with every letter typed into search (a user with 10+ blocked apps saw "1 blocked").
 * [visibleAppUids] holds one UID per app that either list can show; an app whose UID is
 * blocked by a Wi-Fi or mobile-data rule counts once. The firewall's own app never counts,
 * because its rule is never applied.
 */
fun countBlockedApps(visibleAppUids: List<Int>, policies: List<AppPolicy>, ownUid: Int, ownPackage: String): Int {
    val blockedUids = policies
        .filter { (it.wifiBlocked || it.cellBlocked) && !SelfProtection.isOwn(it, ownUid, ownPackage) }
        .map { it.uid }
        .toSet()
    return visibleAppUids.count { it in blockedUids }
}

/** The Apps tab's All / Allowed / Blocked filter. A partial block (Wi-Fi or data only) counts as blocked. */
enum class AppAccessFilter { ALL, ALLOWED, BLOCKED }

fun AppRule.matches(filter: AppAccessFilter): Boolean {
    val blocked = isWifiBlocked || isDataBlocked
    return when (filter) {
        AppAccessFilter.ALL     -> true
        AppAccessFilter.ALLOWED -> !blocked
        AppAccessFilter.BLOCKED -> blocked
    }
}

/**
 * For each UID that more than one listed app runs under: the (package, name) of those apps.
 * Android applies a VPN app rule to the whole UID, so these apps are always blocked and
 * allowed together, and their cards say so.
 */
fun sharedUidGroups(apps: List<Triple<Int, String, String>>): Map<Int, List<Pair<String, String>>> =
    apps.groupBy({ it.first }, { it.second to it.third })
        .mapValues { (_, members) -> members.distinctBy { it.first } }
        .filterValues { it.size > 1 }

/**
 * The names of the other apps that share [packageName]'s UID. Apps with a real name come
 * first; an app Android has no name for shows its package name, listed last.
 */
fun sharedWithNames(uid: Int, packageName: String, groups: Map<Int, List<Pair<String, String>>>): List<String> =
    groups[uid].orEmpty()
        .filter { it.first != packageName }
        .sortedBy { it.second == it.first }
        .map { it.second }
