package com.sentinel.core.rules

/**
 * Protection levels: one tap sets a group of existing protections together, the way a
 * firewall bundles its security profiles into a profile group.
 *
 * What each level turns on: threat-prone sites such as malware and phishing are blocked at
 * every level, so the ad/malware list and look-alike (phishing) blocking are always on; safe
 * search changes what people see in search results, so it starts at Strict, and YouTube's
 * restricted mode only at Kids.
 *
 *              Normal  Strict  Kids
 * Ad list        on      on     on
 * Look-alike     on      on     on
 * Safe search    off     on     on
 * YouTube        -       off    on
 * Watch-only     off     off    off
 *
 * A level never turns watch-only on (that stops enforcement) and never touches PRO lists,
 * the DNS server, app rules, schedules or history. The level shown is worked out from the
 * current settings, so changing any switch afterwards shows "Custom" by itself.
 */
enum class ProtectionProfile(val key: String) {
    NORMAL("normal"),
    STRICT("strict"),
    KIDS("kids");

    val adList: Boolean get() = true
    val lookalikes: Boolean get() = true
    val safeSearch: Boolean get() = this != NORMAL
    /** Only meaningful while safe search is on; Normal leaves the user's choice as it is. */
    val safeSearchYouTube: Boolean get() = this == KIDS

    companion object {
        /** The free ad and tracker list a level switches on. PRO lists are never touched. */
        const val AD_LIST_KEY = "BLOCKLIST_STEVENBLACK"

        /** Stored in change history when the settings matched no level. */
        const val CUSTOM_KEY = "custom"

        fun fromKey(key: String?): ProtectionProfile? = entries.firstOrNull { it.key == key }
    }
}

/** The level the current settings match, or null for "Custom". */
fun detectProfile(options: FirewallOptions, adListOn: Boolean): ProtectionProfile? =
    ProtectionProfile.entries.firstOrNull { p ->
        !options.watchOnly &&
            adListOn == p.adList &&
            options.blockLookalikes == p.lookalikes &&
            options.safeSearch == p.safeSearch &&
            (!p.safeSearch || options.safeSearchYouTube == p.safeSearchYouTube)
    }

/** These options with [profile] applied. Everything a level does not cover is kept. */
fun FirewallOptions.withProfile(profile: ProtectionProfile): FirewallOptions = copy(
    watchOnly         = false,
    blockLookalikes   = profile.lookalikes,
    safeSearch        = profile.safeSearch,
    safeSearchYouTube = if (profile.safeSearch) profile.safeSearchYouTube else safeSearchYouTube
)
