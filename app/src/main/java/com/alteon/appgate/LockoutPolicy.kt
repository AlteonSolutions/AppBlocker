package com.alteon.appgate

/** How long the PIN screen locks after a given number of consecutive wrong entries. */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5

    fun lockoutMillis(consecutiveFailures: Int): Long = when {
        consecutiveFailures < FREE_ATTEMPTS -> 0L
        consecutiveFailures == FREE_ATTEMPTS -> 60_000L
        consecutiveFailures == FREE_ATTEMPTS + 1 -> 5 * 60_000L
        else -> 15 * 60_000L
    }
}
