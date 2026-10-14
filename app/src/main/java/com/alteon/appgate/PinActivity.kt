package com.alteon.appgate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Launcher screen. Creates the PIN on first run, otherwise verifies it before opening settings. */
class PinActivity : Activity() {

    private enum class Mode { VERIFY, CREATE, CONFIRM }

    private lateinit var pins: PinManager
    private lateinit var title: TextView
    private lateinit var input: EditText
    private lateinit var error: TextView
    private lateinit var submit: Button
    private val handler = Handler(Looper.getMainLooper())

    private var mode = Mode.VERIFY
    private var firstEntry: String? = null
    /** False once this screen is gone, so a pending wait for the phone stops. */
    @Volatile private var alive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rules = RuleStore(this)
        if (rules.isParentPhone) {
            openParentControls()
            return
        }
        setContentView(R.layout.activity_pin)
        pins = PinManager(this)
        title = findViewById(R.id.pinTitle)
        input = findViewById(R.id.pinInput)
        error = findViewById(R.id.pinError)
        submit = findViewById(R.id.pinSubmit)

        mode = if (!pins.isSet || intent.getBooleanExtra(EXTRA_RESET, false)) Mode.CREATE else Mode.VERIFY

        submit.setOnClickListener { onSubmit() }
        // Offered only on a device's very first launch: a phone never gets a PIN or blocks anything.
        findViewById<Button>(R.id.parentPhoneButton).apply {
            visibility = if (!pins.isSet && !intent.getBooleanExtra(EXTRA_RESET, false)) View.VISIBLE else View.GONE
            setOnClickListener {
                rules.isParentPhone = true
                openParentControls()
            }
        }
        findViewById<Button>(R.id.unlockButton).setOnClickListener { askPhoneToUnlock() }
        // A paired tablet only shows "ask my phone". The PIN backup is hidden behind tapping the logo
        // three times, so a kid sees no PIN box to guess at, even with the Wi-Fi off.
        val remoteFirst = mode == Mode.VERIFY && rules.pairing != null
        findViewById<View>(R.id.unlockButton).visibility = if (remoteFirst) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pinLogo).setOnClickListener { if (remoteFirst) onLogoTap() }
        showPinEntry(!remoteFirst)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { onSubmit(); true } else false
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        val until = pins.lockedUntil()
        if (mode == Mode.VERIFY && System.currentTimeMillis() < until) showLockout(until)
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showPinEntry(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        input.visibility = v
        submit.visibility = v
        error.visibility = v
        if (show) {
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
        }
        render()
    }

    private var logoTaps = 0
    private var firstLogoTapAt = 0L

    /** Three taps on the logo within LOGO_TAP_WINDOW_MS reveal the PIN entry. */
    private fun onLogoTap() {
        val now = System.currentTimeMillis()
        if (now - firstLogoTapAt > LOGO_TAP_WINDOW_MS) {
            firstLogoTapAt = now
            logoTaps = 0
        }
        logoTaps++
        if (logoTaps >= LOGO_TAPS_FOR_PIN) {
            logoTaps = 0
            showPinEntry(true)
        }
    }

    private fun askPhoneToUnlock() {
        val button = findViewById<Button>(R.id.unlockButton)
        val status = findViewById<TextView>(R.id.unlockStatus)
        button.isEnabled = false
        status.visibility = View.VISIBLE
        status.setText(R.string.unlock_asking)
        Thread {
            val askedAt = System.currentTimeMillis()
            val nonce = RemoteListener.askUnlock(applicationContext, askedAt)
            if (nonce == null) {
                runOnUiThread {
                    // Never reveal the PIN here: a kid could turn the Wi-Fi off to get to it.
                    status.setText(R.string.unlock_failed)
                    button.isEnabled = true
                }
                return@Thread
            }
            runOnUiThread { status.setText(R.string.unlock_waiting) }
            val approved = RemoteListener.awaitUnlock(applicationContext, nonce, askedAt, UNLOCK_WAIT_MS) { alive }
            runOnUiThread {
                if (!alive) return@runOnUiThread
                if (approved) {
                    openSettings()
                } else {
                    status.setText(R.string.unlock_no_answer)
                    button.isEnabled = true
                }
            }
        }.start()
    }

    private fun render() {
        title.setText(
            when (mode) {
                Mode.VERIFY -> if (input.visibility == View.VISIBLE) R.string.pin_enter else R.string.unlock_title
                Mode.CREATE -> R.string.pin_create
                Mode.CONFIRM -> R.string.pin_confirm
            }
        )
    }

    private fun onSubmit() {
        val pin = input.text.toString()
        input.text.clear()
        error.text = ""
        when (mode) {
            Mode.CREATE ->
                if (pin.length < PinManager.MIN_LENGTH) {
                    error.text = getString(R.string.pin_too_short, PinManager.MIN_LENGTH)
                } else {
                    firstEntry = pin
                    mode = Mode.CONFIRM
                    render()
                }
            Mode.CONFIRM ->
                if (pin == firstEntry) {
                    pins.setPin(pin)
                    openSettings()
                } else {
                    firstEntry = null
                    mode = Mode.CREATE
                    render()
                    error.setText(R.string.pin_mismatch)
                }
            Mode.VERIFY -> when (val result = pins.verify(pin)) {
                PinManager.Result.Ok -> openSettings()
                PinManager.Result.Wrong -> error.setText(R.string.pin_wrong)
                is PinManager.Result.LockedOut -> showLockout(result.untilMillis)
            }
        }
    }

    private fun showLockout(until: Long) {
        val minutes = ((until - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
        error.text = resources.getQuantityString(R.plurals.pin_locked_out, minutes.toInt(), minutes.toInt())
        submit.isEnabled = false
        input.isEnabled = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            submit.isEnabled = true
            input.isEnabled = true
            error.text = ""
        }, (until - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun openParentControls() {
        startActivity(Intent(this, ParentActivity::class.java))
        finish()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    companion object {
        private const val EXTRA_RESET = "reset"
        /** Long enough to find the phone; the notification itself stays answerable for 10 minutes. */
        private const val UNLOCK_WAIT_MS = 3 * 60_000L
        /** Deliberate enough not to happen by accident, quick enough to remember. */
        private const val LOGO_TAPS_FOR_PIN = 3
        private const val LOGO_TAP_WINDOW_MS = 2_000L

        fun resetIntent(context: Context): Intent =
            Intent(context, PinActivity::class.java).putExtra(EXTRA_RESET, true)
    }
}
