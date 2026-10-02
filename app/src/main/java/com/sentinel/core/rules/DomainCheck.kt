package com.sentinel.core.rules

/**
 * Answers "is this website blocked, and by which rule?" for the Test-a-domain check.
 *
 * Follows the same matching as [RuleEngine], so the answer agrees with the firewall:
 *   - blocklist entries match the exact domain only
 *   - your own rules (typed domains and country endings) and schedule rules also match
 *     every parent domain, so a rule for "tumblr.com" covers "www.tumblr.com"
 *
 * The caller reads the rules database rather than RuleEngine's memory: each process has
 * its own RuleEngine, and the app screen does not necessarily hold the large blocklist.
 *
 * Pure functions only, so the matching is unit-tested on the JVM.
 */
object DomainCheck {

    /**
     * Cleans what the user typed or pasted. Returns null when it is not a usable domain.
     *
     * "www." is kept on purpose: the firewall sees the real name, and blocklists often list
     * the www. form exactly, so dropping it could report a blocked site as not blocked.
     * A rule for "example.com" still covers "www.example.com" through the parent check.
     */
    fun normalize(input: String): String? {
        val host = input.trim().lowercase()
            .substringAfter("://")                                   // https://example.com
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')                                 // user@example.com
            .substringBefore(':')                                    // example.com:8080
            .removePrefix("*.")                                      // *.example.com
            .trimEnd('.')
        if (host.isEmpty() || host.contains(' ') || host.contains('*') || !host.contains('.')) return null
        return host
    }

    private val RULE_LABEL = Regex("^[a-z0-9_-]{1,63}$")
    private val BARE_ENDING = Regex("^[a-z]{2,3}$")

    /**
     * True when [name] (already lowercased) can be stored as a rule: a DNS name of
     * ASCII labels (1–63 characters, at most 253 in total), or a bare country ending such
     * as "cn". Names that can never match a DNS lookup — spaces, symbols, non-ASCII
     * letters, empty labels — are refused, so a backup file cannot fill the list with junk.
     */
    fun isValidRuleName(name: String): Boolean {
        if (name.length > 253) return false
        if (!name.contains('.')) return BARE_ENDING.matches(name)
        return name.split('.').all { RULE_LABEL.matches(it) }
    }

    /** The domain followed by every parent: a.b.com gives [a.b.com, b.com, com]. */
    fun candidates(domain: String): List<String> {
        val out = ArrayList<String>()
        var d = domain
        while (true) {
            out.add(d)
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }
        return out
    }

    /**
     * The rule that blocks [domain], or null. An exact match wins; otherwise the closest
     * parent that is a user or schedule rule (blocklists never match parents).
     */
    fun findMatch(domain: String, rules: List<FirewallRule>): FirewallRule? {
        val byDomain = rules.filter { it.isBlocked }.associateBy { it.domain }
        byDomain[domain]?.let { return it }
        return candidates(domain).drop(1)
            .firstNotNullOfOrNull { parent -> byDomain[parent]?.takeUnless { isBlocklist(it) } }
    }

    fun isBlocklist(rule: FirewallRule): Boolean = rule.source.startsWith("BLOCKLIST")

    // ── Always allow list ───────────────────────────────────────────────────

    const val SOURCE_ALLOW = "ALLOW"

    fun isAllowRule(rule: FirewallRule): Boolean = rule.source == SOURCE_ALLOW

    /**
     * The rule that decides [domain]: an allow rule, a blocking rule, or null (not blocked).
     * An allowed name covers every subdomain, even ones a blocklist names exactly
     * (lists such as StevenBlack name "www.doubleclick.net", "ad.doubleclick.net" and more).
     * Your own or schedule rule on a closer name still wins. Same order as [isBlocked].
     */
    fun decide(domain: String, rules: List<FirewallRule>): FirewallRule? {
        val byDomain = rules.associateBy { it.domain }
        var exactList: FirewallRule? = null
        candidates(domain).forEachIndexed { index, name ->
            val rule = byDomain[name] ?: return@forEachIndexed
            if (isAllowRule(rule)) return rule
            if (!rule.isBlocked) return@forEachIndexed
            if (isBlocklist(rule)) { if (index == 0) exactList = rule }
            else return exactList ?: rule
        }
        return exactList
    }

    /**
     * The firewall's decision from its in-memory sets. Walks the name and every parent: the
     * closest allow entry or own/schedule rule decides. If neither is found, a blocklist
     * blocks only its exact name.
     */
    fun isBlocked(domain: String, user: Set<String>, allow: Set<String>, blocklist: Set<String>): Boolean {
        val listed = domain in blocklist
        var name = domain
        while (true) {
            if (name in allow) return false
            if (name in user) return true
            val dot = name.indexOf('.')
            if (dot < 0) return listed
            name = name.substring(dot + 1)
        }
    }

    /** True when the closest rule for [domain] is on the Always allow list. */
    fun isAllowed(domain: String, user: Set<String>, allow: Set<String>): Boolean {
        var name = domain
        while (true) {
            if (name in allow) return true
            if (name in user) return false
            val dot = name.indexOf('.')
            if (dot < 0) return false
            name = name.substring(dot + 1)
        }
    }
}
