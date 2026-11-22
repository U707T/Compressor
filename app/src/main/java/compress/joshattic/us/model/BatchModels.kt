package compress.joshattic.us.model

import android.net.Uri
import compress.joshattic.us.utils.formatFileSize

/** Lifecycle of a single video inside a batch. */
enum class BatchItemStatus {
    PENDING,
    PREPARING,
    COMPRESSING,
    SAVING,
    DONE,
    FAILED,
    CANCELLED;

    val isFinished: Boolean
        get() = this == DONE || this == FAILED || this == CANCELLED

    val isRunning: Boolean
        get() = this == PREPARING || this == COMPRESSING || this == SAVING
}

/**
 * One queued video. Everything the UI needs is precomputed here so the list can
 * be rendered without touching the ContentResolver.
 */
data class BatchItem(
    val uri: Uri,
    val displayName: String,
    val originalSize: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val resultSize: Long = 0L,
    val error: String? = null
) {
    val savedBytes: Long
        get() = if (originalSize > 0L && resultSize > 0L) {
            (originalSize - resultSize).coerceAtLeast(0L)
        } else {
            0L
        }

    val formattedOriginalSize: String get() = formatFileSize(originalSize)
    val formattedResultSize: String get() = formatFileSize(resultSize)
    val formattedSavedBytes: String get() = formatFileSize(savedBytes)
}

/** Final tally shown on the batch summary screen. */
data class BatchSummary(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val cancelled: Boolean,
    val originalBytes: Long,
    val resultBytes: Long
) {
    val savedBytes: Long get() = (originalBytes - resultBytes).coerceAtLeast(0L)
    val formattedSavedBytes: String get() = formatFileSize(savedBytes)
    val formattedOriginalBytes: String get() = formatFileSize(originalBytes)
    val formattedResultBytes: String get() = formatFileSize(resultBytes)
    val allSucceeded: Boolean get() = failed == 0 && succeeded > 0
}

/** Hard limits for a batch, kept in one place so UI and engine agree. */
object BatchLimits {
    /** Photo picker / document picker ceiling. */
    const val MAX_ITEMS = 50

    /** Safety timeout per item (6 hours) — a hung codec must not block the queue. */
    const val ITEM_TIMEOUT_MS = 6 * 60 * 60 * 1000L

    /** Wake lock ceiling, mirrors the item timeout so it can never leak for long. */
    const val WAKE_LOCK_TIMEOUT_MS = ITEM_TIMEOUT_MS * MAX_ITEMS
}
