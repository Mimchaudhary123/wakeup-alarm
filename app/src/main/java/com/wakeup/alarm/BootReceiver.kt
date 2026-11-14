package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // After reboot, app update, or time/timezone change: set everything up again.
        AlarmScheduler.scheduleAll(context)
        ReminderScheduler.scheduleNext(context)
        PowerGuardService.ensureEnabled(context)

        // If the phone restarted while an alarm was ringing (and the task was not finished),
        // the alarm rings again. Only within 30 minutes, so an old leftover never wakes anyone.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val state = context.getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)
            val since = state.getLong(AlarmService.KEY_RINGING_SINCE, 0L)
            val now = System.currentTimeMillis()
            if (since > 0L && now >= since && now - since < 30 * 60 * 1000L) {
                try {
                    context.startForegroundService(Intent(context, AlarmService::class.java))
                } catch (e: Exception) {
                }
            } else if (since > 0L) {
                state.edit().remove(AlarmService.KEY_RINGING_SINCE).apply()
            }
        }
    }
}
