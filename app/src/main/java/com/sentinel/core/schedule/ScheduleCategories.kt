package com.sentinel.core.schedule

object ScheduleCategories {

    const val SOCIAL    = "SOCIAL"
    const val STREAMING = "STREAMING"
    const val GAMING    = "GAMING"
    const val NEWS      = "NEWS"

    val all = listOf(SOCIAL, STREAMING, GAMING, NEWS)

    fun labelFor(category: String) = when (category) {
        SOCIAL    -> "Social Media"
        STREAMING -> "Streaming"
        GAMING    -> "Gaming"
        NEWS      -> "News"
        else      -> category
    }

    fun domainsFor(category: String): List<String> = when (category) {
        SOCIAL -> listOf(
            "facebook.com", "instagram.com", "twitter.com", "x.com",
            "tiktok.com", "snapchat.com", "threads.net", "reddit.com",
            "pinterest.com", "linkedin.com", "tumblr.com"
        )
        STREAMING -> listOf(
            "youtube.com", "netflix.com", "twitch.tv", "spotify.com",
            "primevideo.com", "hotstar.com", "disneyplus.com",
            "soundcloud.com", "vimeo.com", "dailymotion.com"
        )
        GAMING -> listOf(
            "roblox.com", "epicgames.com", "steampowered.com",
            "ea.com", "battle.net", "minecraft.net",
            "xbox.com", "playstationnetwork.com"
        )
        NEWS -> listOf(
            "bbc.com", "cnn.com", "nytimes.com", "theguardian.com",
            "reuters.com", "bloomberg.com", "foxnews.com",
            "apnews.com", "washingtonpost.com"
        )
        else -> emptyList()
    }
}
