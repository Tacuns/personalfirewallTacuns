package com.sentinel.core.rules

import kotlin.math.log2

object DgaDetector {

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )
    private const val HEX_CHARS = "0123456789abcdef"
    private const val CONSONANTS = "bcdfghjklmnpqrstvwxyz"

    fun isSuspect(domain: String): Boolean {
        val label = domain.lowercase().substringBefore('.')
        if (label.length < 8) return false

        // UUID subdomains — common in malware C2 and some CDNs
        if (UUID_REGEX.matches(label)) return true

        // Very high Shannon entropy → random-looking string
        if (shannonEntropy(label) > 3.8f) return true

        // Hex-encoded hash as subdomain (e.g. a3f8e91c4b2d7056)
        val hexRatio = label.count { it in HEX_CHARS }.toFloat() / label.length
        if (label.length >= 14 && hexRatio > 0.9f) return true

        // 6+ consecutive consonants — no real English word has this
        var maxRun = 0; var curRun = 0
        for (c in label) {
            if (c in CONSONANTS) { if (++curRun > maxRun) maxRun = curRun }
            else curRun = 0
        }
        if (maxRun >= 6 && label.length >= 10) return true

        return false
    }

    private fun shannonEntropy(s: String): Float {
        val len = s.length.toFloat()
        return s.groupingBy { it }.eachCount().values
            .sumOf { c -> val p = c / len; -(p * log2(p.toDouble())) }
            .toFloat()
    }
}
