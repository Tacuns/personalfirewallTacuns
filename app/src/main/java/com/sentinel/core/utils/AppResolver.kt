package com.sentinel.core.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap

/**
 * AppResolver: Efficiently maps Android UIDs to human-readable App Labels.
 * Implements high-performance caching to prevent UI stutter and packet processing lag.
 */
object AppResolver {
    
    // Cache for UID -> App Label
    private val nameCache = ConcurrentHashMap<Int, String>()

    /**
     * Resolves the application name for a given UID.
     */
    fun getAppName(context: Context, uid: Int): String {
        if (uid <= 0) return "System"
        if (uid == Process.SYSTEM_UID) return "System"
        
        // Return cached name if available
        nameCache[uid]?.let { return it }

        val pm = context.packageManager
        val name = try {
            val packages = pm.getPackagesForUid(uid)
            if (!packages.isNullOrEmpty()) {
                val packageName = packages[0]
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } else {
                "UID: $uid"
            }
        } catch (e: Exception) {
            "UID: $uid"
        }

        nameCache[uid] = name
        return name
    }

    /**
     * Clear cache (useful if apps are installed/uninstalled frequently).
     */
    fun clearCache() {
        nameCache.clear()
    }
}
