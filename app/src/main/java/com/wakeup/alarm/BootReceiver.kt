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
        // Samsung can switch the Power guard off right after an update, so look again in a minute.
        PermissionGate.watch(context)
        PermissionGate.scheduleWatch(context, 60_000L)

        // Backup for LockedBootReceiver: an alarm that was ringing when the phone restarted rings again.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && !AlarmService.isRinging) {
            LockedBootReceiver.resumeAlarmIfNeeded(context)
        }
    }
}
