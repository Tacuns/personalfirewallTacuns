package com.sentinel.core.geo

import com.maxmind.db.Reader
import java.io.Closeable
import java.io.File
import java.net.InetAddress

/**
 * Turns an IP address into an ISO 3166-1 alpha-2 country code, or null when unknown.
 *
 * This exists so the dataset behind the Map tab can be swapped without touching the map:
 * everything outside this package talks to [CountryLookup], which talks to whichever
 * resolver is plugged in here.
 *
 * Map and display use only. Nothing in core/vpn or core/rules may depend on this package -
 * a country guess must never decide whether traffic is allowed.
 */
interface CountryResolver : Closeable {
    fun countryOf(ip: InetAddress): String?
}

/**
 * Reads any MMDB file whose records carry a `country.iso_code` field - the shape the bundled
 * country.mmdb is built with (tools/build_geoip.py) and the shape MaxMind-style databases use.
 * The file is memory-mapped by the reader, so lookups do not load it into the heap.
 */
class MmdbCountryResolver(file: File) : CountryResolver {

    private val reader = Reader(file)

    @Suppress("UNCHECKED_CAST")
    override fun countryOf(ip: InetAddress): String? {
        val record = reader.get(ip, Map::class.java) ?: return null
        val country = record["country"] as? Map<String, Any?> ?: return null
        return (country["iso_code"] as? String)?.takeIf { it.length == 2 }
    }

    override fun close() = reader.close()
}
