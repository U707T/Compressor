# Changelog

All notable changes to this fork of Compressor are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
