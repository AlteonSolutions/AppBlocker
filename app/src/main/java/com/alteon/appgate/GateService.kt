package com.alteon.appgate

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground and covers blocked ones with the blocked screen.
 * Opening an app is caught by Android's own events, so nothing polls. The only timer is one recheck
 * set for the exact moment the app in front could become blocked (RecheckPolicy): the window closing,
 * a grant or Settings pass ending. Any rule change, on the tablet or from the phone, rechecks at
 * once. Both the timer and the remote-control connection pause while the screen is off.
 */
class GateService : AccessibilityService() {

    private val rules by lazy { RuleStore(this) }
    private val remote by lazy { RemoteListener(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null
    /** Window class names already looked up: whether each is an activity of its package. */
    private val activityCache = HashMap<String, Boolean>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pause()
                Intent.ACTION_SCREEN_ON -> resume()
                // A clock or time zone change moves every boundary.
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> handler.post(recheck)
            }
        }
    }

    private val recheck = Runnable {
        foregroundPkg?.let(::enforce)
        scheduleRecheck()
    }

    /** Held here because SharedPreferences only holds it weakly. */
    private var ruleListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var interactive = false

    override fun onServiceConnected() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        ruleListener = rules.listenForRuleChanges { if (interactive) handler.post(recheck) }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive) resume()
    }

    /** Sets the one timer for when the app in front could next become blocked, if ever. */
    private fun scheduleRecheck() {
        handler.removeCallbacks(recheck)
        if (!interactive) return
        val pkg = foregroundPkg ?: return
        val now = System.currentTimeMillis()
        val at = rules.nextCheckAt(pkg, now) ?: return
        handler.postDelayed(recheck, (at - now).coerceAtLeast(0L) + BOUNDARY_SLACK_MS)
    }

    private fun resume() {
        interactive = true
        handler.removeCallbacks(recheck)
        // Check at once: hours or a granted override may have ended while the screen was off.
        handler.post(recheck)
        // Remote control rides on this service because it is the one part of AppGate always running.
        remote.start()
    }

    private fun pause() {
        interactive = false
        handler.removeCallbacks(recheck)
        remote.stop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Keyboards and the notification shade appear on top of apps; don't treat them as a switch.
        if (pkg in TRANSIENT_PACKAGES || pkg == currentKeyboardPackage()) return
        // Only a real app screen (an activity) is a switch. A volume panel, dialog or overlay from
        // another package used to replace the remembered app, so the recheck stopped seeing the
        // video still playing underneath.
        if (isActivity(pkg, event.className?.toString())) {
            foregroundPkg = pkg
        }
        enforce(pkg)
        scheduleRecheck()
    }

    private fun enforce(pkg: String) {
        if (pkg == packageName) return
        val reason = rules.blockReason(pkg) ?: return
        foregroundPkg = null
        // Cover the app with the blocked screen; that screen goes home when it is dismissed. Going home
        // first raced the launch on Kindle Fire: home arrived after the blocked screen opened, stopped
        // it, and it finishes on stop, so kids saw a flicker and no message.
        try {
            startActivity(BlockedActivity.intent(this, reason))
        } catch (e: RuntimeException) {
            Log.w(TAG, "blocked screen failed to open; going home instead", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun isActivity(pkg: String, className: String?): Boolean {
        if (className == null) return false
        return activityCache.getOrPut("$pkg/$className") {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, className), 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pause()
        ruleListener?.let(rules::stopListening)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GateService"
        /** Lands the recheck just past a boundary, so the end of a window reads as closed. */
        private const val BOUNDARY_SLACK_MS = 500L
        private val TRANSIENT_PACKAGES = setOf("com.android.systemui")
    }
}
