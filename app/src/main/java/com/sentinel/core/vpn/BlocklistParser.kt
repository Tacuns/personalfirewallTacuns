package com.sentinel.core.vpn

import com.sentinel.core.rules.BlocklistSource
import java.net.URI

/**
 * Turns one line of a downloaded blocklist into a domain, or null to skip it.
 *
 * Supported formats:
 *   hosts   — "0.0.0.0 domain.com" / "127.0.0.1 domain.com"
 *   domains — plain "domain.com" one per line
 *   abp     — "||domain.com^" (AdBlock Plus / AdGuard syntax)
 *   auto    — decided per line; used for lists the user adds, whose format is unknown
 *
 * The three fixed formats keep exactly the rules the built-in lists always used.
 * Auto adds a strict hostname check, because a user's link may point at anything.
 */
object BlocklistParser {

    const val FORMAT_AUTO = "auto"

    private val SINK_IPS = setOf("0.0.0.0", "127.0.0.1", "::1")
    private val WHITESPACE = Regex("\\s+")
    private val HOSTNAME = Regex("^[a-z0-9_-]+(\\.[a-z0-9_-]+)+$")

    fun parseLine(rawLine: String, format: String): String? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) return null
        if (format == FORMAT_AUTO) return parseAuto(line)

        val domain = when (format) {
            "hosts" -> {
                val parts = line.split(WHITESPACE, limit = 3)
                if (parts[0] !in SINK_IPS) return null
                parts.getOrNull(1)?.substringBefore('#')?.trim() ?: return null
            }
            "abp" -> {
                if (!line.startsWith("||") || line.startsWith("@@")) return null
                line.removePrefix("||")
                    .substringBefore('^')
                    .substringBefore('/')
                    .substringBefore('$')
                    .trim()
            }
            else -> /* "domains" and unknown */ line.substringBefore('#').trim()
        }
        return if (domain.isNotEmpty()
            && domain != "0.0.0.0"
            && !domain.startsWith('.')
            && domain.contains('.')
            && !domain.contains('*')
        ) domain.lowercase() else null
    }

    private fun parseAuto(line: String): String? {
        val format = when {
            line.startsWith("||") || line.startsWith("@@")   -> "abp"
            line.split(WHITESPACE, limit = 2)[0] in SINK_IPS -> "hosts"
            else                                             -> "domains"
        }
        val domain = parseLine(line, format) ?: return null
        return domain.takeIf { it.length <= 253 && HOSTNAME.matches(it) }
    }
}

/** Blocklists the user adds by link. They share the BLOCKLIST prefix, so blocking treats them like any list. */
object CustomBlocklist {

    const val PREFIX = "BLOCKLIST_CUSTOM_"
    const val MAX_LISTS = 3
    private const val MAX_NAME = 40

    fun isCustom(sourceKey: String): Boolean = sourceKey.startsWith(PREFIX)

    fun newKey(): String = PREFIX + System.currentTimeMillis()

    /**
     * Cleans a typed link. A link without a scheme gets https://, and http:// is upgraded to
     * https:// because Android blocks plain-text HTTP for this app. Returns null unless it is
     * a web link with a real host name.
     */
    fun normalizeLink(input: String): String? {
        var text = input.trim()
        if (text.isEmpty() || text.length > 2000 || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "https://$text"
        if (text.startsWith("http://", ignoreCase = true)) text = "https://" + text.substring(7)
        val uri = try { URI(text) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return uri.toString()
    }

    /** A new, switched-off list row. The name falls back to the link's host. */
    fun newSource(sourceKey: String, name: String, link: String): BlocklistSource {
        val host = try { URI(link).host.orEmpty() } catch (e: Exception) { "" }
        return BlocklistSource(
            sourceKey     = sourceKey,
            displayName   = name.trim().take(MAX_NAME).ifBlank { host },
            url           = link,
            format        = BlocklistParser.FORMAT_AUTO,
            description   = host,
            isDefault     = true,   // not a PRO list: the user's own lists are free to use
            enabled       = false,
            domainCount   = 0,
            lastUpdatedMs = 0L
        )
    }
}
