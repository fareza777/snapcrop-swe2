# ShareSafe

Share screenshots without leaking what's in them.

ShareSafe picks up a screenshot, finds sensitive data on-device, redacts it, and
hands you a polished image ready to share — no account, no uploads, no network.

## Core flow

**Select → Detect → Redact → Preview → Safe Share**

1. **Select** — Android Photo Picker (no storage permission) — up to 9 at a
   time as a batch queue — or `Share → ShareSafe` from any app.
2. **Detect** — everything runs on-device via bundled ML Kit models:
   - OCR (Latin text recognition) + pattern engine → email, phone, payment card
     (Luhn-checked), long number sequences/IDs, IPs, JWTs, API keys, passwords,
     plus Indonesian identifiers (NIK/KTP, NPWP, bank account, license plate)
   - Face detection → face regions
   - Barcode scanning → QR / barcode regions
   - Detectors run in parallel on a higher-resolution copy so small text isn't
     lost to downsampling; a failed stage shows a retry banner instead of
     silently finding nothing
   - **Always-redact list** — your own name/email/number auto-marked every scan
   - Auto-crop strips status bar, navigation bar, and uniform borders
3. **Redact** — tap a highlighted area to select it (move, resize, restyle,
   disable, delete), tap any detected word to redact it, or drag to draw a new
   region. Long-press multi-select, select-all, category chips, **undo + redo**,
   per-region or global **Blur / Pixelate / Blackout** with a strength slider,
   pinch-zoom, manual crop handles, and a live rendered preview toggle.
4. **Preview** — final image rendered exactly as it will export; post-render
   verification re-scans for still-decodable barcodes and upgrades them to
   blackout. Beautifier (padding, rounded corners, gradient/solid backgrounds,
   shadow) with PNG / JPEG / WebP output.
5. **Safe Share** — to the system share sheet (WhatsApp etc.) or save to
   `Pictures/ShareSafe` via MediaStore. Batch flow: "Next" advances the queue;
   "Apply to all" carries category settings across images; an interrupted batch
   can be resumed from Home.

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
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease        # minified release APK
./gradlew :app:bundleRelease          # AAB for Play → app/build/outputs/bundle/release/
./gradlew :app:testDebugUnitTest      # unit tests (JUnit + Robolectric)
./gradlew :app:lintDebug              # Android lint
```

## Release signing

Copy `keystore.properties.template` → `keystore.properties` (gitignored) and
point it at your upload keystore. When present, `assembleRelease` /
`bundleRelease` produce signed artifacts; without it they stay unsigned.

## Google Play notes

- **Data safety**: no data collected or shared — see `PRIVACY.md` for the
  pre-filled answers; `docs/privacy.html` is a hostable policy page.
- Zero permissions (including `INTERNET`) keeps the review trivial.
- Ship the AAB, not the APK — per-ABI delivery shrinks the download a lot.
