# ShareSafe

Share screenshots without leaking what's in them.

ShareSafe picks up a screenshot, finds sensitive data on-device, redacts it, and
hands you a polished image ready to share — no account, no uploads, no network.

## Core flow

**Select → Detect → Redact → Preview → Safe Share**

1. **Select** — Android Photo Picker (no storage permission) or `Share → ShareSafe`
   from any app.
2. **Detect** — everything runs on-device via bundled ML Kit models:
   - OCR (Latin text recognition) + pattern engine → email, phone, payment card
     (Luhn-checked), long number sequences/IDs, IPs, JWTs, API keys, passwords
   - Face detection → face regions
   - Barcode scanning → QR / barcode regions
   - Auto-crop strips status bar, navigation bar, and uniform borders
3. **Redact** — tap a highlighted area to select it (move, resize, restyle,
   disable, delete), or drag anywhere to draw a new region. Per-region or global
   **Blur / Pixelate / Blackout**, pinch-zoom for precision, undo, one-tap
   category filters, and a live rendered preview toggle.
4. **Preview** — final image rendered exactly as it will export; optional
   beautifier (padding, rounded corners, gradient/solid backgrounds, shadow).
5. **Safe Share** — PNG to the system share sheet (WhatsApp etc.) or save to
   `Pictures/ShareSafe` via MediaStore.

## Privacy / permissions

- **Zero runtime permissions** — Photo Picker and MediaStore inserts need none
  on API 29+.
- **No `INTERNET` permission** — ML Kit bundled models work fully offline.
- Export writes a flattened PNG: redaction is burned in, not metadata.

## Stack

Kotlin · Jetpack Compose (Material 3, dark-first theme) · ML Kit bundled
text-recognition / face-detection / barcode-scanning · minSdk 29 · targetSdk 36.

Detection heuristics adapted from [SnapCrop](https://github.com/SysAdminDoc/SnapCrop)
(MIT) — reimplemented for ShareSafe's own UX; no SnapCrop branding or assets.

## Build

```bash
./gradlew :app:assembleDebug     # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease   # minified release APK (unsigned)
./gradlew :app:testDebugUnitTest # unit tests
```
