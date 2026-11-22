package compress.joshattic.us.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compress.joshattic.us.R
import compress.joshattic.us.model.CompressorUiState
import compress.joshattic.us.utils.DeviceProfile
import compress.joshattic.us.utils.EncoderTuning
import compress.joshattic.us.utils.PerformanceMode
import compress.joshattic.us.utils.PerformanceTuning
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    state: CompressorUiState,
    onBack: () -> Unit,
    onSelectMode: (PerformanceMode) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val profile = state.deviceProfile ?: remember { DeviceProfile() }
    val tuning = remember(profile, state.performanceMode) {
        PerformanceTuning.resolve(profile, state.performanceMode, 30)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.performance_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Detected hardware
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.performance_device),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = profile.chipset,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    InfoLine(stringResource(R.string.performance_soc), profile.chipset)
                    InfoLine(
                        stringResource(R.string.performance_gpu),
                        profile.gpuRenderer.ifBlank { "—" }
                    )
                    InfoLine(
                        stringResource(R.string.performance_cores),
                        if (profile.cpuCores > 0) {
                            buildString {
                                append(profile.cpuCores)
                                if (profile.performanceCores > 0) {
                                    append(" (")
                                    append(profile.performanceCores)
                                    append(" performance)")
                                }
                                if (profile.maxCpuFreqMhz > 0) {
                                    append(" · ")
                                    append(String.format(Locale.US, "%.2f GHz", profile.maxCpuFreqMhz / 1000f))
                                }
                            }
                        } else {
                            "—"
                        }
                    )
                    InfoLine(
                        stringResource(R.string.performance_ram),
                        if (profile.totalRamGb > 0f) {
                            String.format(Locale.US, "%.1f GB", profile.totalRamGb)
                        } else {
                            "—"
                        }
                    )
                    InfoLine(stringResource(R.string.performance_tier), profile.tier.label)
                    InfoLine(
                        stringResource(R.string.performance_os),
                        profile.osName.ifBlank { "Android" }
                    )
                    if (profile.isRedmiK80Ultra) {
                        InfoLine("Device", "Redmi K80 Ultra / 至尊版")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mode selector
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.performance_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    PerformanceMode.entries.forEach { mode ->
                        val label = when (mode) {
                            PerformanceMode.AUTO -> stringResource(R.string.performance_mode_auto)
                            PerformanceMode.MAXIMUM -> stringResource(R.string.performance_mode_max)
                            PerformanceMode.BALANCED -> stringResource(R.string.performance_mode_balanced)
                        }
                        val description = when (mode) {
                            PerformanceMode.AUTO -> stringResource(R.string.performance_mode_auto_desc)
                            PerformanceMode.MAXIMUM -> stringResource(R.string.performance_mode_max_desc)
                            PerformanceMode.BALANCED -> stringResource(R.string.performance_mode_balanced_desc)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.performanceMode == mode,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectMode(mode)
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.performanceMode == mode,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Active tuning
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.performance_tuning),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))

                    val active = tuningItems(tuning, profile)
                    if (active.isEmpty()) {
                        Text(
                            text = stringResource(R.string.performance_tuning_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        active.forEach { label ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.performance_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.performance_batch_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun tuningItems(tuning: EncoderTuning, profile: DeviceProfile): List<String> {
    val items = mutableListOf<String>()
    if (tuning.realtimePriority) items.add(stringResource(R.string.performance_tuning_realtime))
    if (tuning.operatingRateHint > 0) items.add(stringResource(R.string.performance_tuning_operating_rate))
    if (tuning.holdWakeLock) items.add(stringResource(R.string.performance_tuning_wakelock))
    if (tuning.useForegroundService) items.add(stringResource(R.string.performance_tuning_fgs))
    if (tuning.useCodecDbLite) items.add(stringResource(R.string.performance_tuning_codec_db))
    if (tuning.boostIoThreadPriority) items.add(stringResource(R.string.performance_tuning_io))
    if (tuning.prefetchNextItem) items.add(stringResource(R.string.performance_tuning_prefetch))
    if (tuning.preferCbr) items.add(stringResource(R.string.performance_tuning_cbr))
    if (profile.isMediaTek) items.add(stringResource(R.string.performance_tuning_mtk_vbr))
    if (profile.isHyperOs) items.add(stringResource(R.string.performance_tuning_hyperos))
    return items
}
