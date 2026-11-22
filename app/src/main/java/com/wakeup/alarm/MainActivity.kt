package com.wakeup.alarm

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
    private lateinit var reminderSwitch: Switch
    private lateinit var reminderSub: TextView
    private var updatingReminder = false
    private lateinit var statIstBig: TextView
    private lateinit var statIstSmall: TextView
    private lateinit var statDurBig: TextView
    private lateinit var statDurSmall: TextView

    // permission gate (full-screen overlay shown until everything needed is on)
    private lateinit var gateView: FrameLayout
    private lateinit var gateStep: TextView
    private lateinit var gateTitle: TextView
    private lateinit var gateWhy: TextView
    private lateinit var gateHow: TextView
    private lateinit var gateButton: Button
    private lateinit var gateDone: Button
    private lateinit var gateSkip: TextView
    private var gateCurrentId = ""
    private var gateTaps = 0
    private val gateSkipped = HashSet<String>()

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
            clipChildren = false
            clipToPadding = false
        }

        // ---------------- header with logo ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(12))
        }
        header.addView(Glow.logoWithGlow(this, R.drawable.ic_logo_img, 60))
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
            Glow.textGlow(this, dp(10).toFloat())
            Glow.shimmer(this)
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
            clipChildren = false
            clipToPadding = false
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

        // ---------------- istigfar + durud counters ----------------
        val statCard = card()
        statCard.addView(label("TODAY'S READINGS", softBrown))
        val statRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        fun statColumn(title: String): Pair<TextView, TextView> {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
            }
            col.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(softBrown)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            val big = TextView(this).apply {
                textSize = 42f
                setTextColor(brown)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                Glow.textGlow(this, dp(8).toFloat())
            }
            val small = TextView(this).apply {
                textSize = 12f
                setTextColor(softBrown)
                gravity = Gravity.CENTER
            }
            col.addView(big)
            col.addView(small)
            statRow.addView(col)
            return Pair(big, small)
        }
        val istCol = statColumn("Istigfar")
        statIstBig = istCol.first
        statIstSmall = istCol.second
        val durCol = statColumn("Durud")
        statDurBig = durCol.first
        statDurSmall = durCol.second
        statCard.addView(statRow)
        body.addView(statCard)

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
            // An alarm can only be placed when everything the app needs is on.
            if (gatePending().isEmpty()) showEditor(null) else refreshGate()
        }.also { Glow.pulse(it, dp(1).toFloat(), dp(9).toFloat()) })
        body.addView(styledButton("Test: ring in 10 seconds", Color.TRANSPARENT, paleGold, paleGold) {
            AlarmScheduler.scheduleTest(this, 10_000)
            Toast.makeText(this, "The test alarm will ring in 10 seconds.", Toast.LENGTH_SHORT).show()
        })
        body.addView(styledButton("Test the vibration (5 seconds)", Color.TRANSPARENT, paleGold, paleGold) {
            val switched = DndGuard.begin(this)
            val h = Handler(Looper.getMainLooper())
            h.postDelayed({ Haptics.startZigzag(this) }, if (switched) 900L else 0L)
            h.postDelayed({
                Haptics.stop(this)
                DndGuard.endIfIdle(this)
            }, 6000)
        })
        body.addView(styledButton("Vibration lab (6 different ways)", Color.TRANSPARENT, paleGold, paleGold) {
            Toast.makeText(
                this,
                "Hold the phone. 6 buzzes of 2 seconds each. Remember the numbers you feel.",
                Toast.LENGTH_LONG
            ).show()
            DndGuard.begin(this)
            Handler(Looper.getMainLooper()).postDelayed({ runVibrationLab(1) }, 2500)
        })
        body.addView(styledButton("Test the wake-up check", Color.TRANSPARENT, paleGold, paleGold) {
            AlarmScheduler.scheduleTestWithCheck(this, 10_000)
            Toast.makeText(
                this,
                "Rings in 10 s. Finish the task: the two checks open 15 s and 45 s later.",
                Toast.LENGTH_LONG
            ).show()
        })

        // ---------------- istigfar + durud reminders ----------------
        val remCard = card()
        remCard.addView(label("REMINDERS", softBrown))
        val remRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val remInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
        }
        remInfo.addView(TextView(this).apply {
            text = "Istigfar and Durud"
            textSize = 20f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
        })
        reminderSub = TextView(this).apply {
            textSize = 14f
            setTextColor(softBrown)
        }
        remInfo.addView(reminderSub)
        remRow.addView(remInfo)
        reminderSwitch = Switch(this)
        styleSwitch(reminderSwitch)
        reminderSwitch.setOnCheckedChangeListener { _, on ->
            if (!updatingReminder) {
                ReminderScheduler.setOn(this, on)
                ReminderScheduler.scheduleNext(this)
                refreshStatus()
            }
        }
        remRow.addView(reminderSwitch)
        remCard.addView(remRow)
        remCard.addView(styledButton("Change times", Color.TRANSPARENT, brown, brown) {
            showReminderSettings()
        })
        remCard.addView(styledButton("Preview a reminder now", Color.TRANSPARENT, brown, brown) {
            ReminderScheduler.show(this, ReminderScheduler.nextType(this))
        })
        body.addView(remCard)
        body.addView(styledButton("Read Sayyidul Istighfar", Color.TRANSPARENT, paleGold, paleGold) {
            showDua()
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        gateView = buildGate()
        val root = FrameLayout(this)
        root.background = Glow.background(this)
        root.addView(GlowRaysView(this), FrameLayout.LayoutParams(matchParent, matchParent))
        root.addView(scroll, FrameLayout.LayoutParams(matchParent, matchParent))
        root.addView(gateView, FrameLayout.LayoutParams(matchParent, matchParent))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Set everything up again, so nothing depends on the phone remembering it.
        PowerGuardService.ensureEnabled(this)
        DndGuard.endIfIdle(this)
        AlarmScheduler.scheduleAll(this)
        ReminderScheduler.scheduleNext(this)
        PermissionGate.watch(this)
        PermissionGate.scheduleWatch(this)
        refreshAll()
        refreshGate()
    }

    // ------------------------------------------------------------------
    //  Small view helpers
    // ------------------------------------------------------------------

    private fun dp(v: Int) = (v * d).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = Glow.paleCard(dp(16).toFloat())
        Glow.shadowGlow(this, dp(5).toFloat())
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
        val shape: GradientDrawable = if (fill == glowGold) {
            Glow.goldFill(dp(14).toFloat())
        } else {
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(14).toFloat()
                if (stroke != Color.TRANSPARENT) setStroke(dp(2), stroke)
            }
        }
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 0, 0, 0)), shape, null)
        if (fill == glowGold) Glow.shadowGlow(this, dp(6).toFloat())
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
            background = if (a.enabled) {
                Glow.paleCard(dp(16).toFloat())
            } else {
                GradientDrawable().apply {
                    setColor(Color.parseColor("#C9B48A"))
                    cornerRadius = dp(16).toFloat()
                }
            }
            if (a.enabled) Glow.shadowGlow(this, dp(4).toFloat())
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
        val base = if (a.label.isEmpty()) repeatText(a) else a.label + "  \u2022  " + repeatText(a)
        val sub = if (a.check) base + "  \u2022  Check" else base
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
        // New alarms start with the wake-up check ON; alarms that existed before start with it OFF.
        var checkOn = existing?.check ?: true
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

        // ---- wake-up check and recheck for this alarm ----
        val checkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val checkInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f)
        }
        checkInfo.addView(TextView(this).apply {
            text = "Wake-up check and recheck"
            textSize = 16f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
        })
        checkInfo.addView(TextView(this).apply {
            text = "A short check comes 1 and 10 minutes after you finish the task."
            textSize = 12f
            setTextColor(softBrown)
        })
        checkRow.addView(checkInfo)
        val checkSwitch = Switch(this)
        styleSwitch(checkSwitch)
        checkSwitch.isChecked = checkOn
        checkSwitch.setOnCheckedChangeListener { _, on -> checkOn = on }
        checkRow.addView(checkSwitch)
        content.addView(checkRow)

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
                label = labelEdit.text.toString().trim(),
                check = checkOn
            )
            val next = AlarmScheduler.nextTrigger(item)
            if (next == null) {
                Toast.makeText(this, "That date and time has already passed.", Toast.LENGTH_LONG)
                    .show()
                return
            }
            AlarmScheduler.upsert(this, item)
            AlarmScheduler.scheduleOne(this, item)
            AppLog.add(
                this,
                "alarm saved: rings " + SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(next))
            )
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

    // ------------------------------------------------------------------
    //  Reminder settings window
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Permission gate: walks through every missing permission, one by one
    // ------------------------------------------------------------------

    private fun gatePending(): List<PermStep> =
        PermissionGate.steps().filter { !it.isOk(this) && it.id !in gateSkipped }

    private fun buildGate(): FrameLayout {
        val v = FrameLayout(this)
        v.background = Glow.background(this)
        v.addView(GlowRaysView(this), FrameLayout.LayoutParams(matchParent, matchParent))
        v.visibility = View.GONE
        v.isClickable = true // swallows touches, so nothing underneath can be used

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(56), dp(28), dp(28))
        }
        col.addView(Glow.logoWithGlow(this, R.drawable.ic_logo_img, 70))
        gateStep = TextView(this).apply {
            textSize = 12f
            letterSpacing = 0.1f
            setTextColor(softOnBrown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(6))
        }
        col.addView(gateStep)
        gateTitle = TextView(this).apply {
            textSize = 28f
            setTextColor(glowGold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        col.addView(gateTitle)
        gateWhy = TextView(this).apply {
            textSize = 17f
            setTextColor(paleGold)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(10))
        }
        col.addView(gateWhy)
        gateHow = TextView(this).apply {
            textSize = 15f
            setTextColor(softOnBrown)
            gravity = Gravity.CENTER
        }
        col.addView(gateHow)
        col.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
        })
        gateButton = styledButton("Open settings", glowGold, brown, Color.TRANSPARENT) { onGateOpen() }
        Glow.pulse(gateButton, dp(1).toFloat(), dp(9).toFloat())
        col.addView(gateButton)
        gateDone = styledButton("I turned it on", Color.TRANSPARENT, paleGold, paleGold) { onGateDone() }
        col.addView(gateDone)
        gateSkip = TextView(this).apply {
            text = "Cannot turn this on? Skip this step (the alarm may fail)"
            textSize = 13f
            setTextColor(softOnBrown)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener {
                gateSkipped.add(gateCurrentId)
                refreshGate()
            }
        }
        col.addView(gateSkip)
        v.addView(col, FrameLayout.LayoutParams(matchParent, matchParent))
        return v
    }

    /** Shows the first step that is still off, or hides the gate when everything is on. */
    private fun refreshGate() {
        val all = PermissionGate.steps()
        val pending = gatePending()
        if (pending.isEmpty()) {
            gateView.visibility = View.GONE
            return
        }
        val step = pending[0]
        if (gateCurrentId != step.id) {
            gateCurrentId = step.id
            gateTaps = 0
        }
        val number = all.indexOfFirst { it.id == step.id } + 1
        gateStep.text = "STEP $number OF ${all.size}"
        gateTitle.text = step.title
        gateWhy.text = step.why
        gateHow.text = step.how
        gateButton.text = if (step.manual) "Open settings" else "Turn it on"
        gateDone.visibility = if (step.manual) View.VISIBLE else View.GONE
        // Only offered after a few tries, so nobody is ever stuck on a step that will not switch on.
        gateSkip.visibility = if (gateTaps >= 3) View.VISIBLE else View.GONE
        gateView.visibility = View.VISIBLE
    }

    private fun onGateOpen() {
        val step = PermissionGate.steps().firstOrNull { it.id == gateCurrentId } ?: return
        gateTaps++
        try {
            startActivity(step.intent(this))
        } catch (e: Exception) {
            openSettings(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)
                )
            )
        }
        refreshGate()
    }

    private fun onGateDone() {
        PermissionGate.confirm(this, gateCurrentId)
        refreshGate()
    }

    /** The Sayyidul Istighfar picture: open it, read it, close it, whenever you want. */
    private fun showDua() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "Sayyidul Istighfar"
            textSize = 20f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
        val img = ImageView(this)
        img.setImageResource(R.drawable.dua_sayyidul_istighfar)
        img.adjustViewBounds = true
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        scroll.addView(img, ViewGroup.LayoutParams(matchParent, wrapContent))
        root.addView(scroll)
        root.addView(styledButton("Close", brown, paleGold, Color.TRANSPARENT) { dlg.dismiss() })
        dlg.setContentView(root)
        dlg.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(paleGold)
            cornerRadius = dp(20).toFloat()
        })
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.9f).toInt()
        )
    }

    private fun runVibrationLab(step: Int) {
        if (step > Haptics.LAB_STEPS) {
            Haptics.stop(this)
            DndGuard.endIfIdle(this)
            Toast.makeText(this, "Done. Tell me which numbers you felt.", Toast.LENGTH_LONG).show()
            return
        }
        val name = Haptics.lab(this, step)
        Toast.makeText(this, "Now: $name", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ runVibrationLab(step + 1) }, 3500)
    }

    private fun styleSwitch(sw: Switch) {
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(glowGold, Color.parseColor("#A89570"))
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#8A5F2E"), Color.parseColor("#8C7A5E"))
        )
    }

    private fun everyText(min: Int): String =
        if (min < 60) "Every $min min" else if (min == 60) "Every hour" else "Every ${min / 60} hours"

    private fun showReminderSettings() {
        val s = ReminderScheduler.settings(this)
        var startMin = s.startMin
        var endMin = s.endMin
        var every = s.everyMin

        val dlg = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        content.addView(TextView(this).apply {
            text = "Reminder times"
            textSize = 20f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        val fromBtn = styledButton(
            "From: " + timeText(startMin / 60, startMin % 60), Color.TRANSPARENT, brown, brown
        ) {}
        fromBtn.setOnClickListener {
            TimePickerDialog(
                this,
                TimePickerDialog.OnTimeSetListener { _, h, m ->
                    startMin = h * 60 + m
                    fromBtn.text = "From: " + timeText(h, m)
                },
                startMin / 60, startMin % 60, false
            ).show()
        }
        val toBtn = styledButton(
            "To: " + timeText(endMin / 60, endMin % 60), Color.TRANSPARENT, brown, brown
        ) {}
        toBtn.setOnClickListener {
            TimePickerDialog(
                this,
                TimePickerDialog.OnTimeSetListener { _, h, m ->
                    endMin = h * 60 + m
                    toBtn.text = "To: " + timeText(h, m)
                },
                endMin / 60, endMin % 60, false
            ).show()
        }
        content.addView(fromBtn)
        content.addView(toBtn)
        content.addView(TextView(this).apply {
            text = "12:00 AM as the end time means midnight."
            textSize = 12f
            setTextColor(softBrown)
            setPadding(0, dp(2), 0, dp(10))
        })

        content.addView(label("REMIND ME", softBrown))
        val everyValues = listOf(30, 60, 120, 180)
        val everyLabels = listOf("30 min", "1 hour", "2 hours", "3 hours")
        val chipViews = ArrayList<TextView>()
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        fun styleEvery() {
            for (i in everyValues.indices) {
                val on = everyValues[i] == every
                chipViews[i].setTextColor(if (on) paleGold else brown)
                chipViews[i].background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (on) brown else Color.TRANSPARENT)
                    setStroke(dp(1), brown)
                }
            }
        }
        for (i in everyValues.indices) {
            val chip = TextView(this).apply {
                text = everyLabels[i]
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(10))
                layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            }
            chip.setOnClickListener {
                every = everyValues[i]
                styleEvery()
            }
            chipViews.add(chip)
            chipRow.addView(chip)
        }
        styleEvery()
        content.addView(chipRow)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val cancelBtn = styledButton("Cancel", Color.TRANSPARENT, brown, brown) { dlg.dismiss() }
        val saveBtn = styledButton("Save", brown, paleGold, Color.TRANSPARENT) {
            ReminderScheduler.saveTimes(this, startMin, endMin, every)
            ReminderScheduler.scheduleNext(this)
            dlg.dismiss()
            refreshAll()
            Toast.makeText(this, "Reminder times saved.", Toast.LENGTH_SHORT).show()
        }
        for (b in listOf(cancelBtn, saveBtn)) {
            b.layoutParams = LinearLayout.LayoutParams(0, wrapContent, 1f).apply {
                setMargins(dp(4), dp(6), dp(4), dp(6))
            }
            buttons.addView(b)
        }
        content.addView(buttons)
        showSheet(dlg, content)
    }

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
        rows.add(
            PermRow(
                "Do Not Disturb access", "Lets the alarm ring and vibrate in Do Not Disturb and silent mode",
                DndGuard.hasAccess(this), true
            ) {
                openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        )
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        rows.add(
            PermRow(
                "Background running", "Lets the alarm and reminders run on time",
                pm.isIgnoringBatteryOptimizations(packageName), true
            ) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + packageName)
                        )
                    )
                } catch (e: Exception) {
                    openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )
        rows.add(
            PermRow(
                "Sleeping apps (Samsung)",
                "Open App info > Battery: turn off sleeping for this app (cannot be checked here)",
                null, false
            ) {
                openSettings(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)
                    )
                )
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

        val rs = ReminderScheduler.settings(this)
        updatingReminder = true
        reminderSwitch.isChecked = rs.on
        updatingReminder = false
        val rn = ReminderScheduler.nextTrigger(this)
        reminderSub.text = if (!rs.on) {
            "Off"
        } else {
            everyText(rs.everyMin) + ", " + timeText(rs.startMin / 60, rs.startMin % 60) +
                " - " + timeText(rs.endMin / 60, rs.endMin % 60) +
                (if (rn != null) "\nNext: " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(rn)) else "")
        }

        statIstBig.text = ReminderStats.today(this, ReminderScheduler.TYPE_ISTIGFAR).toString()
        statIstSmall.text = "Month " + ReminderStats.month(this, ReminderScheduler.TYPE_ISTIGFAR) +
            "   -   All time " + ReminderStats.total(this, ReminderScheduler.TYPE_ISTIGFAR)
        statDurBig.text = ReminderStats.today(this, ReminderScheduler.TYPE_DURUD).toString()
        statDurSmall.text = "Month " + ReminderStats.month(this, ReminderScheduler.TYPE_DURUD) +
            "   -   All time " + ReminderStats.total(this, ReminderScheduler.TYPE_DURUD)

        val guard = if (PowerGuardService.isEnabled(this)) "ON" else "OFF"
        val last = getSharedPreferences("power_guard", Context.MODE_PRIVATE)
            .getString("guard_last", "none yet")
        val strength = if (Haptics.hasAmplitudeControl(this)) "yes" else "no"
        diagText.text = "Power guard: $guard\nVibration strength control: $strength" +
            "\n" + DndGuard.describe(this) +
            "\nLog (alarm, sound, checks):\n" + AppLog.text(this) +
            "\nLast other window seen while ringing:\n$last"
    }

    companion object {
        private const val PERM_REQUEST = 3
    }
}
