package com.wakeup.alarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var picker: TimePicker
    private lateinit var status: TextView
    private lateinit var taskGroup: RadioGroup
    private var numbersId = 0
    private var qrId = 0

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

        // ---- Task chooser: what you must do to stop the alarm ----
        root.addView(TextView(this).apply {
            text = "Task needed to stop the alarm:"
            textSize = 18f
            setPadding(0, (8 * d).toInt(), 0, (4 * d).toInt())
        })
        numbersId = View.generateViewId()
        qrId = View.generateViewId()
        taskGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply {
                id = numbersId
                text = "Number game (10 rounds)"
            })
            addView(RadioButton(this@MainActivity).apply {
                id = qrId
                text = "QR code scan"
            })
        }
        root.addView(taskGroup)
        taskGroup.check(
            if (TaskPrefs.get(this) == TaskPrefs.TASK_QR) qrId else numbersId
        )
        // Choosing QR asks for the camera permission right here.
        taskGroup.setOnCheckedChangeListener { _, id ->
            if (id == qrId && !hasCameraPermission()) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            }
        }

        root.addView(makeButton("Save and enable daily alarm") {
            saveTask()
            AlarmScheduler.saveAndEnable(this, picker.hour, picker.minute)
            refreshStatus()
        })
        root.addView(makeButton("Disable alarm") {
            AlarmScheduler.disable(this)
            refreshStatus()
        })
        root.addView(makeButton("Test: ring in 10 seconds") {
            saveTask()
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

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                taskGroup.check(numbersId)
                Toast.makeText(
                    this,
                    "Camera not allowed, so QR cannot be used. Number game selected.",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshStatus()
        }
    }

    private fun saveTask() {
        var task = if (taskGroup.checkedRadioButtonId == qrId) {
            TaskPrefs.TASK_QR
        } else {
            TaskPrefs.TASK_NUMBERS
        }
        if (task == TaskPrefs.TASK_QR && !hasCameraPermission()) {
            // Never save a QR task without camera permission: the alarm could get stuck.
            task = TaskPrefs.TASK_NUMBERS
            taskGroup.check(numbersId)
            Toast.makeText(
                this,
                "Camera permission is missing, so Number game was saved.",
                Toast.LENGTH_LONG
            ).show()
        }
        TaskPrefs.set(this, task)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun refreshStatus() {
        val next = AlarmScheduler.nextTriggerMillis(this)
        val line1 = if (next == null) {
            "No alarm is set."
        } else {
            val f = SimpleDateFormat("EEE d MMM, h:mm:ss a", Locale.getDefault())
            "Next alarm: " + f.format(Date(next))
        }
        val taskName = if (TaskPrefs.get(this) == TaskPrefs.TASK_QR) {
            if (hasCameraPermission()) {
                "QR code scan"
            } else {
                "QR code scan (camera permission missing, Number game will be used)"
            }
        } else {
            "Number game"
        }
        status.text = line1 + "\nSaved task: " + taskName
    }

    companion object {
        private const val CAMERA_REQUEST = 2
    }
}
