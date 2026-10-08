package com.alteon.appgate

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground and bounces blocked ones to the home screen.
 * Event-driven, plus a slow recheck so a video app left open past closing time still gets bounced.
 */
class GateService : AccessibilityService() {

    private val rules by lazy { RuleStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null

    private val recheck = object : Runnable {
        override fun run() {
            foregroundPkg?.let(::enforce)
            handler.postDelayed(this, RECHECK_MS)
        }
    }

    override fun onServiceConnected() {
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, RECHECK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Keyboards and the notification shade appear on top of apps; don't treat them as a switch.
        if (pkg in TRANSIENT_PACKAGES || pkg == currentKeyboardPackage()) return
        foregroundPkg = pkg
        enforce(pkg)
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

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GateService"
        private const val RECHECK_MS = 30_000L
        private val TRANSIENT_PACKAGES = setOf("com.android.systemui")
    }
}
