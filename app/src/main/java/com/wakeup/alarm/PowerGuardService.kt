package com.wakeup.alarm

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Power guard.
 *
 * When another window (the power-off menu) opens while the alarm rings, two things start at
 * the same moment:
 *
 * 1) CLOSING (harmless): Back every 0.12 s, the alarm screen re-opened at about 0.24 s,
 *    0.9 s and 1.9 s. Back does nothing on the alarm screen (onBackPressed is empty).
 *    A new window reported meanwhile keeps the loop going (4 s at most per loop).
 *
 * 2) TOUCH BLOCKER (invisible, nothing is drawn): swallows every tap for 2 seconds, so the
 *    Power off / Restart buttons cannot be tapped even while the menu is still visible.
 *    Safety: it removes itself with its own timer after 2 s (never extended), and cannot
 *    appear again for 3 s afterwards, so the alarm screen is always usable for 3 s in a row.
 *
 * The last 5 decisions are written to the status line in the app ("Last other window...").
 */
class PowerGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var blocker: View? = null
    private var blockerAllowedAt = 0L

    private var backActive = false
    private var backStart = 0L
    private var backUntil = 0L
    private var tickCount = 0

    private val log = ArrayList<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val removeBlocker = Runnable { removeBlockerNow() }

    private val tick = object : Runnable {
        override fun run() {
            if (!backActive) return
            if (!AlarmService.isRinging) {
                backActive = false
                removeBlockerNow()
                return
            }
            if (SystemClock.elapsedRealtime() >= backUntil) {
                backActive = false
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            tickCount++
            if (tickCount == 2 || tickCount == 8 || tickCount == 16) bringAlarmBack()
            handler.postDelayed(this, 120)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!AlarmService.isRinging) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own alarm screen / blocker is fine

        val cls = (event.className?.toString() ?: "").substringAfterLast('.')
        val who = "$pkg/$cls"
        val now = SystemClock.elapsedRealtime()

        if (backActive) {
            backUntil = minOf(now + 2000, backStart + 4000)
            val b = tryBlocker(now)
            note("$who -> extend" + (if (b) " +block" else "") + " [pin=${pinState()}]")
            return
        }

        backActive = true
        backStart = now
        backUntil = now + 2000
        tickCount = 0
        val b = tryBlocker(now)
        handler.post(tick)
        note("$who -> GUARD" + (if (b) " +block" else " (no block)") + " [pin=${pinState()}]")
    }

    /** Starts the invisible touch blocker if the last one ended at least 3 s ago. */
    private fun tryBlocker(now: Long): Boolean {
        if (blocker != null || now < blockerAllowedAt) return false
        showBlocker()
        if (blocker == null) return false
        blockerAllowedAt = now + 2000 + 3000
        handler.removeCallbacks(removeBlocker)
        handler.postDelayed(removeBlocker, 2000)
        return true
    }

    private fun showBlocker() {
        if (blocker != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(this)
            v.setOnTouchListener { _, _ -> true } // swallow every touch
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(v, lp)
            blocker = v
        } catch (e: Exception) {
        }
    }

    private fun removeBlockerNow() {
        val v = blocker ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        blocker = null
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
        handler.removeCallbacks(removeBlocker)
        removeBlockerNow()
    }

    override fun onDestroy() {
        backActive = false
        handler.removeCallbacksAndMessages(null)
        removeBlockerNow()
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
