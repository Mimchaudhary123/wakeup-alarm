package com.wakeup.alarm

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
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
 * (for example the power-off menu) is covered instantly and closed automatically.
 * The person does not have to press anything: the phone lands back on the alarm screen.
 */
class PowerGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var lastGuardAt = 0L
    private val removeCover = Runnable { removeCoverNow() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!AlarmService.isRinging) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own alarm screen is fine

        // Remember what Samsung opened, so we can check the detection later.
        val cls = event.className?.toString() ?: ""
        getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .edit().putString("guard_last", "$pkg / $cls").apply()

        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardAt < 1500) return
        lastGuardAt = now

        // 1) cover the screen so nothing underneath can be tapped
        showCover()
        // 2) close the foreign window automatically (twice, to be sure)
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            if (AlarmService.isRinging) performGlobalAction(GLOBAL_ACTION_BACK)
        }, 300)
        // 3) remove the cover again: the alarm screen is visible again
        handler.removeCallbacks(removeCover)
        handler.postDelayed(removeCover, 1200)
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
        removeCoverNow()
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
