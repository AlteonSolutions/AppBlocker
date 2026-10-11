package com.alteon.appgate

import com.alteon.appgate.RemoteProtocol.Command
import com.alteon.appgate.RemoteProtocol.Incoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {

    private val pairing = RemoteProtocol.parseCode(RemoteProtocol.newPairingCode())!!
    private val other = RemoteProtocol.parseCode(RemoteProtocol.newPairingCode())!!
    private val now = 1_800_000_000_000L

    private fun decode(
        message: String,
        at: Long = now,
        seen: List<String> = emptyList(),
        pending: String? = null,
        pendingAt: Long = 0L,
        p: RemoteProtocol.Pairing = pairing,
    ) = RemoteProtocol.decodeIncoming(p, message, at, seen, pending, pendingAt)

    // ---- Pairing codes ----

    @Test fun codeRoundTripsThroughFormatting() {
        val code = RemoteProtocol.newPairingCode()
        assertEquals(code, RemoteProtocol.parseCode(code)!!.code)
    }

    @Test fun typedCodeIgnoresCaseSpacesAndLookalikeDigits() {
        val code = pairing.code
        val sloppy = code.replace('O', '0').replace('I', '1').lowercase().replace("-", " ")
        assertEquals(code, RemoteProtocol.parseCode(sloppy)!!.code)
    }

    @Test fun incompleteCodeIsRejected() {
        assertNull(RemoteProtocol.parseCode(pairing.code.dropLast(2)))
        assertNull(RemoteProtocol.parseCode("hello"))
    }

    @Test fun topicsAreStableDistinctAndPerPairing() {
        val again = RemoteProtocol.parseCode(pairing.code)!!
        assertEquals(pairing.commandTopic, again.commandTopic)
        assertEquals(3, setOf(pairing.commandTopic, pairing.requestTopic, pairing.statusTopic).size)
        assertNotEquals(pairing.commandTopic, other.commandTopic)
        assertTrue(pairing.commandTopic.matches(Regex("[a-z0-9-]+")))
    }

    // ---- Signed commands ----

    @Test fun everyCommandRoundTrips() {
        val commands = listOf(
            Command.Allow(15), Command.Allow(60), Command.EndAllow, Command.LockSettings, Command.SendStatus,
            Command.SetWindow(weekend = true, window = TimeWindow(true, 9 * 60, 11 * 60)),
            Command.SetWindow(weekend = false, window = TimeWindow(false, 0, 60)),
        )
        for (c in commands) {
            val result = decode(RemoteProtocol.encodeCommand(pairing, c, now))
            assertEquals(c, (result as Incoming.Signed).command)
        }
    }

    @Test fun tamperedCommandIsRejected() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.Allow(15), now)
        assertTrue(decode(msg.replace("|allow|15|", "|allow|60|")) is Incoming.Rejected)
    }

    @Test fun commandForAnotherTabletIsRejected() {
        val msg = RemoteProtocol.encodeCommand(other, Command.Allow(60), now)
        assertTrue(decode(msg) is Incoming.Rejected)
    }

    @Test fun staleCommandIsRejected() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.Allow(60), now - RemoteProtocol.MAX_AGE_MS - 1)
        assertEquals(Incoming.Rejected("stale"), decode(msg))
    }

    @Test fun slightlyFastPhoneClockIsAccepted() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.EndAllow, now + 60_000L)
        assertTrue(decode(msg) is Incoming.Signed)
    }

    @Test fun replayedCommandIsRejected() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.Allow(60), now, nonce = "abc")
        val first = decode(msg) as Incoming.Signed
        val seen = RemoteProtocol.rememberNonce(emptyList(), first.nonce)
        assertEquals(Incoming.Rejected("replayed"), decode(msg, seen = seen))
    }

    @Test fun allowOnlyOffersTheButtonChoices() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.Allow(999), now)
        assertTrue(decode(msg) is Incoming.Rejected)
    }

    @Test fun invertedWindowIsRejectedEvenWhenSigned() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.SetWindow(false, TimeWindow(true, 18 * 60, 15 * 60)), now)
        assertTrue(decode(msg) is Incoming.Rejected)
    }

    @Test fun nonceMemoryIsCappedNewestFirst() {
        var seen = emptyList<String>()
        repeat(RemoteProtocol.NONCE_MEMORY + 5) { seen = RemoteProtocol.rememberNonce(seen, "n$it") }
        assertEquals(RemoteProtocol.NONCE_MEMORY, seen.size)
        assertEquals("n${RemoteProtocol.NONCE_MEMORY + 4}", seen.first())
    }

    // ---- Approvals from notification buttons ----

    @Test fun approvalForTheOpenRequestIsAccepted() {
        val result = decode(RemoteProtocol.approvalBody("req1", 30), pending = "req1", pendingAt = now - 60_000L)
        assertEquals(Incoming.Approval("req1", 30), result)
    }

    @Test fun approvalForAnOldRequestIsRejected() {
        val result = decode(RemoteProtocol.approvalBody("old", 30), pending = "req1", pendingAt = now)
        assertTrue(result is Incoming.Rejected)
    }

    @Test fun approvalWithNoOpenRequestIsRejected() {
        assertTrue(decode(RemoteProtocol.approvalBody("req1", 30)) is Incoming.Rejected)
    }

    @Test fun approvalAfterTheRequestExpiredIsRejected() {
        val result = decode(
            RemoteProtocol.approvalBody("req1", 30),
            pending = "req1", pendingAt = now - RemoteProtocol.MAX_AGE_MS - 1,
        )
        assertEquals(Incoming.Rejected("request expired"), result)
    }

    @Test fun approvalForMinutesNotOfferedIsRejected() {
        val result = decode(RemoteProtocol.approvalBody("req1", 240), pending = "req1", pendingAt = now)
        assertTrue(result is Incoming.Rejected)
    }

    @Test fun requestNotificationButtonsPostApprovalsForThisRequest() {
        val json = RemoteProtocol.requestNotificationJson(pairing, "req1", "Emma's \"tab\"", "msg") { "$it min" }
        for (m in RemoteProtocol.ALLOW_CHOICES) {
            assertTrue(json.contains(RemoteProtocol.json(RemoteProtocol.approvalBody("req1", m))))
        }
        assertTrue(json.contains("\"topic\":\"${pairing.requestTopic}\""))
        assertTrue(json.contains("${RemoteProtocol.SERVER}/${pairing.commandTopic}"))
        assertTrue(json.contains("Emma's \\\"tab\\\""))
    }

    // ---- Status ----

    private val status = RemoteProtocol.Status(
        tabletName = "Emma|s tablet",
        version = "0.3.0",
        overrideUntil = now + 900_000L,
        schedule = Schedule(TimeWindow(true, 15 * 60, 18 * 60), TimeWindow(false, 600, 720)),
        videoAppCount = 4,
        sentAt = now,
    )

    @Test fun statusRoundTrips() {
        val decoded = RemoteProtocol.decodeStatus(pairing, RemoteProtocol.encodeStatus(pairing, status))
        assertEquals(status.copy(tabletName = "Emma/s tablet"), decoded)
    }

    @Test fun tamperedOrForeignStatusIsIgnored() {
        val msg = RemoteProtocol.encodeStatus(pairing, status)
        assertNull(RemoteProtocol.decodeStatus(pairing, msg.replace("|4|", "|9|")))
        assertNull(RemoteProtocol.decodeStatus(other, msg))
    }

    // ---- ntfy JSON lines ----

    @Test fun readsFieldsFromAnNtfyLine() {
        val line = """{"id":"x1Y","time":1700000000,"event":"message","topic":"t",""" +
            """"message":"cmd1|a\"b\\c\né"}"""
        assertEquals("message", RemoteProtocol.jsonStringField(line, "event"))
        assertEquals("x1Y", RemoteProtocol.jsonStringField(line, "id"))
        assertEquals("cmd1|a\"b\\c\né", RemoteProtocol.jsonStringField(line, "message"))
        assertNull(RemoteProtocol.jsonStringField(line, "title"))
    }

    @Test fun keepaliveLinesHaveNoMessage() {
        val line = """{"id":"k","time":1700000000,"event":"keepalive","topic":"t"}"""
        assertEquals("keepalive", RemoteProtocol.jsonStringField(line, "event"))
        assertNull(RemoteProtocol.jsonStringField(line, "message"))
    }

    @Test fun signedMessageSurvivesJsonEscaping() {
        val msg = RemoteProtocol.encodeCommand(pairing, Command.Allow(30), now)
        val line = """{"event":"message","message":${RemoteProtocol.json(msg)}}"""
        val back = RemoteProtocol.jsonStringField(line, "message")
        assertNotNull(back)
        assertTrue(decode(back!!) is Incoming.Signed)
    }
}
