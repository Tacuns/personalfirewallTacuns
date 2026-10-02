package com.sentinel.core.rules

object DnsTunnelDetector {

    private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

    fun isSuspect(domain: String): Boolean {
        val labels = domain.lowercase().split('.')
        if (labels.size < 2) return false

        // Strip known TLD parts to get only the subdomain labels
        val subLabels = labels.dropLast(2)
        if (subLabels.isEmpty()) return false

        // Very long single label — data is being encoded in the subdomain
        val longestLabel = subLabels.maxOf { it.length }
        if (longestLabel > 40) return true

        // Unusually deep nesting (5+ labels total) with moderate label length
        if (labels.size >= 5 && longestLabel > 20) return true

        // Base64-like encoding in subdomain: high ratio of base64 chars + no hyphens/real words
        val combined = subLabels.joinToString("")
        if (combined.length >= 20) {
            val b64Ratio = combined.count { it in BASE64_CHARS }.toFloat() / combined.length
            if (b64Ratio > 0.95f && combined.none { it == '-' } && combined.length > 24) return true
        }

        return false
    }
}
