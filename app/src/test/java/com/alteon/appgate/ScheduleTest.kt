package com.alteon.appgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTest {

    private val weekday = TimeWindow(true, 15 * 60, 18 * 60)
    private val weekend = TimeWindow(true, 10 * 60, 12 * 60)
    private val schedule = Schedule(weekday, weekend)

    // 2026-10-08 is a Thursday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }

    @Test fun openInsideWeekdayWindow() = assertTrue(schedule.isOpen(at(8, 16)))
    @Test fun startIsInclusive() = assertTrue(schedule.isOpen(at(8, 15)))
    @Test fun endIsExclusive() = assertFalse(schedule.isOpen(at(8, 18)))
    @Test fun closedBeforeWindow() = assertFalse(schedule.isOpen(at(8, 14, 59)))
    @Test fun weekendUsesWeekendWindow() {
        assertTrue(schedule.isOpen(at(10, 11)))  // Saturday
        assertFalse(schedule.isOpen(at(10, 16)))
    }
    @Test fun disabledWindowNeverOpen() =
        assertFalse(schedule.copy(weekday = weekday.copy(enabled = false)).isOpen(at(8, 16)))
    @Test fun invertedWindowNeverOpen() =
        assertFalse(Schedule(TimeWindow(true, 18 * 60, 15 * 60), weekend).isOpen(at(8, 16)))

    @Test fun nextOpeningLaterToday() {
        val next = schedule.nextOpening(at(8, 9))!!
        assertEquals(8, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun nextOpeningAfterCloseIsTomorrow() {
        val next = schedule.nextOpening(at(8, 19))!!
        assertEquals(9, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun fridayNightRollsToSaturdayWindow() {
        val next = schedule.nextOpening(at(9, 19))!!
        assertEquals(10, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun disabledWeekendSkipsToMonday() {
        val next = schedule.copy(weekend = weekend.copy(enabled = false)).nextOpening(at(9, 19))!!
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun noUsableWindowGivesNull() {
        val off = TimeWindow(false, 0, 60)
        assertNull(Schedule(off, off).nextOpening(at(8, 9)))
    }
}
