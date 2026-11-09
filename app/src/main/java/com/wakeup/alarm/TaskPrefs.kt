package com.wakeup.alarm

import android.content.Context

/** Remembers which task is required to stop the alarm. */
object TaskPrefs {
    const val TASK_NUMBERS = "numbers"
    const val TASK_QR = "qr"

    private const val FILE = "wakeup_prefs"
    private const val KEY = "stop_task"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, TASK_NUMBERS) ?: TASK_NUMBERS

    fun set(context: Context, task: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, task).apply()
    }
}
