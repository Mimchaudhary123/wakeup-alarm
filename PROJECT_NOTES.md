# For Akhirah (package com.wakeup.alarm) - PROJECT NOTES
Paste this into any new Claude chat. Continue from "STATUS" below. Do not re-plan.

## How to work with the user (important)
- Give READY-MADE files in a zip (folder structure = repo structure, root folder `app`) to drag into GitHub. Never ask the user to type or edit code by hand.
- No helper scripts (.bat), no extra PC steps beyond: upload to GitHub -> wait for green build -> download APK -> `adb install -r wakeup.apk` from C:\platform-tools.
- Give precise click-by-click steps. Analyse and verify first, then give the solution. Be honest about what could not be tested (no compiler or phone is available to Claude).
- User: Windows 10 PC, Chrome, narrow browser window (GitHub upload is behind the "+" button next to "Go to file"). Phone: Samsung Galaxy M31, Android 12, One UI Core 4.1.
- GitHub: Mimchaudhary123/wakeup-alarm (currently Public, holds app/debug.keystore; advise making it private). APK is built by GitHub Actions -> Artifacts. No Android Studio.

## What the app does
Personal Android alarm, built so it is hard to escape half asleep. Name "For Akhirah". Colours: Brown #603E1E, Pale Gold #F1D9A8, Glowing Gold #FFC34D. Logo = praying boy silhouette from the user's own picture (PNG in res/drawable-nodpi: ic_launcher_fg.png, ic_logo_img.png).

- Multiple alarms (like Turbo Alarm): time + repeat days OR one date, label, per-alarm on/off switch, edit, delete. Stored as JSON in SharedPreferences (AlarmScheduler.kt). Old single alarm migrates automatically.
- When an alarm rings: LANDING screen (big "Stop alarm" button + clock) -> number task (10 rounds, wrong answer = back to round 1) -> dhikr screen (4 lines, fits without scrolling, closes ONLY with its Close button).
- While ringing: volume watchdog forces max alarm volume (+8 dB LoudnessEnhancer boost, BOOST_MILLIBELS in AlarmService), strong repeating vibration, volume keys and Back swallowed, screen pinning (startLockTask), AlarmService relaunches the alarm screen if left (needs "Display over other apps").
- Task start: volume drops to 50% for 30 s, ONCE per alarm, then back to max.
- PowerGuardService (accessibility service): when another window (power-off menu) opens while ringing, it presses Back every 0.12 s, re-opens the alarm screen, and shows an invisible touch blocker for 2 s (max, then 3 s pause) so Power off cannot be tapped. Tested OK by the user.
- Reminders: Istigfar and Durud screens alternate, default every hour 6:00 AM to 12:00 AM, on/off switch + changeable times (ReminderScheduler.kt, ReminderActivity.kt). Skipped while an alarm rings.
- If the phone restarts while an alarm is ringing (task not finished) the alarm rings again after boot (within 30 min).
- QR task was removed completely.

## Files (all in app/src/main/java/com/wakeup/alarm unless noted)
MainActivity, AlarmActivity, AlarmService, AlarmScheduler (AlarmItem + storage + scheduling), AlarmReceiver, BootReceiver, PowerGuardService, ReminderScheduler (+ReminderReceiver), ReminderActivity, TaskPrefs (empty, can be deleted).
Resources: app/src/main/res/xml/power_guard_config.xml (NO android:description attribute, a plain string there breaks the build), drawable/ic_launcher_background.xml, drawable-nodpi/*.png, layout/view_time_picker.xml, mipmap-anydpi-v26/ic_launcher.xml. Manifest: app/src/main/AndroidManifest.xml. Build file: app/build.gradle.kts (no extra libraries).

## Known facts
- Samsung switches the accessibility service (Power guard) OFF after every reinstall: the user switches it on again in Permissions -> Power guard.
- User must allow once: Display over other apps, Background running (battery), Power guard, Pin windows (Security settings), Samsung "sleeping apps" off for this app.
- A plain string in power_guard_config.xml description caused build error #20; the attribute was removed.

## STATUS
Parts 1-3 done and installed. Part 4 (this update): alarm resumes after a phone restart + these notes.

## NEXT STEP
Part 5 = final testing, no new features: (1) overnight real alarm test with a backup alarm on the normal clock app, (2) restart the phone while the alarm rings, (3) reminders for a whole day, (4) make the GitHub repo private. Fix only what the user reports.
