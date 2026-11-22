# Changelog

All notable changes to this fork of Compressor are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.8.0] — 2026-11-22

### Added

- **Android 16 / HyperOS adaptation audit** (`docs/AUDIT-1.8.0.md`): 16 KB page
  size, edge-to-edge, predictive back, foreground-service types and the
  Android 15+ `dataSync` six-hour cap are all covered.
- `CompressionForegroundService.onTimeout()` stops the foreground state cleanly
  when the platform hits the six-hour limit instead of letting it ANR.
- Full code audit (`docs/AUDIT-1.8.0.md`) with a refreshed triage table.

### Changed

- `CompressorViewModel` split: `codec/CodecCapabilities` (encoder queries,
  MediaTek detection) and `storage/MediaSaver` (SAF / MediaStore / Uri) are now
  standalone units; the ViewModel dropped from 2,583 to 2,383 lines.
- Portrait-aware resolution maths moved to
  `CompressorUiState.heightForShortSide()` / `shortSideForHeight()` and reused by
  the preset logic, the selection probe and `autoAdjust()`.
- The three copies of the "Start compression" button are now one composable.
- README rewritten: fork differences, permissions, HyperOS setup, build, signing
  and CI release flow, architecture and known limits.

### Fixed

- **A failed save no longer looks like a failed compression.** Save errors use a
  dedicated `saveError` channel, the result screen shows "Not saved yet: …" and
  keeps the finished file available for retry instead of routing to the failure
  screen whose back button deleted the output.
- The batch engine now uses the application context, so a multi-hour queue can
  no longer retain a destroyed Activity.
- A file without a readable video track fails immediately with a clear message
  instead of silently compressing with a 10 MB default target.
- `pendingCompletion` is cleared when an export settles.
- `shareVideo()` no longer force-unwraps `Uri.path`.
- Hard-coded English in `InfoDialog`, `ResultScreen` and `PresetsSettingsScreen`
  replaced with localised strings (en + zh-rCN).

## [1.7.3] — 2026-11-22

### Fixed

- **"Unexpected runtime error" on variable-frame-rate videos** (social media
  downloads, screen recordings, 23.976/29.97 fps sources). Media3 1.11.0 added a
  frame-rate precondition that throws when a decoder reports `frame-rate = 0`;
  the fix (androidx/media#3399) is in Media3 1.11.1, which is now used.
- Reported frame rates are normalised (0 / NaN / >240 fps fall back to 30 fps)
  before they reach the encoder, so VFR metadata can no longer poison the plan.

### Added

- **Compatibility retry.** If an export still fails with
  `ERROR_CODE_UNEXPECTED` / `ERROR_CODE_FAILED_RUNTIME_CHECK`, Compressor
  automatically retries once with the simplest configuration: H.264, original
  frame rate, no scaling, no encoder hints and AAC passthrough. A warning
  explains what happened.

## [1.7.2] — 2026-11-22

### Added

- **Built-in file browser** (`Settings → Video picker → Built-in file browser`).
  It walks shared storage directly with `MANAGE_EXTERNAL_STORAGE`, so no system
  picker, no Storage Access Framework and no HyperOS "secure access" dialog is
  involved. Folders are browsable (including SD cards), videos show their size,
  and batch selection works the same way as in the system pickers.
- The built-in browser is the **default** picker mode. The Android photo picker
  and the third-party chooser (MT Manager, Files, galleries) are still available
  in Settings.

### Changed

- `VideoPickerMode` gained `FILE_BROWSER`; the picker dialog now renders its
  three options through one shared composable instead of three copies.

### Notes

- `MANAGE_EXTERNAL_STORAGE` is an all-files permission: Google Play restricts it
  to file managers and similar apps, so this fork is intended for sideloading.
  Revoke it any time in system settings — the other picker modes keep working.
- Android 7–9 fall back to `READ_EXTERNAL_STORAGE`, Android 10 additionally uses
  `requestLegacyExternalStorage`.

## [1.7.1] — 2026-11-22

### Added

- **Batch compression.** Pick up to 50 videos, review the queue, and compress them
  one after another with per-video progress, per-video error reporting and a
  storage summary at the end. Every queued video uses the current video, audio
  and preset settings.
- **Performance screen** (`Settings → Performance`) that detects the SoC, GPU,
  core count/frequency, RAM and OS, and shows exactly which encoder tunings are
  active.
- **Performance mode** (`Auto` / `Maximum` / `Balanced`). `Auto` enables the
  aggressive profile on flagship and high-end hardware only.
- **MediaTek / Dimensity tuning.** Realtime priority is never requested from
  MediaTek encoders (they reject it on some ROMs), VBR stays forced, and a
  hint-free retry is attempted if an encoder rejects `operating-rate` /
  `priority`.
- **HyperOS background protection.** Batch exports run inside a `dataSync`
  foreground service with a progress notification, plus an optional partial wake
  lock, so HyperOS cannot freeze a long queue mid-encode.
- **Per-device encoder database** (Media3 `CodecDbLite`) enabled for all exports.
- **GPU tone mapping for HDR → H.264.** HDR sources now go through the OpenGL
  tone-map path when the target codec cannot carry HDR (previously Pixel 10 only).
- **Pipelined batch queue**: the next video is probed while the current one is
  encoding, and probes are cached (4-entry LRU) instead of a single slot.
- Simplified Chinese translations for every new string.

### Changed

- The save pipeline (`SAF folder`, `MediaStore`, `CreateDocument`) now shares one
  implementation, so all three destinations behave identically.
- `Transformer.start()` failures are reported as normal export errors instead of
  crashing the app.
- MediaStore rows are deleted if writing the output stream fails, instead of
  leaving a permanently pending item in the gallery.

### Fixed

- Progress no longer keeps running after a cancelled batch item.
- A failed batch save keeps the finished file in the cache instead of silently
  losing the export, and falls back to the gallery when a SAF folder is broken.
- HDR warnings are appended to the plan warnings instead of replacing them.

## [1.7.0] — upstream baseline

See the upstream project history for everything before this fork.
