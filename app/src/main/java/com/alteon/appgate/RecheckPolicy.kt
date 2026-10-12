package com.alteon.appgate

import java.util.Calendar

/**
 * When GateService next needs to look at the app in front. Exists because a fixed 30 s recheck ran
 * all day, allowed hours included, and still blocked up to 30 s late. Opening an app is caught by
 * Android's own events; the only thing a timer is for is time running out while an app is already
 * open, and that happens at known moments: the allowed window closing, a granted override ending,
 * or a Settings pass ending.
 */
object RecheckPolicy {

    /**
     * The next moment the allow/block answer for the app in front could change on its own, or null
     * if it can't (an app AppGate never blocks, or one that is blocked already). Rule changes, from
     * the tablet or the phone, are handled separately: GateService rechecks as soon as one happens.
     */
    fun nextCheckAt(
        isVideoApp: Boolean,
        isSettings: Boolean,
        schedule: Schedule,
        overrideUntil: Long,
        settingsUntil: Long,
        now: Calendar,
    ): Long? {
        val t = now.timeInMillis
        val candidates = when {
            isSettings -> listOf(settingsUntil)
            isVideoApp -> listOf(overrideUntil, schedule.currentClosing(now)?.timeInMillis ?: 0L)
            else -> emptyList()
        }
        return candidates.filter { it > t }.minOrNull()
    }
}
