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
    private const val KEY_SAVED_RINGER = "dnd_saved_ringer"

    @Volatile
    private var lastNoAccessLogAt = 0L

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
            val hasVib = try {
                (c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).hasVibrator()
            } catch (e: Exception) {
                false
            }
            "Do Not Disturb: $filter, alarms allowed: $alarmsAllowed, app access: ${hasAccess(c)}" +
                "\nSound mode: $ringer, alarm vibration setting: $intensity, vibrator found: $hasVib"
        } catch (e: Exception) {
            "Do Not Disturb: unknown"
        }

    /**
     * While an alarm, a check or a reminder is active: switches Do Not Disturb off and leaves the
     * SILENT sound mode (to vibrate), remembering what they were. Needs the Do Not Disturb access.
     * Returns true if it really changed something.
     */
    fun begin(c: Context): Boolean {
        try {
            val n = nm(c)
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val filter = n.currentInterruptionFilter
            val dndOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val silent = am.ringerMode == AudioManager.RINGER_MODE_SILENT
            if (!dndOn && !silent) return false
            if (!hasAccess(c)) {
                val now = System.currentTimeMillis()
                if (now - lastNoAccessLogAt > 6 * 60 * 60 * 1000L) {
                    lastNoAccessLogAt = now
                    AppLog.add(c, "Do Not Disturb or silent mode is ON and the app has no Do Not Disturb access: sound and vibration may be silent")
                }
                return false
            }
            val s = store(c)
            var changed = false
            if (dndOn) {
                if (!s.contains(KEY_SAVED)) s.edit().putInt(KEY_SAVED, filter).apply()
                n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                changed = true
            }
            if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                if (!s.contains(KEY_SAVED_RINGER)) {
                    s.edit().putInt(KEY_SAVED_RINGER, AudioManager.RINGER_MODE_SILENT).apply()
                }
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                changed = true
            }
            if (changed) AppLog.add(c, "Do Not Disturb / silent mode switched off while the alarm is active")
            return changed
        } catch (e: Exception) {
            AppLog.add(c, "could not switch Do Not Disturb off: " + e.javaClass.simpleName)
            return false
        }
    }

    /** Puts Do Not Disturb back, but only when no alarm rings and no wake-up check is open. */
    fun endIfIdle(c: Context) {
        if (!AlarmService.isRinging && !WakeCheckActivity.showing) end(c)
    }

    /** Puts Do Not Disturb and the sound mode back the way they were. */
    fun end(c: Context) {
        try {
            val s = store(c)
            val hadFilter = s.contains(KEY_SAVED)
            val hadRinger = s.contains(KEY_SAVED_RINGER)
            if (!hadFilter && !hadRinger) return
            val savedFilter = s.getInt(KEY_SAVED, NotificationManager.INTERRUPTION_FILTER_ALL)
            val savedRinger = s.getInt(KEY_SAVED_RINGER, AudioManager.RINGER_MODE_NORMAL)
            s.edit().remove(KEY_SAVED).remove(KEY_SAVED_RINGER).apply()
            if (!hasAccess(c)) return
            if (hadFilter && savedFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm(c).setInterruptionFilter(savedFilter)
            }
            if (hadRinger) {
                (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = savedRinger
            }
            AppLog.add(c, "Do Not Disturb / sound mode put back")
        } catch (e: Exception) {
        }
    }
}
