package com.alteon.appgate

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

enum class BlockReason { VIDEOS_CLOSED, SETTINGS_LOCKED }

/** A tablet paired on the parent's phone: the name the parent gave it and its pairing code. */
data class PairedTablet(val name: String, val code: String)

/** All blocking rules and the single decision function the service calls. */
class RuleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val pins = PinManager(context)

    val hasChosenApps: Boolean get() = prefs.contains(KEY_VIDEO)

    var videoPackages: Set<String>
        get() = prefs.getStringSet(KEY_VIDEO, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VIDEO, HashSet(value)).apply()

    var schedule: Schedule
        get() = Schedule(
            weekday = readWindow("weekday", Schedule.DEFAULT.weekday),
            weekend = readWindow("weekend", Schedule.DEFAULT.weekend),
        )
        set(value) {
            val e = prefs.edit()
            writeWindow(e, "weekday", value.weekday)
            writeWindow(e, "weekend", value.weekend)
            e.apply()
        }

    var overrideUntil: Long
        get() = prefs.getLong(KEY_OVERRIDE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_OVERRIDE_UNTIL, value).apply()

    var settingsSessionUntil: Long
        get() = prefs.getLong(KEY_SETTINGS_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_SETTINGS_UNTIL, value).apply()

    fun startSettingsSession(now: Long = System.currentTimeMillis()) {
        settingsSessionUntil = now + SETTINGS_SESSION_MS
    }

    fun lockNow() {
        settingsSessionUntil = 0L
    }

    /** Grants video time; never shortens a grant that already runs longer. */
    fun grantVideos(minutes: Int, now: Long = System.currentTimeMillis()) {
        overrideUntil = maxOf(overrideUntil, now + minutes * 60_000L)
    }

    // ---- Remote control, tablet side (see RemoteProtocol) ----

    /** This tablet's pairing with the parent's phone. Changing it starts the remote state afresh. */
    var pairing: RemoteProtocol.Pairing?
        get() = prefs.getString(KEY_PAIRING, null)?.let(RemoteProtocol::parseCode)
        set(value) {
            prefs.edit()
                .apply { if (value == null) remove(KEY_PAIRING) else putString(KEY_PAIRING, value.code) }
                .remove(KEY_LAST_REMOTE_ID).remove(KEY_SEEN_NONCES)
                .remove(KEY_PENDING_NONCE).remove(KEY_PENDING_AT)
                .apply()
        }

    /** Shown in the parent's request notifications, e.g. "Emma's tablet". */
    var tabletName: String
        get() = prefs.getString(KEY_TABLET_NAME, null) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString(KEY_TABLET_NAME, value.trim().take(40)).apply()

    /** The last ntfy message handled, so a reconnect resumes after it instead of replaying the cache. */
    var lastRemoteMessageId: String?
        get() = prefs.getString(KEY_LAST_REMOTE_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_REMOTE_ID, value).apply()

    var seenNonces: List<String>
        get() = prefs.getString(KEY_SEEN_NONCES, "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_SEEN_NONCES, value.joinToString(",")).apply()

    val pendingRequestNonce: String? get() = prefs.getString(KEY_PENDING_NONCE, null)
    val pendingRequestAt: Long get() = prefs.getLong(KEY_PENDING_AT, 0L)
    val lastAskAt: Long get() = prefs.getLong(KEY_LAST_ASK_AT, 0L)

    fun openRequest(nonce: String, now: Long) {
        prefs.edit().putString(KEY_PENDING_NONCE, nonce).putLong(KEY_PENDING_AT, now)
            .putLong(KEY_LAST_ASK_AT, now).apply()
    }

    fun closeRequest() {
        prefs.edit().remove(KEY_PENDING_NONCE).remove(KEY_PENDING_AT).apply()
    }

    // ---- Remote control, parent phone side ----

    /** Set when this device was set up as the parent's phone: it opens parent controls, never blocks. */
    var isParentPhone: Boolean
        get() = prefs.getBoolean(KEY_PARENT_PHONE, false)
        set(value) = prefs.edit().putBoolean(KEY_PARENT_PHONE, value).apply()

    var pairedTablets: List<PairedTablet>
        get() = prefs.getString(KEY_PAIRED_TABLETS, "")!!.lines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else PairedTablet(line.substring(0, tab), line.substring(tab + 1))
        }
        set(value) = prefs.edit().putString(
            KEY_PAIRED_TABLETS,
            value.joinToString("\n") { "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.code}" },
        ).apply()

    /** Null means allow. Nothing is blocked until a PIN exists, so setup can't lock anyone out. */
    fun blockReason(pkg: String, now: Long = System.currentTimeMillis()): BlockReason? {
        if (!pins.isSet) return null

        if (pkg in SETTINGS_PACKAGES) {
            return if (now < settingsSessionUntil) null else BlockReason.SETTINGS_LOCKED
        }

        if (pkg in videoPackages && now >= overrideUntil) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            if (!schedule.isOpen(cal)) return BlockReason.VIDEOS_CLOSED
        }
        return null
    }

    private fun readWindow(prefix: String, fallback: TimeWindow) = TimeWindow(
        enabled = prefs.getBoolean("${prefix}_enabled", fallback.enabled),
        startMinute = prefs.getInt("${prefix}_start", fallback.startMinute),
        endMinute = prefs.getInt("${prefix}_end", fallback.endMinute),
    )

    private fun writeWindow(e: SharedPreferences.Editor, prefix: String, w: TimeWindow) {
        e.putBoolean("${prefix}_enabled", w.enabled)
            .putInt("${prefix}_start", w.startMinute)
            .putInt("${prefix}_end", w.endMinute)
    }

    companion object {
        private const val KEY_VIDEO = "video_packages"
        private const val KEY_OVERRIDE_UNTIL = "override_until"
        private const val KEY_SETTINGS_UNTIL = "settings_until"
        private const val KEY_PAIRING = "remote_pairing"
        private const val KEY_TABLET_NAME = "remote_tablet_name"
        private const val KEY_LAST_REMOTE_ID = "remote_last_id"
        private const val KEY_SEEN_NONCES = "remote_seen_nonces"
        private const val KEY_PENDING_NONCE = "remote_pending_nonce"
        private const val KEY_PENDING_AT = "remote_pending_at"
        private const val KEY_LAST_ASK_AT = "remote_last_ask_at"
        private const val KEY_PARENT_PHONE = "parent_phone"
        private const val KEY_PAIRED_TABLETS = "paired_tablets"

        const val SETTINGS_SESSION_MS = 5 * 60_000L
        const val OVERRIDE_MS = 30 * 60_000L

        val SETTINGS_PACKAGES = setOf("com.android.settings")

        /** Pre-checked in the picker the first time, if installed. */
        val KNOWN_VIDEO_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.netflix.mediaclient",
            "com.amazon.avod",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
            "com.hulu.plus",
            "org.pbskids.video",
        )
    }
}
