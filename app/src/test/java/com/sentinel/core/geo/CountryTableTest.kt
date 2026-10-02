package com.sentinel.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress

/**
 * Checks the bundled country table (assets/country.mmdb, built by tools/build_geoip.py from
 * the public-domain server-country dataset) through the same resolver the app uses.
 *
 * Expected answers come straight from the source CSV, e.g. the row
 * "1.0.1.0,1.0.3.255,CN" - not from memory of what a country "should" be.
 */
class CountryTableTest {

    private fun table(): File? =
        listOf("src/main/assets/country.mmdb", "app/src/main/assets/country.mmdb")
            .map { File(it) }.firstOrNull { it.exists() && it.length() > 1000 }

    private inline fun withResolver(block: (CountryResolver) -> Unit) {
        val f = table() ?: run { assumeTrue("country.mmdb not present in this build env", false); return }
        MmdbCountryResolver(f).use(block)
    }

    private fun ip(s: String) = InetAddress.getByName(s)   // literals only, no lookup happens

    @Test fun resolvesIpv4FromTheSourceRows() = withResolver { r ->
        assertEquals("AU", r.countryOf(ip("1.0.0.1")))      // 1.0.0.0,1.0.0.255,AU
        assertEquals("CN", r.countryOf(ip("1.0.1.0")))      // first address of 1.0.1.0-1.0.3.255
        assertEquals("CN", r.countryOf(ip("1.0.3.255")))    // last address of the same range
        assertEquals("US", r.countryOf(ip("8.8.8.8")))
    }

    @Test fun resolvesIpv6FromTheSourceRows() = withResolver { r ->
        assertEquals("JP", r.countryOf(ip("2001:200::1")))  // 2001:200::/32,JP
        assertEquals("SG", r.countryOf(ip("2001:208::1")))  // 2001:208::/32,SG
    }

    @Test fun privateAndLoopbackAddressesHaveNoCountry() = withResolver { r ->
        listOf("10.0.0.1", "192.168.1.1", "127.0.0.1", "::1").forEach {
            assertNull("$it must not be placed on the map", r.countryOf(ip(it)))
        }
    }

    @Test fun theMaxMindDatabaseIsNoLongerShipped() {
        val old = listOf("src/main/assets/GeoLite2-Country.mmdb", "app/src/main/assets/GeoLite2-Country.mmdb")
        old.forEach { assertFalse("$it must not be bundled any more", File(it).exists()) }
    }

    @Test fun jacksonIsNotOnTheClasspath() {
        // The mmdb reader is self-contained; jackson-databind was removed as unused.
        var present = true
        try { Class.forName("com.fasterxml.jackson.databind.ObjectMapper") }
        catch (_: ClassNotFoundException) { present = false }
        assertEquals("jackson-databind should be gone from the classpath", false, present)
    }

    /**
     * A country guess must never decide whether traffic is allowed. This fails the build if
     * anything in the firewall's decision code starts using the geo package.
     */
    @Test fun blockingCodeNeverUsesGeoIp() {
        val roots = listOf("src/main/java/com/sentinel/core", "app/src/main/java/com/sentinel/core")
            .map { File(it) }.firstOrNull { it.isDirectory }
            ?: run { assumeTrue("sources not found from this working dir", false); return }
        val decisionCode = listOf("vpn", "rules").flatMap { dir ->
            File(roots, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("expected to find the decision code", decisionCode.isNotEmpty())
        val offenders = decisionCode.filter { f ->
            val text = f.readText()
            "com.sentinel.core.geo" in text || "CountryLookup" in text || "CountryResolver" in text
        }
        assertEquals("GeoIP used in blocking code: ${offenders.map { it.name }}", 0, offenders.size)
    }
}
