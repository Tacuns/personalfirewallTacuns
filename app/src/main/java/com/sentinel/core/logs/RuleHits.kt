package com.sentinel.core.logs

import com.sentinel.core.rules.DomainCheck

/**
 * How many recent blocks each of the user's own domain rules caused.
 *
 * Built from the activity log, which keeps only the most recent entries, so the numbers mean
 * "recently", not "ever". Each blocked website is credited to the rule the firewall used for
 * it: the exact domain first, otherwise its closest parent — the same order as RuleEngine.
 *
 * Pure function only, so the matching is unit-tested on the JVM.
 */
object RuleHits {

    /**
     * @param rules the user's domain rules, e.g. "tumblr.com"
     * @param rows  blocked websites with how often each was blocked
     */
    fun count(rules: Collection<String>, rows: List<StatRow>): Map<String, Int> {
        if (rules.isEmpty() || rows.isEmpty()) return emptyMap()
        val ruleSet = rules.toHashSet()
        val hits = HashMap<String, Int>()
        for (row in rows) {
            val rule = DomainCheck.candidates(row.name).firstOrNull { it in ruleSet } ?: continue
            hits[rule] = (hits[rule] ?: 0) + row.count
        }
        return hits
    }
}
