package com.sentinel.core.rules

import android.content.Context
import java.io.File

/**
 * Notices when the rules database was reset because its file was damaged.
 *
 * Android deletes a database file it finds corrupt, and Room then creates an empty one.
 * The user's rules were gone while the app still showed "Firewall Active" (seen on the
 * emulator). A marker file records that the database has existed before; if a new, empty
 * database is created while the marker is there, the rules were lost and Home says so.
 *
 * Clearing the app's data or reinstalling removes the marker too, so neither shows the
 * warning. Tiny files are used because both the app and the :vpn process may open the
 * database first, and DataStore is not safe across processes.
 */
object RulesLossNotice {

    private const val MARKER = "rules_db_seen"
    private const val LOST   = "rules_db_lost"

    /** From the database's onCreate: a fresh database while the marker exists means loss. */
    fun onDatabaseCreated(context: Context) {
        try {
            if (File(context.filesDir, MARKER).exists()) {
                File(context.filesDir, LOST).writeText("1")
                com.sentinel.core.alerts.SecurityAlerts.record(context, com.sentinel.core.alerts.AlertType.RULES_LOST, com.sentinel.core.alerts.AlertSeverity.HIGH)
            }
        } catch (_: Exception) { }
    }

    /** From the database's onOpen: remember that it existed (also covers installs from before this). */
    fun onDatabaseOpened(context: Context) {
        try {
            val marker = File(context.filesDir, MARKER)
            if (!marker.exists()) marker.writeText("1")
        } catch (_: Exception) { }
    }

    fun isRaised(context: Context): Boolean =
        try { File(context.filesDir, LOST).exists() } catch (_: Exception) { false }

    fun dismiss(context: Context) {
        try { File(context.filesDir, LOST).delete() } catch (_: Exception) { }
    }
}
