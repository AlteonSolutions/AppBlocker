package com.alteon.appgate

import com.alteon.appgate.UpdateCheck.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {

    @Test fun readsTheVersionFromGithubsRedirect() {
        assertEquals("0.4.4", UpdateCheck.versionFromTagUrl("https://github.com/AlteonSolutions/AppBlocker/releases/tag/v0.4.4"))
    }

    @Test fun ignoresAnythingButAReleaseTag() {
        assertNull(UpdateCheck.versionFromTagUrl(null))
        assertNull(UpdateCheck.versionFromTagUrl("https://github.com/login"))
        assertNull(UpdateCheck.versionFromTagUrl("https://github.com/AlteonSolutions/AppBlocker/releases"))
    }

    @Test fun comparesNumericallyNotAlphabetically() {
        assertEquals(1, Integer.signum(UpdateCheck.compare("0.10.0", "0.9.9")!!))
        assertEquals(0, UpdateCheck.compare("1.0", "1.0.0"))
        assertNull(UpdateCheck.compare("1.x", "1.0"))
    }

    @Test fun newerReleaseIsAnUpdate() {
        assertEquals(State.UPDATE_AVAILABLE, UpdateCheck.state("0.4.4", "0.5.0"))
    }

    @Test fun sameOrOlderReleaseIsUpToDate() {
        assertEquals(State.UP_TO_DATE, UpdateCheck.state("0.4.4", "0.4.4"))
        assertEquals(State.UP_TO_DATE, UpdateCheck.state("0.5.0", "0.4.4"))
    }

    @Test fun unknownWhenTheCheckFailedOrTheInstalledVersionIsOdd() {
        assertEquals(State.UNKNOWN, UpdateCheck.state("0.4.4", null))
        assertEquals(State.UNKNOWN, UpdateCheck.state("?", "0.4.4"))
    }
}
