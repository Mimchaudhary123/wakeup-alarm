package com.wakeup.alarm

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Power guard (SAFE version).
 * When a foreign window (for example the power-off menu) opens while the alarm rings:
 *  - a black cover is shown for AT MOST 1.5 seconds (hard timer removes it at 1.8 s),
 *  - Back is pressed every 0.25 s and the alarm screen is re-opened once,
 *  - the guard can NEVER be extended, and after it ends there is a 3 second cooldown.
 */
class PowerGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var guarding = false
    private var guardUntil = 0L
    private var cooldownUntil = 0L
    private var tickCount = 0

    private val hardStop = Runnable { stopGuard() }

    private val tick = object : Runnable {
        override fun run() {
            if (!guarding) return
            if (!AlarmService.isRinging || SystemClock.elapsedRealtime() >= guardUntil) {
                stopGuard()
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
        if (pkg == packageName) return // our own alarm screen / cover is fine

        // Remember what Samsung opened, so we can check the detection later.
        val cls = event.className?.toString() ?: ""
        getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .edit().putString("guard_last", "$pkg / $cls").apply()

        val now = SystemClock.elapsedRealtime()
        if (guarding) return          // never extend a running guard
        if (now < cooldownUntil) return // never chain guards

        guarding = true
        guardUntil = now + 1500
        tickCount = 0
        showCover()
        handler.post(tick)
        handler.postDelayed(hardStop, 1800)
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

    private fun stopGuard() {
        guarding = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(hardStop)
        removeCoverNow()
        cooldownUntil = SystemClock.elapsedRealtime() + 3000
    }

    private fun showCover() {
        if (cover != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(this).apply { setBackgroundColor(Color.BLACK) }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE
            )
            wm.addView(v, lp)
            cover = v
        } catch (e: Exception) {
        }
    }

    private fun removeCoverNow() {
        val v = cover ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        cover = null
    }

    override fun onInterrupt() {
        stopGuard()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeCoverNow()
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
