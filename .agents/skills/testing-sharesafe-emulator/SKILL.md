---
name: testing-sharesafe-emulator
description: How to end-to-end test the ShareSafe Android app on the "sharesafe" emulator — UI driving via adb/uiautomator, gesture quirks, share-intent workarounds, and slow-scan patience.
---

# Testing ShareSafe on the Android emulator

## Environment
- AVD `sharesafe` (API 36, x86_64, 1080x2400). App installs as `com.sharesafe.app.debug`; main activity `com.sharesafe.app.MainActivity`.
- `adb` = `$HOME/Android/Sdk/platform-tools/adb`; emulator binary `$HOME/Android/Sdk/emulator/emulator`.
- **For screen recording, launch the emulator WITH a window** (omit `-no-window`) — a headless emulator produces a black/empty recording. Then maximize: `wmctrl -r :ACTIVE: -b add,maximized_vert,maximized_horz` or resize it to a fixed rectangle.
- Boot readiness: `adb shell getprop sys.boot_completed` == 1, then wait ~1 more min for the /sdcard FUSE mount before `adb install -r`.
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Driving the UI
- Assert state with `adb shell uiautomator dump /data/local/tmp/ui.xml` + `cat` — grep `text="..."`/`content-desc="..."`/bounds. The app exposes content-desc on icon buttons (Undo, Redo, Select all, Preview, Edit crop, duplicate, delete, deselect).
- Screenshot evidence: prefer `adb exec-out screencap -p > /tmp/x.png` (streams to host, never touches /sdcard). If you do `adb pull /sdcard/x.png`, `adb shell rm` the device copy immediately — **MediaStore indexes any PNG on /sdcard and it will appear in the photo picker, shifting grid positions between your uiautomator dump and your taps**.
- Device is 1080x2400; if you must drive the GUI window directly, map device px → screenshot px via the emulator window's frame corners.

## Gestures (what actually works)
- **Long-press**: `adb shell input swipe X Y X Y 700` (same-point, ≥350ms) reliably toggles region multi-select.
- **Tap a detected region**: its label badge is drawn ABOVE the rect — tap ~40-60 device px below the badge center (interior) or the tap misses. Pure `input tap` jitter can also set the drag "moved" flag; prefer the same-point swipe as a robust "press".
- **Canvas drags** (draw region / move / crop corner resize) via `adb shell input swipe` are flaky — taps and Compose sliders/scroll-rows work fine, but pointerInput canvas gestures mostly no-op; occasionally a ~1.8s slow drag registers a partial move. A real mouse `left_click_drag` on the emulator window works reliably *if a host display exists* (see below).
- `input text` into an EditText often injects a stray first char (IME race) — clear with `input keyevent 67` ×N, then type WITHOUT re-tapping (field keeps focus), and re-dump to verify before submitting.
- ANR dialogs ("System UI isn't responding") appear under load — detect via uiautomator and tap "Wait".

## No host GUI?
If `computer` tool fails with "enigo init failed" and `/tmp/.X11-unix` is empty, there is no X session — screenshots/recording of the host are impossible. Drive everything via adb: `exec-out screencap -p > /tmp/x.png` for evidence + `uiautomator dump` for state. `adb shell screenrecord` can capture short device clips if a video artifact is needed.

## Scan timing
- Since the audit-upgrades commit the OCR/faces/codes detectors run in PARALLEL — a scan finishes in ~5-15s, not minutes. Older guidance of 1-2min per phase no longer applies; poll uiautomator every ~8-10s.

## Verifying photo-picker selections deterministically
- Picker orders by date_modified DESC; `adb push` PRESERVES the source mtime — a re-pushed old file sorts deep in the grid. To force a fixture to the top: copy to a fresh filename, `touch` it (new mtime), push, then `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/name.png`, reopen picker.
- To identify an ambiguous thumbnail without eyes: downscale the fixture on the host with the same center-crop-square the picker uses, then RMSE-compare per cell (~90px thumbs; RMSE<15 = match, >100 = different image).

## Probing coverage / verification claims
- `run-as com.sharesafe.app.debug cat .../shared_prefs/sharesafe_prefs.xml` reads settings state directly (hints_seen, blacklist, queue_uris, apply_to_all) — decisive for persistence assertions.
- Tap-word is also a coverage probe: tapping a word inside a region selects it; a bare OCR'd word creates a MANUAL region (+1 count); non-OCR'd empty space does nothing.
- To verify redaction claims end-to-end (e.g., "QR destroyed"), decode the exported preview screencap directly: `pip install opencv-python-headless` then `cv2.QRCodeDetector().detectAndDecode(img)`. A decodable code while the app claims "Verified" = real finding.
- A deliberately DISABLED region's content is verifiable the same way — disabled CODE regions are skipped by the post-export verifier, so the "Verified" banner can overstate.

## Share-intent testing
- `am broadcast/start` with `-a android.intent.action.SEND --eu android.intent.extra.STREAM <uri>` reaches the app but the URI grant does NOT survive `onNewIntent` — the app shows "Could not open this image." and logcat shows `SecurityException ... has no access to content://media/...`. This is a harness artifact, not an app bug.
- `ACTION_SEND_MULTIPLE` cannot be driven by `am` at all (no Uri-array flag) — verify it by code review or a real app that shares multiple images.
- **Authentic intake test = self-share loop**: pick an image → Preview & Share → "Safe Share" → the system chooser lists ShareSafe itself as a target (it declares an image intent-filter) → tap it → the app receives its own FileProvider URI with a real grant and goes to "Scanning screenshot" → editor. This exercises `extractSharedImages` → `loadImage` end-to-end.
- The resumable-queue card: force-stop mid-queue, relaunch → "Resume batch" card appears (Prefs.persistQueue), but picker-URI grants usually die with the process → resume currently lands on "Could not open this image." and clears the card. Expected unless the app starts taking persistable URI permissions.

## Test image fixtures
- Push fixtures to `/sdcard/Pictures/` before the run; note MediaStore order changes as you add/remove files. Verify picker state via uiautomator immediately before tapping thumbnails.

## Devin Secrets Needed
- none
