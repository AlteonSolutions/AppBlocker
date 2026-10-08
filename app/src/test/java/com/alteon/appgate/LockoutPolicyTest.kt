package com.alteon.appgate

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutPolicyTest {
    @Test fun freeAttemptsHaveNoLockout() {
        for (n in 0 until LockoutPolicy.FREE_ATTEMPTS) assertEquals(0L, LockoutPolicy.lockoutMillis(n))
    }

    @Test fun lockoutEscalates() {
        assertEquals(60_000L, LockoutPolicy.lockoutMillis(5))
        assertEquals(300_000L, LockoutPolicy.lockoutMillis(6))
        assertEquals(900_000L, LockoutPolicy.lockoutMillis(7))
        assertEquals(900_000L, LockoutPolicy.lockoutMillis(20))
    }
}
