package com.sentinel.core.rules

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sentinel.core.logs.ChangeAction
import com.sentinel.core.logs.ChangeHistory
import com.sentinel.core.logs.ChangeValue
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning

/**
 * Block or always-allow one website from anywhere outside the Protect tab (today: the Activity
 * details sheet), with an exact Undo.
 *
 * A new rule replaces any rule stored for the same name (one row per name), so each action
 * remembers the row it replaced and [undo] puts that row back — not just "delete what was added".
 * The caller checks App Lock's view-only mode first (that lives in the UI layer).
 */
object RuleActions {

    /** What to undo: the name, which kind of rule was added, and the row it replaced (or null). */
    data class Undo(val domain: String, val added: String, val previous: FirewallRule?)

    sealed interface Result {
        data class Done(val undo: Undo) : Result
        data object Invalid : Result
        data class AllowListFull(val max: Int) : Result
        data object Failed : Result
    }

    const val ADDED_BLOCK = "USER"
    const val ADDED_ALLOW = DomainCheck.SOURCE_ALLOW
    /** Same cap as the Always allow card in the Protect tab. */
    const val MAX_ALLOWED = 500

    /**
     * The name a block really adds — the same cleaning as the Protect tab, so blocking
     * "www.tumblr.com" blocks "tumblr.com" and everything under it. Null when not usable.
     */
    fun blockTarget(domain: String): String? {
        val clean = domain.trim().lowercase().removePrefix("*.").removePrefix("www.")
        return clean.takeUnless { it.isBlank() || it.contains(' ') || it.contains('*') || !it.contains('.') }
    }

    /** The name an always-allow really adds — the same cleaning as the Protect tab. */
    fun allowTarget(domain: String): String? =
        DomainCheck.normalize(domain)?.removePrefix("www.")?.takeIf { it.contains('.') }

    suspend fun block(context: Context, domain: String): Result {
        val name = blockTarget(domain) ?: return Result.Invalid
        return try {
            val engine = RuleEngine.getInstance(context)
            val previous = engine.db.ruleDao().getRuleForDomain(name)
            engine.addBlockRule(name, -1, "", ADDED_BLOCK)
            ChangeHistory.record(context, ChangeAction.DOMAIN_BLOCKED, name,
                before = ChangeValue.ALLOWED, after = ChangeValue.BLOCKED)
            notifyVpn(context)
            Result.Done(Undo(name, ADDED_BLOCK, previous))
        } catch (e: Exception) {
            android.util.Log.w("RuleActions", "block failed: ${e.javaClass.simpleName}")
            Result.Failed
        }
    }

    suspend fun allow(context: Context, domain: String): Result {
        val name = allowTarget(domain) ?: return Result.Invalid
        return try {
            val engine = RuleEngine.getInstance(context)
            val dao = engine.db.ruleDao()
            val allowed = dao.getAllowRules()
            if (allowed.none { it.domain == name } && allowed.size >= MAX_ALLOWED) return Result.AllowListFull(MAX_ALLOWED)
            val previous = dao.getRuleForDomain(name)
            engine.addAllowRule(name)
            ChangeHistory.record(context, ChangeAction.DOMAIN_ALWAYS_ALLOWED, name,
                before = ChangeValue.BLOCKED, after = ChangeValue.ALLOWED)
            notifyVpn(context)
            Result.Done(Undo(name, ADDED_ALLOW, previous))
        } catch (e: Exception) {
            android.util.Log.w("RuleActions", "allow failed: ${e.javaClass.simpleName}")
            Result.Failed
        }
    }

    /** Takes the added rule away and puts back exactly the row it replaced. */
    suspend fun undo(context: Context, undo: Undo): Boolean = try {
        val engine = RuleEngine.getInstance(context)
        if (undo.added == ADDED_ALLOW) {
            // removeAllowRule already restores a blocklist entry the allow replaced (while that
            // list is still on) — re-inserting it here could revive a list switched off since.
            engine.removeAllowRule(undo.domain)
            undo.previous?.takeUnless { DomainCheck.isBlocklist(it) }?.let { engine.db.ruleDao().insertRule(it) }
            ChangeHistory.record(context, ChangeAction.DOMAIN_ALLOW_REMOVED, undo.domain,
                before = ChangeValue.ALLOWED, after = ChangeValue.BLOCKED)
        } else {
            engine.removeBlockRule(undo.domain)
            undo.previous?.let { engine.db.ruleDao().insertRule(it) }
            ChangeHistory.record(context, ChangeAction.DOMAIN_UNBLOCKED, undo.domain,
                before = ChangeValue.BLOCKED, after = ChangeValue.ALLOWED)
        }
        // The in-memory sets were only partly updated above; rebuild them from the database.
        engine.reloadCache()
        engine.reloadBlocklist()
        notifyVpn(context)
        true
    } catch (e: Exception) {
        android.util.Log.w("RuleActions", "undo failed: ${e.javaClass.simpleName}")
        false
    }

    /** Tells a running firewall to reload its rules — same as the Protect tab does. */
    private fun notifyVpn(context: Context) {
        if (!context.vpnRunning()) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_RELOAD_RULES
            })
        } catch (_: Exception) { }
    }
}
