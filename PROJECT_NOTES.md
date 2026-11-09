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
1. Basic daily alarm + test button + volume watchdog + GitHub build workflow  <- Part 1 (written)
2. Volume key lock (onKeyDown intercept) + stronger watchdog
3. QR scan screen (camera + torch + secret check + 20%/30s once rule)
4. Device Owner kiosk lockdown + failsafe command
5. Reboot handling + hardening, Samsung quirks
6. Testing checklist, final polish

## STATUS
- Part 1 files written and handed to user. Not yet uploaded/built.

## NEXT STEP
User uploads Part 1 to the repo, waits for the green build, installs the APK, tests "Test: ring in 10 seconds", and reports the result (or pastes build errors from the Actions log). Then start Part 2.
