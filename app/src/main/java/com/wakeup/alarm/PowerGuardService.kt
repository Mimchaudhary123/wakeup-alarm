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
 * Power guard: while the alarm rings, anything that opens on top of the alarm screen
 * (for example the power-off menu) is covered instantly and then closed again and again
 * (Back press every 0.25 s, alarm screen re-opened every 1 s) for up to 5 seconds.
 * The cover is removed only when this guard period is over.
 */
class PowerGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var guarding = false
    private var guardUntil = 0L
    private var tickCount = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!AlarmService.isRinging || SystemClock.elapsedRealtime() >= guardUntil) {
                stopGuard()
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            tickCount++
            if (tickCount % 4 == 2) bringAlarmBack()
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

        // Cover the screen and (re)start the repeated closing for 5 seconds.
        showCover()
        guardUntil = SystemClock.elapsedRealtime() + 5000
        if (!guarding) {
            guarding = true
            tickCount = 0
            handler.post(tick)
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

    private fun stopGuard() {
        guarding = false
        handler.removeCallbacks(tick)
        removeCoverNow()
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
