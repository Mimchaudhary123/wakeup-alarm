package com.wakeup.alarm

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * Three screens in one activity:
 *  1) LANDING: big "Stop alarm" button (the alarm keeps ringing).
 *  2) TASK:    the number game. The first time it opens, the alarm volume drops to 50% for 30 s.
 *  3) DHIKR:   shown after the task. Closes ONLY with its Close button.
 */
class AlarmActivity : Activity() {

    private val totalRounds = 10
    private val optionCount = 4

    private var stage = STAGE_LANDING
    private var round = 1
    private var target = ""

    private lateinit var roundView: TextView
    private lateinit var leftView: TextView
    private val optionViews = ArrayList<TextView>()
    private var d = 1f

    // For Akhirah colours
    private val brown = Color.parseColor("#603E1E")
    private val boxBrown = Color.parseColor("#3E2712")
    private val paleGold = Color.parseColor("#F1D9A8")
    private val glowGold = Color.parseColor("#FFC34D")

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        d = resources.displayMetrics.density
        showLanding()
    }

    private fun dp(v: Int) = (v * d).toInt()

    // ------------------------------------------------------------------
    //  1) Landing screen
    // ------------------------------------------------------------------

    private fun showLanding() {
        stage = STAGE_LANDING
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(brown)
            setPadding(dp(24), dp(56), dp(24), dp(40))
        }
        root.addView(TextView(this).apply {
            text = "WAKE UP!"
            textSize = 44f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextClock(this).apply {
            format12Hour = "h:mm a"
            format24Hour = "h:mm a"
            textSize = 52f
            setTextColor(paleGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        root.addView(spacer())
        root.addView(Button(this).apply {
            text = "Stop alarm"
            setAllCaps(false)
            textSize = 32f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(glowGold)
                cornerRadius = dp(28).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)
            )
            setOnClickListener { startTask() }
        })
        root.addView(TextView(this).apply {
            text = "Tap it, then finish the task to stop the alarm."
            textSize = 14f
            setTextColor(Color.parseColor("#D9C497"))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        root.addView(spacer())
        setContentView(root)
    }

    private fun spacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    // ------------------------------------------------------------------
    //  2) Number task
    // ------------------------------------------------------------------

    private fun startTask() {
        stage = STAGE_TASK
        round = 1
        optionViews.clear()
        buildNumberGame()
        // Volume drops to 50% for 30 seconds. Works only once per alarm.
        AlarmService.startQuietPeriod()
    }

    private fun buildNumberGame() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(brown)
            setPadding(dp(12), dp(24), dp(12), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "WAKE UP!"
            textSize = 40f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        roundView = TextView(this).apply {
            textSize = 20f
            setTextColor(paleGold)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(roundView)
        root.addView(TextView(this).apply {
            text = "Tap the number on the right that is exactly the same as the left one"
            textSize = 14f
            setTextColor(Color.parseColor("#D9C497"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // Left: the number to match.
        leftView = makeBox(36f).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(8), dp(4)) }
        }
        row.addView(leftView)

        // Right: 4 numbers stacked in a column.
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        for (i in 0 until optionCount) {
            val tv = makeBox(32f).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply { setMargins(dp(8), dp(4), dp(4), dp(4)) }
                isClickable = true
            }
            optionViews.add(tv)
            right.addView(tv)
        }
        row.addView(right)
        root.addView(row)

        setContentView(root)
        newQuestion()
    }

    private fun makeBox(size: Float): TextView = TextView(this).apply {
        textSize = size
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(boxBrown)
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun colorFromHue(h: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = 0.65f + Random.nextFloat() * 0.35f
        return Color.HSVToColor(floatArrayOf(hue, sat, 1f))
    }

    /** A 5-digit number whose digits are all different and which does not start with 0. */
    private fun makeTarget(): String {
        while (true) {
            val digits = ('0'..'9').shuffled().take(5)
            if (digits[0] != '0') return digits.joinToString("")
        }
    }

    private fun swapAdjacent(s: String, i: Int): String {
        val c = s.toCharArray()
        val t = c[i]; c[i] = c[i + 1]; c[i + 1] = t
        return String(c)
    }

    private fun newQuestion() {
        roundView.text = "Round $round / $totalRounds"

        target = makeTarget()
        val baseHue = Random.nextFloat() * 360f
        val targetColor = colorFromHue(baseHue)
        leftView.text = target
        leftView.setTextColor(targetColor)

        // Wrong answers use the SAME digits in a different order.
        // Two are close look-alikes (two neighbouring digits swapped), one is fully mixed up.
        val wrong = ArrayList<String>()
        for (i in (0..3).shuffled()) {
            val s = swapAdjacent(target, i)
            if (s[0] != '0' && s !in wrong) wrong.add(s)
            if (wrong.size == 2) break
        }
        while (wrong.size < optionCount - 1) {
            val s = target.toList().shuffled().joinToString("")
            if (s != target && s[0] != '0' && s !in wrong) wrong.add(s)
        }

        // Colours: the right answer has a DIFFERENT colour from the left number.
        // Two wrong answers use the SAME colour as the left number (to trick you).
        val rightColor = colorFromHue(baseHue + 60f + Random.nextFloat() * 240f)
        val wrongColors = listOf(targetColor, targetColor, colorFromHue(Random.nextFloat() * 360f))
            .shuffled()

        val options = ArrayList<Pair<String, Int>>()
        options.add(Pair(target, rightColor))
        for (i in wrong.indices) options.add(Pair(wrong[i], wrongColors[i]))
        options.shuffle()

        for (i in 0 until optionCount) {
            val (n, c) = options[i]
            val tv = optionViews[i]
            tv.text = n
            tv.setTextColor(c)
            tv.setOnClickListener { onPicked(n) }
        }
    }

    private fun onPicked(n: String) {
        if (n == target) {
            if (round >= totalRounds) {
                finishAlarm()
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

    // ------------------------------------------------------------------
    //  3) Dhikr screen (after the task)
    // ------------------------------------------------------------------

    /** The task is done: stop the alarm, release the screen pin and show the dhikr screen. */
    private fun finishAlarm() {
        // Mark the alarm as stopped right away, so nothing reopens the alarm screen
        // while the service is still shutting down.
        AlarmService.isRinging = false
        stopService(Intent(this, AlarmService::class.java))
        leaveLockTask()
        showDhikr()
    }

    private fun showDhikr() {
        stage = STAGE_DHIKR
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(brown)
            setPadding(dp(16), dp(28), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "Begin your day with dhikr"
            textSize = 20f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        val texts = listOf(
            "Subhan Allah",
            "Alhamdulillah",
            "Allahu Akbar",
            "L\u0101 il\u0101ha ill-all\u0101hu, wa\u1e25dahu l\u0101 shar\u012bka lah, " +
                "lahul-mulku wa lahul-\u1e25amdu, wa huwa \u02bfal\u0101 kulli shay'in qad\u012br"
        )
        val weights = listOf(1f, 1f, 1f, 2.4f)
        for (i in texts.indices) {
            val tv = TextView(this).apply {
                text = texts[i]
                setTextColor(brown)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply {
                    setColor(paleGold)
                    cornerRadius = dp(16).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, weights[i]
                ).apply { setMargins(0, dp(6), 0, dp(6)) }
            }
            // The text shrinks or grows to fit its box, so nothing ever needs scrolling.
            tv.setAutoSizeTextTypeUniformWithConfiguration(
                14, if (i < 3) 40 else 26, 1, TypedValue.COMPLEX_UNIT_SP
            )
            root.addView(tv)
        }

        root.addView(Button(this).apply {
            text = "Close"
            setAllCaps(false)
            textSize = 22f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(glowGold)
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    // ------------------------------------------------------------------
    //  Lifecycle, pinning, keys
    // ------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        if (AlarmService.isRinging) {
            // A new alarm started while an old dhikr screen was still open: start over.
            if (stage == STAGE_DHIKR) showLanding()
            isForeground = true
            enterLockTask()
            return
        }
        // Not ringing: only the dhikr screen may stay. Any other screen is stale.
        if (stage != STAGE_DHIKR) {
            leaveLockTask()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
    }

    /** Screen pinning: blocks Home, Recents and the notification shade while the alarm rings. */
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

    /** Swallow the volume keys completely while the alarm is still ringing. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (stage != STAGE_DHIKR) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back does nothing while the alarm rings. On the dhikr screen it closes the screen. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (stage == STAGE_DHIKR) finish()
    }

    companion object {
        private const val STAGE_LANDING = 0
        private const val STAGE_TASK = 1
        private const val STAGE_DHIKR = 2

        /** True while the alarm screen is in front. The service re-opens it if this goes false. */
        @Volatile
        var isForeground: Boolean = false
    }
}
