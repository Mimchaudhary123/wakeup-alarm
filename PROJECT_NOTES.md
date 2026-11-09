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
- Block force-stop/uninstall, time changes, factory reset via Device Owner policies.
- Reboot: save state, relaunch alarm after boot.
- Failsafe: remove the app's admin from the PC with an adb command (to be given before lock is enabled). Keep a backup alarm during testing.
- APK must be shareable to family phones (works as a normal alarm without Device Owner).
- Package name: com.wakeup.alarm. Debug keystore is committed (app/debug.keystore) so new builds install over old ones.

## Parts plan
1. Basic daily alarm + test button + volume watchdog + GitHub build workflow  <- Part 1 (DONE)
2. Volume key lock (dispatchKeyEvent intercept) + stronger watchdog  <- Part 2 (written)
3. QR scan screen (camera + torch + secret check + 20%/30s once rule)
4. Device Owner kiosk lockdown + failsafe command
5. Reboot handling + hardening, Samsung quirks
6. Testing checklist, final polish

## Task details (decided in chat)
- Task is chosen on the main screen next to the time picker (radio buttons), saved when "Save and enable daily alarm" or "Test" is pressed. Stored in TaskPrefs ("numbers" default / "qr").
- Number game: left = random 5-digit number (all digits different, no leading 0) in a random colour. Right = 4 options stacked: the correct one has the SAME digits and a DIFFERENT colour; 3 wrong ones are the same digits in another order (2 are close look-alikes with two neighbouring digits swapped, 1 fully shuffled); 2 of the wrong ones use the SAME colour as the left number (trick). Tap correct -> next round; 10 rounds -> alarm stops; wrong -> back to round 1 with a new question.
- QR task: app asks for camera permission on the main screen when QR is chosen. If permission is missing at alarm time, fall back to the number game so the alarm can never get stuck.
- Claude makes up the secret text and gives the user a printable QR image.

## STATUS
- Part 1 DONE. Part 2 DONE and tested (volume keys, Back, watchdog OK).
- Part 2 open problems (need Part 4 / Device Owner): Home leaves the alarm screen; power button shows the power-off menu.
- Part 3a written (not yet tested): task chooser in MainActivity.kt + number game in AlarmActivity.kt + TaskPrefs.kt. QR option is shown but still uses the number game.
- Repo files known: MainActivity, AlarmActivity, AlarmService, AlarmReceiver, BootReceiver, AlarmScheduler, TaskPrefs. Manifest has no camera permission yet.

## NEXT STEP
User uploads AlarmActivity.kt, MainActivity.kt, TaskPrefs.kt, PROJECT_NOTES.md, waits for the green build, installs, runs the Part 3a test list and reports. Then Part 3b: QR scan (needs app/build.gradle(.kts) from the user to add the camera/scanner library; manifest camera permission; torch; secret check; 20%/30s once rule; QR image). Then Part 4.
