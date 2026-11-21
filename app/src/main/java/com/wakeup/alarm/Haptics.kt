package com.wakeup.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** All vibration of the app in one place. Uses the alarm channel, so it also works in silent mode. */
object Haptics {
    // This phone's motor has NO strength control, so strength cannot change; only the timing can.
    // A motor of this kind needs time to spin up, so short pulses feel weak. The pulses here are
    // LONG, with only short gaps: long - short - long - medium, a zigzag in rhythm.
    private val ZIGZAG_TIMINGS = longArrayOf(0, 1200, 120, 450, 120, 1200, 120, 700, 150)
    private val ZIGZAG_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0)

    // Reminder: a notification-style burst, felt once.
    private val BURST_TIMINGS = longArrayOf(0, 700, 200, 700, 200, 700)
    private val BURST_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    @Suppress("DEPRECATION")
    private fun vibrator(c: Context): Vibrator? =
        try {
            c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        } catch (e: Exception) {
            null
        }

    private fun attrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** Intense zigzag vibration that repeats until stop() is called. */
    fun startZigzag(c: Context) = play(c, ZIGZAG_TIMINGS, ZIGZAG_AMPLITUDES, 0)

    /** Short strong burst, once. */
    fun burst(c: Context) = play(c, BURST_TIMINGS, BURST_AMPLITUDES, -1)

    fun stop(c: Context) {
        try {
            vibrator(c)?.cancel()
        } catch (e: Exception) {
        }
    }

    const val LAB_STEPS = 6

    /** Vibration lab: the same 2 second buzz sent in different ways. Returns a name for the way. */
    @Suppress("DEPRECATION")
    fun lab(c: Context, step: Int): String {
        val v = vibrator(c) ?: return "no vibrator found"
        return try {
            v.cancel()
            val t = longArrayOf(0, 2000)
            when (step) {
                1 -> {
                    v.vibrate(VibrationEffect.createWaveform(t, -1), attrs())
                    "1: alarm channel"
                }
                2 -> {
                    v.vibrate(VibrationEffect.createWaveform(t, -1))
                    "2: no channel"
                }
                3 -> {
                    v.vibrate(
                        VibrationEffect.createWaveform(t, -1),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                    )
                    "3: notification channel"
                }
                4 -> {
                    v.vibrate(
                        VibrationEffect.createWaveform(t, -1),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build()
                    )
                    "4: accessibility channel"
                }
                5 -> {
                    v.vibrate(t, -1)
                    "5: old style"
                }
                else -> {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    "6: heavy click"
                }
            }
        } catch (e: Exception) {
            "$step: error " + e.javaClass.simpleName
        }
    }

    fun hasAmplitudeControl(c: Context): Boolean =
        try {
            vibrator(c)?.hasAmplitudeControl() ?: false
        } catch (e: Exception) {
            false
        }

    @Suppress("DEPRECATION")
    private fun play(c: Context, timings: LongArray, amplitudes: IntArray, repeat: Int) {
        try {
            val v = vibrator(c) ?: return
            // Without strength control the plain on/off pattern is used.
            val effect = try {
                if (v.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, repeat)
                } else {
                    VibrationEffect.createWaveform(timings, repeat)
                }
            } catch (e: Exception) {
                VibrationEffect.createWaveform(timings, repeat)
            }
            v.vibrate(effect, attrs())
        } catch (e: Exception) {
        }
    }
}
