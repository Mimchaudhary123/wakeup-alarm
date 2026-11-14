package com.wakeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One alarm.
 * days: bit 0 = Monday ... bit 6 = Sunday. 0 means "one time" on year/month/day
 * (month is 0-11 like Calendar). label is optional.
 */
data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val days: Int,
    val year: Int,
    val month: Int,
    val day: Int,
    val label: String
)

object AlarmScheduler {
    private const val PREFS = "wakeup_prefs"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_NEXT_ID = "alarms_next_id"

    // Old single-alarm keys (only used once, to carry an existing alarm over).
    private const val OLD_HOUR = "hour"
    private const val OLD_MINUTE = "minute"
    private const val OLD_ENABLED = "enabled"

    const val EXTRA_ALARM_ID = "alarm_id"
    const val TEST_ID = 0
    private const val TEST_REQUEST = 999999

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    //  Storage
    // ------------------------------------------------------------------

    fun all(c: Context): List<AlarmItem> {
        val p = prefs(c)
        val raw = p.getString(KEY_ALARMS, null)
        if (raw == null) {
            migrateOldAlarm(c)
            return parse(prefs(c).getString(KEY_ALARMS, "[]") ?: "[]")
        }
        return parse(raw)
    }

    private fun parse(raw: String): List<AlarmItem> {
        val out = ArrayList<AlarmItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    AlarmItem(
                        id = o.getInt("id"),
                        hour = o.getInt("hour"),
                        minute = o.getInt("minute"),
                        enabled = o.getBoolean("enabled"),
                        days = o.getInt("days"),
                        year = o.optInt("year", 2000),
                        month = o.optInt("month", 0),
                        day = o.optInt("day", 1),
                        label = o.optString("label", "")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveAll(c: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("hour", a.hour)
                    .put("minute", a.minute)
                    .put("enabled", a.enabled)
                    .put("days", a.days)
                    .put("year", a.year)
                    .put("month", a.month)
                    .put("day", a.day)
                    .put("label", a.label)
            )
        }
        prefs(c).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    /** The app used to have ONE daily alarm. If it was on, it becomes alarm number 1. */
    private fun migrateOldAlarm(c: Context) {
        val p = prefs(c)
        val list = ArrayList<AlarmItem>()
        if (p.getBoolean(OLD_ENABLED, false)) {
            list.add(
                AlarmItem(
                    id = 1,
                    hour = p.getInt(OLD_HOUR, 6),
                    minute = p.getInt(OLD_MINUTE, 0),
                    enabled = true,
                    days = 127,
                    year = 2000, month = 0, day = 1,
                    label = ""
                )
            )
            p.edit().putInt(KEY_NEXT_ID, 2).apply()
        }
        saveAll(c, list)
        // Cancel the old single alarm (it used request code 0).
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val old = PendingIntent.getBroadcast(
                c, 0, Intent(c, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(old)
        } catch (e: Exception) {
        }
    }

    fun get(c: Context, id: Int): AlarmItem? = all(c).firstOrNull { it.id == id }

    fun newId(c: Context): Int {
        val p = prefs(c)
        val used = all(c).maxOfOrNull { it.id } ?: 0
        val id = maxOf(p.getInt(KEY_NEXT_ID, 1), used + 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    /** Adds the alarm or replaces the one with the same id. */
    fun upsert(c: Context, item: AlarmItem) {
        val list = ArrayList(all(c))
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list[i] = item else list.add(item)
        saveAll(c, list)
    }

    fun delete(c: Context, id: Int) {
        val list = all(c).filter { it.id != id }
        saveAll(c, list)
        cancel(c, id)
    }

    // ------------------------------------------------------------------
    //  When does an alarm ring next?
    // ------------------------------------------------------------------

    /** Next time this alarm will ring, or null if it never will (off, or the date has passed). */
    fun nextTrigger(a: AlarmItem, now: Long = System.currentTimeMillis()): Long? {
        if (!a.enabled) return null
        if (a.days == 0) {
            val cal = Calendar.getInstance()
            cal.clear()
            cal.set(a.year, a.month, a.day, a.hour, a.minute, 0)
            return if (cal.timeInMillis > now + 1000) cal.timeInMillis else null
        }
        for (offset in 0..7) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.set(Calendar.HOUR_OF_DAY, a.hour)
            cal.set(Calendar.MINUTE, a.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val bit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0 ... Sunday = 6
            if (((a.days shr bit) and 1) == 1 && cal.timeInMillis > now + 1000) {
                return cal.timeInMillis
            }
        }
        return null
    }

    /** The soonest ring time over all alarms that are on. */
    fun nextOverall(c: Context): Pair<AlarmItem, Long>? {
        var best: Pair<AlarmItem, Long>? = null
        for (a in all(c)) {
            val t = nextTrigger(a) ?: continue
            if (best == null || t < best.second) best = Pair(a, t)
        }
        return best
    }

    // ------------------------------------------------------------------
    //  Scheduling
    // ------------------------------------------------------------------

    /** Sets (or removes) the system alarm for one alarm item. */
    fun scheduleOne(c: Context, a: AlarmItem) {
        val t = nextTrigger(a)
        if (t == null) {
            cancel(c, a.id)
            // A one-time alarm whose date has passed switches itself off.
            if (a.enabled && a.days == 0) upsert(c, a.copy(enabled = false))
        } else {
            scheduleAt(c, a.id, t)
        }
    }

    fun scheduleAll(c: Context) {
        for (a in all(c)) scheduleOne(c, a)
    }

    fun scheduleTest(c: Context, delayMillis: Long) {
        scheduleAt(c, TEST_ID, System.currentTimeMillis() + delayMillis)
    }

    private fun scheduleAt(c: Context, id: Int, triggerAt: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock is exact, survives Doze, and needs no special permission.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), firePendingIntent(c, id))
    }

    private fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(c, id))
    }

    private fun firePendingIntent(c: Context, id: Int): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, id)
        val request = if (id == TEST_ID) TEST_REQUEST else id
        return PendingIntent.getBroadcast(
            c, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
