package compress.joshattic.us.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes

/**
 * Read-only view of the device's MediaCodec encoders/decoders.
 *
 * Extracted from [compress.joshattic.us.viewmodel.CompressorViewModel] so the
 * capability rules (hardware-only filtering, size/rate checks, MediaTek quirks)
 * live in one place and can be unit tested with a mocked codec list.
 */
object CodecCapabilities {

    /** MediaCodecList is expensive to build; keep one snapshot per process. */
    val codecInfos: List<MediaCodecInfo> by lazy {
        try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val PREFERRED_VIDEO_MIMES = listOf(
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_AV1
    )

    /** Every video encoder MIME the device exposes, preferred codecs first. */
    fun deviceEncoders(): List<String> {
        val codecs = mutableSetOf<String>()
        for (info in codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.startsWith("video/", ignoreCase = true)) {
                    codecs.add(type.lowercase())
                }
            }
        }
        return codecs.sortedWith { a, b ->
            val indexA = PREFERRED_VIDEO_MIMES.indexOf(a)
            val indexB = PREFERRED_VIDEO_MIMES.indexOf(b)
            when {
                indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                indexA != -1 -> -1
                indexB != -1 -> 1
                else -> a.compareTo(b)
            }
        }
    }

    /** True when the device has a *hardware* encoder for [mimeType]. */
    fun hasEncoder(mimeType: String): Boolean {
        for (info in codecInfos) {
            if (!info.isEncoder) continue
            if (isSoftwareEncoder(info)) continue
            if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) return true
        }
        return false
    }

    /** True when the only encoder for [mimeType] is a software one. */
    fun isSoftwareCodec(mimeType: String): Boolean {
        var hasHardware = false
        var hasSoftware = false
        for (info in codecInfos) {
            if (!info.isEncoder) continue
            if (!info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) continue
            if (isSoftwareEncoder(info)) hasSoftware = true else hasHardware = true
        }
        return hasSoftware && !hasHardware
    }

    /** Size/rate support check against every matching codec. */
    fun isConfigurationSupported(
        mimeType: String,
        width: Int,
        height: Int,
        fps: Float,
        encoder: Boolean
    ): Boolean {
        val safeFps = kotlin.math.ceil(if (fps > 0f) fps.toDouble() else 30.0)
        return codecInfos.asSequence()
            .filter { it.isEncoder == encoder }
            .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            .any { info ->
                try {
                    val videoCaps = info.getCapabilitiesForType(mimeType).videoCapabilities
                        ?: return@any false
                    videoCaps.areSizeAndRateSupported(width, height, safeFps) ||
                        videoCaps.areSizeAndRateSupported(height, width, safeFps)
                } catch (_: Exception) {
                    false
                }
            }
    }

    fun isBitrateModeSupported(mimeType: String, bitrateMode: Int): Boolean =
        codecInfos.asSequence()
            .filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            .any { info ->
                try {
                    info.getCapabilitiesForType(mimeType)
                        .encoderCapabilities
                        ?.isBitrateModeSupported(bitrateMode) ?: false
                } catch (_: Exception) {
                    false
                }
            }

    /**
     * MediaTek encoders mis-report CBR support and reject realtime priority on
     * some ROMs, so every bitrate-mode / priority decision goes through this.
     */
    fun isMediaTekDeviceOrEncoder(mimeType: String): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.lowercase()
        } else {
            ""
        }

        if (hardware.contains("mediatek") || board.contains("mediatek") ||
            manufacturer.contains("mediatek") || soc.contains("mediatek") ||
            soc.contains("dimensity") ||
            hardware.matches(Regex(""".*mt\d{4}.*""")) ||
            board.matches(Regex(""".*mt\d{4}.*"""))
        ) {
            return true
        }

        for (info in codecInfos) {
            if (!info.isEncoder) continue
            if (!info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) continue
            val name = info.name.lowercase()
            if (name.contains("mtk") || name.contains("mediatek")) return true
        }
        return false
    }

    private fun isSoftwareEncoder(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isSoftwareOnly
        } else {
            val name = info.name.lowercase()
            name.startsWith("c2.android") || name.startsWith("omx.google")
        }
}
