package com.wakeup.alarm

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** "Are you awake?": 3 quick rounds of the number game before the countdown runs out. */
class WakeCheckActivity : Activity() {

    private val rounds = 3
    private var stage = 1
    private val brown = Color.parseColor("#603E1E")
    private var round = 1
    private var target = ""
    private var deadlineAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val optionViews = ArrayList<TextView>()
    private lateinit var infoView: TextView
    private lateinit var leftView: TextView
    private var d = 1f
    private var foreground = false
    private var createdAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val left = deadlineAt - System.currentTimeMillis()
            // Let go of the screen pin just before time runs out, so the alarm screen can come up.
            if (left <= 1000) leaveLockTask()
            // The alarm is ringing again (time ran out): this screen is no longer needed.
            if (AlarmService.isRinging || left <= 0) {
                leaveLockTask()
                finish()
                return
            }
            // Left with Home or similar: bring the check back to the front.
            if (!foreground && System.currentTimeMillis() - createdAt > 2500 &&
                Settings.canDrawOverlays(this@WakeCheckActivity)
            ) {
                try {
                    startActivity(
                        Intent(this@WakeCheckActivity, WakeCheckActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    )
                } catch (e: Exception) {
                }
            }
            showInfo(left)
            handler.postDelayed(this, 500)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2003)
        } catch (e: Exception) {
        }

        deadlineAt = WakeCheck.deadlineAt(this)
        if (deadlineAt <= System.currentTimeMillis()) {
            finish() // nothing to check any more
            return
        }
        createdAt = System.currentTimeMillis()
        d = resources.displayMetrics.density
        stage = WakeCheck.stage(this)
        buildScreen()
        handler.post(ticker)
    }

    private fun dp(v: Int) = (v * d).toInt()

    private fun showInfo(leftMs: Long) {
        val secs = (leftMs / 1000).coerceAtLeast(0)
        infoView.text = String.format(
            "Check %d of %d  -  Round %d / %d  -  %d:%02d left",
            stage, WakeCheck.CHECKS, round, rounds, secs / 60, secs % 60
        )
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(40), dp(12), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "ARE YOU AWAKE?"
            textSize = 32f
            setTextColor(Glow.GOLD)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            Glow.textGlow(this, dp(10).toFloat())
        })
        infoView = TextView(this).apply {
            textSize = 16f
            setTextColor(Glow.PALE)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(infoView)
        root.addView(TextView(this).apply {
            text = "Tap the number on the right that matches the left one. If time runs out, the alarm rings again."
            textSize = 14f
            setTextColor(Color.parseColor("#D9C497"))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(14))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        leftView = makeBox(36f).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(8), dp(4)) }
        }
        row.addView(leftView)
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        for (i in 0 until 4) {
            val tv = makeBox(32f).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    .apply { setMargins(dp(8), dp(4), dp(4), dp(4)) }
            }
            optionViews.add(tv)
            right.addView(tv)
        }
        row.addView(right)
        root.addView(row)
        setContentView(Glow.screen(this, root, false))
        newQuestion()
    }

    private fun makeBox(size: Float): TextView = TextView(this).apply {
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Glow.PALE)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#3E2712"))
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun newQuestion() {
        // A 5-digit number with different digits, no leading 0; wrong answers are the same digits mixed up.
        var digits: List<Char>
        do {
            digits = ('0'..'9').shuffled().take(5)
        } while (digits[0] == '0')
        target = digits.joinToString("")
        leftView.text = target
        val options = ArrayList<String>()
        options.add(target)
        while (options.size < 4) {
            val s = target.toList().shuffled().joinToString("")
            if (s[0] != '0' && s !in options) options.add(s)
        }
        options.shuffle()
        for (i in 0 until 4) {
            val n = options[i]
            optionViews[i].text = n
            optionViews[i].setOnClickListener { onPicked(n) }
        }
        showInfo(deadlineAt - System.currentTimeMillis())
    }

    private fun onPicked(n: String) {
        if (n == target) {
            if (round >= rounds) {
                WakeCheck.passed(this)
                leaveLockTask()
                val msg = if (stage < WakeCheck.CHECKS) "Good. One more check comes later." else "Good. You are awake."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                round++
                newQuestion()
            }
        } else {
            Toast.makeText(this, "Wrong! Back to round 1", Toast.LENGTH_SHORT).show()
            round = 1
            newQuestion()
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        enterLockTask()
    }

    override fun onPause() {
        super.onPause()
        foreground = false
    }

    /** Screen pinning, like the alarm screen: blocks Home, Recents and the notification shade. */
    private fun enterLockTask() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
        } catch (e: Exception) {
        }
    }

    private fun leaveLockTask() {
        try {
            stopLockTask()
        } catch (e: Exception) {
        }
    }

    /** The volume keys do nothing during the check. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back does nothing during the check. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }

    override fun onDestroy() {
        showing = false
        handler.removeCallbacks(ticker)
        leaveLockTask()
        // Stop the check vibration, unless the alarm has taken over and is vibrating itself.
        if (!AlarmService.isRinging) Haptics.stop(this)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var showing: Boolean = false
    }
}
