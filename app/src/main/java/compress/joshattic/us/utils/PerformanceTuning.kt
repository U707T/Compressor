package compress.joshattic.us.utils

/**
 * How aggressively the encoder should be driven.
 *
 * - [AUTO] follows the detected hardware class: flagship/high-end devices get
 *   maximum throughput hints, everything else stays balanced.
 * - [MAXIMUM] always asks for realtime encoder priority, an operating-rate hint
 *   and keeps the CPU awake, even on mid-range hardware.
 * - [BALANCED] disables every optional hint (lowest heat/battery impact).
 */
enum class PerformanceMode {
    AUTO,
    MAXIMUM,
    BALANCED;

    companion object {
        fun fromName(name: String?): PerformanceMode =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Concrete knobs handed to the export pipeline. Pure data so the resolution
 * logic can be unit tested on the JVM.
 */
data class EncoderTuning(
    /** MediaFormat "operating-rate" hint in fps; 0 disables the hint. */
    val operatingRateHint: Int = 0,
    /** MediaFormat "priority" = 0 (realtime) when true. */
    val realtimePriority: Boolean = false,
    /** Hold a partial wake lock while a batch is running. */
    val holdWakeLock: Boolean = false,
    /** Run exports inside a dataSync foreground service. */
    val useForegroundService: Boolean = true,
    /** Let Media3 apply its per-chipset encoder database. */
    val useCodecDbLite: Boolean = true,
    /** Raise the priority of the copy/save worker threads. */
    val boostIoThreadPriority: Boolean = false,
    /** Prefer CBR when the encoder supports it (MediaTek uses VBR). */
    val preferCbr: Boolean = false,
    /** Probe the next batch item while the current one is encoding. */
    val prefetchNextItem: Boolean = true,
    /** Tone map HDR sources to SDR when the target codec cannot carry HDR. */
    val toneMapHdrToSdr: Boolean = true,
    /** True when at least one non-default knob is active. */
    val isAggressive: Boolean = false
) {
    val hasAnyHint: Boolean
        get() = operatingRateHint > 0 || realtimePriority || holdWakeLock ||
            boostIoThreadPriority || preferCbr
}

object PerformanceTuning {

    /**
     * Resolves the tuning for the current device and user preference.
     *
     * @param targetFps frame rate of the export, used for the operating-rate hint.
     */
    fun resolve(
        profile: DeviceProfile,
        mode: PerformanceMode,
        targetFps: Int = 30
    ): EncoderTuning {
        val aggressive = when (mode) {
            PerformanceMode.MAXIMUM -> true
            PerformanceMode.BALANCED -> false
            PerformanceMode.AUTO -> profile.tier == PerformanceTier.FLAGSHIP ||
                profile.tier == PerformanceTier.HIGH
        }

        val fpsHint = when {
            !aggressive -> 0
            targetFps > 0 -> targetFps.coerceIn(24, 120)
            else -> 30
        }

        return EncoderTuning(
            operatingRateHint = fpsHint,
            // MediaTek encoders dislike realtime priority on some ROMs; only ask
            // for it on non-MediaTek hardware.
            realtimePriority = aggressive && !profile.isMediaTek,
            holdWakeLock = aggressive,
            useForegroundService = true,
            useCodecDbLite = true,
            boostIoThreadPriority = aggressive,
            // MediaTek VBR workaround (see DeviceWorkarounds): never force CBR.
            preferCbr = aggressive && !profile.isMediaTek,
            prefetchNextItem = true,
            toneMapHdrToSdr = true,
            isAggressive = aggressive
        )
    }

    /**
     * Decides whether an HDR input needs an OpenGL tone-map pass before encoding.
     * H.264 has no HDR signalling, so it always needs the GPU path; H.265/AV1 can
     * carry HDR and only needs it on devices known to break (Pixel 10).
     */
    fun shouldToneMap(
        tuning: EncoderTuning,
        inputIsHdr: Boolean,
        outputMimeType: String,
        isPixel10: Boolean
    ): Boolean {
        if (!inputIsHdr || !tuning.toneMapHdrToSdr) return false
        return when {
            outputMimeType == androidx.media3.common.MimeTypes.VIDEO_H264 -> true
            isPixel10 && (outputMimeType == androidx.media3.common.MimeTypes.VIDEO_H265 ||
                outputMimeType == androidx.media3.common.MimeTypes.VIDEO_H264) -> true
            else -> false
        }
    }
}
