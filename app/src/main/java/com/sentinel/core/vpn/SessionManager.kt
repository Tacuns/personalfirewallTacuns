package com.sentinel.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * SessionManager: Maintains the state of all active network flows.
 * Maps (Source Port, Dest IP, Dest Port) -> App UID.
 */
class SessionManager(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Key: Protocol|SourcePort|DestIP|DestPort
    // Value: SessionInfo(uid, lastSeen)
    private val sessionCache = ConcurrentHashMap<String, SessionInfo>()
    
    data class SessionInfo(val uid: Int, val lastSeen: Long = System.currentTimeMillis())

    companion object {
        const val TIMEOUT_TCP = 10 * 60 * 1000L // 10 minutes
        const val TIMEOUT_UDP = 30 * 1000L // 30 seconds
    }

    /**
     * Resolves the UID for a specific flow.
     * Uses Android 10+ getConnectionOwnerUid for precise attribution.
     */
    fun resolveUid(protocol: Int, srcIP: String, srcPort: Int, destIP: String, destPort: Int): Int {
        val key = "$protocol|$srcIP|$srcPort|$destIP|$destPort"

        // Return cached UID if available and update lastSeen
        sessionCache[key]?.let {
            sessionCache[key] = it.copy(lastSeen = System.currentTimeMillis())
            return it.uid
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val proto = if (protocol == PacketEngine.PROTOCOL_TCP) 6 else 17
                val src = InetSocketAddress(srcIP, srcPort)
                val dest = InetSocketAddress(destIP, destPort)
                
                val uid = connectivityManager.getConnectionOwnerUid(proto, src, dest)
                // Cache it even if it's -1 (meaning OS doesn't know) so we don't hammer the OS
                sessionCache[key] = SessionInfo(uid)
                return uid
            } catch (e: Exception) {
                // Fallback to -1 and cache the failure
                sessionCache[key] = SessionInfo(-1)
                return -1
            }
        }
        
        sessionCache[key] = SessionInfo(-1)
        return -1
    }

    /**
     * Periodically clean up session cache to prevent memory leaks.
     * Production reaper: prunes sessions based on protocol-specific timeouts.
     */
    fun pruneStaleSessions() {
        val now = System.currentTimeMillis()
        val iterator = sessionCache.entries.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val protocol = entry.key.split("|")[0].toInt()
            val timeout = if (protocol == PacketEngine.PROTOCOL_TCP) TIMEOUT_TCP else TIMEOUT_UDP
            
            if (now - entry.value.lastSeen > timeout) {
                iterator.remove()
            }
        }
    }
}
