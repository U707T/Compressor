package compress.joshattic.us.utils

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTuningTest {

    private val mediaTekFlagship = DeviceProfile(
        chipset = "Mediatek MT6991",
        socModel = "MT6991",
        vendor = SocVendor.MEDIATEK,
        gpuRenderer = "Mali-G925-Immortalis MC12",
        cpuCores = 12,
        performanceCores = 4,
        maxCpuFreqMhz = 3730,
        totalRamGb = 15.3f,
        isXiaomiDevice = true,
        osName = "HyperOS 2.0",
        isHyperOs = true,
        tier = PerformanceTier.FLAGSHIP,
        isRedmiK80Ultra = true
    )

    private val entryLevel = DeviceProfile(
        chipset = "Unisoc T760",
        vendor = SocVendor.UNISOC,
        cpuCores = 8,
        maxCpuFreqMhz = 2200,
        totalRamGb = 4f,
        tier = PerformanceTier.LOW
    )

    @Test
    fun autoModeIsAggressiveOnFlagshipHardware() {
        val tuning = PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.AUTO, 60)

        assertTrue(tuning.isAggressive)
        assertEquals(60, tuning.operatingRateHint)
        assertTrue(tuning.holdWakeLock)
        assertTrue(tuning.boostIoThreadPriority)
        assertTrue(tuning.prefetchNextItem)
    }

    @Test
    fun autoModeStaysBalancedOnEntryLevelHardware() {
        val tuning = PerformanceTuning.resolve(entryLevel, PerformanceMode.AUTO, 30)

        assertFalse(tuning.isAggressive)
        assertEquals(0, tuning.operatingRateHint)
        assertFalse(tuning.holdWakeLock)
        assertFalse(tuning.realtimePriority)
    }

    @Test
    fun mediaTekNeverRequestsRealtimePriorityOrCbr() {
        val tuning = PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.MAXIMUM, 30)

        assertTrue(tuning.isAggressive)
        assertFalse("MediaTek encoders reject realtime priority on some ROMs", tuning.realtimePriority)
        assertFalse("MediaTek VBR workaround must stay active", tuning.preferCbr)
        assertTrue(tuning.operatingRateHint > 0)
    }

    @Test
    fun qualcommFlagshipGetsRealtimePriorityAndCbr() {
        val qualcomm = mediaTekFlagship.copy(vendor = SocVendor.QUALCOMM)
        val tuning = PerformanceTuning.resolve(qualcomm, PerformanceMode.MAXIMUM, 30)

        assertTrue(tuning.realtimePriority)
        assertTrue(tuning.preferCbr)
    }

    @Test
    fun balancedModeDisablesEveryOptionalHint() {
        val tuning = PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.BALANCED, 60)

        assertFalse(tuning.hasAnyHint)
        assertFalse(tuning.isAggressive)
        assertTrue("Foreground service is a safety feature, not a tuning hint", tuning.useForegroundService)
    }

    @Test
    fun operatingRateIsClampedToSaneRange() {
        assertEquals(24, PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.MAXIMUM, 5).operatingRateHint)
        assertEquals(120, PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.MAXIMUM, 240).operatingRateHint)
    }

    @Test
    fun hdrIsToneMappedWhenTargetCodecCannotCarryHdr() {
        val tuning = PerformanceTuning.resolve(mediaTekFlagship, PerformanceMode.AUTO, 30)

        assertTrue(
            PerformanceTuning.shouldToneMap(
                tuning = tuning,
                inputIsHdr = true,
                outputMimeType = MimeTypes.VIDEO_H264,
                isPixel10 = false
            )
        )
        assertFalse(
            PerformanceTuning.shouldToneMap(
                tuning = tuning,
                inputIsHdr = true,
                outputMimeType = MimeTypes.VIDEO_H265,
                isPixel10 = false
            )
        )
        assertTrue(
            PerformanceTuning.shouldToneMap(
                tuning = tuning,
                inputIsHdr = true,
                outputMimeType = MimeTypes.VIDEO_H265,
                isPixel10 = true
            )
        )
        assertFalse(
            PerformanceTuning.shouldToneMap(
                tuning = tuning,
                inputIsHdr = false,
                outputMimeType = MimeTypes.VIDEO_H264,
                isPixel10 = true
            )
        )
    }
}
