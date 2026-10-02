package com.sentinel.ui.logs

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakAtDotsTest {

    @Test fun addsABreakChanceAfterEveryDotOnly() {
        assertEquals("android.​googleapis.​com", breakAtDots("android.googleapis.com"))
    }

    @Test fun removingTheBreaksGivesTheOriginalName() {
        val name = "beacons.gcp.gvt2.com"
        assertEquals(name, breakAtDots(name).replace("​", ""))
    }

    @Test fun nameWithoutDotsIsUnchanged() {
        assertEquals("localhost", breakAtDots("localhost"))
    }
}
