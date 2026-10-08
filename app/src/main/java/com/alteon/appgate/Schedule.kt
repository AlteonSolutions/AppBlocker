package com.alteon.appgate

import java.util.Calendar

/**
 * A daily allowed window, in minutes after midnight. Start is inclusive, end is exclusive.
 * Overnight windows (start after end) are not supported and count as closed.
 */
data class TimeWindow(val enabled: Boolean, val startMinute: Int, val endMinute: Int) {
    val isValid: Boolean get() = enabled && startMinute < endMinute

    fun contains(minuteOfDay: Int): Boolean =
        isValid && minuteOfDay >= startMinute && minuteOfDay < endMinute
}

/** One window for Monday to Friday and one for Saturday and Sunday. */
data class Schedule(val weekday: TimeWindow, val weekend: TimeWindow) {

    fun windowFor(dayOfWeek: Int): TimeWindow =
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) weekend else weekday

    fun isOpen(at: Calendar): Boolean =
        windowFor(at.get(Calendar.DAY_OF_WEEK)).contains(minuteOfDay(at))

    /** The next moment a window opens strictly after [from], or null if no window is usable. */
    fun nextOpening(from: Calendar): Calendar? {
        for (offset in 0..7) {
            val day = from.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, offset)
            val window = windowFor(day.get(Calendar.DAY_OF_WEEK))
            if (!window.isValid) continue
            day.set(Calendar.HOUR_OF_DAY, window.startMinute / 60)
            day.set(Calendar.MINUTE, window.startMinute % 60)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)
            if (day.after(from)) return day
        }
        return null
    }

    companion object {
        fun minuteOfDay(c: Calendar): Int =
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

        val DEFAULT = Schedule(
            weekday = TimeWindow(true, 15 * 60, 18 * 60),
            weekend = TimeWindow(true, 15 * 60, 18 * 60),
        )
    }
}
