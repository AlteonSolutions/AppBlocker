package com.alteon.appgate

import android.content.Context
import android.util.Log
import java.io.IOException

/**
 * The tablet's half of remote control. Runs inside GateService, which is always running while the
 * blocker is on, and keeps a subscription open to this tablet's ntfy command topic. Every message
 * goes through RemoteProtocol, which decides whether it is obeyed; this class only applies the
 * result to RuleStore and reports the new settings back to the parent's phone.
 *
 * Network trouble never affects blocking: it is retried with backoff, and the PIN still works.
 */
class RemoteListener(context: Context) {

    private val appContext = context.applicationContext
    private val rules = RuleStore(appContext)
    /**
     * Bumped by every start and stop. A loop runs only while the generation it started with is
     * current. The screen turning off and on stops and restarts the listener, and an old loop still
     * blocked in a read must not carry on beside the new one.
     */
    @Volatile private var generation = 0
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        val gen = ++generation
        thread = Thread({ loop(gen) }, "AppGate remote").apply {
            isDaemon = true
            start()
        }
    }

    /** The open read ends within NtfyClient's stream timeout at worst; the thread is a daemon. */
    fun stop() {
        running = false
        generation++
        thread?.interrupt()
        thread = null
    }

    private fun current(gen: Int) = running && generation == gen

    private fun loop(gen: Int) {
        var backoffMs = MIN_BACKOFF_MS
        while (current(gen)) {
            val pairing = rules.pairing
            if (pairing == null) {
                if (!pause(UNPAIRED_CHECK_MS, gen)) return
                continue
            }
            val code = pairing.code
            try {
                NtfyClient.stream(
                    pairing.commandTopic,
                    since = rules.lastRemoteMessageId ?: FIRST_CONNECT_SINCE,
                    // Re-pairing changes the topic, so drop this connection and open the new one.
                    keepGoing = { current(gen) && rules.pairing?.code == code },
                ) { line ->
                    backoffMs = MIN_BACKOFF_MS
                    if (current(gen)) handle(pairing, line)
                }
            } catch (e: IOException) {
                Log.w(TAG, "remote connection dropped; retrying in ${backoffMs / 1000} s", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "remote listener error; retrying in ${backoffMs / 1000} s", e)
            }
            if (!pause(backoffMs, gen)) return
            backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
        }
    }

    private fun handle(pairing: RemoteProtocol.Pairing, line: String) {
        if (RemoteProtocol.jsonStringField(line, "event") != "message") return
        val message = RemoteProtocol.jsonStringField(line, "message") ?: return
        val now = System.currentTimeMillis()
        val result = RemoteProtocol.decodeIncoming(
            pairing, message, now, rules.seenNonces, rules.pendingRequestNonce, rules.pendingRequestAt,
        )
        when (result) {
            is RemoteProtocol.Incoming.Signed -> {
                rules.seenNonces = RemoteProtocol.rememberNonce(rules.seenNonces, result.nonce)
                apply(result.command, now)
                publishStatus(appContext, pairing)
            }
            is RemoteProtocol.Incoming.Approval -> {
                rules.closeRequest()
                rules.grantVideos(result.minutes, now)
                publishStatus(appContext, pairing)
            }
            is RemoteProtocol.Incoming.Rejected -> Log.i(TAG, "ignored remote message: ${result.reason}")
        }
        RemoteProtocol.jsonStringField(line, "id")?.let { rules.lastRemoteMessageId = it }
    }

    private fun apply(command: RemoteProtocol.Command, now: Long) {
        when (command) {
            is RemoteProtocol.Command.Allow -> rules.grantVideos(command.minutes, now)
            RemoteProtocol.Command.EndAllow -> rules.overrideUntil = 0L
            is RemoteProtocol.Command.SetWindow -> {
                val s = rules.schedule
                rules.schedule = if (command.weekend) s.copy(weekend = command.window) else s.copy(weekday = command.window)
            }
            RemoteProtocol.Command.LockSettings -> rules.lockNow()
            RemoteProtocol.Command.SendStatus -> Unit
        }
    }

    /** False if the listener was stopped while waiting. */
    private fun pause(ms: Long, gen: Int): Boolean = try {
        Thread.sleep(ms)
        current(gen)
    } catch (e: InterruptedException) {
        false
    }

    enum class AskResult { SENT, TOO_SOON, NOT_PAIRED, FAILED }

    companion object {
        private const val TAG = "RemoteListener"
        private const val MIN_BACKOFF_MS = 5_000L
        /** Short, so a tablet waking from sleep reconnects within half a minute. */
        private const val MAX_BACKOFF_MS = 30_000L
        /** How often an unpaired tablet checks whether it has been paired. */
        private const val UNPAIRED_CHECK_MS = 15_000L
        /** Older messages are stale under RemoteProtocol.MAX_AGE_MS anyway. */
        private const val FIRST_CONNECT_SINCE = "10m"
        /** One request at a time; also stops a kid from flooding the parent's phone. */
        const val ASK_COOLDOWN_MS = 2 * 60_000L

        /** Sends "can I have videos?" to the parent's phone. Blocking: call off the main thread. */
        fun askParent(context: Context): AskResult {
            val rules = RuleStore(context)
            val pairing = rules.pairing ?: return AskResult.NOT_PAIRED
            val now = System.currentTimeMillis()
            if (now - rules.lastAskAt < ASK_COOLDOWN_MS) return AskResult.TOO_SOON
            val nonce = RemoteProtocol.newNonce()
            rules.openRequest(nonce, now)
            val json = RemoteProtocol.requestNotificationJson(
                pairing,
                nonce,
                title = context.getString(R.string.remote_request_title, rules.tabletName),
                message = context.getString(R.string.remote_request_message),
                buttonLabel = { context.getString(R.string.remote_allow_minutes, it) },
            )
            return try {
                NtfyClient.publishJson(json)
                AskResult.SENT
            } catch (e: IOException) {
                Log.w(TAG, "request to parent failed", e)
                AskResult.FAILED
            }
        }

        /**
         * Asks the parent's phone to unlock AppGate's settings on this tablet. Returns the request's
         * nonce, which the waiting PIN screen matches against the approval, or null if not paired or
         * not sent. Blocking: call off the main thread.
         */
        fun askUnlock(context: Context, now: Long): String? {
            val rules = RuleStore(context)
            val pairing = rules.pairing ?: return null
            val nonce = RemoteProtocol.newNonce()
            val json = RemoteProtocol.unlockNotificationJson(
                pairing,
                nonce,
                title = context.getString(R.string.remote_unlock_title, rules.tabletName),
                message = context.getString(R.string.remote_unlock_message),
                button = context.getString(R.string.remote_unlock_button),
            )
            return try {
                NtfyClient.publishJson(json)
                nonce
            } catch (e: IOException) {
                Log.w(TAG, "unlock request failed", e)
                null
            }
        }

        /**
         * Waits for the parent to tap "Unlock settings" for [nonce], until [maxWaitMs] passes or
         * [keepWaiting] turns false. Blocking: call off the main thread.
         */
        fun awaitUnlock(context: Context, nonce: String, askedAt: Long, maxWaitMs: Long, keepWaiting: () -> Boolean): Boolean {
            val pairing = RuleStore(context).pairing ?: return false
            var approved = false
            fun waiting() = !approved && keepWaiting() && System.currentTimeMillis() - askedAt < maxWaitMs
            // Unix seconds, a second early: ntfy's "since" has one-second precision.
            val since = (askedAt / 1000 - 1).toString()
            while (waiting()) {
                try {
                    NtfyClient.stream(pairing.commandTopic, since, keepGoing = ::waiting) { line ->
                        if (RemoteProtocol.jsonStringField(line, "event") != "message") return@stream
                        val message = RemoteProtocol.jsonStringField(line, "message") ?: return@stream
                        if (RemoteProtocol.isUnlockApproval(message, nonce, askedAt, System.currentTimeMillis())) {
                            approved = true
                        }
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "waiting for unlock: connection dropped", e)
                    if (waiting()) Thread.sleep(UNLOCK_RETRY_MS)
                }
            }
            return approved
        }

        private const val UNLOCK_RETRY_MS = 3_000L

        /** Tells the parent's phone the tablet's current settings. Failure is logged, never thrown. */
        fun publishStatus(context: Context, pairing: RemoteProtocol.Pairing) {
            val rules = RuleStore(context)
            val status = RemoteProtocol.Status(
                tabletName = rules.tabletName,
                version = installedVersion(context),
                overrideUntil = rules.overrideUntil,
                schedule = rules.schedule,
                videoAppCount = rules.videoPackages.size,
                sentAt = System.currentTimeMillis(),
            )
            try {
                NtfyClient.publish(pairing.statusTopic, RemoteProtocol.encodeStatus(pairing, status))
            } catch (e: IOException) {
                Log.w(TAG, "status update failed", e)
            }
        }

        fun installedVersion(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
}
