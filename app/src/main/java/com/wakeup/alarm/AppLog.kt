package com.wakeup.alarm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short log of what the alarm did (planned, fired, sound, checks), shown under Diagnostics.
 * Kept in storage that also works before the first unlock after a restart.
 */
object AppLog {
    private const val KEY = "app_log"
    private const val MAX_LINES = 16

    private fun store(c: Context) = c.createDeviceProtectedStorageContext()
        .getSharedPreferences(AlarmService.STATE_PREFS, Context.MODE_PRIVATE)

    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    fun add(c: Context, msg: String) {
        try {
            val s = store(c)
            val lines = ArrayList<String>()
            val old = s.getString(KEY, "") ?: ""
            if (old.isNotEmpty()) lines.addAll(old.split("\n"))
            lines.add(time(System.currentTimeMillis()) + " " + msg)
            while (lines.size > MAX_LINES) lines.removeAt(0)
            s.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (e: Exception) {
        }
    }

    fun text(c: Context): String =
        try {
            val t = store(c).getString(KEY, "") ?: ""
            if (t.isEmpty()) "none yet" else t
        } catch (e: Exception) {
            "none yet"
        }
}
