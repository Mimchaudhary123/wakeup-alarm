package com.wakeup.alarm

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
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
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private lateinit var nextAlarmText: TextView
    private lateinit var nextAlarmSub: TextView
    private lateinit var permSummary: TextView
    private lateinit var diagText: TextView
    private lateinit var alarmList: LinearLayout
    private lateinit var emptyText: TextView
    private var d = 1f

    // For Akhirah colours (from the colour video)
    private val brown = Color.parseColor("#603E1E")
    private val paleGold = Color.parseColor("#F1D9A8")
    private val glowGold = Color.parseColor("#FFC34D")
    private val softOnBrown = Color.parseColor("#D9C497")
    private val softBrown = Color.parseColor("#8A6A45")
    private val good = Color.parseColor("#2E7D32")
    private val bad = Color.parseColor("#B3261E")

    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT
    private val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        d = resources.displayMetrics.density
        window.statusBarColor = brown
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(brown)
        }

        // ---------------- header with logo ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(12))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo_img)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
                setMargins(dp(16), 0, 0, 0)
            }
        }
        titles.addView(TextView(this).apply {
            text = "For Akhirah"
            textSize = 26f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
        })
        titles.addView(TextView(this).apply {
            text = "Wake up with purpose"
            textSize = 13f
            setTextColor(softOnBrown)
        })
        header.addView(titles)
        page.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        // ---------------- next alarm ----------------
        val nextCard = card()
        nextCard.addView(label("NEXT ALARM", softBrown))
        nextAlarmText = TextView(this).apply {
            textSize = 26f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        nextCard.addView(nextAlarmText)
        nextAlarmSub = TextView(this).apply {
            textSize = 14f
            setTextColor(softBrown)
            setPadding(0, dp(2), 0, 0)
        }
        nextCard.addView(nextAlarmSub)
        body.addView(nextCard)

        // ---------------- permissions (one button) ----------------
        body.addView(styledButton("Permissions", Color.TRANSPARENT, paleGold, paleGold) {
            showPermissions()
        })
        permSummary = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(softOnBrown)
            setPadding(0, 0, 0, dp(12))
        }
        body.addView(permSummary)

        // ---------------- alarm list ----------------
        body.addView(TextView(this).apply {
            text = "ALARMS"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(softOnBrown)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(4), 0, dp(8))
        })
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(alarmList)
        emptyText = TextView(this).apply {
            text = "No alarms yet. Tap Add alarm."
            textSize = 15f
            setTextColor(softOnBrown)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        body.addView(emptyText)

        body.addView(styledButton("Add alarm", glowGold, brown, Color.TRANSPARENT) {
            showEditor(null)
        })
        body.addView(styledButton("Test: ring in 10 seconds", Color.TRANSPARENT, paleGold, paleGold) {
            AlarmScheduler.scheduleTest(this, 10_000)
            Toast.makeText(this, "The test alarm will ring in 10 seconds.", Toast.LENGTH_SHORT).show()
        })

        // ---------------- diagnostics (hidden until tapped) ----------------
        val diagTitle = TextView(this).apply {
            text = "Diagnostics \u25BE"
            textSize = 13f
            setTextColor(softOnBrown)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        diagText = TextView(this).apply {
            textSize = 12f
            setTextColor(softOnBrown)
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
            setBackgroundColor(brown)
            addView(page)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // ------------------------------------------------------------------
    //  Small view helpers
    // ------------------------------------------------------------------

    private fun dp(v: Int) = (v * d).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(paleGold)
            cornerRadius = dp(16).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply {
            setMargins(0, 0, 0, dp(12))
        }
    }

    private fun label(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        letterSpacing = 0.08f
        setTextColor(color)
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

    /** Shows a rounded pale-gold window around the given content. */
    private fun showSheet(dlg: Dialog, content: View) {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(ScrollView(this).apply { addView(content) })
        dlg.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(paleGold)
            cornerRadius = dp(20).toFloat()
        })
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ------------------------------------------------------------------
    //  Text helpers
    // ------------------------------------------------------------------

    private fun timeText(h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.getDefault(), "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
    }

    private fun dateText(y: Int, mo: Int, da: Int): String {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(y, mo, da)
        return SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(cal.time)
    }

    private fun repeatText(a: AlarmItem): String {
        if (a.days == 0) return dateText(a.year, a.month, a.day)
        if (a.days == 127) return "Every day"
        val names = ArrayList<String>()
        for (i in 0..6) {
            if (((a.days shr i) and 1) == 1) names.add(dayNames[i])
        }
        return names.joinToString(", ")
    }

    private fun untilText(t: Long): String {
        val mins = ((t - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
        val days = mins / 1440
        val hours = (mins % 1440) / 60
        val rest = mins % 60
        return if (days > 0) "in ${days}d ${hours}h ${rest}m"
        else if (hours > 0) "in ${hours}h ${rest}m"
        else "in ${rest}m"
    }

    // ------------------------------------------------------------------
    //  Alarm list
    // ------------------------------------------------------------------

    private fun refreshAll() {
        refreshStatus()
        refreshList()
    }

    private fun refreshList() {
        alarmList.removeAllViews()
        val alarms = AlarmScheduler.all(this).sortedWith(compareBy({ it.hour }, { it.minute }))
        emptyText.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        for (a in alarms) addAlarmRow(a)
    }

    private fun addAlarmRow(a: AlarmItem) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(if (a.enabled) paleGold else Color.parseColor("#C9B48A"))
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(matchParent, wrapContent).apply {
                setMargins(0, 0, 0, dp(10))
            }
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
        }
        info.addView(TextView(this).apply {
            text = timeText(a.hour, a.minute)
            textSize = 28f
            setTextColor(if (a.enabled) brown else softBrown)
            setTypeface(typeface, Typeface.BOLD)
        })
        val sub = if (a.label.isEmpty()) repeatText(a) else a.label + "  \u2022  " + repeatText(a)
        info.addView(TextView(this).apply {
            text = sub
            textSize = 14f
            setTextColor(softBrown)
        })
        row.addView(info)

        val sw = Switch(this)
        sw.isChecked = a.enabled
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(glowGold, Color.parseColor("#A89570"))
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#8A5F2E"), Color.parseColor("#8C7A5E"))
        )
        sw.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }
        row.addView(sw)

        row.setOnClickListener { showEditor(a) }
        alarmList.addView(row)
    }

    private fun onToggle(a: AlarmItem, checked: Boolean) {
        val updated = a.copy(enabled = checked)
        if (checked && AlarmScheduler.nextTrigger(updated) == null) {
            Toast.makeText(
                this, "That date has already passed. Tap the alarm to pick a new one.",
                Toast.LENGTH_LONG
            ).show()
            refreshAll()
            return
        }
        AlarmScheduler.upsert(this, updated)
        AlarmScheduler.scheduleOne(this, updated)
        refreshAll()
    }

    // ------------------------------------------------------------------
    //  Add / edit alarm window
    // ------------------------------------------------------------------

    private fun showEditor(existing: AlarmItem?) {
        val now = Calendar.getInstance()
        val storedDate = existing?.takeIf { it.days == 0 }
        var repeatMode = existing == null || existing.days != 0
        var mask = existing?.days ?: 0
        var yy = storedDate?.year ?: now.get(Calendar.YEAR)
        var mm = storedDate?.month ?: now.get(Calendar.MONTH)
        var dd = storedDate?.day ?: now.get(Calendar.DAY_OF_MONTH)

        val dlg = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        content.addView(TextView(this).apply {
            text = if (existing == null) "New alarm" else "Edit alarm"
            textSize = 20f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        val picker = layoutInflater.inflate(R.layout.view_time_picker, null) as TimePicker
        picker.layoutParams = LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        picker.hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        picker.minute = existing?.minute ?: now.get(Calendar.MINUTE)
        content.addView(picker)

        // ---- mode: repeat on days / one date ----
        val segRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val segRepeat = TextView(this)
        val segDate = TextView(this)
        for (tv in listOf(segRepeat, segDate)) {
            tv.textSize = 15f
            tv.gravity = Gravity.CENTER
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setPadding(0, dp(10), 0, dp(10))
            tv.layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            segRow.addView(tv)
        }
        segRepeat.text = "Repeat on days"
        segDate.text = "Pick a date"
        content.addView(segRow)

        val repeatPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()
        val chipLetters = arrayOf("M", "T", "W", "T", "F", "S", "S")
        fun styleChip(i: Int) {
            val on = ((mask shr i) and 1) == 1
            chips[i].setTextColor(if (on) paleGold else brown)
            chips[i].background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (on) brown else Color.TRANSPARENT)
                setStroke(dp(1), brown)
            }
        }
        for (i in 0..6) {
            val chip = TextView(this).apply {
                text = chipLetters[i]
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            }
            chips.add(chip)
            chip.setOnClickListener {
                mask = mask xor (1 shl i)
                styleChip(i)
            }
            chipRow.addView(chip)
            styleChip(i)
        }
        repeatPanel.addView(chipRow)
        repeatPanel.addView(TextView(this).apply {
            text = "No day selected = rings once, the next time this time comes."
            textSize = 12f
            setTextColor(softBrown)
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(repeatPanel)

        val datePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dateBtn = styledButton(dateText(yy, mm, dd), Color.TRANSPARENT, brown, brown) {}
        dateBtn.setOnClickListener {
            val dateDialog = DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, day ->
                    yy = y
                    mm = m
                    dd = day
                    dateBtn.text = dateText(yy, mm, dd)
                },
                yy, mm, dd
            )
            dateDialog.datePicker.minDate = System.currentTimeMillis() - 1000
            dateDialog.show()
        }
        datePanel.addView(dateBtn)
        content.addView(datePanel)

        fun styleSegments() {
            for (pair in listOf(Pair(segRepeat, repeatMode), Pair(segDate, !repeatMode))) {
                val tv = pair.first
                val active = pair.second
                tv.setTextColor(if (active) paleGold else brown)
                tv.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (active) brown else Color.TRANSPARENT)
                    setStroke(dp(1), brown)
                }
            }
            repeatPanel.visibility = if (repeatMode) View.VISIBLE else View.GONE
            datePanel.visibility = if (repeatMode) View.GONE else View.VISIBLE
        }
        segRepeat.setOnClickListener {
            repeatMode = true
            styleSegments()
        }
        segDate.setOnClickListener {
            repeatMode = false
            styleSegments()
        }
        styleSegments()

        val labelEdit = EditText(this).apply {
            hint = "Label (optional)"
            setSingleLine(true)
            setText(existing?.label ?: "")
            setTextColor(brown)
            setHintTextColor(softBrown)
            textSize = 16f
            setPadding(dp(4), dp(14), dp(4), dp(10))
        }
        content.addView(labelEdit)

        fun trySave() {
            val h = picker.hour
            val mi = picker.minute
            var days = if (repeatMode) mask else 0
            var y = yy
            var mo = mm
            var da = dd
            if (repeatMode && mask == 0) {
                // No day chosen: ring once, the next time this clock time comes.
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, mi)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
                y = c.get(Calendar.YEAR)
                mo = c.get(Calendar.MONTH)
                da = c.get(Calendar.DAY_OF_MONTH)
                days = 0
            }
            val item = AlarmItem(
                id = existing?.id ?: AlarmScheduler.newId(this),
                hour = h, minute = mi, enabled = true, days = days,
                year = y, month = mo, day = da,
                label = labelEdit.text.toString().trim()
            )
            val next = AlarmScheduler.nextTrigger(item)
            if (next == null) {
                Toast.makeText(this, "That date and time has already passed.", Toast.LENGTH_LONG)
                    .show()
                return
            }
            AlarmScheduler.upsert(this, item)
            AlarmScheduler.scheduleOne(this, item)
            dlg.dismiss()
            refreshAll()
            Toast.makeText(this, "Alarm set, rings " + untilText(next), Toast.LENGTH_SHORT).show()
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val cancelBtn = styledButton("Cancel", Color.TRANSPARENT, brown, brown) { dlg.dismiss() }
        val saveBtn = styledButton("Save", brown, paleGold, Color.TRANSPARENT) { trySave() }
        for (b in listOf(cancelBtn, saveBtn)) {
            b.layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f).apply {
                setMargins(dp(4), dp(6), dp(4), dp(6))
            }
            buttons.addView(b)
        }
        content.addView(buttons)

        if (existing != null) {
            val delBtn = styledButton("Delete alarm", Color.TRANSPARENT, bad, Color.TRANSPARENT) {
                AlarmScheduler.delete(this, existing.id)
                dlg.dismiss()
                refreshAll()
            }
            content.addView(delBtn)
        }

        showSheet(dlg, content)
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
        val dlg = Dialog(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(16), dp(10), dp(8))
        }
        list.addView(TextView(this).apply {
            text = "Permissions"
            textSize = 20f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, 0, dp(8))
        })
        for (r in permissionRows()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(12), dp(8), dp(12))
            }
            row.addView(TextView(this).apply {
                text = r.title
                textSize = 16f
                setTextColor(brown)
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                text = r.detail
                textSize = 13f
                setTextColor(softBrown)
            })
            row.addView(TextView(this).apply {
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(2), 0, 0)
                if (r.ok == true) {
                    text = "\u2713 Allowed"
                    setTextColor(good)
                } else if (r.ok == false) {
                    text = "\u2717 Not allowed - tap to fix"
                    setTextColor(bad)
                } else {
                    text = "Tap to open settings"
                    setTextColor(softBrown)
                }
            })
            row.setOnClickListener {
                dlg.dismiss()
                r.action()
            }
            list.addView(row)
            list.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#D9C497"))
                layoutParams = LinearLayout.LayoutParams(matchParent, dp(1))
            })
        }
        list.addView(styledButton("Close", brown, paleGold, Color.TRANSPARENT) { dlg.dismiss() })
        showSheet(dlg, list)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAll()
    }

    // ------------------------------------------------------------------
    //  Status
    // ------------------------------------------------------------------

    private fun refreshStatus() {
        val next = AlarmScheduler.nextOverall(this)
        if (next == null) {
            nextAlarmText.text = "No alarm set"
            nextAlarmSub.text = "Tap Add alarm to create one"
        } else {
            nextAlarmText.text =
                SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(next.second))
            val name = if (next.first.label.isEmpty()) "" else next.first.label + "  \u2022  "
            nextAlarmSub.text = name + untilText(next.second)
        }

        val missing = permissionRows().count { it.needed && it.ok == false }
        if (missing == 0) {
            permSummary.text = "\u2713 All set"
        } else {
            permSummary.text = "$missing need attention"
        }

        val guard = if (PowerGuardService.isEnabled(this)) "ON" else "OFF"
        val last = getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .getString("guard_last", "none yet")
        diagText.text = "Power guard: $guard\nLast other window seen while ringing:\n$last"
    }

    companion object {
        private const val PERM_REQUEST = 3
    }
}
