package com.sentinel.core.logs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PacketLogEntity)

    @Query("SELECT * FROM packet_logs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PacketLogEntity>

    // Room re-emits this Flow whenever packet_logs changes — including writes from the
    // VPN service process when enableMultiInstanceInvalidation() is active on the DB.
    @Query("SELECT * FROM packet_logs ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<PacketLogEntity>>

    // Keep only the most-recent keepCount rows; delete the rest. Returns the number removed.
    @Query("DELETE FROM packet_logs WHERE id NOT IN (SELECT id FROM packet_logs ORDER BY timestampMs DESC LIMIT :keepCount)")
    suspend fun deleteExcess(keepCount: Int): Int

    // Drops everything the chosen retention window no longer covers. Returns the number removed.
    @Query("DELETE FROM packet_logs WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM packet_logs")
    suspend fun deleteAll(): Int

    // Real figures for the retention card in Settings - never an estimate.
    @Query("SELECT COUNT(*) FROM packet_logs")
    suspend fun countAll(): Int

    @Query("SELECT MIN(timestampMs) FROM packet_logs")
    suspend fun oldestTimestamp(): Long?

    // ── Statistics queries ────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM packet_logs WHERE status = :status AND timestampMs >= :sinceMs")
    suspend fun countByStatusSince(status: String, sinceMs: Long): Int

    @Query("""SELECT destination AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = :status AND timestampMs >= :sinceMs
              GROUP BY destination ORDER BY count DESC LIMIT :limit""")
    suspend fun topDestinations(status: String, sinceMs: Long, limit: Int): List<StatRow>

    @Query("""SELECT appName AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE timestampMs >= :sinceMs
              GROUP BY appName ORDER BY count DESC LIMIT :limit""")
    suspend fun topApps(sinceMs: Long, limit: Int): List<StatRow>

    @Query("""SELECT packageName AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = 'BLOCKED' AND timestampMs >= :sinceMs
              GROUP BY packageName""")
    suspend fun blockedCountPerApp(sinceMs: Long): List<StatRow>

    // ── Weekly report helpers ─────────────────────────────────────────────────

    @Query("""SELECT appName AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = 'BLOCKED' AND timestampMs >= :sinceMs
              GROUP BY appName ORDER BY count DESC LIMIT :limit""")
    suspend fun topBlockedByApp(sinceMs: Long, limit: Int): List<StatRow>

    // ── Query rate — all requests per app in a given window (last 60 s) ─────────

    @Query("""SELECT packageName AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE timestampMs >= :sinceMs
              GROUP BY packageName""")
    suspend fun queriesPerApp(sinceMs: Long): List<StatRow>

    // ── Hourly timeline (last 24 h) — returns up to 24 rows, hour 0–23 ─────────

    @Query("""SELECT CAST((timestampMs / 3600000) AS TEXT) AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = 'BLOCKED' AND timestampMs >= :sinceMs
              GROUP BY CAST(timestampMs / 3600000 AS INTEGER)""")
    suspend fun blockedPerHourSlot(sinceMs: Long): List<StatRow>

    @Query("""SELECT CAST((timestampMs / 3600000) AS TEXT) AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = 'ALLOWED' AND timestampMs >= :sinceMs
              GROUP BY CAST(timestampMs / 3600000 AS INTEGER)""")
    suspend fun allowedPerHourSlot(sinceMs: Long): List<StatRow>

    // Raw times for the peak-hour stat; the table holds at most a few hundred rows.
    @Query("SELECT timestampMs FROM packet_logs WHERE status = 'BLOCKED' AND timestampMs >= :sinceMs")
    suspend fun blockedTimestampsSince(sinceMs: Long): List<Long>

    // ── Update behavior change — count all queries for one package in a window ─

    @Query("SELECT COUNT(*) FROM packet_logs WHERE packageName = :pkg AND timestampMs >= :sinceMs AND timestampMs < :untilMs")
    suspend fun countByPackageInRange(pkg: String, sinceMs: Long, untilMs: Long): Int

    // ── App Connection Profile — per-app domain breakdown ────────────────────

    @Query("""SELECT destination AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE packageName = :pkg AND status = :status AND timestampMs >= :sinceMs
              GROUP BY destination ORDER BY count DESC LIMIT :limit""")
    suspend fun topDestinationsForApp(pkg: String, status: String, sinceMs: Long, limit: Int): List<StatRow>

    @Query("SELECT COUNT(*) FROM packet_logs WHERE packageName = :pkg AND status = :status AND timestampMs >= :sinceMs")
    suspend fun countByAppAndStatus(pkg: String, status: String, sinceMs: Long): Int

    // ── Rule hit counts — blocks caused by the user's own rules, per website ─────

    @Query("""SELECT destination AS name, COUNT(*) AS count
              FROM packet_logs
              WHERE status = 'BLOCKED' AND threatLabel = 'USER BLOCK'
              GROUP BY destination""")
    suspend fun userRuleBlocksByDestination(): List<StatRow>

    // ── Activity details sheet — everything saved about one website ──────────────

    @Query("""SELECT COUNT(*) AS total,
                     COALESCE(SUM(CASE WHEN status = 'BLOCKED' THEN 1 ELSE 0 END), 0) AS blocked,
                     COUNT(DISTINCT appName) AS apps,
                     MIN(timestampMs) AS firstMs
              FROM packet_logs WHERE destination = :destination""")
    suspend fun destinationSummary(destination: String): DestinationSummary

    @Query("SELECT * FROM packet_logs WHERE destination = :destination ORDER BY timestampMs DESC, id DESC LIMIT :limit")
    suspend fun entriesFor(destination: String, limit: Int): List<PacketLogEntity>
}
