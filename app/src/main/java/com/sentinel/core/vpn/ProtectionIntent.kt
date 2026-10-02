package com.sentinel.core.vpn

import android.content.Context
import java.io.File

/**
 * Whether the user wants protection on. Shared by the app and the :vpn process through a tiny
 * file (DataStore is not safe across processes).
 *
 * Needed because Android kills the whole app when its notifications are switched off, and
 * then destroys the restarted VPN service (seen on the emulator: "Killing … PermissionHelper",
 * then "Destroy ServiceRecord … SentinelVpnService"). With this flag the app can turn
 * protection back on the next time it is opened — but only if the user had it on.
 *
 * Set when protection starts; cleared only by the user stopping it (in the app, the
 * notification's Stop button, or disconnecting it in Android's VPN settings).
 */
object ProtectionIntent {

    private const val FILE = "protection_wanted"

    fun setWanted(context: Context, wanted: Boolean) {
        try {
            val file = File(context.filesDir, FILE)
            if (wanted) {
                val tmp = File(context.filesDir, "$FILE.tmp")
                tmp.writeText("1")
                if (!tmp.renameTo(file)) file.writeText("1")
            } else {
                file.delete()
            }
        } catch (_: Exception) { }
    }

    fun isWanted(context: Context): Boolean =
        try { File(context.filesDir, FILE).exists() } catch (_: Exception) { false }
}
