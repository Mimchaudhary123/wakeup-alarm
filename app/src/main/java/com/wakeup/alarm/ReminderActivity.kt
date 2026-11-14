package com.wakeup.alarm

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** The Istigfar reminder and the Durud reminder screen. Closes with its Close button. */
class ReminderActivity : Activity() {

    private val brown = Color.parseColor("#603E1E")
    private val paleGold = Color.parseColor("#F1D9A8")
    private val glowGold = Color.parseColor("#FFC34D")

    private val istigfarTexts = listOf(
        "Astaghfirullah",
        "Astaghfirullah wa atubu ilayh",
        "Rabbi-ghfir li",
        "Allahummaghfir li"
    )
    private val durudText = "Allahumma salli wa sallim 'ala nabiyyina Muhammad"

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

        val isIstigfar =
            intent.getIntExtra(ReminderScheduler.EXTRA_TYPE, ReminderScheduler.TYPE_ISTIGFAR) ==
                ReminderScheduler.TYPE_ISTIGFAR
        val title = if (isIstigfar) "Istigfar reminder" else "Durud reminder"
        val text = if (isIstigfar) istigfarTexts.random() else durudText

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(brown)
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }
        root.addView(TextView(this).apply {
            this.text = title
            textSize = 26f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        val card = TextView(this).apply {
            this.text = text
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(paleGold)
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        card.setAutoSizeTextTypeUniformWithConfiguration(
            20, 48, 1, TypedValue.COMPLEX_UNIT_SP
        )
        root.addView(card)
        root.addView(Button(this).apply {
            this.text = "Close"
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
            ).apply { setMargins(0, dp(16), 0, 0) }
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }

    companion object {
        /** True while a reminder screen is open, so a second one is not stacked on top. */
        @Volatile
        var showing: Boolean = false
    }
}
