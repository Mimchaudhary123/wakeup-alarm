package com.wakeup.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The wake-up checks. After the alarm task is finished, a first check opens after 1 minute
 * and a second one 10 minutes after the task (each is 5 rounds of the number game).
 * If a check is not finished within 2 minutes, the alarm rings again (and the checks start over
 * after that task). The state lives in storage that also works before the first unlock.
 */
object WakeCheck {
    const val MODE_REAL = 1   // a real alarm: checks after 1 and 10 minutes, 2 minutes to finish each
    const val MODE_FAST = 2   // the test button: checks after 15 and 45 seconds, 1 minute to finish each
    const val CHECKS = 2

    private const val KEY_MODE = "wc_mode"
    private const val KEY_STAGE = "wc_stage"           // 1 = first check, 2 = second check
    private const val KEY_FINISH_AT = "wc_finish_at"   // when the alarm task was finished
    private const val KEY_CHECK_AT = "wc_check_at"
    private const val KEY_DEADLINE_AT = "wc_deadline_at"
    private const val PHASE_CHECK = 0
    private const val PHASE_DEADLINE = 1
    private const val EXTRA_PHASE = "wc_phase"
    private const val REQUEST_CHECK = 4246
    private const val REQUEST_DEADLINE = 4247
    private const val REQUEST_OPEN = 4248
    private const val CHANNEL = "akhirah_wake_check"
    private const val NOTIF_ID = 2003

    private fun store(c: Context) = c.createDeviceProtectedStorageContext()
        .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)

    /** Remembers what kind of ring this is. 0 = a plain test, no check afterwards. */
    fun markRing(c: Context, mode: Int) {
        try {
            store(c).edit().putInt(KEY_MODE, mode).apply()
        } catch (e: Exception) {
        }
    }

    private fun firstDelay(mode: Int) = if (mode == MODE_FAST) 15_000L else 60_000L
    private fun secondDelay(mode: Int) = if (mode == MODE_FAST) 45_000L else 10 * 60_000L
    private fun grace(mode: Int) = if (mode == MODE_FAST) 60_000L else 2 * 60_000L

    /** Called when the alarm task is finished: the first check is planned. */
    fun afterTaskFinished(c: Context) {
        try {
            val s = store(c)
            val mode = s.getInt(KEY_MODE, 0)
            if (mode == 0) return
            val now = System.currentTimeMillis()
            val at = now + firstDelay(mode)
            s.edit().putInt(KEY_STAGE, 1).putLong(KEY_FINISH_AT, now)
                .putLong(KEY_CHECK_AT, at).putLong(KEY_DEADLINE_AT, 0L).apply()
            schedule(c, PHASE_CHECK, at)
        } catch (e: Exception) {
        }
    }

    /** The planned time has come: open the check and start the countdown. */
    fun onCheckTime(c: Context) {
        val s = store(c)
        if (s.getLong(KEY_CHECK_AT, 0L) == 0L) return
        s.edit().putLong(KEY_CHECK_AT, 0L).apply()
        // An alarm is ringing right now, so the check is not needed.
        if (AlarmService.isRinging) return
        val deadline = System.currentTimeMillis() + grace(s.getInt(KEY_MODE, 0))
        s.edit().putLong(KEY_DEADLINE_AT, deadline).apply()
        schedule(c, PHASE_DEADLINE, deadline)
        show(c)
    }

    /** The countdown ran out without a finished check: the alarm rings again. */
    fun onDeadline(c: Context) {
        val s = store(c)
        if (s.getLong(KEY_DEADLINE_AT, 0L) == 0L) return // the check was finished in time
        s.edit().putLong(KEY_DEADLINE_AT, 0L).apply()
        cancelNotification(c)
        Haptics.stop(c)
        try {
            c.startForegroundService(Intent(c, AlarmService::class.java))
        } catch (e: Exception) {
        }
    }

    /** A check was finished in time. After the first one the second is planned, after the second all is done. */
    fun passed(c: Context) {
        try {
            val s = store(c)
            val stage = s.getInt(KEY_STAGE, 1)
            val mode = s.getInt(KEY_MODE, 0)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(c, PHASE_DEADLINE))
            cancelNotification(c)
            Haptics.stop(c)
            if (stage == 1 && mode != 0) {
                val now = System.currentTimeMillis()
                val finishAt = s.getLong(KEY_FINISH_AT, now)
                val at = maxOf(finishAt + secondDelay(mode), now + 20_000L)
                s.edit().putInt(KEY_STAGE, 2).putLong(KEY_DEADLINE_AT, 0L)
                    .putLong(KEY_CHECK_AT, at).apply()
                schedule(c, PHASE_CHECK, at)
            } else {
                s.edit().putLong(KEY_CHECK_AT, 0L).putLong(KEY_DEADLINE_AT, 0L)
                    .putInt(KEY_MODE, 0).putInt(KEY_STAGE, 1).apply()
                am.cancel(pending(c, PHASE_CHECK))
            }
        } catch (e: Exception) {
        }
    }

    fun stage(c: Context): Int = store(c).getInt(KEY_STAGE, 1)

    fun deadlineAt(c: Context): Long = store(c).getLong(KEY_DEADLINE_AT, 0L)

    /**
     * After a restart: if a check was still waiting, the alarm rings again at once.
     * (Restarting the phone must not be a way out of the check.) Returns true if it started.
     */
    fun ringIfPending(c: Context): Boolean {
        return try {
            val s = store(c)
            val pending = s.getLong(KEY_CHECK_AT, 0L) > 0L || s.getLong(KEY_DEADLINE_AT, 0L) > 0L
            if (!pending) return false
            s.edit().putLong(KEY_CHECK_AT, 0L).putLong(KEY_DEADLINE_AT, 0L).apply()
            c.startForegroundService(Intent(c, AlarmService::class.java))
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------

    private fun pending(c: Context, phase: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, if (phase == PHASE_CHECK) REQUEST_CHECK else REQUEST_DEADLINE,
            Intent(c, WakeCheckReceiver::class.java).putExtra(EXTRA_PHASE, phase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun schedule(c: Context, phase: Int, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c, phase)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun handle(c: Context, phase: Int) {
        if (phase == PHASE_CHECK) onCheckTime(c) else onDeadline(c)
    }

    fun phaseOf(intent: Intent): Int = intent.getIntExtra(EXTRA_PHASE, PHASE_CHECK)

    private fun cancelNotification(c: Context) {
        try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        } catch (e: Exception) {
        }
    }

    /** Zigzag vibration, a backup notification and the check screen itself. */
    private fun show(c: Context) {
        // Intense zigzag vibration, until the check is finished or the time runs out.
        Haptics.startZigzag(c)
        val intent = Intent(c, WakeCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL, "Wake-up check", NotificationManager.IMPORTANCE_HIGH)
            channel.enableVibration(false)
            nm.createNotificationChannel(channel)
            val pi = PendingIntent.getActivity(
                c, REQUEST_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Are you awake?")
                .setContentText("Finish the wake-up check, or the alarm rings again.")
                .setCategory(Notification.CATEGORY_ALARM)
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

class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WakeCheck.handle(context, WakeCheck.phaseOf(intent))
    }
}
