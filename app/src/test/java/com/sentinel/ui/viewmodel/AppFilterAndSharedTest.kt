package com.sentinel.ui.viewmodel

import com.sentinel.ui.rules.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterAndSharedTest {

    private val allowed = AppRule("Maps", "com.google.android.apps.maps", 10167)
    private val partial = AppRule("Chrome", "com.android.chrome", 10162, isWifiBlocked = true)
    private val blocked = AppRule("Game", "com.example.game", 10200, isWifiBlocked = true, isDataBlocked = true)
    private val all = listOf(allowed, partial, blocked)

    @Test fun `All shows every app`() =
        assertEquals(all, all.filter { it.matches(AppAccessFilter.ALL) })

    @Test fun `Blocked shows full and partial blocks`() =
        assertEquals(listOf(partial, blocked), all.filter { it.matches(AppAccessFilter.BLOCKED) })

    @Test fun `Allowed shows only apps with no block`() =
        assertEquals(listOf(allowed), all.filter { it.matches(AppAccessFilter.ALLOWED) })

    private val groups = sharedUidGroups(listOf(
        Triple(10111, "com.android.providers.downloads", "Download Manager"),
        Triple(10111, "com.android.providers.downloads.ui", "Downloads"),
        Triple(10111, "com.android.mtp", "MTP Host"),
        Triple(10162, "com.android.chrome", "Chrome")
    ))

    @Test fun `only UIDs with more than one app are grouped`() =
        assertEquals(setOf(10111), groups.keys)

    @Test fun `a card names the other apps, not itself`() =
        assertEquals(listOf("Download Manager", "MTP Host"),
            sharedWithNames(10111, "com.android.providers.downloads.ui", groups))

    @Test fun `apps without a name are listed after named ones`() =
        assertEquals(listOf("Download Manager", "com.android.providers.media"),
            sharedWithNames(1, "x", sharedUidGroups(listOf(
                Triple(1, "com.android.providers.media", "com.android.providers.media"),
                Triple(1, "com.android.providers.downloads", "Download Manager"),
                Triple(1, "x", "X")))))

    @Test fun `an app with its own UID gets no note`() =
        assertTrue(sharedWithNames(10162, "com.android.chrome", groups).isEmpty())
}
