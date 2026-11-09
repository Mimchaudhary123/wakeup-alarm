package com.wakeup.alarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class AlarmActivity : Activity() {

    private val totalRounds = 10
    private val optionCount = 4

    private var round = 1
    private var target = ""

    private lateinit var roundView: TextView
    private lateinit var leftView: TextView
    private val optionViews = ArrayList<TextView>()
    private var d = 1f

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        d = resources.displayMetrics.density

        // The QR task is added in the next update. Until then every alarm uses the number game.
        when (TaskPrefs.get(this)) {
            TaskPrefs.TASK_QR -> buildNumberGame()
            else -> buildNumberGame()
        }
    }

    private fun dp(v: Int) = (v * d).toInt()

    private fun buildNumberGame() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(12), dp(24), dp(12), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "WAKE UP!"
            textSize = 40f
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
        })
        roundView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(roundView)
        root.addView(TextView(this).apply {
            text = "Tap the number on the right that is exactly the same as the left one"
            textSize = 14f
            setTextColor(Color.LTGRAY)
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
            setColor(Color.parseColor("#2A2A2A"))
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
                stopService(Intent(this, AlarmService::class.java))
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
        // If the alarm is already stopped (stale screen), close this screen.
        if (!AlarmService.isRinging) finish()
    }

    /** Swallow the volume keys completely while this screen is showing. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back button does nothing while the alarm is ringing. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // intentionally empty
    }
}
