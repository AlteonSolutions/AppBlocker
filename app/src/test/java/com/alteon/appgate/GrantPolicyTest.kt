package com.alteon.appgate

import org.junit.Assert.assertEquals
import org.junit.Test

class GrantPolicyTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test fun oneHourLastsSixtyFiveMinutes() {
        assertEquals(now + 65 * minute, GrantPolicy.grantUntil(0L, 60, now))
    }

    @Test fun everyChoiceGetsTimeToPickAShow() {
        assertEquals(now + 20 * minute, GrantPolicy.grantUntil(0L, 15, now))
        assertEquals(now + 35 * minute, GrantPolicy.grantUntil(0L, 30, now))
    }

    @Test fun shorterGrantNeverCutsALongerOneShort() {
        val running = now + 50 * minute
        assertEquals(running, GrantPolicy.grantUntil(running, 15, now))
    }

    @Test fun expiredGrantIsReplaced() {
        assertEquals(now + 35 * minute, GrantPolicy.grantUntil(now - minute, 30, now))
    }
}
