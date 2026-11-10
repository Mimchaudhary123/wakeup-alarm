package com.wakeup.alarm

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Power guard (no black cover).
 *
 * While the alarm rings, any other window that opens on top of the alarm screen (the
 * power-off menu) starts a Back-press loop: Back every 0.25 s, the alarm screen is
 * re-opened once, and the loop keeps going while new windows are reported (4 s at most per loop).
 * Back does nothing on the alarm screen (onBackPressed is empty), so extra presses are harmless.
 *
 * Nothing is drawn on the screen, so the alarm screen can never be covered or blocked.
 * The guard does NOT depend on the pin state. The pin state is only written to the status line.
 *
 * The last 5 decisions are written to the status line in the app ("Last other window...").
 */
class PowerGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var backActive = false
    private var backStart = 0L
    private var backUntil = 0L
    private var tickCount = 0

    private val log = ArrayList<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            if (!backActive) return
            if (!AlarmService.isRinging || SystemClock.elapsedRealtime() >= backUntil) {
                backActive = false
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            tickCount++
            if (tickCount == 3) bringAlarmBack()
            handler.postDelayed(this, 250)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!AlarmService.isRinging) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own alarm screen is fine

        val cls = (event.className?.toString() ?: "").substringAfterLast('.')
        val who = "$pkg/$cls"

        val now = SystemClock.elapsedRealtime()
        if (backActive) {
            backUntil = minOf(now + 1500, backStart + 4000)
            note("$who -> extend [pin=${pinState()}]")
            return
        }

        backActive = true
        backStart = now
        backUntil = now + 1500
        tickCount = 0
        handler.post(tick)
        note("$who -> GUARD [pin=${pinState()}]")
    }

    /** For the status line only: what Android says about the pin right now. */
    private fun pinState(): String {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            when (am.lockTaskModeState) {
                ActivityManager.LOCK_TASK_MODE_NONE -> "none"
                ActivityManager.LOCK_TASK_MODE_PINNED -> "pinned"
                else -> "locked"
            }
        } catch (e: Exception) {
            "?"
        }
    }

    private fun bringAlarmBack() {
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
        }
    }

    private fun note(msg: String) {
        log.add(timeFormat.format(Date()) + " " + msg)
        while (log.size > 5) log.removeAt(0)
        getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .edit().putString("guard_last", log.joinToString("\n")).apply()
    }

    override fun onInterrupt() {
        backActive = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        backActive = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        /** True when the person has switched the Power guard on in Accessibility settings. */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, PowerGuardService::class.java)
            return enabled.split(':').any {
                it.equals(me.flattenToString(), true) ||
                    it.equals(me.flattenToShortString(), true)
            }
        }
    }
}
