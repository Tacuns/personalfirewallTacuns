package com.sentinel.core.rules

import androidx.compose.ui.graphics.Color

object DomainCategorizer {

    enum class Category(val label: String, val color: Color) {
        CRASH_RPT  ("CRASH RPT", Color(0xFFFF6B35)),
        ANALYTICS  ("ANALYTICS", Color(0xFF4FC3F7)),
        SOCIAL     ("SOCIAL",    Color(0xFFCE93D8)),
        CDN        ("CDN",       Color(0xFF80CBC4)),
        TELEMETRY  ("TELEMETRY", Color(0xFFFFB74D)),
        GOOGLE     ("GOOGLE",    Color(0xFF64B5F6)),
    }

    fun categorize(domain: String): Category? {
        val d = domain.lowercase()
        return when {
            d.contains("crashlytics") || d.contains("bugsnag") ||
            d.contains("sentry")      || d.contains("rollbar")  -> Category.CRASH_RPT

            d.contains("firebase") || d.contains("analytics") ||
            d.contains("amplitude") || d.contains("mixpanel")  ||
            d.contains("appsflyer") || d.contains("adjust.")   ||
            d.contains("segment.io") -> Category.ANALYTICS

            d.contains("facebook")  || d.contains("fbcdn")     ||
            d.contains("instagram") || d.contains("cdninstagram") ||
            d.contains("twitter")   || d.contains("tiktok")    ||
            d.contains("snapchat")  || d.contains("linkedin")  -> Category.SOCIAL

            d.contains("google") || d.contains("gstatic") ||
            d.contains("googleapis") || d.contains("googlevideo") ||
            d.contains("gvt1")       -> Category.GOOGLE

            d.contains("cloudfront") || d.contains("fastly")   ||
            d.contains("akamai")     || d.contains("cloudflare") ||
            d.contains("akamaitechnologies") -> Category.CDN

            d.contains("telemetry") || d.contains(".metrics.") ||
            d.contains("collect.")  -> Category.TELEMETRY

            else -> null
        }
    }
}
