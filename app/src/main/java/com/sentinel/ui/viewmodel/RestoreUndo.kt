package com.sentinel.ui.viewmodel

import android.content.Context
import com.sentinel.core.rules.AppPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What the most recent Restore changed, so one tap can put it back.
 *
 * Only the changes are kept, not a full copy of every setting:
 *   - Restore ADDS domains, so undo removes exactly those domains.
 *   - Restore OVERWRITES app rules, the schedule, blocklist switches and how often each list
 *     updates, so undo writes the previous values back.
 *   - Restored activity-log entries are history and are left alone.
 *
 * One undo is kept — the newest restore — as a small JSON file in app-private storage.
 */
data class RestoreUndo(
    val addedDomains:       List<String>,
    val previousApps:       List<AppPolicy>,
    val previousSchedule:   AppControlViewModel.ScheduleState?,
    val previousBlocklists: Map<String, Boolean>,
    val previousIntervals:  Map<String, Int> = emptyMap()   // list key -> hours, see BlocklistSchedule
) {
    val isEmpty: Boolean
        get() = addedDomains.isEmpty() && previousApps.isEmpty() &&
                previousSchedule == null && previousBlocklists.isEmpty() && previousIntervals.isEmpty()

    companion object {
        private fun file(context: Context) = File(context.filesDir, "last_restore_undo.json")

        fun exists(context: Context): Boolean = file(context).exists()

        fun clear(context: Context) { file(context).delete() }

        fun save(context: Context, undo: RestoreUndo) {
            val json = JSONObject().apply {
                put("domains", JSONArray(undo.addedDomains))
                put("apps", JSONArray().apply {
                    undo.previousApps.forEach { p ->
                        put(JSONObject().put("uid", p.uid).put("package", p.packageName)
                            .put("wifi", p.wifiBlocked).put("cell", p.cellBlocked))
                    }
                })
                undo.previousSchedule?.let { s ->
                    put("schedule", JSONObject().put("enabled", s.enabled).put("category", s.category)
                        .put("startHour", s.startHour).put("startMin", s.startMin)
                        .put("endHour", s.endHour).put("endMin", s.endMin).put("days", s.days))
                }
                put("blocklists", JSONObject(undo.previousBlocklists))
                put("intervals", JSONObject(undo.previousIntervals))
            }
            // Write to a temp file, then rename over the real one: a crash mid-write can
            // never leave a half-written undo behind. If the rename fails, the OLD undo is
            // deleted too — otherwise Undo would reverse an earlier restore, not this one.
            val tmp    = File(context.filesDir, "last_restore_undo.tmp")
            val target = file(context)
            tmp.writeText(json.toString())
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.delete()
                error("Could not save the undo record")
            }
        }

        /** Null when there is nothing to undo or the file cannot be read. */
        fun load(context: Context): RestoreUndo? = try {
            val root    = JSONObject(file(context).readText())
            val domains = root.optJSONArray("domains") ?: JSONArray()
            val apps    = root.optJSONArray("apps") ?: JSONArray()
            val lists   = root.optJSONObject("blocklists") ?: JSONObject()
            val every   = root.optJSONObject("intervals") ?: JSONObject()   // absent in older undo files
            RestoreUndo(
                addedDomains = List(domains.length()) { domains.getString(it) },
                previousApps = List(apps.length()) { i ->
                    val o = apps.getJSONObject(i)
                    AppPolicy(o.getInt("uid"), o.getString("package"), o.getBoolean("wifi"), o.getBoolean("cell"))
                },
                previousSchedule = root.optJSONObject("schedule")?.let { s ->
                    AppControlViewModel.ScheduleState(
                        enabled   = s.getBoolean("enabled"),
                        category  = s.getString("category"),
                        startHour = s.getInt("startHour"),
                        startMin  = s.getInt("startMin"),
                        endHour   = s.getInt("endHour"),
                        endMin    = s.getInt("endMin"),
                        days      = s.getString("days")
                    )
                },
                previousBlocklists = lists.keys().asSequence().associateWith { lists.getBoolean(it) },
                previousIntervals  = every.keys().asSequence().associateWith { every.getInt(it) }
            )
        } catch (e: Exception) {
            null
        }
    }
}
