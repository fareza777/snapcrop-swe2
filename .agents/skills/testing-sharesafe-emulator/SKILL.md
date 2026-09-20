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
- **Canvas drags** (draw region / crop corner resize) via `adb shell input swipe` are flaky and often no-op. A real mouse `left_click_drag` on the emulator window works reliably.
- ANR dialogs ("System UI isn't responding") appear under load — detect via uiautomator and tap "Wait".

## Scan timing
- Each ML Kit phase (OCR/faces/codes) takes ~1-2 min on 2 vCPU + swiftshader. Poll uiautomator every ~10-12s for `protected`/`Could not`; a full scan can take ~3-4 min.

## Share-intent testing
- `am broadcast/start` with `-a android.intent.action.SEND --eu android.intent.extra.STREAM <uri>` reaches the app but the URI grant does NOT survive `onNewIntent` — the app shows "Could not open this image." and logcat shows `SecurityException ... has no access to content://media/...`. This is a harness artifact, not an app bug.
- `ACTION_SEND_MULTIPLE` cannot be driven by `am` at all (no Uri-array flag) — verify it by code review or a real app that shares multiple images.
- **Authentic intake test = self-share loop**: pick an image → Preview & Share → "Safe Share" → the system chooser lists ShareSafe itself as a target (it declares an image intent-filter) → tap it → the app receives its own FileProvider URI with a real grant and goes to "Scanning screenshot" → editor. This exercises `extractSharedImages` → `loadImage` end-to-end.

## Test image fixtures
- Push fixtures to `/sdcard/Pictures/` before the run; note MediaStore order changes as you add/remove files. Verify picker state via uiautomator immediately before tapping thumbnails.

## Devin Secrets Needed
- none
