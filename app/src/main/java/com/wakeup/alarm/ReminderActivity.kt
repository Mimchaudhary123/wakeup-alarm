package com.wakeup.alarm

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * The Istigfar reminder and the Durud reminder screen.
 * Each time it opens it asks for 3 to 5 readings. Every tap on Close counts one reading
 * (added to the day and month totals) and lowers the number; at 0 the screen closes.
 */
class ReminderActivity : Activity() {

    private val brown = Color.parseColor("#603E1E")

    private val istigfarTexts = listOf(
        "Astaghfirullah",
        "Astaghfirullah wa atubu ilayh",
        "Rabbi-ghfir li",
        "Allahummaghfir li"
    )
    private val durudText = "Allahumma salli wa sallim 'ala nabiyyina Muhammad"

    private var type = ReminderScheduler.TYPE_ISTIGFAR
    private var left = 0
    private var lastText = ""
    private lateinit var counter: TextView
    private lateinit var card: TextView

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
        // The backup notification is not needed once the screen is open.
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(ReminderScheduler.NOTIF_ID)
        } catch (e: Exception) {
        }

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        type = intent.getIntExtra(ReminderScheduler.EXTRA_TYPE, ReminderScheduler.TYPE_ISTIGFAR)
        left = Random.nextInt(3, 6) // 3, 4 or 5 readings
        val title = if (type == ReminderScheduler.TYPE_ISTIGFAR) "Istigfar reminder" else "Durud reminder"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(Glow.GOLD)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            Glow.textGlow(this, dp(10).toFloat())
            setPadding(0, 0, 0, dp(16))
        })

        // The number that goes down with every tap.
        val counterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }
        counter = TextView(this).apply {
            textSize = 40f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Glow.GOLD_LIGHT, Glow.GOLD, Glow.GOLD_DEEP)
            ).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
        }
        Glow.shadowGlow(counter, dp(8).toFloat())
        Glow.pulse(counter, dp(1).toFloat(), dp(10).toFloat())
        counterRow.addView(counter)
        counterRow.addView(TextView(this).apply {
            text = "left to read"
            textSize = 18f
            setTextColor(Glow.PALE)
            setPadding(dp(14), 0, 0, 0)
        })
        root.addView(counterRow)

        card = TextView(this).apply {
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = Glow.paleCard(dp(24).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        Glow.shadowGlow(card, dp(6).toFloat())
        root.addView(card)

        val close = Button(this).apply {
            text = "Close"
            setAllCaps(false)
            textSize = 22f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            stateListAnimator = null
            background = Glow.goldFill(dp(18).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)
            ).apply { setMargins(0, dp(18), 0, 0) }
            setOnClickListener { onReadingDone() }
        }
        Glow.shadowGlow(close, dp(8).toFloat())
        Glow.pulse(close, dp(1).toFloat(), dp(10).toFloat())
        root.addView(close)

        setContentView(Glow.screen(this, root))
        showReading()
    }

    /** Shows the number and the text for the next reading. */
    private fun showReading() {
        counter.text = left.toString()
        val text = if (type == ReminderScheduler.TYPE_ISTIGFAR) {
            var t = istigfarTexts.random()
            while (t == lastText && istigfarTexts.size > 1) t = istigfarTexts.random()
            t
        } else {
            durudText
        }
        lastText = text
        TextFit.apply(card, text, 18f, 60f)
    }

    private fun onReadingDone() {
        ReminderStats.add(this, type)
        left--
        if (left <= 0) {
            finish()
            return
        }
        showReading()
        counter.scaleX = 1.3f
        counter.scaleY = 1.3f
        counter.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** Back does nothing until every reading is done. Home still leaves the screen. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }

    override fun onDestroy() {
        showing = false
        DndGuard.endIfIdle(this)
        super.onDestroy()
    }

    companion object {
        /** True while a reminder screen is open, so a second one is not stacked on top. */
        @Volatile
        var showing: Boolean = false
    }
}
