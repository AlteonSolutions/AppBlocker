package com.alteon.appgate

/**
 * How long a grant of video time really lasts. A 1-hour grant usually comes before the kids have
 * picked a show, so "1 hour" ended five minutes into the last episode they chose. Grants of an hour
 * or more get a few minutes on top for picking; shorter ones are exact, by the parent's choice. The
 * buttons keep their round numbers either way.
 */
object GrantPolicy {

    /** Time to pick a show, added to grants of at least [PICK_A_SHOW_FROM_MINUTES]. */
    const val PICK_A_SHOW_MS = 5 * 60_000L
    const val PICK_A_SHOW_FROM_MINUTES = 60

    /** When a grant of [minutes] made at [now] ends. Never shortens a grant already running longer. */
    fun grantUntil(currentUntil: Long, minutes: Int, now: Long): Long =
        maxOf(currentUntil, now + minutes * 60_000L + if (minutes >= PICK_A_SHOW_FROM_MINUTES) PICK_A_SHOW_MS else 0L)
}
