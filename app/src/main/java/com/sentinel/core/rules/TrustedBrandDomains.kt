package com.sentinel.core.rules

/**
 * Reference set of trusted domains for [LookalikeDomainDetector].
 *
 * [STATIC_LIST] is an original, self-curated, factual list of major domains —
 * a list of public company domain names, not creative expression. The categories
 * chosen (payment/financial, big-tech/webmail, social, shipping/delivery) match
 * what phishing-trend research consistently reports as the most-impersonated
 * sectors; nothing here is scraped or copied from any third-party dataset.
 */
object TrustedBrandDomains {

    val STATIC_LIST: Set<String> = setOf(
        // Payment / financial
        "paypal.com", "venmo.com", "cashapp.com", "stripe.com", "visa.com", "mastercard.com",
        "americanexpress.com", "chase.com", "bankofamerica.com", "wellsfargo.com", "citibank.com",
        "hsbc.com", "revolut.com", "wise.com", "coinbase.com", "binance.com",
        // Big tech / webmail
        "google.com", "gmail.com", "microsoft.com", "outlook.com", "live.com", "apple.com",
        "icloud.com", "amazon.com", "yahoo.com", "protonmail.com",
        // Social / messaging
        "facebook.com", "instagram.com", "whatsapp.com", "linkedin.com", "x.com", "twitter.com",
        "snapchat.com", "tiktok.com", "telegram.org", "discord.com",
        // Shipping / delivery
        "ups.com", "fedex.com", "dhl.com", "usps.com", "amazon.com",
        // Streaming / marketplaces
        "netflix.com", "spotify.com", "ebay.com", "walmart.com",
        // App stores
        "play.google.com", "apps.apple.com"
    )

    private const val MIN_LABEL_LENGTH = 4

    fun buildTrustedSet(userFrequentDomains: Collection<String>): Set<String> {
        // A fake site that was opened a few times lands in the frequent list. Trusting it
        // switched the look-alike check off for itself (seen on the emulator: paypa1.com,
        // arnazon.com), so frequent names that imitate a known brand are never trusted.
        val frequent = userFrequentDomains.filter {
            LookalikeDomainDetector.findLookalike(it, STATIC_LIST) == null
        }
        val normalized = (STATIC_LIST + frequent)
            .map { it.trim().trimEnd('.').lowercase().removePrefix("www.") }
            .filter { it.contains('.') && it.substringBefore('.').length >= MIN_LABEL_LENGTH }
        return normalized.toSet()
    }
}
