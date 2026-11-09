package com.wakeup.alarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var picker: TimePicker
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (28 * d).toInt(), pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Wakeup Alarm"
            textSize = 26f
        })

        picker = TimePicker(this)
        val p = AlarmScheduler.prefs(this)
        picker.hour = p.getInt(AlarmScheduler.KEY_HOUR, 6)
        picker.minute = p.getInt(AlarmScheduler.KEY_MINUTE, 0)
        root.addView(picker)

        root.addView(makeButton("Save and enable daily alarm") {
            AlarmScheduler.saveAndEnable(this, picker.hour, picker.minute)
            refreshStatus()
        })
        root.addView(makeButton("Disable alarm") {
            AlarmScheduler.disable(this)
            refreshStatus()
        })
        root.addView(makeButton("Test: ring in 10 seconds") {
            AlarmScheduler.scheduleAt(this, System.currentTimeMillis() + 10_000)
            refreshStatus()
        })
        root.addView(makeButton("Allow background running (battery settings)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * d).toInt(), 0, 0)
        }
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun refreshStatus() {
        val next = AlarmScheduler.nextTriggerMillis(this)
        status.text = if (next == null) {
            "No alarm is set."
        } else {
            val f = SimpleDateFormat("EEE d MMM, h:mm:ss a", Locale.getDefault())
            "Next alarm: " + f.format(Date(next))
        }
    }
}
