package com.sentinel.core.logs

import com.sentinel.core.rules.DomainCheck
import com.sentinel.core.rules.FirewallRule
import com.sentinel.core.schedule.ScheduleCategories

/**
 * The rule that decides a website **right now**, in the terms the Activity details sheet uses.
 *
 * The log entry records only the kind of reason (its label), not which list or rule. So the
 * sheet looks the website up in the current rules — the same decision "Check if blocked" uses
 * ([DomainCheck.decide]) — and says so honestly when today's rules no longer match the entry.
 */
sealed interface CurrentRule {
    /** On a blocklist; [listName] is the name the user sees (e.g. StevenBlack, or their own name). */
    data class OnList(val listName: String) : CurrentRule
    /** The user's own rule on this website or a parent website ([ruleDomain]). */
    data class OwnRule(val ruleDomain: String) : CurrentRule
    /** The user's country rule, stored as the bare ending, e.g. "cn". */
    data class Country(val tld: String) : CurrentRule
    /** A schedule rule; [category] is a [ScheduleCategories] key, or null when it can't be told. */
    data class Schedule(val category: String?) : CurrentRule
    /** The Always allow list decides it; [ruleDomain] is the allowed name (the site or a parent). */
    data class Allowed(val ruleDomain: String) : CurrentRule
    /** Nothing decides it now. */
    data object None : CurrentRule
}

/** Extra sentence after the first "why" line when today's rules differ from the entry. */
sealed interface WhyNote {
    data object None : WhyNote
    /** The entry was blocked; no rule blocks the website any more. */
    data object RuleGone : WhyNote
    /** The entry was blocked; a different kind of rule decides the website now. */
    data object Changed : WhyNote
    /** The entry was blocked; the website is on the Always allow list now. */
    data class AllowedSince(val ruleDomain: String) : WhyNote
}

/** [specific] = use the sentence for [CurrentRule] instead of the general one for the reason. */
data class WhyExplanation(val specific: Boolean, val note: WhyNote)

/** Whether the app of an entry is blocked right now, per network. Null when the app is unknown. */
data class AppBlockNow(val wifi: Boolean, val mobile: Boolean)

object LogWhy {

    /** Finds today's deciding rule for [domain]. [listNames] maps a blocklist key to its shown name. */
    fun currentRule(domain: String, rules: List<FirewallRule>, listNames: Map<String, String>): CurrentRule {
        val rule = DomainCheck.decide(domain, rules) ?: return CurrentRule.None
        return when {
            DomainCheck.isAllowRule(rule) -> CurrentRule.Allowed(rule.domain)
            // A list that is no longer known can't be named honestly.
            DomainCheck.isBlocklist(rule) -> listNames[rule.source]?.let { CurrentRule.OnList(it) } ?: CurrentRule.None
            rule.source == SOURCE_SCHEDULE -> CurrentRule.Schedule(
                ScheduleCategories.all.firstOrNull { rule.domain in ScheduleCategories.domainsFor(it) }
            )
            !rule.domain.contains('.') -> CurrentRule.Country(rule.domain)
            else -> CurrentRule.OwnRule(rule.domain)
        }
    }

    /**
     * How the sheet should explain an entry, given what it recorded and today's rule.
     * A specific name is shown only when today's rule is the same kind the entry recorded,
     * so the sheet never claims a list blocked something that a different rule blocked.
     */
    fun explain(reason: LogReason, now: CurrentRule): WhyExplanation = when (reason) {
        LogReason.AD_LIST   -> blockedExplanation(now, now is CurrentRule.OnList)
        LogReason.USER_RULE -> blockedExplanation(now, now is CurrentRule.OwnRule || now is CurrentRule.Country)
        LogReason.SCHEDULE  -> blockedExplanation(now,
            now is CurrentRule.Schedule && now.category != null,
            sameKind = now is CurrentRule.Schedule)
        // Watch-only only reports: name what would have blocked it, if something still would.
        LogReason.WATCH_ONLY -> WhyExplanation(
            specific = now is CurrentRule.OnList || now is CurrentRule.OwnRule ||
                now is CurrentRule.Country || (now is CurrentRule.Schedule && now.category != null),
            note = WhyNote.None
        )
        LogReason.ALLOWED -> WhyExplanation(specific = now is CurrentRule.Allowed, note = WhyNote.None)
        LogReason.LOOKALIKE, LogReason.APP_BLOCKED, LogReason.BLOCKED_OTHER ->
            WhyExplanation(specific = false, note = WhyNote.None)
    }

    private fun blockedExplanation(now: CurrentRule, specific: Boolean, sameKind: Boolean = specific) = when {
        specific -> WhyExplanation(true, WhyNote.None)
        sameKind -> WhyExplanation(false, WhyNote.None)          // e.g. schedule without a known group
        now is CurrentRule.Allowed -> WhyExplanation(false, WhyNote.AllowedSince(now.ruleDomain))
        now is CurrentRule.None -> WhyExplanation(false, WhyNote.RuleGone)
        else -> WhyExplanation(false, WhyNote.Changed)
    }

    /** Today's per-network block for the app with [packageName] (all its UID rows), or null. */
    fun appBlockNow(packageName: String, policies: List<Pair<String, Pair<Boolean, Boolean>>>): AppBlockNow? {
        if (packageName.isBlank()) return null
        val mine = policies.filter { it.first == packageName }
        return AppBlockNow(wifi = mine.any { it.second.first }, mobile = mine.any { it.second.second })
    }

    private const val SOURCE_SCHEDULE = "SCHEDULE"
}
