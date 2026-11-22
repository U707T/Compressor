package compress.joshattic.us.utils

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import java.io.File
import java.util.Locale

data class HardwareInfo(
    val chipset: String,
    val totalRam: String,
    val gpu: String,
    val cpuArch: String
)

data class DeviceWorkarounds(
    val isMediaTekVbrPatchActive: Boolean,
    val isPixel10HdrPatchActive: Boolean,
    val isHuaweiMuxerPatchActive: Boolean
) {
    val hasAnyActivePatch: Boolean
        get() = isMediaTekVbrPatchActive || isPixel10HdrPatchActive || isHuaweiMuxerPatchActive
}

/** SoC vendor, resolved from Build fields and known model names. */
enum class SocVendor(val label: String) {
    MEDIATEK("MediaTek"),
    QUALCOMM("Qualcomm"),
    SAMSUNG("Samsung"),
    GOOGLE("Google"),
    UNISOC("Unisoc"),
    HISILICON("HiSilicon"),
    OTHER("Unknown")
}

/**
 * Coarse performance class. Deliberately conservative: it only decides whether
 * encoder tuning hints are offered by default, never the output quality.
 */
enum class PerformanceTier(val label: String) {
    FLAGSHIP("Flagship"),
    HIGH("High"),
    MID("Mid-range"),
    LOW("Entry")
}

/**
 * Everything the encoder tuner needs to know about the current device.
 * Pure data so it can be unit tested without Android runtime.
 */
data class DeviceProfile(
    val chipset: String = "Unknown",
    val socModel: String = "",
    val vendor: SocVendor = SocVendor.OTHER,
    val gpuRenderer: String = "",
    val cpuCores: Int = 0,
    val performanceCores: Int = 0,
    val maxCpuFreqMhz: Int = 0,
    val totalRamGb: Float = 0f,
    val isXiaomiDevice: Boolean = false,
    val osName: String = "",
    val isHyperOs: Boolean = false,
    val tier: PerformanceTier = PerformanceTier.MID,
    val isRedmiK80Ultra: Boolean = false
) {
    val isMediaTek: Boolean get() = vendor == SocVendor.MEDIATEK

    val isFlagship: Boolean get() = tier == PerformanceTier.FLAGSHIP

    /** Short "MediaTek Dimensity 9400+ · 12 cores · 15.3 GB" style line. */
    val hardwareSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (chipset.isNotBlank()) parts.add(chipset)
            if (cpuCores > 0) parts.add("$cpuCores cores")
            if (totalRamGb > 0f) parts.add(String.format(Locale.US, "%.1f GB RAM", totalRamGb))
            return parts.joinToString(" · ")
        }
}

object HardwareUtils {

    private const val TAG = "HardwareUtils"

    fun getHardwareInfo(context: Context): HardwareInfo {
        val chipset = getChipsetInfo()
        val ram = getTotalRam(context)
        val gpu = getGpuRenderer()
        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: ""

        return HardwareInfo(
            chipset = chipset,
            totalRam = ram,
            gpu = gpu,
            cpuArch = cpuArch
        )
    }

    fun getDeviceWorkarounds(): DeviceWorkarounds {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val board = Build.BOARD.lowercase(Locale.US)
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase(Locale.US) else ""

        val isMediaTek = isMediaTekHardware(manufacturer, hardware, board, soc)

        val isPixel10 = manufacturer.contains("google") && model.contains("pixel 10")
        val isHuawei = manufacturer.contains("huawei")

        return DeviceWorkarounds(
            isMediaTekVbrPatchActive = isMediaTek,
            isPixel10HdrPatchActive = isPixel10,
            isHuaweiMuxerPatchActive = isHuawei
        )
    }

    /**
     * Full device profile used by the performance tuner. Reads CPU frequency
     * tables and system properties defensively: every lookup is allowed to fail
     * and falls back to Build fields.
     */
    fun detectDeviceProfile(context: Context): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val board = Build.BOARD.orEmpty()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else ""

        val vendor = resolveVendor(
            socModel = socModel,
            socManufacturer = socManufacturer,
            hardware = hardware,
            board = board,
            manufacturer = manufacturer
        )
        val chipset = getChipsetInfo()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val maxFreq = readMaxCpuFrequencyMhz()
        val performanceCores = countPerformanceCores(maxFreq)
        val ramGb = readTotalRamGb(context)

        val miuiVersion = readSystemProperty("ro.miui.ui.version.name")
        val hyperOsVersion = readSystemProperty("ro.mi.os.version.name")
        val isXiaomi = manufacturer.contains("xiaomi", true) ||
            manufacturer.contains("redmi", true) ||
            manufacturer.contains("poco", true) ||
            !miuiVersion.isNullOrBlank() ||
            !hyperOsVersion.isNullOrBlank()
        val isHyperOs = !hyperOsVersion.isNullOrBlank() ||
            (isXiaomi && (Build.DISPLAY.contains("V816") || Build.VERSION.INCREMENTAL.contains("V816"))) ||
            (isXiaomi && Build.VERSION.INCREMENTAL.contains(Regex("OS\\d\\.\\d")))

