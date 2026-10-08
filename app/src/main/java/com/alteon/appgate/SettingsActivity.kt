package com.alteon.appgate

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Parent-only screen. Reachable only through PinActivity, and closes itself when left. */
class SettingsActivity : Activity() {

    private lateinit var rules: RuleStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        rules = RuleStore(this)

        button(R.id.enableServiceButton).setOnClickListener {
            rules.startSettingsSession()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(R.id.deviceAdminButton).setOnClickListener { requestDeviceAdmin() }
        button(R.id.pickAppsButton).setOnClickListener { loadAppsThenPick() }

        wireWindow(isWeekend = false, R.id.weekdayEnabled, R.id.weekdayStart, R.id.weekdayEnd)
        wireWindow(isWeekend = true, R.id.weekendEnabled, R.id.weekendStart, R.id.weekendEnd)

        button(R.id.overrideButton).setOnClickListener {
            val now = System.currentTimeMillis()
            rules.overrideUntil = if (now < rules.overrideUntil) 0L else now + RuleStore.OVERRIDE_MS
            refresh()
        }
        button(R.id.openSettingsButton).setOnClickListener {
            rules.startSettingsSession()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        button(R.id.updateButton).setOnClickListener { downloadLatest() }
        button(R.id.changePinButton).setOnClickListener {
            startActivity(PinActivity.resetIntent(this))
            finish()
        }
        button(R.id.lockButton).setOnClickListener {
            rules.lockNow()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the parent leaves; getting back in requires the PIN again.
        if (!isChangingConfigurations) finish()
    }

    private fun refresh() {
        val serviceOn = isServiceEnabled()
        val adminOn = isAdminActive()
        findViewById<TextView>(R.id.statusText).text = getString(
            R.string.status_line,
            getString(if (serviceOn) R.string.on else R.string.off),
            getString(if (adminOn) R.string.on else R.string.off),
        )
        button(R.id.enableServiceButton).isEnabled = !serviceOn
        button(R.id.deviceAdminButton).isEnabled = !adminOn

        button(R.id.pickAppsButton).text =
            getString(R.string.pick_apps_button, rules.videoPackages.size)

        val s = rules.schedule
        renderWindow(s.weekday, R.id.weekdayEnabled, R.id.weekdayStart, R.id.weekdayEnd)
        renderWindow(s.weekend, R.id.weekendEnabled, R.id.weekendStart, R.id.weekendEnd)

        val now = System.currentTimeMillis()
        button(R.id.updateButton).text = getString(R.string.update_app, installedVersion())

        button(R.id.overrideButton).text = if (now < rules.overrideUntil) {
            getString(R.string.override_end, formatTime(rules.overrideUntil))
        } else {
            getString(R.string.override_start, (RuleStore.OVERRIDE_MS / 60_000L).toInt())
        }
    }

    private fun renderWindow(w: TimeWindow, checkId: Int, startId: Int, endId: Int) {
        findViewById<CheckBox>(checkId).isChecked = w.enabled
        button(startId).text = getString(R.string.window_start, formatMinute(w.startMinute))
        button(endId).text = getString(R.string.window_end, formatMinute(w.endMinute))
        button(startId).isEnabled = w.enabled
        button(endId).isEnabled = w.enabled
    }

    private fun wireWindow(isWeekend: Boolean, checkId: Int, startId: Int, endId: Int) {
        findViewById<CheckBox>(checkId).setOnCheckedChangeListener { _, checked ->
            updateWindow(isWeekend) { it.copy(enabled = checked) }
        }
        button(startId).setOnClickListener {
            pickTime(windowOf(isWeekend).startMinute) { m ->
                updateWindow(isWeekend) { it.copy(startMinute = m) }
            }
        }
        button(endId).setOnClickListener {
            pickTime(windowOf(isWeekend).endMinute) { m ->
                updateWindow(isWeekend) { it.copy(endMinute = m) }
            }
        }
    }

    private fun windowOf(isWeekend: Boolean): TimeWindow =
        rules.schedule.let { if (isWeekend) it.weekend else it.weekday }

    private fun updateWindow(isWeekend: Boolean, change: (TimeWindow) -> TimeWindow) {
        val s = rules.schedule
        val updated = change(if (isWeekend) s.weekend else s.weekday)
        if (updated.enabled && updated.startMinute >= updated.endMinute) {
            Toast.makeText(this, R.string.window_invalid, Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        rules.schedule = if (isWeekend) s.copy(weekend = updated) else s.copy(weekday = updated)
        refresh()
    }

    private fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, h, m -> onPicked(h * 60 + m) },
            currentMinute / 60,
            currentMinute % 60,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun loadAppsThenPick() {
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
            runOnUiThread { if (!isFinishing) showPicker(apps) }
        }.start()
    }

    private fun showPicker(apps: List<Pair<String, String>>) {
        val current = if (rules.hasChosenApps) rules.videoPackages else RuleStore.KNOWN_VIDEO_PACKAGES
        val checked = BooleanArray(apps.size) { apps[it].first in current }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_apps_title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, isChecked ->
                checked[i] = isChecked
            }
            .setPositiveButton(R.string.save) { _, _ ->
                rules.videoPackages = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Opens the browser on the newest release APK; the browser downloads it and the system installer
     * updates AppGate in place, keeping the PIN and rules. This replaces keeping Obtainium on the
     * tablet, where the kids could use it. The Settings pass is needed because the first install from a
     * browser sends the parent to Android Settings to allow it, and AppGate otherwise blocks Settings.
     */
    private fun downloadLatest() {
        rules.startSettingsSession()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK_URL)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun installedVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    private fun requestDeviceAdmin() {
        rules.startSettingsSession()
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
        )
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, GateService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent())
    }

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun formatMinute(minute: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        return DateFormat.getTimeFormat(this).format(c.time)
    }

    private fun formatTime(millis: Long): String =
        DateFormat.getTimeFormat(this).format(java.util.Date(millis))

    private fun button(id: Int): Button = findViewById(id)

    companion object {
        // GitHub redirects this to the newest release's asset. release.yml uploads every release under
        // this fixed name as well as a versioned one, so this one link never goes stale.
        private const val LATEST_APK_URL =
            "https://github.com/AlteonSolutions/AppBlocker/releases/latest/download/appgate.apk"
    }
}
