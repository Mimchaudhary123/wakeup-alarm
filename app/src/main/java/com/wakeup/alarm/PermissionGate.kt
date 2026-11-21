package com.wakeup.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * One thing the app needs. manual = true means Android cannot tell us whether it is on,
 * so the person confirms it once with the "I turned it on" button.
 */
class PermStep(
    val id: String,
    val title: String,
    val why: String,
    val how: String,
    val manual: Boolean,
    val isOk: (Context) -> Boolean,
    val intent: (Context) -> Intent
)

object PermissionGate {
    private const val PREFS = "gate_prefs"
    private const val CHANNEL = "akhirah_attention"
    private const val NOTIF_ID = 2002
    private const val REQUEST_WATCH = 4244
    private const val REQUEST_OPEN = 4245
    private const val WATCH_EVERY_MS = 30 * 60 * 1000L

    /** Everything the app needs, in the order the person is walked through it. */
    fun steps(): List<PermStep> = listOf(
        PermStep(
            "notif", "Notifications",
            "Lets the alarm and the reminders show on the lock screen.",
            "Turn Notifications on for For Akhirah.",
            false,
            { c ->
                (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .areNotificationsEnabled()
            },
            { c ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            }
        ),
        PermStep(
            "overlay", "Display over other apps",
            "Brings the alarm screen back if someone leaves it.",
            "Find For Akhirah in the list and switch it on.",
            false,
            { c -> Settings.canDrawOverlays(c) },
            { c ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))
            }
        ),
        PermStep(
            "dnd", "Do Not Disturb access",
            "Do Not Disturb can silence the alarm sound and the vibration. With this, the app switches it off only while an alarm or check is active, then puts it back.",
            "Find For Akhirah in the list and switch it on.",
            false,
            { c -> DndGuard.hasAccess(c) },
            { _ -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
        ),
        PermStep(
            "battery", "Run in the background",
            "Stops the phone from putting the alarm and the reminders to sleep.",
            "Tap Allow on the box that opens.",
            false,
            { c ->
                (c.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(c.packageName)
            },
            { c ->
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + c.packageName)
                )
            }
        ),
        PermStep(
            "guard", "Power guard",
            "Blocks the power-off menu while the alarm rings.",
            "Open For Akhirah power guard, switch it on, and accept the warning.",
            false,
            { c -> PowerGuardService.isEnabled(c) },
            { _ -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        ),
        PermStep(
            "pin", "Pin windows",
            "Locks the alarm screen so it cannot be left.",
            "Open Biometrics and security > Other security settings > Pin windows and switch it on. " +
                "Then come back and tap I turned it on.",
            true,
            { c -> confirmed(c, "pin") },
            { _ -> Intent(Settings.ACTION_SECURITY_SETTINGS) }
        ),
        PermStep(
            "sleep", "Sleeping apps",
            "Samsung can put unused apps to sleep, and a sleeping app misses its alarm.",
            "Open Battery, turn off Put unused apps to sleep, and make sure For Akhirah is in no " +
                "sleeping list. Then come back and tap I turned it on.",
            true,
            { c -> confirmed(c, "sleep") },
            { c ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
            }
        )
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun confirmed(c: Context, id: String): Boolean = prefs(c).getBoolean("ok_$id", false)

    fun confirm(c: Context, id: String) {
        prefs(c).edit().putBoolean("ok_$id", true).apply()
    }

    /** Steps Android can check by itself and that are currently off. */
    fun missingDetectable(c: Context): List<PermStep> =
        steps().filter { !it.manual && !it.isOk(c) }

    // ------------------------------------------------------------------
    //  Background watch: every 30 minutes, and once a minute after a restart or update
    // ------------------------------------------------------------------

    fun scheduleWatch(c: Context, delayMs: Long = WATCH_EVERY_MS) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, watchIntent(c)
            )
        } catch (e: Exception) {
        }
    }

    private fun watchIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, REQUEST_WATCH, Intent(c, PermissionWatchReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Shows a "needs attention" notification while an alarm or the reminders are on and
     * something is switched off. Removes it again once everything is back on.
     */
    fun watch(c: Context) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val needed = AlarmScheduler.all(c).any { it.enabled } || ReminderScheduler.settings(c).on
            val missing = missingDetectable(c)
            if (!needed || missing.isEmpty()) {
                nm.cancel(NOTIF_ID)
                return
            }
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Needs attention", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                c, REQUEST_OPEN,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = missing.joinToString(", ") { it.title } + " is turned off. Tap to fix it."
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("For Akhirah needs attention")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (e: Exception) {
        }
    }
}

class PermissionWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PermissionGate.watch(context)
        PermissionGate.scheduleWatch(context) // keep the chain going
    }
}
