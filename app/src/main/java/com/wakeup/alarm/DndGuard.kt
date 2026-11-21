package com.wakeup.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings

/**
 * Do Not Disturb can silence the sound AND the vibration of an alarm app (when "alarms" are not
 * allowed, or in total silence). With the "Do Not Disturb access" permission the app switches
 * Do Not Disturb off while an alarm or a wake-up check is active, and puts it back afterwards.
 */
object DndGuard {
    private const val KEY_SAVED = "dnd_saved_filter"

    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun store(c: Context) = c.createDeviceProtectedStorageContext()
        .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)

    fun hasAccess(c: Context): Boolean =
        try {
            nm(c).isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            false
        }

    /** A one-line description of the Do Not Disturb situation, for Diagnostics. */
    fun describe(c: Context): String =
        try {
            val n = nm(c)
            val filter = when (n.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms only"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "total silence"
                else -> "unknown"
            }
            val alarmsAllowed =
                (n.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val ringer = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "silent"
            }
            val intensity = try {
                Settings.System.getInt(c.contentResolver, "alarm_vibration_intensity", -1)
            } catch (e: Exception) {
                -1
            }
            "Do Not Disturb: $filter, alarms allowed: $alarmsAllowed, app access: ${hasAccess(c)}" +
                "\nSound mode: $ringer, alarm vibration setting: $intensity"
        } catch (e: Exception) {
            "Do Not Disturb: unknown"
        }

    /**
     * Switches Do Not Disturb off (remembering what it was) while an alarm or check is active.
     * Returns true if it really switched something off.
     */
    fun begin(c: Context): Boolean {
        try {
            val n = nm(c)
            val current = n.currentInterruptionFilter
            if (current == NotificationManager.INTERRUPTION_FILTER_ALL ||
                current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) return false
            if (!hasAccess(c)) {
                AppLog.add(c, "Do Not Disturb is ON and the app has no access to switch it off: sound and vibration may be silent")
                return false
            }
            val s = store(c)
            if (!s.contains(KEY_SAVED)) s.edit().putInt(KEY_SAVED, current).apply()
            n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            AppLog.add(c, "Do Not Disturb was on: switched off while the alarm is active")
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Puts Do Not Disturb back, but only when no alarm rings and no wake-up check is open. */
    fun endIfIdle(c: Context) {
        if (!AlarmService.isRinging && !WakeCheckActivity.showing) end(c)
    }

    /** Puts Do Not Disturb back the way it was. */
    fun end(c: Context) {
        try {
            val s = store(c)
            if (!s.contains(KEY_SAVED)) return
            val saved = s.getInt(KEY_SAVED, NotificationManager.INTERRUPTION_FILTER_ALL)
            s.edit().remove(KEY_SAVED).apply()
            if (hasAccess(c) && saved != NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm(c).setInterruptionFilter(saved)
                AppLog.add(c, "Do Not Disturb put back")
            }
        } catch (e: Exception) {
        }
    }
}
