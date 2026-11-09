# Wakeup Alarm - PROJECT NOTES (paste this into any new Claude chat/account)

Instruction for Claude: continue EXACTLY from the NEXT STEP below. Do not re-plan. Give the user clear, numbered, beginner-level instructions for anything they must do. Write complete files, one part at a time.

## Goal
Personal Android alarm app (not for Play Store, user's own use). Alarm must be impossible to escape while half-asleep.
User: Windows 10 PC, Chrome. Phone: Samsung Galaxy M31, Android 12, One UI Core 4.1.
GitHub account: Mimchaudhary123, private repo "wakeup-alarm". APK is built by GitHub Actions (no Android Studio). User downloads APK from Actions -> Artifacts.
adb is at C:\platform-tools (Samsung USB driver installed, `adb devices` works).

## Decided design
- Task to stop the alarm: scan a specific QR code (secret text in the code) stuck in bathroom/kitchen. Only the correct code counts.
- While scanning: alarm volume drops to 20% for 30 seconds, ONLY ONCE per alarm, then back to max. If no valid scan, volume returns to max.
- Flashlight (torch) ON while scanning (power cuts).
- Volume keys locked while ringing + watchdog forcing alarm volume to max.
- Home/Back/Recents/notification shade/power menu/safe boot blocked via Device Owner kiosk (lock task) mode. Fallback for phones without Device Owner: screen pinning.
- (Device Owner is NOT possible: the user's phone has many important accounts that cannot be removed, and adb set-device-owner needs no accounts. Part 4 uses screen pinning + overlay-permission relaunch instead.)
- Block force-stop/uninstall, time changes, factory reset via Device Owner policies.
- Reboot: save state, relaunch alarm after boot.
- Failsafe: remove the app's admin from the PC with an adb command (to be given before lock is enabled). Keep a backup alarm during testing.
- APK must be shareable to family phones (works as a normal alarm without Device Owner).
- Package name: com.wakeup.alarm. Debug keystore is committed (app/debug.keystore) so new builds install over old ones.

## Parts plan
1. Basic daily alarm + test button + volume watchdog + GitHub build workflow  <- Part 1 (DONE)
2. Volume key lock (dispatchKeyEvent intercept) + stronger watchdog  <- Part 2 (written)
3. QR scan screen (camera + torch + secret check + 20%/30s once rule)  <- Part 3a DONE+tested; Part 3b written
4. Lockdown WITHOUT Device Owner: screen pinning (startLockTask) + service relaunches the alarm screen via 'Display over other apps'  <- Part 4 (written)
5. Reboot handling + hardening, Samsung quirks
6. Testing checklist, final polish

## Task details (decided in chat)
- Task is chosen on the main screen next to the time picker (radio buttons), saved when "Save and enable daily alarm" or "Test" is pressed. Stored in TaskPrefs ("numbers" default / "qr").
- Number game: left = random 5-digit number (all digits different, no leading 0) in a random colour. Right = 4 options stacked: the correct one has the SAME digits and a DIFFERENT colour; 3 wrong ones are the same digits in another order (2 are close look-alikes with two neighbouring digits swapped, 1 fully shuffled); 2 of the wrong ones use the SAME colour as the left number (trick). Tap correct -> next round; 10 rounds -> alarm stops; wrong -> back to round 1 with a new question.
- QR task details (decided): camera starts ONLY after the user taps a big "Scan QR code" button. Camera stays on until the correct code is scanned (no 30s camera timeout). Flashlight is a user-controlled ON/OFF button (default OFF). When the button is tapped the alarm volume drops to 20% for 30 s, ONCE per alarm (AlarmService.startQuietPeriod), then the watchdog returns it to max; later scans have no quiet period. QR is printed at a shop; tested from the PC screen first.
- QR library: com.journeyapps:zxing-android-embedded:4.3.0 (added to app/build.gradle.kts). Secret text is TaskPrefs.QR_SECRET; image file wakeup-qr.png.
- QR task: app asks for camera permission on the main screen when QR is chosen. If permission is missing at alarm time, fall back to the number game so the alarm can never get stuck.
- Claude makes up the secret text and gives the user a printable QR image.

## STATUS
- Part 1 DONE. Part 2 DONE and tested (volume keys, Back, watchdog OK).
- Part 2 open problems (need Part 4 / Device Owner): Home leaves the alarm screen; power button shows the power-off menu.
- Part 3a DONE and tested OK (task chooser + number game).
- Part 3b DONE and tested OK (all 12 steps): QR scan screen in AlarmActivity.kt, camera permission request + save guard in MainActivity.kt, quiet-period logic in AlarmService.kt, TaskPrefs.QR_SECRET, manifest CAMERA permission, zxing dependency in app/build.gradle.kts, wakeup-qr.png.
- Repo files known: MainActivity, AlarmActivity, AlarmService, AlarmReceiver, BootReceiver, AlarmScheduler, TaskPrefs. Manifest now has the CAMERA permission.

- Part 4 written (not yet tested): AlarmActivity calls startLockTask while ringing and stopLockTask when done; AlarmService relaunches AlarmActivity if it is not in front (needs SYSTEM_ALERT_WINDOW granted by the user); MainActivity has buttons for the overlay permission and security settings (Pin windows). Known gap: power button menu cannot be blocked without Device Owner.

## NEXT STEP
User uploads AndroidManifest.xml, AlarmActivity.kt, AlarmService.kt, MainActivity.kt, PROJECT_NOTES.md, builds, installs, turns on Pin windows + Display over other apps, runs the Part 4 test list and reports exactly what the pin prompt/Home/power button did. Then Part 5: reboot handling + hardening, Samsung quirks (power menu / side key settings).
