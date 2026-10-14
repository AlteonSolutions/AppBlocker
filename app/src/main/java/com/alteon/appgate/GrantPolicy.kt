package com.alteon.appgate

/**
 * How long a grant of video time really lasts. A grant usually comes before the kids have picked a
 * show, so "1 hour" ended five minutes into the last episode they chose. Every grant gets a few
 * minutes on top for picking, while the buttons keep their round numbers.
 */
object GrantPolicy {

    /** Time to pick a show, added to every grant. */
    const val PICK_A_SHOW_MS = 5 * 60_000L

    /** When a grant of [minutes] made at [now] ends. Never shortens a grant already running longer. */
    fun grantUntil(currentUntil: Long, minutes: Int, now: Long): Long =
        maxOf(currentUntil, now + minutes * 60_000L + PICK_A_SHOW_MS)
}
