package com.alteon.appgate

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

enum class BlockReason { VIDEOS_CLOSED, SETTINGS_LOCKED }

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
