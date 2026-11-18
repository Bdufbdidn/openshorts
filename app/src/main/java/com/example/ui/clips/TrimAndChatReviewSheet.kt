package com.example.ui.clips

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.TrimChatMessage
import com.example.ui.components.VideoFilterStackPanel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.ViralAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimAndChatReviewSheet(
    title: String,
    sourceLabel: String,
    playableVideoUri: Uri?,
    thumbnailUrl: String?,
    totalDurationSec: Double,
    initialStartSec: Double,
    initialEndSec: Double,
    transcriptOrContext: String,
    isLiveSource: Boolean,
    chatMessages: List<TrimChatMessage>,
    isChatBusy: Boolean,
    canSaveToClip: Boolean,
    initialFilterStack: List<String> = emptyList(),
    initialFilterContrast: Float = 0f,
    initialFilterSaturation: Float = 0f,
    initialFilterBrightness: Float = 0f,
    onFiltersChanged: ((List<String>, Float, Float, Float) -> Unit)? = null,
    onSendChatReview: (message: String, currentStartSec: Double, currentEndSec: Double, durationSec: Double) -> Unit,
    onSaveTrim: (startSec: Double, endSec: Double) -> Unit,
    onBurnTrimmedMp4: ((startMs: Long, endMs: Long, filterStack: List<String>, contrast: Float, saturation: Float, brightness: Float) -> Unit)? = null,
    onAddToExportBatch: ((startMs: Long, endMs: Long, filterStack: List<String>, contrast: Float, saturation: Float, brightness: Float) -> Unit)? = null,
    onOpenYouTubeSearch: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeTotalMs = (totalDurationSec.coerceAtLeast(15.0) * 1000.0).toLong()

    var trimStartMs by remember(initialStartSec, title) {
        mutableLongStateOf((initialStartSec * 1000.0).toLong().coerceIn(0L, (safeTotalMs - 1000L).coerceAtLeast(0L)))
    }
    var trimEndMs by remember(initialEndSec, title) {
        mutableLongStateOf((initialEndSec * 1000.0).toLong().coerceIn((trimStartMs + 1000L).coerceAtMost(safeTotalMs), safeTotalMs))
    }
    var previewPositionMs by remember(title) { mutableLongStateOf(trimStartMs) }
    var chatInput by remember { mutableStateOf("") }

    var activeFilterStack by remember(title, initialFilterStack) { mutableStateOf(initialFilterStack) }
    var filterContrast by remember(title, initialFilterContrast) { mutableFloatStateOf(initialFilterContrast) }
    var filterSaturation by remember(title, initialFilterSaturation) { mutableFloatStateOf(initialFilterSaturation) }
    var filterBrightness by remember(title, initialFilterBrightness) { mutableFloatStateOf(initialFilterBrightness) }
    val filterEffectsKey = remember(activeFilterStack, filterContrast, filterSaturation, filterBrightness) {
        Media3VideoFilterFactory.cacheKey(activeFilterStack, filterContrast, filterSaturation, filterBrightness)
    }
    val activeVideoEffects = remember(filterEffectsKey) {
        Media3VideoFilterFactory.buildVideoEffects(activeFilterStack, filterContrast, filterSaturation, filterBrightness)
    }

    val latestAiProposal = remember(chatMessages) {
        chatMessages.lastOrNull { !it.fromUser && it.suggestedStartSec != null && it.suggestedEndSec != null }
    }
    LaunchedEffect(latestAiProposal?.id) {
        val start = latestAiProposal?.suggestedStartSec
        val end = latestAiProposal?.suggestedEndSec
        if (start != null && end != null && end > start) {
            val newStart = (start * 1000.0).toLong().coerceIn(0L, (safeTotalMs - 1000L).coerceAtLeast(0L))
            val newEnd = (end * 1000.0).toLong().coerceIn((newStart + 1000L).coerceAtMost(safeTotalMs), safeTotalMs)
            trimStartMs = newStart
            trimEndMs = newEnd
            previewPositionMs = newStart
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = stringResource(R.string.cd_trim_clip),
                        tint = ViralAmber
                    )
                    Column {
                        Text("Visual Trim & Chat Review", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = title.ifBlank { sourceLabel },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onOpenYouTubeSearch != null) {
                    TextButton(
                        onClick = onOpenYouTubeSearch,
                        modifier = Modifier.testTag("trim_sheet_youtube_search_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("YouTube")
                    }
                }
            }

            if (playableVideoUri != null) {
                VideoPlayer(
                    uri = playableVideoUri,
                    autoplay = false,
                    repeat = true,
                    trimEnabled = true,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    isLiveStream = isLiveSource,
                    videoEffects = activeVideoEffects,
                    videoEffectsKey = filterEffectsKey,
                    onTrimRangeChange = { s, e ->
                        trimStartMs = s
                        trimEndMs = e
                        previewPositionMs = s
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                VideoFilterStackPanel(
                    filterStack = activeFilterStack,
                    onFilterStackChange = { stack ->
                        activeFilterStack = stack
                        onFiltersChanged?.invoke(stack, filterContrast, filterSaturation, filterBrightness)
                    },
                    contrastAdjust = filterContrast,
                    saturationAdjust = filterSaturation,
                    brightnessAdjust = filterBrightness,
                    onToneAdjustChange = { c, s, b ->
                        filterContrast = c
                        filterSaturation = s
                        filterBrightness = b
                        onFiltersChanged?.invoke(activeFilterStack, c, s, b)
                    },
                    compact = true,
                    testTagPrefix = "trim_sheet_filter"
                )
            } else {
                TrimThumbnailTimelineFallback(
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    isLiveSource = isLiveSource,
                    previewPositionMs = previewPositionMs,
                    safeTotalMs = safeTotalMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    onPreviewPositionChange = { previewPositionMs = it },
                    onTrimChange = { s, e ->
                        trimStartMs = s
                        trimEndMs = e
                    }
                )
            }

            TrimTimingNudgeCard(
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                safeTotalMs = safeTotalMs,
                onTrimStartChange = { trimStartMs = it },
                onTrimEndChange = { trimEndMs = it }
            )

            TrimChatReviewSection(
                chatMessages = chatMessages,
                transcriptOrContext = transcriptOrContext,
                chatInput = chatInput,
                onChatInputChange = { chatInput = it },
                isChatBusy = isChatBusy,
                safeTotalMs = safeTotalMs,
                onSendPrompt = { msg ->
                    onSendChatReview(msg, trimStartMs / 1000.0, trimEndMs / 1000.0, safeTotalMs / 1000.0)
                },
                onSnapHandles = { ns, ne ->
                    trimStartMs = ns
                    trimEndMs = ne
                    previewPositionMs = ns
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBurnTrimmedMp4 != null && playableVideoUri != null) {
                    OutlinedButton(
                        onClick = {
                            onBurnTrimmedMp4(
                                trimStartMs,
                                trimEndMs,
                                activeFilterStack,
                                filterContrast,
                                filterSaturation,
                                filterBrightness
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("trim_sheet_burn_mp4_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Burn Trimmed MP4")
                    }
                }
                Button(
                    onClick = { onSaveTrim(trimStartMs / 1000.0, trimEndMs / 1000.0) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_trim_range_button")
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (canSaveToClip) "Save Clip Trim" else "Apply Trim Range")
                }
            }
            if (onAddToExportBatch != null && playableVideoUri != null) {
                OutlinedButton(
                    onClick = {
                        onAddToExportBatch(
                            trimStartMs,
                            trimEndMs,
                            activeFilterStack,
                            filterContrast,
                            filterSaturation,
                            filterBrightness
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("trim_sheet_add_to_batch_button")
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Trimmed Clip to Export Batch")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
