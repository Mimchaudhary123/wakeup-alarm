package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Runs as soon as the phone has restarted, BEFORE the person unlocks it (Direct Boot).
 * If an alarm was ringing at the moment the phone went down, it starts ringing again right away.
 *
 * Safety rules, so a restart can never start an alarm by mistake:
 *  - the ringing heartbeat must have been written BEFORE this boot, and less than 5 minutes
 *    before it (the phone went down while the alarm was ringing),
 *  - anything written after this boot started (for example the check that was planned a moment
 *    ago) is ignored,
 *  - old leftovers are deleted.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        resumeAlarmIfNeeded(context)
    }

    companion object {
        private const val MAX_DOWNTIME_MS = 5 * 60 * 1000L

        /** When the phone started, in clock time. */
        fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        fun resumeAlarmIfNeeded(context: Context) {
            try {
                if (AlarmService.isRinging) return
                val boot = bootTime()
                // Restarting the phone during a wake-up check rings the alarm again.
                if (WakeCheck.ringIfPending(context, boot)) return
                val state = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)
                val beat = state.getLong(AlarmService.KEY_RINGING_BEAT, 0L)
                if (beat <= 0L) return
                if (beat < boot && boot - beat < MAX_DOWNTIME_MS) {
                    WakeCheck.log(context, "restart while ringing: the alarm rings again")
                    context.startForegroundService(Intent(context, AlarmService::class.java))
                } else if (beat < boot) {
                    // Old leftover from an earlier day: forget it.
                    state.edit().remove(AlarmService.KEY_RINGING_BEAT).apply()
                    WakeCheck.log(context, "old ringing leftover ignored")
                }
            } catch (e: Exception) {
            }
        }
    }
}
