package com.wakeup.alarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {

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

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
        }
        root.addView(TextView(this).apply {
            text = "WAKE UP!"
            textSize = 44f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Part 2 test screen.\nVolume keys and Back are locked.\nThe QR-scan task is added in Part 3."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, (16 * d).toInt(), 0, (32 * d).toInt())
        })
        root.addView(Button(this).apply {
            text = "Stop alarm (temporary button)"
            setOnClickListener {
                stopService(Intent(this@AlarmActivity, AlarmService::class.java))
                finish()
            }
        })
        setContentView(root)
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
