# Batch compression & hardware performance tuning (1.7.1)

This document describes the two features added on top of upstream Compressor
1.7.0: the batch queue and the hardware-aware encoder tuning. It also lists what
still needs on-device verification.

## Batch compression

**Entry point:** the empty screen → *Batch compress*.

**Picker**

- Photo picker mode: `ActivityResultContracts.PickMultipleVisualMedia(50)`.
- Gallery app mode: `ACTION_GET_CONTENT` + `EXTRA_ALLOW_MULTIPLE`, falling back to
  the photo picker when no gallery activity can handle the intent.

**Engine** (`CompressorViewModel.runBatch`)

The queue is deliberately sequential: one MediaCodec encoder session at a time.
Hardware encoders expose a small number of concurrent instances, and MediaTek
parts in particular fail the second 4K session instead of queueing it. Sequential
execution also means the existing single-item pipeline (codec plan, H.264
fallback, HEVC muxer fallback, bitrate guardrails) is reused unchanged.

Per item:

1. `loadSelection()` probes the video and applies the default preset.
2. `startCompression()` runs the export; a `CompletableDeferred<Boolean>` reports
   success/failure so the queue can await it without polling.
3. The output is persisted to the configured destination (SAF folder, otherwise
   `Movies/Compressor`); a broken SAF folder falls back to the gallery.
4. The temp file is deleted only after a successful save.

Cancellation completes the pending deferred with `false`, so a cancelled item can
never leave the queue awaiting a result.

**Limits:** 50 items per batch, 6 h per item (`BatchLimits`). The wake lock uses
the same ceiling so it cannot leak indefinitely.

## Hardware detection

`HardwareUtils.detectDeviceProfile()` collects:

| Field | Source |
|---|---|
| SoC model / vendor | `Build.SOC_MODEL`, `Build.SOC_MANUFACTURER`, `Build.HARDWARE`, `Build.BOARD`, model-name heuristics |
| GPU | EGL context + `GL_RENDERER` |
| CPU cores / max frequency | `Runtime.availableProcessors()` + `/sys/devices/system/cpu/cpu*/cpufreq/*` |
| RAM | `ActivityManager.MemoryInfo` |
| Xiaomi / HyperOS | `Build.MANUFACTURER` + `ro.mi.os.version.name` / `ro.miui.ui.version.name` (reflection, failure-tolerant) |
| Tier | Known flagship SoC list, then frequency/RAM/cores heuristics |

Redmi K80 Ultra (K80 至尊版) is detected by marketing name, the Chinese retail
model codes (`25xxRK…`), the `dali` codename, or "Xiaomi + Dimensity 9400/9400+".

## Encoder tuning

`PerformanceTuning.resolve()` maps *(device tier, mode, fps)* to `EncoderTuning`:

| Knob | Auto (flagship/high) | Maximum | Balanced |
|---|---|---|---|
| `operating-rate` hint | target fps | target fps | off |
| realtime `priority` | non-MediaTek only | non-MediaTek only | off |
| partial wake lock | on | on | off |
| foreground service | on | on | on |
| `CodecDbLite` | on | on | on |
| CBR when supported | non-MediaTek only | non-MediaTek only | off |
| next-item prefetch | on | on | on |
| HDR → SDR tone map | on | on | on |

MediaTek is never asked for realtime priority or CBR: the upstream VBR workaround
exists because several MTK encoder builds mis-report CBR support. If an encoder
still rejects the `operating-rate`/`priority` keys, the export retries once with a
hint-free `DefaultEncoderFactory` and finally with VBR, so tuning can never make
an export fail that would otherwise succeed.

`DefaultEncoderFactory.setEnableCodecDbLite(true)` lets Media3 apply its bundled
per-chipset encoder recommendations (B-frames, temporal layers, bitrate curves).

## What is intentionally *not* done

- **No concurrent encoder sessions.** Running two Transformers in parallel would
  require splitting the single-item UI state machine into per-item state; on
  MediaTek it also risks `ERROR_INSUFFICIENT_RESOURCES` for the second 4K session.
  The pipelined prefetch gets most of the wall-clock win with none of the risk.
- **No vendor-private `vendor.mtk.*` MediaFormat keys.** They are undocumented and
  throw on some ROMs.
- **No forced B-frames / temporal layers.** Only applied when `CodecDbLite` knows
  the exact chipset.

## On-device verification checklist

The sandbox used to build this release has no Android device or emulator, so the
following must be checked on real hardware:

1. Batch of 5+ 1080p clips on the K80 Ultra: verify the notification progress,
   screen-off survival, and that `Movies/Compressor` contains all outputs.
2. HDR10 clip → H.264 export: colours must not be washed out (tone-map path) and
   the export must not fail.
3. MediaTek: confirm no `operating-rate` rejection (look for the hint-free retry
   in logcat if an export fails).
4. Cancel a batch mid-encode: the queue must stop, no stray `Transformer` keeps
   writing, and the summary shows the cancelled items.
5. Custom SAF output folder + revoke access: batch must fall back to the gallery
   instead of losing the export.
