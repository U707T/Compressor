package compress.joshattic.us.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import compress.joshattic.us.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a finished export to one of the three supported destinations.
 *
 * Every function returns `null` on success or a human readable reason on
 * failure, so callers can decide between retrying, falling back or surfacing an
 * error without catching exceptions.
 */
object MediaSaver {

    private const val COPY_BUFFER_BYTES = 1024 * 1024

    /** User-picked destination via `CreateDocument`. */
    suspend fun copyToUri(context: Context, targetUri: Uri, file: File): String? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out, COPY_BUFFER_BYTES) }
                } ?: return@withContext "No output stream"
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }

    /** User-picked SAF folder. */
    suspend fun copyToTree(
        context: Context,
        treeUri: Uri,
        file: File,
        targetName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            if (tree == null || !tree.canWrite()) return@withContext "Folder not writable"
            tree.findFile(targetName)?.takeIf { it.isFile }?.delete()
            val target = tree.createFile("video/mp4", targetName)
                ?: return@withContext context.getString(R.string.error_gallery_entry)
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out, COPY_BUFFER_BYTES) }
            } ?: return@withContext "No output stream"
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /** MediaStore (Movies/Compressor) on Android 10+, app-specific folder below that. */
    suspend fun copyToGallery(
        context: Context,
        file: File,
        targetName: String
    ): String? = withContext(Dispatchers.IO) {
        // Pre-Q MediaStore inserts need WRITE_EXTERNAL_STORAGE, which this app
        // deliberately does not request. Use the app-specific Movies folder instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext copyToLegacyMovies(context, file, targetName)
        }

        var itemUri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (!containsKey(MediaStore.Video.Media.DATE_MODIFIED)) {
                    put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Compressor"
                )
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            itemUri = context.contentResolver.insert(collection, values)
                ?: return@withContext context.getString(R.string.error_gallery_entry)

            context.contentResolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out, COPY_BUFFER_BYTES) }
            } ?: return@withContext "No output stream"

            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(itemUri, done, null, null)
            null
        } catch (e: Exception) {
            // Never leave a half-written pending row behind.
            if (itemUri != null) {
                runCatching { context.contentResolver.delete(itemUri, null, null) }
            }
            e.message ?: e.javaClass.simpleName
        }
    }

    private fun copyToLegacyMovies(context: Context, file: File, targetName: String): String? {
        return try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Compressor")
            if (!dir.exists() && !dir.mkdirs()) return "Cannot create ${dir.absolutePath}"
            val target = File(dir, targetName)
            file.inputStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out, COPY_BUFFER_BYTES) }
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }
}
