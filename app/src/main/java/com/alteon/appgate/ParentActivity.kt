package com.alteon.appgate

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.Calendar
import java.util.Date

/**
 * Parent controls, on the parent's phone (the same APK, set up as a phone on first launch). Sends
 * signed commands to a paired tablet and shows the settings the tablet reports back. The screen
 * never assumes a command worked: after each one it waits for the tablet's own status.
 *
 * Requests from the tablet ("can I have videos?") don't come here. They arrive as ntfy notifications
 * whose buttons answer the tablet directly, because the ntfy app keeps a push connection that this
 * app would otherwise have to run in the background.
 */
class ParentActivity : Activity() {

    private lateinit var rules: RuleStore
    private var current: PairedTablet? = null
    private var status: RemoteProtocol.Status? = null
    /** Bumped on every send or tablet switch, so a slow reply for an older one is dropped. */
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)
        setTitle(R.string.parent_title)
        rules = RuleStore(this)
        current = rules.pairedTablets.firstOrNull()

        button(R.id.parentTabletButton).setOnClickListener { chooseTablet() }
        listOf(R.id.parentAllow15 to 15, R.id.parentAllow30 to 30, R.id.parentAllow60 to 60).forEach { (id, m) ->
            button(id).text = getString(R.string.override_start, m)
            button(id).setOnClickListener { send(RemoteProtocol.Command.Allow(m)) }
        }
        button(R.id.parentEndAllow).setOnClickListener { send(RemoteProtocol.Command.EndAllow) }
        wireWindow(weekend = false, R.id.parentWeekdayEnabled, R.id.parentWeekdayStart, R.id.parentWeekdayEnd)
        wireWindow(weekend = true, R.id.parentWeekendEnabled, R.id.parentWeekendStart, R.id.parentWeekendEnd)
        button(R.id.parentRefresh).setOnClickListener { send(RemoteProtocol.Command.SendStatus) }
        button(R.id.parentLockSettings).setOnClickListener { send(RemoteProtocol.Command.LockSettings) }
        button(R.id.parentAlerts).setOnClickListener { showAlertsSetup() }
        button(R.id.parentSwitchRole).setOnClickListener {
            rules.isParentPhone = false
            startActivity(Intent(this, PinActivity::class.java))
            finish()
        }
        render()
        if (current != null) send(RemoteProtocol.Command.SendStatus)
    }

    private fun pairing(): RemoteProtocol.Pairing? = current?.let { RemoteProtocol.parseCode(it.code) }

    private fun render() {
        val tablet = current
        button(R.id.parentTabletButton).text =
            if (tablet == null) getString(R.string.parent_pair_tablet) else getString(R.string.parent_tablet, tablet.name)
        findViewById<View>(R.id.parentControls).visibility = if (tablet == null) View.GONE else View.VISIBLE
        val s = status
        val statusText = findViewById<TextView>(R.id.parentStatus)
        when {
            tablet == null -> statusText.setText(R.string.parent_no_tablets)
            s == null -> Unit // left as set by the last send: waiting, or no answer
            else -> statusText.text = describe(s)
        }
        // Hours can only be edited from what the tablet reported, never from a guess.
        val hoursKnown = s != null
        renderWindow(s?.schedule?.weekday, R.id.parentWeekdayEnabled, R.id.parentWeekdayStart, R.id.parentWeekdayEnd, hoursKnown)
        renderWindow(s?.schedule?.weekend, R.id.parentWeekendEnabled, R.id.parentWeekendStart, R.id.parentWeekendEnd, hoursKnown)
    }

    private fun describe(s: RemoteProtocol.Status): String {
        val now = System.currentTimeMillis()
        val first = if (now < s.overrideUntil) {
            getString(R.string.parent_status_allowed, formatTime(s.overrideUntil))
        } else {
            getString(R.string.parent_status_schedule)
        }
        return first + "\n" + getString(
            R.string.parent_status_detail,
            windowText(s.schedule.weekday), windowText(s.schedule.weekend),
            s.videoAppCount, s.version, formatTime(s.sentAt),
        )
    }

    private fun windowText(w: TimeWindow) = if (!w.enabled) getString(R.string.parent_window_off)
        else getString(R.string.parent_window_range, formatMinute(w.startMinute), formatMinute(w.endMinute))

    private fun renderWindow(w: TimeWindow?, checkId: Int, startId: Int, endId: Int, known: Boolean) {
        val check = findViewById<CheckBox>(checkId)
        check.isEnabled = known
        check.isChecked = w?.enabled ?: false
        button(startId).text = getString(R.string.window_start, w?.let { formatMinute(it.startMinute) } ?: "–")
        button(endId).text = getString(R.string.window_end, w?.let { formatMinute(it.endMinute) } ?: "–")
        button(startId).isEnabled = known && w?.enabled == true
        button(endId).isEnabled = known && w?.enabled == true
    }

    /** Click listeners, not checked-change ones, so rendering a status never sends a command. */
    private fun wireWindow(weekend: Boolean, checkId: Int, startId: Int, endId: Int) {
        findViewById<CheckBox>(checkId).setOnClickListener { v ->
            changeWindow(weekend) { it.copy(enabled = (v as CheckBox).isChecked) }
        }
        button(startId).setOnClickListener {
            windowOf(weekend)?.let { w -> pickTime(w.startMinute) { m -> changeWindow(weekend) { it.copy(startMinute = m) } } }
        }
        button(endId).setOnClickListener {
            windowOf(weekend)?.let { w -> pickTime(w.endMinute) { m -> changeWindow(weekend) { it.copy(endMinute = m) } } }
        }
    }

    private fun windowOf(weekend: Boolean): TimeWindow? =
        status?.schedule?.let { if (weekend) it.weekend else it.weekday }

    private fun changeWindow(weekend: Boolean, change: (TimeWindow) -> TimeWindow) {
        val updated = windowOf(weekend)?.let(change) ?: return
        if (!RemoteProtocol.isAcceptableWindow(updated)) {
            Toast.makeText(this, R.string.window_invalid, Toast.LENGTH_LONG).show()
            render()
            return
        }
        send(RemoteProtocol.Command.SetWindow(weekend, updated))
    }

    /** Sends [command], then polls the tablet's status topic for its reply. */
    private fun send(command: RemoteProtocol.Command) {
        val p = pairing() ?: return
        val gen = ++generation
        val statusText = findViewById<TextView>(R.id.parentStatus)
        statusText.setText(if (command == RemoteProtocol.Command.SendStatus) R.string.parent_status_waiting else R.string.parent_sent)
        Thread {
            val sentAt = System.currentTimeMillis()
            val reply = try {
                NtfyClient.publish(p.commandTopic, RemoteProtocol.encodeCommand(p, command, sentAt))
                awaitStatus(p, sentAt)
            } catch (e: IOException) {
                runOnUiThread {
                    if (gen == generation) Toast.makeText(this, R.string.parent_send_failed, Toast.LENGTH_LONG).show()
                }
                null
            }
            runOnUiThread {
                if (gen != generation || isFinishing) return@runOnUiThread
                if (reply != null) status = reply else statusText.setText(R.string.parent_status_none)
                render()
            }
        }.start()
    }

    /** The newest status the tablet sent after [since], or null if it didn't answer in time. */
    private fun awaitStatus(p: RemoteProtocol.Pairing, since: Long): RemoteProtocol.Status? {
        // Status is sent to ntfy with second precision; ask from a little before the command.
        val sinceSeconds = (since - STATUS_CLOCK_SLACK_MS) / 1000
        repeat(STATUS_POLLS) {
            Thread.sleep(STATUS_POLL_INTERVAL_MS)
            val newest = NtfyClient.poll(p.statusTopic, sinceSeconds.toString())
                .mapNotNull { RemoteProtocol.jsonStringField(it, "message") }
                .mapNotNull { RemoteProtocol.decodeStatus(p, it) }
                .filter { it.sentAt >= since - STATUS_CLOCK_SLACK_MS }
                .maxByOrNull { it.sentAt }
            if (newest != null) return newest
        }
        return null
    }

    private fun chooseTablet() {
        val tablets = rules.pairedTablets
        if (tablets.isEmpty()) {
            pairTablet()
            return
        }
        val items = (tablets.map { it.name } + getString(R.string.parent_pair_tablet)).toTypedArray()
        AlertDialog.Builder(this)
            .setItems(items) { _, i ->
                if (i == tablets.size) pairTablet() else select(tablets[i])
            }
            .setNeutralButton(R.string.parent_remove_tablet) { _, _ ->
                val remove = current ?: return@setNeutralButton
                rules.pairedTablets = rules.pairedTablets.filter { it != remove }
                select(rules.pairedTablets.firstOrNull())
            }
            .show()
    }

    private fun select(tablet: PairedTablet?) {
        generation++
        current = tablet
        status = null
        render()
        if (tablet != null) send(RemoteProtocol.Command.SendStatus)
    }

    private fun pairTablet() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val code = EditText(this).apply {
            setHint(R.string.parent_code_hint)
            setSingleLine()
        }
        val name = EditText(this).apply {
            setHint(R.string.parent_name_hint)
            setSingleLine()
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(code)
            addView(name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.parent_pair_title)
            .setView(body)
            .setPositiveButton(R.string.save) { _, _ ->
                val pairing = RemoteProtocol.parseCode(code.text.toString())
                if (pairing == null) {
                    Toast.makeText(this, R.string.parent_code_invalid, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val tablet = PairedTablet(
                    name.text.toString().trim().ifEmpty { getString(R.string.parent_section_tablet) },
                    pairing.code,
                )
                rules.pairedTablets = rules.pairedTablets.filter { it.code != tablet.code } + tablet
                select(tablet)
                showAlertsSetup()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Subscribes the ntfy app to this tablet's requests, so "Ask a parent" reaches this phone. */
    private fun showAlertsSetup() {
        val p = pairing() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.parent_alerts_title)
            .setMessage(getString(R.string.parent_alerts_message, p.requestTopic))
            .setPositiveButton(R.string.parent_alerts_open) { _, _ ->
                open("ntfy://${Uri.parse(RemoteProtocol.SERVER).host}/${p.requestTopic}", NTFY_STORE)
            }
            .setNeutralButton(R.string.parent_alerts_store) { _, _ -> open(NTFY_STORE, NTFY_WEB_STORE) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun open(url: String, fallback: String?) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) open(fallback, null)
        }
    }

    private fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, currentMinute / 60, currentMinute % 60,
            DateFormat.is24HourFormat(this)).show()
    }

    private fun formatMinute(minute: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        return DateFormat.getTimeFormat(this).format(c.time)
    }

    private fun formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

    private fun button(id: Int): Button = findViewById(id)

    companion object {
        /** The tablet answers within a second or two on Wi-Fi; 8 polls 1.5 s apart allow a slow wake. */
        private const val STATUS_POLLS = 8
        private const val STATUS_POLL_INTERVAL_MS = 1_500L
        /** Phone and tablet clocks can differ slightly. */
        private const val STATUS_CLOCK_SLACK_MS = 5_000L
        private const val NTFY_STORE = "market://details?id=io.heckel.ntfy"
        private const val NTFY_WEB_STORE = "https://play.google.com/store/apps/details?id=io.heckel.ntfy"
    }
}
