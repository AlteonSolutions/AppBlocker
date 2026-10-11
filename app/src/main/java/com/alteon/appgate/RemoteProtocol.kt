package com.alteon.appgate

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The phone-to-tablet remote control protocol, carried over ntfy (a free publish/subscribe relay).
 *
 * Exists so a parent can grant video time or change hours from their phone instead of walking over
 * and typing the PIN, and so a kid can ask from the blocked screen. Framework-free so every rule
 * that decides whether a message is obeyed is unit tested.
 *
 * Anyone who knows an ntfy topic name can read and post to it, so topic names alone are not trusted:
 * - Commands from the parent's phone are signed with an HMAC key derived from the pairing code, carry
 *   a timestamp and a nonce, and are rejected when stale or replayed. ntfy caches messages for 12 hours
 *   and replays them on reconnect, which is what the freshness window and nonce list stop.
 * - Approvals tapped in the ntfy notification are not signed (the ntfy app sends a fixed body). They
 *   are accepted only if they name the one pending request's random nonce, which only the request
 *   notification carried, and only while that request is fresh.
 */
object RemoteProtocol {

    const val SERVER = "https://ntfy.sh"

    /** The grant buttons. Android notifications allow at most three action buttons. */
    val ALLOW_CHOICES = listOf(15, 30, 60)

    /** Accepted clock difference between phone and tablet, and how long a request can be answered. */
    const val MAX_AGE_MS = 10 * 60_000L

    /** Recent command nonces remembered per tablet; far more than ntfy could replay in MAX_AGE_MS. */
    const val NONCE_MEMORY = 64

    private const val CMD = "cmd1"
    private const val STATUS = "status1"
    private const val APPROVE = "approve"
    private const val SEP = '|'

    // ---- Pairing ----

    /** 16 random bytes: 128 bits, as 26 base32 characters. */
    fun newPairingCode(random: SecureRandom = SecureRandom()): String =
        formatCode(Base32.encode(ByteArray(16).also { random.nextBytes(it) }))

    /** Groups of four for reading aloud and typing: ABCD-EFGH-... */
    fun formatCode(raw: String): String = raw.chunked(4).joinToString("-")

