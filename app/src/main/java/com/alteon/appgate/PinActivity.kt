package com.alteon.appgate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        pins = PinManager(this)
        title = findViewById(R.id.pinTitle)
        input = findViewById(R.id.pinInput)
        error = findViewById(R.id.pinError)
        submit = findViewById(R.id.pinSubmit)

        mode = if (!pins.isSet || intent.getBooleanExtra(EXTRA_RESET, false)) Mode.CREATE else Mode.VERIFY

        submit.setOnClickListener { onSubmit() }
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
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render() {
        title.setText(
            when (mode) {
                Mode.VERIFY -> R.string.pin_enter
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

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    companion object {
        private const val EXTRA_RESET = "reset"

        fun resetIntent(context: Context): Intent =
            Intent(context, PinActivity::class.java).putExtra(EXTRA_RESET, true)
    }
}
