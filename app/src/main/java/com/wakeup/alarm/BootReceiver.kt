package com.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // After reboot, app update, or time/timezone change: schedule again.
        AlarmScheduler.scheduleNext(context)
    }
}
