package compress.joshattic.us.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compress.joshattic.us.R
import compress.joshattic.us.utils.scaleOnPress

@Composable
fun EmptyScreen(
    totalSaved: String,
    showStorageSaved: Boolean = true,
    onPick: () -> Unit,
    onPickBatch: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPick()
                },
                modifier = Modifier.size(96.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_video_desc), modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.select_video),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onPickBatch != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.scaleOnPress { onPickBatch() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.batch_compress),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (showStorageSaved && totalSaved != "0.0 MB" && totalSaved != "0 MB") {
             Text(
                text = stringResource(R.string.total_saved, totalSaved),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}
