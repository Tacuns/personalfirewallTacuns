package com.sentinel.core.logs

import org.junit.Assert.assertEquals
import org.junit.Test

class LogReasonTest {

    @Test fun blockedLabelsMapToTheirReason() {
        assertEquals(LogReason.AD_LIST, LogReason.of("BLOCKED", "AD/TRACKER"))
        assertEquals(LogReason.USER_RULE, LogReason.of("BLOCKED", "USER BLOCK"))
        assertEquals(LogReason.SCHEDULE, LogReason.of("BLOCKED", "SCHEDULE"))
        assertEquals(LogReason.LOOKALIKE, LogReason.of("BLOCKED", "LOOK-ALIKE"))
    }

    @Test fun blockedWithoutLabelIsAnAppBlock() {
        // Same rule as the row badge (LogLabels.displayLabel).
        assertEquals(LogReason.APP_BLOCKED, LogReason.of("BLOCKED", ""))
    }

    @Test fun watchOnlyIsLoggedAsAllowedWithWouldBlock() {
        assertEquals(LogReason.WATCH_ONLY, LogReason.of("ALLOWED", "WOULD BLOCK"))
    }

    @Test fun allowedIgnoresAnyOtherLabel() {
        assertEquals(LogReason.ALLOWED, LogReason.of("ALLOWED", ""))
        // A label only explains a block; an allowed row never shows it, so neither does the sheet.
        assertEquals(LogReason.ALLOWED, LogReason.of("ALLOWED", "AD/TRACKER"))
    }

    @Test fun unknownBlockLabelFallsBackWithoutGuessing() {
        assertEquals(LogReason.BLOCKED_OTHER, LogReason.of("BLOCKED", "SOMETHING NEW"))
    }

    @Test fun allowedCountIsTotalMinusBlocked() {
        assertEquals(3, DestinationSummary(total = 5, blocked = 2, apps = 1, firstMs = 1L).allowed)
        assertEquals(0, DestinationSummary(total = 0, blocked = 0, apps = 0, firstMs = null).allowed)
    }
}
