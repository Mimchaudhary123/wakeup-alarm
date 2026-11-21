package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Updating the app cancels its system alarms, so any "waiting" state is now meaningless.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WakeCheck.clearState(context, "app updated")
            try {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(AlarmService.KEY_RINGING_BEAT)
                    .remove(AlarmService.KEY_OLD_RINGING_SINCE)
                    .apply()
            } catch (e: Exception) {
            }
        }

        // If the app had switched Do Not Disturb off for an alarm and the phone went down, put it back.
        if (!AlarmService.isRinging && intent.action == Intent.ACTION_BOOT_COMPLETED) DndGuard.end(context)

        // After reboot, app update, or time/timezone change: set everything up again.
        AlarmScheduler.scheduleAll(context)
        ReminderScheduler.scheduleNext(context)
        PowerGuardService.ensureEnabled(context)
        // Samsung can switch the Power guard off right after an update, so look again in a minute.
        PermissionGate.watch(context)
        PermissionGate.scheduleWatch(context, 60_000L)

        // Backup for LockedBootReceiver. It only acts on state written before this boot,
        // so it can never start an alarm because of something that just happened.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && !AlarmService.isRinging) {
            LockedBootReceiver.resumeAlarmIfNeeded(context)
        }
    }
}
