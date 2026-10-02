package com.sentinel.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Random

class DnsTxIdTest {

    private fun u(id: Short) = id.toInt() and 0xFFFF

    @Test fun idIsInRange() {
        val r = SecureRandom()
        repeat(5000) {
            val u = u(DnsTxId.next(r, emptySet()))
            assertTrue("$u out of range", u in 0..0xFFFF)
        }
    }

    @Test fun avoidsIdsInUse() {
        // A biased generator that would return 5 first; next() must skip it because it is in use.
        val queue = ArrayDeque(listOf(5, 5, 5, 9))
        val fake = object : Random() {
            override fun nextInt(bound: Int) = queue.removeFirst()
        }
        assertEquals(9.toShort(), DnsTxId.next(fake, setOf(5.toShort())))
    }

    @Test fun givesUpAfterManyCollisionsRatherThanHang() {
        // Every candidate collides; must return after the bounded retries, not loop forever.
        val fake = object : Random() { override fun nextInt(bound: Int) = 7 }
        assertEquals(7.toShort(), DnsTxId.next(fake, setOf(7.toShort())))
    }

    @Test fun notSequentialAndWellSpread() {
        // The old bug was 1,2,3,4… . Random IDs must not march upward and must be mostly distinct.
        val r = SecureRandom()
        val ids = (0 until 2000).map { u(DnsTxId.next(r, emptySet())) }
        val ascendingSteps = ids.zipWithNext().count { (a, b) -> b == a + 1 }
        assertTrue("too many sequential steps: $ascendingSteps", ascendingSteps < 50)
        assertTrue("too few distinct ids: ${ids.toSet().size}", ids.toSet().size > 1900)
        assertNotEquals(ids, ids.sorted())
    }
}
