package com.alteon.appgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RecheckPolicyTest {

    // Weekdays 15:00 to 18:00; weekends closed.
    private val schedule = Schedule(TimeWindow(true, 15 * 60, 18 * 60), TimeWindow(false, 0, 0))

    // 2026-10-08 is a Thursday.
    private fun at(hour: Int, minute: Int = 0): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 8, hour, minute)
    }

    private fun video(now: Calendar, overrideUntil: Long = 0L) =
        RecheckPolicy.nextCheckAt(true, false, schedule, overrideUntil, 0L, now)

    @Test fun videoDuringWindowIsCheckedWhenTheWindowCloses() {
        assertEquals(at(18).timeInMillis, video(at(16)))
    }

    @Test fun grantEndingInsideTheWindowIsCheckedFirst() {
        assertEquals(at(16, 30).timeInMillis, video(at(16), overrideUntil = at(16, 30).timeInMillis))
    }

    @Test fun grantOutsideTheWindowIsCheckedWhenItEnds() {
        assertEquals(at(20, 15).timeInMillis, video(at(19, 45), overrideUntil = at(20, 15).timeInMillis))
    }

    @Test fun grantThatAlreadyEndedIsIgnored() {
        assertEquals(at(18).timeInMillis, video(at(16), overrideUntil = at(15).timeInMillis))
    }

    @Test fun blockedVideoNeedsNoTimer() {
        assertNull(video(at(19)))
    }

    @Test fun settingsIsCheckedWhenItsPassEnds() {
        val pass = at(16, 5).timeInMillis
        assertEquals(pass, RecheckPolicy.nextCheckAt(false, true, schedule, 0L, pass, at(16)))
    }

    @Test fun appsAppGateNeverBlocksNeedNoTimer() {
        assertNull(RecheckPolicy.nextCheckAt(false, false, schedule, at(17).timeInMillis, at(17).timeInMillis, at(16)))
    }

    @Test fun windowEndingAtMidnightClosesAtMidnight() {
        val late = Schedule(TimeWindow(true, 20 * 60, 24 * 60), TimeWindow(false, 0, 0))
        val next = RecheckPolicy.nextCheckAt(true, false, late, 0L, 0L, at(23))!!
        val midnight = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 9, 0, 0) }
        assertEquals(midnight.timeInMillis, next)
    }
}
