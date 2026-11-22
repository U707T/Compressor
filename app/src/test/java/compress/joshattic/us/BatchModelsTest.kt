package compress.joshattic.us

import compress.joshattic.us.model.BatchItem
import compress.joshattic.us.model.BatchItemStatus
import compress.joshattic.us.model.BatchLimits
import compress.joshattic.us.model.BatchSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class BatchModelsTest {

    private val uri = mock<android.net.Uri>()

    @Test
    fun statusFlagsCoverTheWholeLifecycle() {
        assertFalse(BatchItemStatus.PENDING.isFinished)
        assertTrue(BatchItemStatus.PREPARING.isRunning)
        assertTrue(BatchItemStatus.COMPRESSING.isRunning)
        assertTrue(BatchItemStatus.SAVING.isRunning)
        assertTrue(BatchItemStatus.DONE.isFinished)
        assertTrue(BatchItemStatus.FAILED.isFinished)
        assertTrue(BatchItemStatus.CANCELLED.isFinished)
        assertFalse(BatchItemStatus.DONE.isRunning)
    }

    @Test
    fun savedBytesNeverGoNegative() {
        val item = BatchItem(uri = uri, displayName = "clip.mp4", originalSize = 100L, resultSize = 40L)
        assertEquals(60L, item.savedBytes)

        val grown = item.copy(resultSize = 140L)
        assertEquals(0L, grown.savedBytes)

        val unknown = item.copy(originalSize = 0L)
        assertEquals(0L, unknown.savedBytes)
    }

    @Test
    fun summaryTotalsOnlyCountWhatWasProduced() {
        val summary = BatchSummary(
            total = 3,
            succeeded = 2,
            failed = 1,
            cancelled = false,
            originalBytes = 30L * 1024 * 1024,
            resultBytes = 12L * 1024 * 1024
        )

        assertEquals(18L * 1024 * 1024, summary.savedBytes)
        assertEquals("18.0 MB", summary.formattedSavedBytes)
        assertFalse(summary.allSucceeded)
    }

    @Test
    fun summaryWithoutSuccessIsNotAllSucceeded() {
        val summary = BatchSummary(
            total = 2,
            succeeded = 0,
            failed = 2,
            cancelled = true,
            originalBytes = 0L,
            resultBytes = 0L
        )

        assertFalse(summary.allSucceeded)
        assertTrue(summary.cancelled)
    }

    @Test
    fun batchLimitsStayInSyncWithThePickerCeiling() {
        assertTrue(BatchLimits.MAX_ITEMS in 1..100)
        assertTrue(BatchLimits.WAKE_LOCK_TIMEOUT_MS >= BatchLimits.ITEM_TIMEOUT_MS)
    }
}
