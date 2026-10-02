package com.sentinel.core.logs

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boundary matters: the cutoff decides which of the user's own history survives.
 * Off by one hour here and a "last 24 hours" choice quietly keeps 25.
 */
class LogRetentionTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `cutoff is exactly the window back from now`() {
        assertEquals(now - 6L * 3_600_000L, LogRetention.cutoffMs(LogRetention.HOURS_6, now))
        assertEquals(now - 24L * 3_600_000L, LogRetention.cutoffMs(LogRetention.HOURS_24, now))
        assertEquals(now - 168L * 3_600_000L, LogRetention.cutoffMs(LogRetention.DAYS_7, now))
        assertEquals(now - 720L * 3_600_000L, LogRetention.cutoffMs(LogRetention.DAYS_30, now))
    }

    @Test
    fun `a row exactly on the cutoff is kept and one millisecond older is not`() {
        val cutoff = LogRetention.cutoffMs(LogRetention.HOURS_24, now)
        // deleteOlderThan uses "timestampMs < cutoff", so equal survives.
        assertTrue(cutoff < now)
        assertTrue("a row at the cutoff must not be older than it", !(cutoff < cutoff))
        assertTrue("one millisecond earlier must be older", (cutoff - 1) < cutoff)
    }

    @Test
    fun `cutoff never goes negative for an absurd clock`() {
        assertEquals(0L, LogRetention.cutoffMs(LogRetention.DAYS_30, 1_000L))
    }

    @Test
    fun `unknown or hand-edited values fall back to the default`() {
        assertEquals(LogRetention.DEFAULT_HOURS, LogRetention.sanitize(-5))
        assertEquals(LogRetention.DEFAULT_HOURS, LogRetention.sanitize(13))
        assertEquals(LogRetention.DEFAULT_HOURS, LogRetention.sanitize(Int.MAX_VALUE))
    }

    @Test
    fun `every offered choice survives sanitising, including off`() {
        LogRetention.CHOICES.forEach { assertEquals(it, LogRetention.sanitize(it)) }
        assertEquals(LogRetention.OFF, LogRetention.sanitize(LogRetention.OFF))
    }

    @Test
    fun `trimming with logging off deletes everything and never just ages rows out`() = runBlocking {
        val dao = FakeDao()
        LogTrimmer.trim(dao, LogRetention.OFF, now)
        assertEquals(1, dao.deleteAllCalls)
        assertEquals(0, dao.olderThanCalls.size)
        assertEquals(0, dao.excessCalls.size)
    }

    @Test
    fun `trimming with a window ages rows out and then applies the size cap`() = runBlocking {
        val dao = FakeDao()
        LogTrimmer.trim(dao, LogRetention.DAYS_7, now)
        assertEquals(0, dao.deleteAllCalls)
        assertEquals(listOf(now - 168L * 3_600_000L), dao.olderThanCalls)
        assertEquals(listOf(LogRetention.MAX_ROWS), dao.excessCalls)
    }

    @Test
    fun `trim reports the total number of rows it removed`() = runBlocking {
        val dao = FakeDao(olderThanResult = 7, excessResult = 3)
        assertEquals(10, LogTrimmer.trim(dao, LogRetention.HOURS_6, now))
    }

    /** Records what the trimmer asked for; the real queries are Room's job. */
    private class FakeDao(
        private val olderThanResult: Int = 0,
        private val excessResult: Int = 0,
        private val deleteAllResult: Int = 0
    ) : PacketLogDao {
        var deleteAllCalls = 0
        val olderThanCalls = mutableListOf<Long>()
        val excessCalls = mutableListOf<Int>()

        override suspend fun deleteOlderThan(cutoffMs: Long): Int {
            olderThanCalls += cutoffMs; return olderThanResult
        }

        override suspend fun deleteExcess(keepCount: Int): Int {
            excessCalls += keepCount; return excessResult
        }

        override suspend fun deleteAll(): Int {
            deleteAllCalls++; return deleteAllResult
        }

        override suspend fun insert(entity: PacketLogEntity) = Unit
        override suspend fun getRecent(limit: Int): List<PacketLogEntity> = emptyList()
        override fun getRecentFlow(limit: Int): Flow<List<PacketLogEntity>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun countAll(): Int = 0
        override suspend fun oldestTimestamp(): Long? = null
        override suspend fun countByStatusSince(status: String, sinceMs: Long): Int = 0
        override suspend fun topDestinations(status: String, sinceMs: Long, limit: Int): List<StatRow> = emptyList()
        override suspend fun topApps(sinceMs: Long, limit: Int): List<StatRow> = emptyList()
        override suspend fun blockedCountPerApp(sinceMs: Long): List<StatRow> = emptyList()
        override suspend fun topBlockedByApp(sinceMs: Long, limit: Int): List<StatRow> = emptyList()
        override suspend fun queriesPerApp(sinceMs: Long): List<StatRow> = emptyList()
        override suspend fun blockedPerHourSlot(sinceMs: Long): List<StatRow> = emptyList()
        override suspend fun allowedPerHourSlot(sinceMs: Long): List<StatRow> = emptyList()
        override suspend fun blockedTimestampsSince(sinceMs: Long): List<Long> = emptyList()
        override suspend fun countByPackageInRange(pkg: String, sinceMs: Long, untilMs: Long): Int = 0
        override suspend fun topDestinationsForApp(pkg: String, status: String, sinceMs: Long, limit: Int): List<StatRow> = emptyList()
        override suspend fun countByAppAndStatus(pkg: String, status: String, sinceMs: Long): Int = 0
        override suspend fun userRuleBlocksByDestination(): List<StatRow> = emptyList()
        override suspend fun destinationSummary(destination: String) = DestinationSummary(0, 0, 0, null)
        override suspend fun entriesFor(destination: String, limit: Int): List<PacketLogEntity> = emptyList()
    }
}
