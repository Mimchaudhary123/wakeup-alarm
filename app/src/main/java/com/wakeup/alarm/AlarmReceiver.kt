package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm tomorrow's alarm first, so it keeps repeating daily.
        AlarmScheduler.scheduleNext(context)
        context.startForegroundService(Intent(context, AlarmService::class.java))
    }
}
