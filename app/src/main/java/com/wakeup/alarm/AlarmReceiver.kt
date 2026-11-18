package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id > 0) {
            // A normal alarm: ignore it if it was deleted or switched off meanwhile.
            val alarm = AlarmScheduler.get(context, id) ?: return
            if (!alarm.enabled) return
            if (alarm.days == 0) {
                // One-time alarm: switch it off now that it is ringing.
                AlarmScheduler.upsert(context, alarm.copy(enabled = false))
            } else {
                // Repeating alarm: set the next one first, so it keeps repeating.
                AlarmScheduler.scheduleOne(context, alarm)
            }
        }
        // Remember the kind of ring, for the wake-up check after the task:
        // a real alarm gets the full check, the check test button a fast one, a plain test none.
        WakeCheck.markRing(
            context,
            when {
                id > 0 -> if (AlarmScheduler.get(context, id)?.check == true) WakeCheck.MODE_REAL else 0
                id == AlarmScheduler.TEST_CHECK_ID -> WakeCheck.MODE_FAST
                else -> 0
            }
        )
        // id 0 is the "ring in 10 seconds" test, -1 is an old alarm: both just ring.
        context.startForegroundService(Intent(context, AlarmService::class.java))
    }
}
