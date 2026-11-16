package com.wakeup.alarm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Counts every Istigfar and Durud reading: per day, per month and in total. */
object ReminderStats {
    private const val PREFS = "reminder_stats"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun letter(type: Int) = if (type == ReminderScheduler.TYPE_ISTIGFAR) "i" else "d"

    private fun dayKey(type: Int, date: Date): String =
        letter(type) + "_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    /** One more reading, counted for today. */
    fun add(c: Context, type: Int) {
        val key = dayKey(type, Date())
        val p = prefs(c)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun today(c: Context, type: Int): Int = prefs(c).getInt(dayKey(type, Date()), 0)

    /** All readings in the current calendar month. */
    fun month(c: Context, type: Int): Int {
        val prefix = letter(type) + "_" + SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) + "-"
        var sum = 0
        for ((k, v) in prefs(c).all) {
            if (k.startsWith(prefix) && v is Int) sum += v
        }
        return sum
    }

    fun total(c: Context, type: Int): Int {
        val prefix = letter(type) + "_"
        var sum = 0
        for ((k, v) in prefs(c).all) {
            if (k.startsWith(prefix) && v is Int) sum += v
        }
        return sum
    }
}
