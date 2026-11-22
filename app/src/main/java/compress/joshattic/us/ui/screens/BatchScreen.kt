package compress.joshattic.us.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compress.joshattic.us.R
import compress.joshattic.us.model.BatchItem
import compress.joshattic.us.model.BatchItemStatus
import compress.joshattic.us.model.BatchLimits
import compress.joshattic.us.model.CompressorUiState

/**
 * Queue, progress and summary for batch compression: one screen, three states,
 * driven entirely by [CompressorUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    state: CompressorUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddMore: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val summary = state.batchSummary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (summary != null) R.string.batch_result_title else R.string.batch_title
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.batch_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (summary != null) {
                BatchSummaryContent(state = state, onDone = onDismiss)
            } else {
                BatchQueueContent(
                    state = state,
                    onStart = onStart,
                    onCancel = onCancel,
                    onRemoveItem = onRemoveItem,
                    onAddMore = onAddMore
                )
            }
        }
    }
}

@Composable
private fun BatchQueueContent(
    state: CompressorUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddMore: (() -> Unit)?
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.batch_subtitle, state.batchItems.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.batch_max_items, BatchLimits.MAX_ITEMS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.batchActive) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.batch_overall_progress),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.batchOverallProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(Modifier.height(8.dp))
                        val current = state.batchCurrentItemIndex
                        Text(
                            text = if (current >= 0) {
                                stringResource(
                                    R.string.batch_running,
                                    (current + 1).coerceAtMost(state.batchItems.size),
                                    state.batchItems.size
                                )
                            } else {
                                stringResource(R.string.batch_status_preparing)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.batch_keep_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.batchItems) { index, item ->
                BatchRow(
                    item = item,
                    isCurrent = state.batchActive && index == state.batchCurrentItemIndex,
                    progress = if (state.batchActive && index == state.batchCurrentItemIndex) state.progress else 0f,
                    removable = !state.batchActive,
                    onRemove = { onRemoveItem(index) }
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (state.batchActive) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.batch_cancel), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    enabled = state.batchItems.isNotEmpty()
                ) {
                    Text(
                        text = if (state.batchFinishedCount > 0) {
                            stringResource(R.string.batch_resume)
                        } else {
                            stringResource(R.string.batch_start, state.batchItems.size)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onAddMore != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddMore()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.batch_add_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(
    item: BatchItem,
    isCurrent: Boolean,
    progress: Float,
    removable: Boolean,
    onRemove: () -> Unit
) {
    val container = if (isCurrent) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                when {
                    item.status.isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    item.status == BatchItemStatus.DONE -> Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    item.status == BatchItemStatus.FAILED -> Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    item.status == BatchItemStatus.CANCELLED -> Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = batchItemSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == BatchItemStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrent) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.batch_remove_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun batchItemSubtitle(item: BatchItem): String {
    val status = when (item.status) {
        BatchItemStatus.PENDING -> stringResource(R.string.batch_status_pending)
        BatchItemStatus.PREPARING -> stringResource(R.string.batch_status_preparing)
        BatchItemStatus.COMPRESSING -> stringResource(R.string.batch_status_compressing)
        BatchItemStatus.SAVING -> stringResource(R.string.batch_status_saving)
        BatchItemStatus.DONE -> stringResource(R.string.batch_status_done)
        BatchItemStatus.FAILED -> stringResource(R.string.batch_status_failed)
        BatchItemStatus.CANCELLED -> stringResource(R.string.batch_status_cancelled)
    }
    return when {
        item.status == BatchItemStatus.FAILED && !item.error.isNullOrBlank() ->
            "$status · ${item.error}"
        item.status == BatchItemStatus.DONE ->
            "$status · " + stringResource(
                R.string.batch_item_saved,
                item.formattedOriginalSize,
                item.formattedResultSize
            )
        item.originalSize > 0L -> "$status · ${item.formattedOriginalSize}"
        else -> status
    }
}

@Composable
private fun BatchSummaryContent(
    state: CompressorUiState,
    onDone: () -> Unit
) {
    val summary = state.batchSummary ?: return
    val haptics = LocalHapticFeedback.current
    val failedItems = state.batchItems.filter { it.status == BatchItemStatus.FAILED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (summary.allSucceeded) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (summary.succeeded > 0) {
                        stringResource(
                            R.string.batch_result_saved,
                            summary.formattedSavedBytes,
                            summary.succeeded
                        )
                    } else {
                        stringResource(R.string.batch_result_none)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.cancelled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.batch_result_cancelled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (summary.failed > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.batch_result_failed, summary.failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.customOutputTreeUri?.let {
                        stringResource(R.string.batch_saved_to_folder, state.customOutputFolderName ?: it)
                    } ?: stringResource(R.string.batch_saved_to_gallery),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (failedItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.batch_result_failed, failedItems.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    failedItems.forEach { item ->
                        Text(
                            text = "${item.displayName}: ${item.error ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text(stringResource(R.string.batch_done), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }
}
