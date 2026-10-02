package com.sentinel.core.rules

/**
 * Detects when a DNS lookup resembles a domain the user trusts — the classic
 * typosquat/homograph phishing pattern (e.g. "paypaI.com" vs "paypal.com").
 *
 * Pure, stateless, synchronous — same family as [DgaDetector] / [DnsTunnelDetector].
 * Unlike those two, this check is inherently comparative (a domain vs. a reference
 * set), so it returns the matched trusted domain rather than a bare Boolean —
 * "this looks like paypal.com" is far more actionable than a flag alone.
 *
 * Character-folding is grounded in the concept of Unicode Technical Standard #39
 * ("Unicode Security Mechanisms") — a small hand-curated subset of visually
 * confusable characters, not the full UTS #39 table, to stay dependency-free.
 */
object LookalikeDomainDetector {

    enum class MatchReason {
        CONFUSABLE_CHARACTERS,
        EDIT_DISTANCE_TYPO,
        BRAND_NAME_WITH_EXTRA_TOKENS,
        /** Level-squat: the trusted domain appears as a subdomain of an attacker-owned apex. */
        BRAND_AS_SUBDOMAIN
    }

    data class LookalikeMatch(
        val queriedDomain: String,
        val matchedTrustedDomain: String,
        val reason: MatchReason
    )

    // Registrable-domain (apex) extraction is a simplified eTLD+1: last 2 labels,
    // except for this small set of known multi-label public suffixes.
    private val MULTI_LABEL_TLDS = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk",
        "co.in", "co.jp", "co.nz", "co.za", "co.kr",
        "com.au", "com.br", "com.mx", "com.sg",
        "or.jp", "ne.jp"
    )

    // Visually-confusable characters folded to a canonical Latin form before comparison.
    // Covers the swaps seen in real phishing kits, not the exhaustive Unicode confusables table.
    private val CONFUSABLES: Map<Char, Char> = mapOf(
        '1' to 'l', 'I' to 'l', '0' to 'o',
        // Cyrillic look-alikes
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y', 'і' to 'i',
        // Greek look-alikes
        'ο' to 'o', 'α' to 'a', 'ρ' to 'p'
    )

    private const val MIN_LABEL_LENGTH = 4

    // A brand name appearing as a token (e.g. "amazon" in "media-amazon.com") is not
    // suspicious by itself — companies legitimately run infrastructure like that
    // (media-amazon.com, ssl-images-amazon.com are real Amazon domains, not phishing).
    // Real combosquat phishing almost always pairs the brand with an urgency/action
    // word, so require one of these to be present too before flagging.
    private val SUSPICIOUS_KEYWORDS = setOf(
        "secure", "security", "login", "signin", "verify", "verification",
        "account", "update", "confirm", "support", "billing", "alert",
        "suspended", "unlock", "recover", "recovery", "password", "credential"
    )

    fun findLookalike(domain: String, trustedDomains: Set<String>): LookalikeMatch? {
        if (domain.isBlank() || !domain.contains('.')) return null

        val normalizedDomain = normalize(domain)
        val queriedApex = apexOf(normalizedDomain)
        val queriedLabel = brandLabel(queriedApex)
        if (queriedLabel.length < MIN_LABEL_LENGTH) return null

        // Reduce each trusted name to its registrable domain. Frequent domains from the logs
        // include full host names such as "googleads.g.doubleclick.net"; comparing against
        // "googleads" flagged Google's own update.googleapis.com as a typo (seen on the emulator).
        val trustedApexes = trustedDomains
            .map { apexOf(normalize(it)) }
            .filter { brandLabel(apexOf(it)).length >= MIN_LABEL_LENGTH }
            .toSet()

        // A trusted domain (or a subdomain of one) is never a lookalike of itself.
        if (queriedApex in trustedApexes) return null

        val queriedTokens = tokensOf(normalizedDomain)
        val querySkeleton = skeleton(queriedLabel)

        for (trustedApex in trustedApexes) {
            val trustedLabel = brandLabel(trustedApex)

            // 1) Level-squat — the whole trusted domain appears as a label sequence, but
            //    the registrable domain belongs to someone else, e.g.
            //    "paypal.com.secure-login.net" (apex is secure-login.net).
            //    Unambiguous by construction: a genuine subdomain of the trusted domain
            //    ends with it, and that case is excluded here (and again at the
            //    trustedApexes check above), so this cannot fire on legitimate traffic.
            //    A known look-alike technique next to typo-, combo-, bit- and homograph-squatting.
            if (normalizedDomain != trustedApex &&
                !normalizedDomain.endsWith(".$trustedApex") &&
                (normalizedDomain.startsWith("$trustedApex.") ||
                 normalizedDomain.contains(".$trustedApex."))
            ) {
                return LookalikeMatch(domain, trustedApex, MatchReason.BRAND_AS_SUBDOMAIN)
            }

            // 2) Confusable-character skeleton match — highest confidence.
            if (skeleton(trustedLabel) == querySkeleton && trustedLabel != queriedLabel) {
                return LookalikeMatch(domain, trustedApex, MatchReason.CONFUSABLE_CHARACTERS)
            }

            // 3) Combosquat — brand name present as a whole token, alongside a
            //    suspicious action/urgency word, domain isn't the trusted apex or a
            //    subdomain of it. Requiring the extra keyword avoids flagging a
            //    company's own legitimate multi-domain infrastructure.
            val hasSuspiciousKeyword = queriedTokens.any { it in SUSPICIOUS_KEYWORDS }
            if (trustedLabel in queriedTokens && hasSuspiciousKeyword &&
                !normalizedDomain.endsWith(".$trustedApex") && normalizedDomain != trustedApex
            ) {
                return LookalikeMatch(domain, trustedApex, MatchReason.BRAND_NAME_WITH_EXTRA_TOKENS)
            }

            // 4) Bounded edit distance (Damerau-Levenshtein — includes transpositions).
            val maxDistance = when {
                trustedLabel.length <= 5 -> 1
                trustedLabel.length <= 10 -> 2
                else -> 3
            }
            if (damerauLevenshtein(queriedLabel, trustedLabel, maxDistance) <= maxDistance) {
                return LookalikeMatch(domain, trustedApex, MatchReason.EDIT_DISTANCE_TYPO)
            }
        }

        return null
    }

    private fun normalize(domain: String): String =
        domain.trim().trimEnd('.').lowercase().removePrefix("www.")

    private fun apexOf(normalizedDomain: String): String {
        val labels = normalizedDomain.split('.')
        if (labels.size < 2) return normalizedDomain
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (labels.size >= 3 && lastTwo in MULTI_LABEL_TLDS) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    // The brand-identifying label of an apex, e.g. "paypal" from "paypal.com".
    private fun brandLabel(apex: String): String = apex.substringBefore('.')

    private fun tokensOf(domain: String): Set<String> =
        domain.split('.', '-').filter { it.isNotEmpty() }.toSet()

    private fun skeleton(s: String): String {
        val folded = s.replace("rn", "m").replace("vv", "w")
        return buildString(folded.length) {
            folded.forEach { append(CONFUSABLES[it] ?: it) }
        }
    }

    // Damerau-Levenshtein with an early-exit bound — cheap for short domain labels.
    private fun damerauLevenshtein(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, d[i - 2][j - 2] + 1)
                }
                d[i][j] = value
            }
        }
        return d[a.length][b.length]
    }
}
