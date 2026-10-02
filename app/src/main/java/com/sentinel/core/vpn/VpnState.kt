package com.sentinel.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Returns true if this app's own VPN is running, for this Android user.
 *
 * Uses getAllNetworks() instead of activeNetwork so this works correctly
 * in both DNS-only mode (VPN is the active network) and full-tunnel mode
 * where our app process bypasses the VPN — in that case activeNetwork
 * returns the physical network and would incorrectly report false.
 *
 * Any VPN is not enough: in a second user or work profile the main user's VPN is visible
 * too, so the app said "Firewall Active" with no firewall there, and every rule change then
 * started the service for a reload it cannot serve, which crashed the :vpn process
 * (ForegroundServiceDidNotStartInTimeException, seen on the emulator). The same applies to
 * another VPN app. NetworkCapabilities.getOwnerUid() is filled in only when "the caller is
 * the network owner" and "the described Network is a VPN" (android-36.1 sources); a UID
 * includes the user, so it matches only our own VPN in this user.
 */
fun Context.vpnRunning(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    val me = Process.myUid()
    return cm.allNetworks.any {
        val caps = cm.getNetworkCapabilities(it) ?: return@any false
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        // getOwnerUid() exists from Android 11 (API 30). Android 10 has no way to tell whose
        // VPN it is, so there any VPN still counts, as before.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) isOwnVpn(isVpn, caps.ownerUid, me)
        else isVpn
    }
}

/** A VPN network that this app (this UID, so this user) owns. */
internal fun isOwnVpn(isVpn: Boolean, ownerUid: Int, myUid: Int): Boolean =
    isVpn && ownerUid == myUid && ownerUid != Process.INVALID_UID

/**
 * A cold [Flow<Boolean>] that emits the current VPN state and re-emits on every change.
 *
 * Three layers of correctness:
 *
 * 1. NetworkCallback (corrected request) — NetworkRequest.Builder() adds
 *    NET_CAPABILITY_NOT_VPN by default; VPN networks don't have this capability,
 *    so the default request is a contradiction that never fires. Removing it makes
 *    onAvailable() fire as expected when a VPN network appears or disappears.
 *
 * 2. 2-second periodic poll — when our process is in addDisallowedApplication()
 *    (full-tunnel mode with an app blocked), ConnectivityService may not deliver
 *    the callback to our UID. The poll calls getAllNetworks() which is system-wide
 *    and unfiltered by UID, so it sees the VPN regardless of per-UID routing.
 *
 * 3. 700 ms false-debounce via transformLatest — when the VPN tunnel is rebuilt
 *    during a rule update (block/unblock app), Android briefly removes the old
 *    VPN network and registers the new one (~50–200 ms gap). Without debouncing,
 *    onLost fires and emits false, flashing the Dashboard to "Offline" for a frame.
 *    transformLatest delays any false by 700 ms; if a true arrives first (new VPN
 *    is up) it cancels the delay — the Dashboard never flickers during rebuilds.
 *    When the VPN genuinely stops, false is emitted after 700 ms (acceptable).
 *
 * distinctUntilChanged() suppresses duplicate emissions from the 2-second poll.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Context.vpnRunningFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)!!

    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    val cb = object : ConnectivityManager.NetworkCallback() {
        // Any VPN appearing triggers a re-check; only our own counts (see vpnRunning()).
        override fun onAvailable(network: Network) { trySend(vpnRunning()) }
        override fun onLost(network: Network)      { trySend(vpnRunning()) }
    }
    cm.registerNetworkCallback(request, cb)

    // Emit current state immediately so collectors start with the correct value.
    trySend(vpnRunning())

    // Fallback poll every 2 s — catches missed callbacks in full-tunnel bypass mode.
    val pollJob = launch(Dispatchers.IO) {
        while (true) {
            delay(2_000)
            // No polling while the app is closed; the real state is read again on return.
            com.sentinel.core.utils.AppVisibility.awaitVisible()
            trySend(vpnRunning())
        }
    }

    awaitClose {
        cm.unregisterNetworkCallback(cb)
        pollJob.cancel()
    }
}.transformLatest { running ->
    // true  → emit immediately (VPN came up or stayed up)
    // false → wait 700 ms before emitting; cancelled if true arrives first
    if (running) emit(true)
    else { delay(700); emit(false) }
}.distinctUntilChanged()