    /**
     * Parses what a parent typed. Ignores case, spaces and dashes, and reads the digits people confuse
     * with letters (0, 1, 8) as the letters base32 uses. Null if it isn't a complete code.
     */
    fun parseCode(typed: String): Pairing? {
        val cleaned = typed.uppercase()
            .filter { it.isLetterOrDigit() }
            .map { when (it) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; else -> it } }
            .joinToString("")
        if (cleaned.length != 26) return null
        val bytes = Base32.decode(cleaned) ?: return null
        return if (bytes.size == 16) Pairing(bytes) else null
    }

    class Pairing(private val secret: ByteArray) {
        val code: String get() = formatCode(Base32.encode(secret))
        internal val key: ByteArray = hmac(secret, "appgate key")
        /** Phone to tablet: signed commands, and approvals from notification buttons. */
        val commandTopic: String = topic("commands")
        /** Tablet to the parent's ntfy app: requests for more time. */
        val requestTopic: String = topic("requests")
        /** Tablet to the parent's phone: current settings. */
        val statusTopic: String = topic("status")

        private fun topic(label: String) = "appgate-" + hex(hmac(secret, "appgate topic $label")).take(24)
    }

    // ---- Messages ----

    sealed class Command {
        data class Allow(val minutes: Int) : Command()
        object EndAllow : Command()
        data class SetWindow(val weekend: Boolean, val window: TimeWindow) : Command()
        /** Ends any Settings pass, so Android Settings is locked again right away. */
        object LockSettings : Command()
        object SendStatus : Command()
    }

    data class Status(
        val tabletName: String,
        val version: String,
        val overrideUntil: Long,
        val schedule: Schedule,
        val videoAppCount: Int,
        val sentAt: Long,
    )

    sealed class Incoming {
        data class Signed(val command: Command, val nonce: String) : Incoming()
        data class Approval(val requestNonce: String, val minutes: Int) : Incoming()
        data class Rejected(val reason: String) : Incoming()
    }

    fun newNonce(random: SecureRandom = SecureRandom()): String =
        hex(ByteArray(12).also { random.nextBytes(it) })

    fun encodeCommand(p: Pairing, command: Command, now: Long, nonce: String = newNonce()): String {
        val args = when (command) {
            is Command.Allow -> listOf("allow", command.minutes.toString())
            Command.EndAllow -> listOf("end")
            is Command.SetWindow -> listOf(
                "window",
                if (command.weekend) "weekend" else "weekday",
                if (command.window.enabled) "1" else "0",
                command.window.startMinute.toString(),
                command.window.endMinute.toString(),
            )
            Command.LockSettings -> listOf("lock")
            Command.SendStatus -> listOf("status")
        }
        return sign(p, listOf(CMD, now.toString(), nonce) + args)
    }

    /**
     * Decides whether a message on the command topic is obeyed. [seenNonces] are recent command
     * nonces (see [rememberNonce]); [pendingRequestNonce] and [pendingRequestAt] describe the one
     * open request from the blocked screen, if any.
     */
    fun decodeIncoming(
        p: Pairing,
        message: String,
        now: Long,
        seenNonces: List<String>,
        pendingRequestNonce: String?,
        pendingRequestAt: Long,
    ): Incoming {
        val parts = message.trim().split(SEP)
        return when (parts.firstOrNull()) {
            APPROVE -> decodeApproval(parts, now, pendingRequestNonce, pendingRequestAt)
            CMD -> decodeSigned(p, parts, now, seenNonces)
            else -> Incoming.Rejected("not an AppGate command")
        }
    }

    private fun decodeApproval(
        parts: List<String>,
        now: Long,
        pendingRequestNonce: String?,
        pendingRequestAt: Long,
    ): Incoming {
        if (parts.size != 3) return Incoming.Rejected("malformed approval")
        val minutes = parts[2].toIntOrNull()
        if (minutes == null || minutes !in ALLOW_CHOICES) return Incoming.Rejected("approval minutes not offered")
        if (pendingRequestNonce == null) return Incoming.Rejected("no open request")
        if (now - pendingRequestAt > MAX_AGE_MS) return Incoming.Rejected("request expired")
        if (!constantEquals(parts[1], pendingRequestNonce)) return Incoming.Rejected("approval for another request")
        return Incoming.Approval(parts[1], minutes)
    }

    private fun decodeSigned(p: Pairing, parts: List<String>, now: Long, seenNonces: List<String>): Incoming {
        val body = verify(p, parts) ?: return Incoming.Rejected("bad signature")
        if (body.size < 4) return Incoming.Rejected("malformed command")
        val sentAt = body[1].toLongOrNull() ?: return Incoming.Rejected("malformed time")
        if (kotlin.math.abs(now - sentAt) > MAX_AGE_MS) return Incoming.Rejected("stale")
        val nonce = body[2]
        if (nonce in seenNonces) return Incoming.Rejected("replayed")
        val command = parseCommand(body.drop(3)) ?: return Incoming.Rejected("unknown command")
        return Incoming.Signed(command, nonce)
    }

    /** Newest first, capped at [NONCE_MEMORY]. */
    fun rememberNonce(seen: List<String>, nonce: String): List<String> =
        (listOf(nonce) + seen.filter { it != nonce }).take(NONCE_MEMORY)

    private fun parseCommand(args: List<String>): Command? = when (args.firstOrNull()) {
        "allow" -> args.getOrNull(1)?.toIntOrNull()?.takeIf { it in ALLOW_CHOICES && args.size == 2 }
            ?.let { Command.Allow(it) }
        "end" -> Command.EndAllow.takeIf { args.size == 1 }
        "lock" -> Command.LockSettings.takeIf { args.size == 1 }
        "status" -> Command.SendStatus.takeIf { args.size == 1 }
        "window" -> parseWindow(args)
        else -> null
    }

    private fun parseWindow(args: List<String>): Command.SetWindow? {
        if (args.size != 5) return null
        val weekend = when (args[1]) { "weekend" -> true; "weekday" -> false; else -> return null }
        val enabled = when (args[2]) { "1" -> true; "0" -> false; else -> return null }
        val start = args[3].toIntOrNull() ?: return null
        val end = args[4].toIntOrNull() ?: return null
        val w = TimeWindow(enabled, start, end)
        return if (isAcceptableWindow(w)) Command.SetWindow(weekend, w) else null
    }

    /** Minutes within a day; when on, start before end (overnight windows are unsupported). */
    fun isAcceptableWindow(w: TimeWindow): Boolean =
        w.startMinute in 0..MINUTES_PER_DAY && w.endMinute in 0..MINUTES_PER_DAY &&
            (!w.enabled || w.startMinute < w.endMinute)

    private const val MINUTES_PER_DAY = 24 * 60

    fun encodeStatus(p: Pairing, s: Status, nonce: String = newNonce()): String = sign(
        p,
        listOf(
            STATUS, s.sentAt.toString(), nonce, clean(s.tabletName), clean(s.version),
            s.overrideUntil.toString(),
            windowFields(s.schedule.weekday), windowFields(s.schedule.weekend),
            s.videoAppCount.toString(),
        ).flatMap { it.split(SEP) },
    )

    /** Status is signed too, so the phone never shows settings that didn't come from the tablet. */
    fun decodeStatus(p: Pairing, message: String): Status? {
        val body = verify(p, message.trim().split(SEP)) ?: return null
        if (body.size != 13 || body[0] != STATUS) return null
        fun window(i: Int): TimeWindow? {
            val start = body[i + 1].toIntOrNull() ?: return null
            val end = body[i + 2].toIntOrNull() ?: return null
            return TimeWindow(body[i] == "1", start, end)
        }
        return Status(
            tabletName = body[3],
            version = body[4],
            overrideUntil = body[5].toLongOrNull() ?: return null,
            schedule = Schedule(window(6) ?: return null, window(9) ?: return null),
            videoAppCount = body[12].toIntOrNull() ?: return null,
            sentAt = body[1].toLongOrNull() ?: return null,
        )
    }

    private fun windowFields(w: TimeWindow) =
        listOf(if (w.enabled) "1" else "0", w.startMinute.toString(), w.endMinute.toString()).joinToString("$SEP")

    private fun clean(s: String) = s.replace(SEP, '/').replace('\n', ' ').take(40)

    /** The body an ntfy notification button posts back to the command topic. */
    fun approvalBody(requestNonce: String, minutes: Int) = "$APPROVE$SEP$requestNonce$SEP$minutes"

    /**
     * JSON for ntfy's publish endpoint: a notification on the parent's phone with one button per
     * [ALLOW_CHOICES], each posting an approval for this request straight back to the tablet.
     */
    fun requestNotificationJson(
        p: Pairing,
        requestNonce: String,
        title: String,
        message: String,
        buttonLabel: (Int) -> String,
    ): String {
        val actions = ALLOW_CHOICES.joinToString(",") { minutes ->
            """{"action":"http","label":${json(buttonLabel(minutes))},""" +
                """"url":${json("$SERVER/${p.commandTopic}")},"method":"POST",""" +
                """"body":${json(approvalBody(requestNonce, minutes))},"clear":true}"""
        }
        return """{"topic":${json(p.requestTopic)},"title":${json(title)},""" +
            """"message":${json(message)},"tags":["hourglass"],"priority":4,"actions":[$actions]}"""
    }

    // ---- ntfy's JSON stream ----

    /**
     * Reads one top-level string field from a line of ntfy's JSON stream, e.g. "event" or "message".
     * ntfy lines are flat objects, and a quote inside a string value is always escaped, so the first
     * unescaped `"field":"` is the field itself. Avoids org.json, which unit tests can't use.
     */
    fun jsonStringField(line: String, field: String): String? {
        val marker = "\"$field\":\""
        val start = line.indexOf(marker).takeIf { it >= 0 } ?: return null
        val out = StringBuilder()
        var i = start + marker.length
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> return out.toString()
                c == '\\' && i + 1 < line.length -> {
                    when (val e = line[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            val code = line.substring(i + 2, minOf(i + 6, line.length)).toIntOrNull(16)
                                ?: return null
                            out.append(code.toChar()); i += 4
                        }
                        else -> out.append(e)
                    }
                    i += 2
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return null
    }

    fun json(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    // ---- Signing ----

    private fun sign(p: Pairing, fields: List<String>): String {
        val body = fields.joinToString("$SEP")
        return body + SEP + hex(hmac(p.key, body))
    }

    /** The fields without the signature, or null if the signature is missing or wrong. */
    private fun verify(p: Pairing, parts: List<String>): List<String>? {
        if (parts.size < 4) return null
        val body = parts.dropLast(1)
        val expected = hex(hmac(p.key, body.joinToString("$SEP")))
        return if (constantEquals(parts.last(), expected)) body else null
    }

    private fun constantEquals(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun hmac(key: ByteArray, text: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(text.toByteArray(Charsets.UTF_8))
        }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/** RFC 4648 base32 without padding: letters and 2-7 only, so a code survives being read aloud. */
internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c).takeIf { it >= 0 } ?: return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
