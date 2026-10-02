package com.sentinel.core.vpn

import android.content.Context

/**
 * Remembers why the last download of a blocklist failed, so the screen can say so.
 * Cleared on the next successful download. Main process only (the worker and the screen).
 */
object BlocklistSyncStatus {

    const val HTTP      = "http"       // the link answered with an error page (4xx)
    const val NETWORK   = "network"    // no connection, timeout or server trouble — retried later
    const val TOO_LARGE = "too_large"
    const val EMPTY     = "empty"      // downloaded, but no usable domains inside
    const val LINK      = "link"       // not a web link

    private fun prefs(context: Context) =
        context.getSharedPreferences("blocklist_sync_status", Context.MODE_PRIVATE)

    fun setError(context: Context, sourceKey: String, code: String) {
        prefs(context).edit().putString(sourceKey, "$code|${System.currentTimeMillis()}").apply()
    }

    fun clear(context: Context, sourceKey: String) {
        prefs(context).edit().remove(sourceKey).apply()
    }

    /** The error code and when it happened, or null. */
    fun get(context: Context, sourceKey: String): Pair<String, Long>? {
        val value = prefs(context).getString(sourceKey, null) ?: return null
        val time  = value.substringAfter('|', "").toLongOrNull() ?: return null
        return value.substringBefore('|') to time
    }
}
