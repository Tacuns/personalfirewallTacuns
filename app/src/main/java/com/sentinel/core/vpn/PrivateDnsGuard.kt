package com.sentinel.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities

/**
 * Detects Android's Private DNS in strict mode ("Private DNS provider hostname").
 *
 * In that mode Android sends every lookup, encrypted, only to the chosen provider:
 * LinkProperties.getPrivateDnsServerName() — "If not null, private DNS is in strict mode …
 * DNS queries must be sent to the specified DNS server." The firewall filters by answering
 * lookups itself, so the two cannot work together: with both on, Android rejected the
 * firewall's answers, marked the network "validation failed" and every lookup failed — the
 * phone had no internet until the firewall was stopped (reproduced on the emulator:
 * dns.google strict, firewall off 10/10 lookups, firewall on 0/10). "Automatic" mode is
 * not affected (12/12). So the firewall does not run while strict mode is on.
 */
object PrivateDnsGuard {

    /** The provider name when strict mode is on for the phone's real network, else null. */
    fun strictHostname(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            strictHostname(cm.getLinkProperties(network))?.let { return it }
        }
        return null
    }

    fun strictHostname(lp: LinkProperties?): String? =
        lp?.privateDnsServerName?.takeIf { it.isNotBlank() }
}
