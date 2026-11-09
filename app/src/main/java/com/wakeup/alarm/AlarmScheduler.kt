package com.wakeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {
    private const val PREFS = "wakeup_prefs"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"
    const val KEY_ENABLED = "enabled"
    private const val KEY_NEXT = "next_trigger"

    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveAndEnable(c: Context, hour: Int, minute: Int) {
        prefs(c).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        scheduleNext(c)
    }

    fun disable(c: Context) {
        prefs(c).edit().putBoolean(KEY_ENABLED, false).remove(KEY_NEXT).apply()
        cancel(c)
    }

    /** Schedules the next daily occurrence of the saved time (if enabled). */
    fun scheduleNext(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean(KEY_ENABLED, false)) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getInt(KEY_HOUR, 6))
            set(Calendar.MINUTE, p.getInt(KEY_MINUTE, 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        scheduleAt(c, cal.timeInMillis)
    }

    /** The time of OUR next alarm (not other apps' alarms), or null if none is pending. */
    fun nextTriggerMillis(c: Context): Long? {
        val t = prefs(c).getLong(KEY_NEXT, 0L)
        return if (t > System.currentTimeMillis()) t else null
    }

    fun scheduleAt(c: Context, triggerAt: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock is exact, survives Doze, and needs no special permission.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), firePendingIntent(c))
        prefs(c).edit().putLong(KEY_NEXT, triggerAt).apply()
    }

    private fun firePendingIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 0, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(c))
    }
}
