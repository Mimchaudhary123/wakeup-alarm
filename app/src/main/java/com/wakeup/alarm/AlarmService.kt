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
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.media.audiofx.LoudnessEnhancer
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import java.io.File

class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var observerRegistered = false
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerOn = false
    private var vibrator: Vibrator? = null

    // Backup watchdog: re-checks the alarm volume 5 times per second.
    private var watchTicks = 0
    private var soundRetryLogged = false

    private val volumeWatchdog = object : Runnable {
        override fun run() {
            enforceVolume()
            watchTicks++
            if (watchTicks % 10 == 0) ensureSound() // every 2 seconds
            if (watchTicks % 150 == 0) Haptics.startZigzag(this@AlarmService) // every 30 seconds, in case it was dropped
            handler.postDelayed(this, 200)
        }
    }

    // Brings the alarm screen back if the person leaves it (for example with Home).
    // Needs the "Display over other apps" permission, otherwise Android blocks it.
    private val relaunchWatch = object : Runnable {
        override fun run() {
            if (isRinging && !AlarmActivity.isForeground && Settings.canDrawOverlays(this@AlarmService)) {
                try {
                    startActivity(
                        Intent(this@AlarmService, AlarmActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } catch (e: Exception) {
                }
            }
            handler.postDelayed(this, 700)
        }
    }

    // Instant reaction: fires the moment ANY system volume setting changes.
    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            enforceVolume()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Do Not Disturb needs a moment to switch off; vibrations started before that are dropped,
    // so the vibration is started once more shortly after, and the state is written to the log.
    private val vibrationRestart = Runnable { if (isRinging) Haptics.startZigzag(this) }
    private val dndStateLog = Runnable {
        AppLog.add(this, "after switching Do Not Disturb: " + DndGuard.describe(this).replace("\n", " | "))
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            try {
                stateStore().edit().putLong(KEY_RINGING_BEAT, System.currentTimeMillis()).apply()
            } catch (e: Exception) {
            }
            handler.postDelayed(this, 10_000)
        }
    }

    /** Storage that works even before the phone is unlocked after a restart. */
    private fun stateStore() =
        createDeviceProtectedStorageContext().getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRinging = true
        soundRetryLogged = false
        AppLog.add(this, "alarm service started")
        if (DndGuard.begin(this)) {
            handler.postDelayed(vibrationRestart, 900)
            handler.postDelayed(dndStateLog, 1800)
        }
        // Heartbeat: while the alarm rings, the time is written every 10 seconds. Only if the phone
        // goes down while this heartbeat is fresh can the alarm start again after the restart.
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)

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

        startVibration()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakeup:alarm")
            wakeLock?.acquire(15 * 60 * 1000L)
        }

        enforceVolume()
        startSound()

        if (!observerRegistered) {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
            observerRegistered = true
        }
        handler.removeCallbacks(volumeWatchdog)
        handler.post(volumeWatchdog)
        handler.removeCallbacks(relaunchWatch)
        handler.postDelayed(relaunchWatch, 2500)

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
            val speaker = builtInSpeaker()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                // Always the phone's own speaker, never Bluetooth earbuds lying somewhere unheard.
                if (speaker != null) setPreferredDevice(speaker)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            setupBoost()
            AppLog.add(
                this,
                "sound playing (alarm tone), phone speaker forced: " + (speaker != null) +
                    ", outputs: " + outputsText()
            )
        } catch (e: Exception) {
            AppLog.add(this, "alarm tone failed (" + e.javaClass.simpleName + "), using a backup sound")
            startSoundFromFile()
        }
    }

    /** The phone's own speaker, if there is one. */
    private fun builtInSpeaker(): AudioDeviceInfo? =
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } catch (e: Exception) {
            null
        }

    /** Which audio outputs are connected right now (for the log). */
    private fun outputsText(): String =
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .joinToString(", ") {
                    when (it.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headset"
                        else -> "type " + it.type
                    }
                }
        } catch (e: Exception) {
            "unknown"
        }

    /** Every 2 seconds: if the sound stopped for any reason, start it again. */
    private fun ensureSound() {
        try {
            val p = player
            if (p != null && p.isPlaying) return
            if (!soundRetryLogged) {
                soundRetryLogged = true
                AppLog.add(this, "the sound was not playing: starting it again")
            }
            try {
                p?.release()
            } catch (e: Exception) {
            }
            player = null
            try {
                enhancer?.release()
            } catch (e: Exception) {
            }
            enhancer = null
            enhancerOn = false
            startSound()
        } catch (e: Exception) {
        }
    }

    /** Finds a ringtone file in the system folders (they can be read before the first unlock). */
    private fun systemSoundPath(): String? {
        for (dir in listOf(
            "/system/media/audio/alarms", "/product/media/audio/alarms",
            "/system/media/audio/ringtones", "/product/media/audio/ringtones"
        )) {
            val files = File(dir).listFiles()?.filter { it.isFile }?.sortedBy { it.name }
            if (files != null && files.isNotEmpty()) return files[0].absolutePath
        }
        return null
    }

    /** Backup sound, used when the normal alarm sound cannot be opened (for example before the first unlock). */
    private fun startSoundFromFile() {
        try {
            if (player != null) return
            val path = systemSoundPath() ?: return
            val speaker = builtInSpeaker()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(path)
                if (speaker != null) setPreferredDevice(speaker)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            setupBoost()
            AppLog.add(this, "sound playing (backup sound file), outputs: " + outputsText())
        } catch (e: Exception) {
            AppLog.add(this, "backup sound failed too (" + e.javaClass.simpleName + ")")
        }
    }

    /** Extra loudness on top of the maximum alarm volume (see BOOST_MILLIBELS). */
    private fun setupBoost() {
        try {
            val p = player ?: return
            val e = LoudnessEnhancer(p.audioSessionId)
            e.setTargetGain(BOOST_MILLIBELS)
            e.setEnabled(true)
            enhancer = e
            enhancerOn = true
        } catch (ex: Exception) {
        }
    }

    private fun setBoost(on: Boolean) {
        val e = enhancer ?: return
        if (on == enhancerOn) return
        try {
            e.setEnabled(on)
            enhancerOn = on
        } catch (ex: Exception) {
        }
    }

    /** Intense zigzag vibration that starts at once and repeats until the alarm is stopped. */
    private fun startVibration() {
        Haptics.startZigzag(this)
    }

    private fun enforceVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.isStreamMute(AudioManager.STREAM_ALARM)) {
                am.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
            }
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            // During the one-time quiet period the target is 50%, otherwise max (plus the boost).
            val quiet = SystemClock.elapsedRealtime() < quietUntil
            val target = if (quiet) {
                maxOf(1, Math.round(max * QUIET_FRACTION))
            } else {
                max
            }
            setBoost(!quiet)
            // Only write when it actually differs, so we never trigger our own observer in a loop.
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != target) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        isRinging = false
        // The alarm was stopped on purpose (task done): forget it.
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(vibrationRestart)
        handler.removeCallbacks(dndStateLog)
        // Put Do Not Disturb back, unless a wake-up check is open and still needs it off.
        if (!WakeCheckActivity.showing) DndGuard.end(this)
        try {
            stateStore().edit().remove(KEY_RINGING_BEAT).apply()
        } catch (e: Exception) {
        }
        quietUntil = 0L
        quietUsed = false
        handler.removeCallbacks(volumeWatchdog)
        handler.removeCallbacks(relaunchWatch)
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
        Haptics.stop(this)
        try {
            enhancer?.release()
        } catch (e: Exception) {
        }
        enhancer = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val STATE_PREFS = "alarm_state"
        const val KEY_RINGING_BEAT = "ringing_beat"
        const val KEY_OLD_RINGING_SINCE = "ringing_since" // from older versions, only cleaned up

        private const val CHANNEL_ID = "wakeup_alarm"
        private const val NOTIF_ID = 1001

        /** True while an alarm is ringing. The alarm screen uses this to close itself if stale. */
        @Volatile
        var isRinging: Boolean = false

        private const val QUIET_MILLIS = 30_000L
        private const val QUIET_FRACTION = 0.5f

        /**
         * Loudness boost on top of the maximum alarm volume, in millibels (100 = 1 dB).
         * 800 = about +8 dB. Raise it for louder, lower it if the sound crackles. 0 = no boost.
         */
        private const val BOOST_MILLIBELS = 800

        @Volatile
        private var quietUntil = 0L

        @Volatile
        private var quietUsed = false

        /**
         * Starts the 30 second quiet period (volume 50%). It works ONLY ONCE per alarm:
         * later calls do nothing. After 30 seconds the watchdog puts the volume back to max.
         */
        fun startQuietPeriod() {
            if (quietUsed || !isRinging) return
            quietUsed = true
            quietUntil = SystemClock.elapsedRealtime() + QUIET_MILLIS
        }
    }
}
