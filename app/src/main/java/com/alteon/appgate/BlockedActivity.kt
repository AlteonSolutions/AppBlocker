package com.alteon.appgate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.TextView
import java.util.Calendar

/** The friendly "not right now" screen shown after a bounce. */
class BlockedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)
        findViewById<Button>(R.id.okButton).setOnClickListener { goHome() }
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    // The blocked app is still open underneath, so leaving this screen must not reveal it.
    @Deprecated("Back is still delivered here at targetSdk 34 without the predictive-back opt-in")
    override fun onBackPressed() = goHome()

    override fun onStop() {
        super.onStop()
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun render(i: Intent) {
        val reason = i.getStringExtra(EXTRA_REASON)
            ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
            ?: BlockReason.VIDEOS_CLOSED
        val title = findViewById<TextView>(R.id.blockedTitle)
        val message = findViewById<TextView>(R.id.blockedMessage)
        when (reason) {
            BlockReason.SETTINGS_LOCKED -> {
                title.setText(R.string.blocked_settings_title)
                message.setText(R.string.blocked_settings_message)
            }
            BlockReason.VIDEOS_CLOSED -> {
                title.setText(R.string.blocked_videos_title)
                message.text = nextOpeningText()
            }
        }
    }

    private fun nextOpeningText(): String {
        val now = Calendar.getInstance()
        val next = RuleStore(this).schedule.nextOpening(now)
            ?: return getString(R.string.blocked_videos_off)
        val time = DateFormat.getTimeFormat(this).format(next.time)
        val days = daysBetween(now, next)
        return when (days) {
            0 -> getString(R.string.blocked_videos_today, time)
            1 -> getString(R.string.blocked_videos_tomorrow, time)
            else -> getString(
                R.string.blocked_videos_day,
                DateFormat.format("EEEE", next).toString(),
                time,
            )
        }
    }

    private fun daysBetween(a: Calendar, b: Calendar): Int {
        val x = (a.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
        val y = (b.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
        return Math.round((y.timeInMillis - x.timeInMillis) / 86_400_000.0).toInt()
    }

    companion object {
        private const val EXTRA_REASON = "reason"

        fun intent(context: Context, reason: BlockReason): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_REASON, reason.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
