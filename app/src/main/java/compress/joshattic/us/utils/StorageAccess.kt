package compress.joshattic.us.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Direct filesystem access ("All files access" / legacy read permission).
 *
 * The in-app file browser uses this instead of the Storage Access Framework so
 * that HyperOS 4 never shows its "secure access" dialog. The permission is
 * optional: the photo picker and the third-party chooser still work without it.
 */
object StorageAccess {

    /** Extensions the browser lists. Kept in sync with MediaExtractor's reach. */
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "mov", "avi", "webm", "3gp", "3g2", "ts", "mts", "m2ts",
        "flv", "wmv", "mpg", "mpeg", "ogv", "ogg", "rmvb", "rm", "asf", "vob", "f4v", "mxf", "divx"
    )

    fun isVideoFile(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.US) in VIDEO_EXTENSIONS

    /** True when the app can read arbitrary paths on shared storage. */
    fun hasFullAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Settings intent for "All files access" (API 30+). Returns null on older
     * versions, where a runtime READ_EXTERNAL_STORAGE request is enough.
     */
    fun requestFullAccessIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        val resolved = perApp.resolveActivity(context.packageManager)
        return if (resolved != null) {
            perApp
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    /** Primary shared storage, e.g. /storage/emulated/0. */
    fun primaryRoot(): File = Environment.getExternalStorageDirectory()

    /** Primary storage first, then removable volumes (SD cards / USB OTG). */
    fun storageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        val primary = primaryRoot()
        if (primary.exists()) roots.add(primary)
        // /storage/<volume>/Android/data/<pkg>/files -> /storage/<volume>
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            var candidate: File? = dir
            repeat(4) { candidate = candidate?.parentFile }
            candidate?.let { volume ->
                if (volume.exists() && volume.canRead() && volume != primary) roots.add(volume)
            }
        }
        return roots.toList()
    }

    /**
     * Lists one directory: sub-folders first, then videos, both alphabetical.
     * Hidden entries are skipped unless [showHidden] is set.
     */
    fun listDirectory(dir: File, showHidden: Boolean = false): List<File> {
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val folders = children.filter {
            it.isDirectory && (showHidden || !it.name.startsWith(".")) && it.canRead()
        }
        val videos = children.filter {
            isVideoFile(it) && (showHidden || !it.name.startsWith("."))
        }
        return folders.sortedBy { it.name.lowercase(Locale.US) } +
            videos.sortedBy { it.name.lowercase(Locale.US) }
    }

    fun formatSize(bytes: Long): String = formatFileSize(bytes)
}
