package com.wakeup.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var observerRegistered = false

    // Backup watchdog: re-checks the alarm volume 5 times per second.
    private val volumeWatchdog = object : Runnable {
        override fun run() {
            forceMaxVolume()
            handler.postDelayed(this, 200)
        }
    }

    // Instant reaction: fires the moment ANY system volume setting changes.
    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            forceMaxVolume()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRinging = true

        val alarmIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 2, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Wake up!")
            .setContentText("Tap to open the alarm")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakeup:alarm")
            wakeLock?.acquire(15 * 60 * 1000L)
        }

        forceMaxVolume()
        startSound()

        if (!observerRegistered) {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
            observerRegistered = true
        }
        handler.removeCallbacks(volumeWatchdog)
        handler.post(volumeWatchdog)

        // Try to open the alarm screen directly (the full-screen notification is the backup).
        try {
            startActivity(alarmIntent)
        } catch (e: Exception) {
        }

        return START_STICKY
    }

    private fun startSound() {
        if (player != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (e: Exception) {
        }
    }

    private fun forceMaxVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.isStreamMute(AudioManager.STREAM_ALARM)) {
                am.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
            }
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            // Only write when it actually differs, so we never trigger our own observer in a loop.
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        isRinging = false
        handler.removeCallbacks(volumeWatchdog)
        if (observerRegistered) {
            contentResolver.unregisterContentObserver(volumeObserver)
            observerRegistered = false
        }
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "wakeup_alarm"
        private const val NOTIF_ID = 1001

        /** True while an alarm is ringing. The alarm screen uses this to close itself if stale. */
        @Volatile
        var isRinging: Boolean = false
    }
}
