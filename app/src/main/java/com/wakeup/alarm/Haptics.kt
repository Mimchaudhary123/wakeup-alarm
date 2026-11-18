package com.wakeup.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** All vibration of the app in one place. Uses the alarm channel, so it also works in silent mode. */
object Haptics {
    // Zigzag: hard strokes at full strength, a lighter stroke in between, very short gaps.
    // Without strength control the same rhythm still buzzes hard.
    private val ZIGZAG_TIMINGS = longArrayOf(0, 220, 40, 120, 40, 220, 40, 120, 40, 380, 100)
    private val ZIGZAG_AMPLITUDES = intArrayOf(0, 255, 0, 150, 0, 255, 0, 150, 0, 255, 0)

    // Reminder: a notification-style burst, felt once.
    private val BURST_TIMINGS = longArrayOf(0, 300, 120, 300, 120, 600)
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
            val effect = try {
                VibrationEffect.createWaveform(timings, amplitudes, repeat)
            } catch (e: Exception) {
                VibrationEffect.createWaveform(timings, repeat)
            }
            v.vibrate(effect, attrs())
        } catch (e: Exception) {
        }
    }
}