        val osName = when {
            isHyperOs && !hyperOsVersion.isNullOrBlank() -> "HyperOS $hyperOsVersion"
            isHyperOs -> "HyperOS"
            isXiaomi && !miuiVersion.isNullOrBlank() -> "MIUI $miuiVersion"
            else -> "Android ${Build.VERSION.RELEASE}"
        }

        val isK80Ultra = isRedmiK80Ultra(model, hardware, board, socModel, isXiaomi)

        return DeviceProfile(
            chipset = chipset,
            socModel = socModel.ifBlank { hardware },
            vendor = vendor,
            gpuRenderer = getGpuRenderer(),
            cpuCores = cores,
            performanceCores = performanceCores,
            maxCpuFreqMhz = maxFreq,
            totalRamGb = ramGb,
            isXiaomiDevice = isXiaomi,
            osName = osName,
            isHyperOs = isHyperOs,
            tier = resolveTier(vendor, socModel, hardware, board, maxFreq, ramGb, cores),
            isRedmiK80Ultra = isK80Ultra
        )
    }

    internal fun isMediaTekHardware(
        manufacturer: String,
        hardware: String,
        board: String,
        soc: String
    ): Boolean {
        val hw = hardware.lowercase(Locale.US)
        val bd = board.lowercase(Locale.US)
        val socLower = soc.lowercase(Locale.US)
        return hw.contains("mediatek") || bd.contains("mediatek") ||
            manufacturer.lowercase(Locale.US).contains("mediatek") ||
            socLower.contains("mediatek") || socLower.contains("dimensity") ||
            socLower.contains("helio") ||
            hw.matches(Regex(""".*mt\d{4}.*""")) || bd.matches(Regex(""".*mt\d{4}.*""")) ||
            socLower.matches(Regex(""".*mt\d{4}.*"""))
    }

    internal fun resolveVendor(
        socModel: String,
        socManufacturer: String,
        hardware: String,
        board: String,
        manufacturer: String
    ): SocVendor {
        val haystack = listOf(socModel, socManufacturer, hardware, board, manufacturer)
            .joinToString(" ")
            .lowercase(Locale.US)

        return when {
            isMediaTekHardware(manufacturer, hardware, board, socModel) -> SocVendor.MEDIATEK
            haystack.contains("qualcomm") || haystack.contains("qcom") ||
                Regex(""".*\bsm\d{4}.*""").matches(haystack) ||
                haystack.contains("snapdragon") -> SocVendor.QUALCOMM
            haystack.contains("exynos") || haystack.contains("samsung") -> SocVendor.SAMSUNG
            haystack.contains("tensor") || haystack.contains("google") -> SocVendor.GOOGLE
            haystack.contains("unisoc") || haystack.contains("spreadtrum") ||
                Regex(""".*\bums\d{3}.*""").matches(haystack) -> SocVendor.UNISOC
            haystack.contains("kirin") || Regex(""".*\bhi\d{4}.*""").matches(haystack) -> SocVendor.HISILICON
            else -> SocVendor.OTHER
        }
    }

    /**
     * Heuristic tier. Frequency and RAM dominate; known flagship SoCs are
     * whitelisted because many OEMs clamp the readable max frequency.
     */
    internal fun resolveTier(
        vendor: SocVendor,
        socModel: String,
        hardware: String,
        board: String,
        maxFreqMhz: Int,
        ramGb: Float,
        cores: Int
    ): PerformanceTier {
        val signature = "$socModel $hardware $board".lowercase(Locale.US)
        val knownFlagship = listOf(
            "9400", "9300", "9200", "9000",          // MediaTek Dimensity
            "mt6991", "mt6989", "mt6985", "mt6983",  // MediaTek model numbers
            "8 elite", "8 gen 3", "8 gen 2", "sm8750", "sm8650", "sm8550",
            "exynos 2400", "exynos 2500", "tensor g3", "tensor g4", "tensor g5"
        ).any { signature.contains(it) }
        val knownHighEnd = listOf(
            "8s gen", "7+ gen", "sm8635", "sm7675", "dimensity 8", "mt68",
            "exynos 1480", "exynos 1580", "tensor g2"
        ).any { signature.contains(it) }

        return when {
            knownFlagship -> PerformanceTier.FLAGSHIP
            maxFreqMhz >= 3000 && ramGb >= 8f -> PerformanceTier.FLAGSHIP
            maxFreqMhz >= 2400 || (knownHighEnd && ramGb >= 6f) -> PerformanceTier.HIGH
            maxFreqMhz >= 1800 || ramGb >= 6f || cores >= 8 -> PerformanceTier.MID
            else -> PerformanceTier.LOW
        }
    }

    /**
     * Redmi K80 Ultra (a.k.a. K80 至尊版) is the reference MediaTek Dimensity
     * 9400+ device. Match by marketing name, Chinese model code and codename so
     * detection also works when the ROM reports a numeric model only.
     */
    internal fun isRedmiK80Ultra(
        model: String,
        hardware: String,
        board: String,
        socModel: String,
        isXiaomi: Boolean
    ): Boolean {
        val haystack = "$model $hardware $board".lowercase(Locale.US)
        if (haystack.contains("k80") || haystack.contains("dali")) return true
        // Chinese retail units report "2510DRK44C" / "25060RK16C" style codes.
        if (Regex("""25(06|10)rk\d{2}[a-z]{0,2}""").containsMatchIn(haystack)) return true
        // Xiaomi + Dimensity 9400/9400+ is effectively K80 Ultra / K80 Ultra-class.
        val soc = socModel.lowercase(Locale.US)
        return isXiaomi && (soc.contains("9400") || soc.contains("mt6991"))
    }

    private fun readMaxCpuFrequencyMhz(): Int {
        var maxKhz = 0
        for (cpu in 0 until 16) {
            for (name in arrayOf("cpuinfo_max_freq", "scaling_max_freq")) {
                val file = File("/sys/devices/system/cpu/cpu$cpu/cpufreq/$name")
                val value = try {
                    if (file.canRead()) file.readText().trim().toIntOrNull() ?: 0 else 0
                } catch (_: Exception) {
                    0
                }
                if (value > maxKhz) maxKhz = value
            }
        }
        return maxKhz / 1000
    }

    private fun countPerformanceCores(maxFreqMhz: Int): Int {
        if (maxFreqMhz <= 0) return 0
        val threshold = (maxFreqMhz * 0.75f).toInt()
        var count = 0
        for (cpu in 0 until 16) {
            val file = File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
            val khz = try {
                if (file.canRead()) file.readText().trim().toIntOrNull() ?: 0 else 0
            } catch (_: Exception) {
                0
            }
            if (khz / 1000 >= threshold) count++
        }
        return count
    }

    private fun readTotalRamGb(context: Context): Float {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalBytes = memInfo.totalMem
            if (totalBytes <= 0L) 0f else (totalBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
        } catch (_: Exception) {
            0f
        }
    }

    /** Reads a system property without hard-failing when the hidden API is blocked. */
    internal fun readSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getChipsetInfo(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socManufacturer = Build.SOC_MANUFACTURER.trim()
            val socModel = Build.SOC_MODEL.trim()
            when {
                socManufacturer.isNotBlank() && socModel.isNotBlank() -> {
                    if (socModel.lowercase(Locale.US).contains(socManufacturer.lowercase(Locale.US))) {
                        capitalizeWords(socModel)
                    } else {
                        capitalizeWords("$socManufacturer $socModel")
                    }
                }
                socModel.isNotBlank() -> capitalizeWords(socModel)
                socManufacturer.isNotBlank() -> capitalizeWords(socManufacturer)
                else -> getLegacyChipset()
            }
        } else {
            getLegacyChipset()
        }
    }

    private fun getLegacyChipset(): String {
        val hardware = Build.HARDWARE.trim()
        val board = Build.BOARD.trim()
        return when {
            hardware.isNotBlank() && !hardware.equals("unknown", ignoreCase = true) -> capitalizeWords(hardware)
            board.isNotBlank() && !board.equals("unknown", ignoreCase = true) -> capitalizeWords(board)
            else -> "Unknown"
        }
    }

    private fun getTotalRam(context: Context): String {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalBytes = memInfo.totalMem
            if (totalBytes <= 0L) return "Unknown"

            val gb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val roundedGb = Math.round(gb * 10.0) / 10.0
            if (roundedGb % 1.0 == 0.0) {
                "${roundedGb.toInt()} GB"
            } else {
                String.format(Locale.US, "%.1f GB", roundedGb)
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getGpuRenderer(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return ""
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return ""

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val selectedConfig = configs[0] ?: return ""

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val eglContext = EGL14.eglCreateContext(display, selectedConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return ""

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, selectedConfig, pbufferAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, eglContext)
                return ""
            }

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            renderer.trim()
        } catch (e: Exception) {
            ""
        }
    }

    internal fun capitalizeWords(str: String): String {
        return str.split(" ").joinToString(" ") { word ->
            val lower = word.lowercase(Locale.US)
            if (lower in listOf("sm", "mt", "qcom") || lower.startsWith("sm") || lower.startsWith("mt")) word.uppercase(Locale.US)
            else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
}
