package com.sentinel.core.logs

/**
 * How long the activity history is kept.
 *
 * Before this existed the table was capped at 500 rows, which on a busy phone is a
 * couple of hours. Everything that reads the history — the weekly report, the security
 * score, the per-app risk signals, "blocked N times recently" — was quietly working off
 * that tiny window. The user now chooses the window instead, and [OFF] really means
 * nothing is written at all.
 */
object LogRetention {

    const val OFF      = 0
    const val HOURS_6  = 6
    const val HOURS_24 = 24
    const val DAYS_7   = 24 * 7
    const val DAYS_30  = 24 * 30

    const val DEFAULT_HOURS = DAYS_7

    /** Offered in Settings, in this order. */
    val CHOICES = listOf(OFF, HOURS_6, HOURS_24, DAYS_7, DAYS_30)

    /**
     * A hard stop on row count regardless of the chosen window, so a very busy phone
     * cannot grow the database without limit between trims. At roughly 200 bytes a row
     * this keeps the file around 10 MB in the worst case.
     */
    const val MAX_ROWS = 50_000

    /** Anything not offered in [CHOICES] — a hand-edited or corrupted file — falls back to the default. */
    fun sanitize(hours: Int): Int = if (hours in CHOICES) hours else DEFAULT_HOURS

    /**
     * The oldest timestamp still worth keeping. Rows strictly older than this are deleted.
     * Guarded against overflow so a silly window can never wrap into a positive cutoff and
     * delete everything.
     */
    fun cutoffMs(hours: Int, nowMs: Long): Long {
        val span = hours.toLong() * 3_600_000L
        val cutoff = nowMs - span
        return if (cutoff < 0L) 0L else cutoff
    }
}

/**
 * Deletes what the chosen window no longer covers.
 *
 * Lives apart from [LogManager] because two different processes need it: the VPN process
 * trims as it writes, and [com.sentinel.core.logs.LogRetentionWorker] trims on a timer in
 * the app process. Returns how many rows went, so callers can report a real number.
 */
object LogTrimmer {

    suspend fun trim(
        dao: PacketLogDao,
        retentionHours: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        // Logging off is not "keep zero hours" — it means the history should not exist.
        if (retentionHours == LogRetention.OFF) return dao.deleteAll()

        var removed = dao.deleteOlderThan(LogRetention.cutoffMs(retentionHours, nowMs))
        removed += dao.deleteExcess(LogRetention.MAX_ROWS)
        return removed
    }
}
