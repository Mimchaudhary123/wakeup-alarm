package com.wakeup.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** startMin / endMin are minutes after midnight. endMin 0 means 12:00 AM (midnight). */
data class ReminderSettings(
    val on: Boolean,
    val startMin: Int,
    val endMin: Int,
    val everyMin: Int
)

object ReminderScheduler {
    const val TYPE_ISTIGFAR = 0
    const val TYPE_DURUD = 1
    const val EXTRA_TYPE = "reminder_type"
    const val NOTIF_ID = 2001

    private const val PREFS = "reminder_prefs"
    private const val KEY_ON = "on"
    private const val KEY_START = "start_min"
    private const val KEY_END = "end_min"
    private const val KEY_EVERY = "every_min"
    private const val KEY_LAST_TYPE = "last_type"
    private const val CHANNEL = "akhirah_reminders"
    private const val REQUEST_FIRE = 4242
    private const val REQUEST_OPEN = 4243

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun settings(c: Context): ReminderSettings {
        val p = prefs(c)
        return ReminderSettings(
            on = p.getBoolean(KEY_ON, true),
            startMin = p.getInt(KEY_START, 6 * 60),   // 6:00 AM
            endMin = p.getInt(KEY_END, 0),            // 12:00 AM
            everyMin = p.getInt(KEY_EVERY, 60)        // every hour
        )
    }

    fun setOn(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ON, on).apply()
    }

    fun saveTimes(c: Context, startMin: Int, endMin: Int, everyMin: Int) {
        prefs(c).edit()
            .putInt(KEY_START, startMin)
            .putInt(KEY_END, endMin)
            .putInt(KEY_EVERY, everyMin)
            .apply()
    }

    /**
     * The next reminder time, or null when reminders are off.
     * Slots are start, start+every, ... up to and including the end time.
     * If the end is not after the start, the window runs past midnight.
     */
    fun nextTrigger(c: Context, now: Long = System.currentTimeMillis()): Long? {
        val s = settings(c)
        if (!s.on) return null
        val every = maxOf(5, s.everyMin)
        val end = if (s.endMin <= s.startMin) s.endMin + 1440 else s.endMin
        var best: Long? = null
        // Yesterday's window can still have slots after midnight, tomorrow's is the fallback.
        for (dayOffset in -1..1) {
            val base = Calendar.getInstance()
            base.add(Calendar.DAY_OF_YEAR, dayOffset)
            base.set(Calendar.HOUR_OF_DAY, 0)
            base.set(Calendar.MINUTE, 0)
            base.set(Calendar.SECOND, 0)
            base.set(Calendar.MILLISECOND, 0)
            var m = s.startMin
            while (m <= end) {
                val slot = base.clone() as Calendar
                slot.add(Calendar.MINUTE, m)
                val t = slot.timeInMillis
                if (t > now + 1000 && (best == null || t < best)) best = t
                m += every
            }
        }
        return best
    }

    /** Sets (or cancels) the one system alarm for the next reminder. Safe to call any time. */
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePendingIntent(c)
        val t = nextTrigger(c)
        if (t == null) {
            am.cancel(pi)
            return
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun firePendingIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, REQUEST_FIRE, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Istigfar and Durud take turns. */
    fun nextType(c: Context): Int {
        val p = prefs(c)
        val t = 1 - p.getInt(KEY_LAST_TYPE, TYPE_DURUD)
        p.edit().putInt(KEY_LAST_TYPE, t).apply()
        return t
    }

    /** Opens the reminder screen. A notification is posted too, as a backup. */
    fun show(c: Context, type: Int) {
        if (ReminderActivity.showing) return
        val intent = Intent(c, ReminderActivity::class.java)
            .putExtra(EXTRA_TYPE, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(channel)
            val pi = PendingIntent.getActivity(
                c, REQUEST_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = if (type == TYPE_ISTIGFAR) "Istigfar reminder" else "Durud reminder"
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("Tap to open")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (e: Exception) {
        }
        try {
            c.startActivity(intent)
        } catch (e: Exception) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Always set the next reminder first, so the chain never breaks.
        ReminderScheduler.scheduleNext(context)
        if (!ReminderScheduler.settings(context).on) return
        // Never disturb a ringing alarm.
        if (AlarmService.isRinging) return
        ReminderScheduler.show(context, ReminderScheduler.nextType(context))
    }
}
