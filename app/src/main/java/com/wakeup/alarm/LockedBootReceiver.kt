package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Runs as soon as the phone has restarted, BEFORE the person unlocks it (Direct Boot).
 * If an alarm was ringing when the phone restarted, it starts ringing again right away.
 * Only the ringing state is read here, from storage that is readable before the first unlock.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        resumeAlarmIfNeeded(context)
    }

    companion object {
        /** Starts the alarm again if it was ringing less than 30 minutes before the restart. */
        fun resumeAlarmIfNeeded(context: Context) {
            try {
                if (AlarmService.isRinging) return
                // Restarting the phone during the wake-up check rings the alarm again.
                if (WakeCheck.ringIfPending(context)) return
                val state = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)
                val since = state.getLong(AlarmService.KEY_RINGING_SINCE, 0L)
                val now = System.currentTimeMillis()
                if (since > 0L && now >= since && now - since < 30 * 60 * 1000L) {
                    context.startForegroundService(Intent(context, AlarmService::class.java))
                } else if (since > 0L) {
                    state.edit().remove(AlarmService.KEY_RINGING_SINCE).apply()
                }
            } catch (e: Exception) {
            }
        }
    }
}
