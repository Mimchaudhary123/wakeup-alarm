package com.wakeup.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
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

/** One line in the Permissions window. ok == null means "cannot be checked automatically". */
private class PermRow(
    val title: String,
    val detail: String,
    val ok: Boolean?,
    val needed: Boolean,
    val action: () -> Unit
)

class MainActivity : Activity() {

    private lateinit var picker: TimePicker
    private lateinit var nextAlarmText: TextView
    private lateinit var taskLine: TextView
    private lateinit var permSummary: TextView
    private lateinit var diagText: TextView
    private lateinit var taskGroup: RadioGroup
    private var numbersId = 0
    private var qrId = 0
    private var d = 1f

    private val navy = Color.parseColor("#1F2A5C")
    private val pageBg = Color.parseColor("#F3F5FA")
    private val textMain = Color.parseColor("#1B1F3B")
    private val textSoft = Color.parseColor("#6B7080")
    private val good = Color.parseColor("#2E7D32")
    private val bad = Color.parseColor("#C62828")

    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        d = resources.displayMetrics.density
        window.statusBarColor = navy
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        // ---------------- header with logo ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            background = GradientDrawable().apply {
                setColor(navy)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
                setMargins(dp(16), 0, 0, 0)
            }
        }
        titles.addView(TextView(this).apply {
            text = "Wakeup Alarm"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        titles.addView(TextView(this).apply {
            text = "Built to get you out of bed"
            textSize = 13f
            setTextColor(Color.parseColor("#C5CAE9"))
        })
        header.addView(titles)
        page.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // ---------------- next alarm ----------------
        val nextCard = card()
        nextCard.addView(label("NEXT ALARM"))
        nextAlarmText = TextView(this).apply {
            textSize = 26f
            setTextColor(navy)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        nextCard.addView(nextAlarmText)
        taskLine = TextView(this).apply {
            textSize = 14f
            setTextColor(textSoft)
            setPadding(0, dp(2), 0, 0)
        }
        nextCard.addView(taskLine)
        body.addView(nextCard)

        // ---------------- permissions (one button) ----------------
        val permCard = card()
        permCard.addView(styledButton("Permissions", Color.WHITE, navy, navy) { showPermissions() })
        permSummary = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        permCard.addView(permSummary)
        body.addView(permCard)

        // ---------------- alarm time ----------------
        val timeCard = card()
        timeCard.addView(label("ALARM TIME"))
        picker = layoutInflater.inflate(R.layout.view_time_picker, null) as TimePicker
        picker.layoutParams = LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val p = AlarmScheduler.prefs(this)
        picker.hour = p.getInt(AlarmScheduler.KEY_HOUR, 6)
        picker.minute = p.getInt(AlarmScheduler.KEY_MINUTE, 0)
        timeCard.addView(picker)
        body.addView(timeCard)

        // ---------------- task chooser ----------------
        val taskCard = card()
        taskCard.addView(label("HOW TO STOP THE ALARM"))
        numbersId = View.generateViewId()
        qrId = View.generateViewId()
        taskGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@MainActivity).apply {
                id = numbersId
                text = "Number game (10 rounds)"
                textSize = 16f
                setTextColor(textMain)
                buttonTintList = ColorStateList.valueOf(navy)
                setPadding(dp(8), dp(10), dp(8), dp(10))
            })
            addView(RadioButton(this@MainActivity).apply {
                id = qrId
                text = "QR code scan"
                textSize = 16f
                setTextColor(textMain)
                buttonTintList = ColorStateList.valueOf(navy)
                setPadding(dp(8), dp(10), dp(8), dp(10))
            })
        }
        taskCard.addView(taskGroup)
        taskGroup.check(
            if (TaskPrefs.get(this) == TaskPrefs.TASK_QR) qrId else numbersId
        )
        // Choosing QR asks for the camera permission right here.
        taskGroup.setOnCheckedChangeListener { _, id ->
            if (id == qrId && !hasCameraPermission()) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            }
        }
        body.addView(taskCard)

        // ---------------- action buttons ----------------
        body.addView(styledButton("Save and enable daily alarm", navy, Color.WHITE, Color.TRANSPARENT) {
            saveTask()
            AlarmScheduler.saveAndEnable(this, picker.hour, picker.minute)
            refreshStatus()
        })
        body.addView(styledButton("Test: ring in 10 seconds", Color.WHITE, navy, navy) {
            saveTask()
            AlarmScheduler.scheduleAt(this, System.currentTimeMillis() + 10_000)
            refreshStatus()
        })
        body.addView(styledButton("Disable alarm", Color.TRANSPARENT, bad, Color.TRANSPARENT) {
            AlarmScheduler.disable(this)
            refreshStatus()
        })

        // ---------------- diagnostics (hidden until tapped) ----------------
        val diagTitle = TextView(this).apply {
            text = "Diagnostics \u25BE"
            textSize = 13f
            setTextColor(textSoft)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        diagText = TextView(this).apply {
            textSize = 12f
            setTextColor(textSoft)
            visibility = View.GONE
        }
        diagTitle.setOnClickListener {
            diagText.visibility = if (diagText.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        body.addView(diagTitle)
        body.addView(diagText)

        page.addView(body)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBg)
            addView(page)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ------------------------------------------------------------------
    //  Small view helpers
    // ------------------------------------------------------------------

    private fun dp(v: Int) = (v * d).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply {
            setMargins(0, 0, 0, dp(12))
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        letterSpacing = 0.08f
        setTextColor(textSoft)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun styledButton(
        label: String, fill: Int, textColor: Int, stroke: Int, onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        setAllCaps(false)
        textSize = 16f
        setTextColor(textColor)
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(2), stroke)
        }
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 0, 0, 0)), shape, null)
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
        setOnClickListener { onClick() }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open this settings page on this phone.", Toast.LENGTH_LONG)
                .show()
        }
    }

    // ------------------------------------------------------------------
    //  Permissions window
    // ------------------------------------------------------------------

    private fun permissionRows(): List<PermRow> {
        val rows = ArrayList<PermRow>()
        rows.add(
            PermRow(
                "Display over other apps", "Brings the alarm screen back if it is left",
                Settings.canDrawOverlays(this), true
            ) {
                openSettings(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
        )
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        rows.add(
            PermRow(
                "Background running", "Lets the alarm ring on time",
                pm.isIgnoringBatteryOptimizations(packageName), true
            ) {
                openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
        rows.add(
            PermRow(
                "Power guard", "Blocks the power-off menu while the alarm rings",
                PowerGuardService.isEnabled(this), true
            ) {
                openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        rows.add(
            PermRow(
                "Pin windows", "Turn it on in Security settings (cannot be checked here)",
                null, false
            ) {
                openSettings(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
        )
        rows.add(
            PermRow(
                "Camera", "Only needed for the QR task",
                hasCameraPermission(), TaskPrefs.get(this) == TaskPrefs.TASK_QR
            ) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_REQUEST)
            }
        )
        if (Build.VERSION.SDK_INT >= 33) {
            rows.add(
                PermRow(
                    "Notifications", "Shows the alarm notification",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED,
                    true
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_REQUEST)
                }
            )
        }
        return rows
    }

    private fun showPermissions() {
        val rows = permissionRows()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        var dialogRef: AlertDialog? = null
        for (r in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            row.addView(TextView(this).apply {
                text = r.title
                textSize = 16f
                setTextColor(textMain)
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                text = r.detail
                textSize = 13f
                setTextColor(textSoft)
            })
            row.addView(TextView(this).apply {
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(2), 0, 0)
                if (r.ok == true) {
                    text = "\u2713 Allowed"
                    setTextColor(good)
                } else if (r.ok == false && r.needed) {
                    text = "\u2717 Not allowed - tap to fix"
                    setTextColor(bad)
                } else if (r.ok == false) {
                    text = "Not allowed (not needed now) - tap to allow"
                    setTextColor(textSoft)
                } else {
                    text = "Tap to open settings"
                    setTextColor(textSoft)
                }
            })
            row.setOnClickListener {
                dialogRef?.dismiss()
                r.action()
            }
            list.addView(row)
            list.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#E6E8F0"))
                layoutParams = LinearLayout.LayoutParams(matchParent, dp(1))
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Permissions")
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton("Close", null)
            .create()
        dialogRef = dialog
        dialog.show()
    }

    // ------------------------------------------------------------------
    //  Camera + task handling (unchanged behaviour)
    // ------------------------------------------------------------------

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
        }
        refreshStatus()
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

    // ------------------------------------------------------------------
    //  Status
    // ------------------------------------------------------------------

    private fun refreshStatus() {
        val next = AlarmScheduler.nextTriggerMillis(this)
        nextAlarmText.text = if (next == null) {
            "No alarm set"
        } else {
            SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(next))
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
        taskLine.text = "Stop task: $taskName"

        val missing = permissionRows().count { it.needed && it.ok == false }
        if (missing == 0) {
            permSummary.text = "\u2713 All set"
            permSummary.setTextColor(good)
        } else {
            permSummary.text = "$missing need attention"
            permSummary.setTextColor(bad)
        }

        val guard = if (PowerGuardService.isEnabled(this)) "ON" else "OFF"
        val last = getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .getString("guard_last", "none yet")
        diagText.text = "Power guard: $guard\nLast other window seen while ringing:\n$last"
    }

    companion object {
        private const val CAMERA_REQUEST = 2
        private const val PERM_REQUEST = 3
    }
}
