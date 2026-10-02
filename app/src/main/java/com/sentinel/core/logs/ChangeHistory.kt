package com.sentinel.core.logs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Records real rule and setting changes so the user can see what changed and when.
 *
 * Recording is fire-and-forget on a background thread: a failure here must never
 * stop the change itself from being applied. Nothing is ever written to logcat that
 * could contain a visited domain.
 */
object ChangeHistory {

    private const val TAG = "ChangeHistory"

    /** Oldest rows are dropped past this; changes are rare, so this covers a long time. */
    const val MAX_ROWS = 500

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun record(
        context: Context,
        action: String,
        target: String,
        before: String = "",
        after: String = ""
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val dao = LogDatabase.getInstance(app).changeHistoryDao()
                dao.insert(
                    ChangeHistoryEntity(
                        action      = action,
                        target      = target,
                        beforeValue = before,
                        afterValue  = after
                    )
                )
                dao.deleteExcess(MAX_ROWS)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record a change: ${e.javaClass.simpleName}")
            }
        }
    }

    suspend fun clear(context: Context) {
        LogDatabase.getInstance(context.applicationContext).changeHistoryDao().deleteAll()
    }
}
