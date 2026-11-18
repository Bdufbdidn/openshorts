package com.example.ui.clips

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Effect
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LiveShortsSkillPreset
import com.example.data.model.UiState
import com.example.data.model.YouTubeSearchResult
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoFilterStackPanel
import com.example.ui.components.VideoPlayer
import com.example.ui.components.formatSeconds
import com.example.ui.theme.ObsidianBorder

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClipSourceInputCard(
    sourceMode: String,
    onSourceModeChange: (String) -> Unit,
    pickedVideoUri: Uri?,
    onPickVideo: () -> Unit,
    sourceTrimStartMs: Long,
    sourceTrimEndMs: Long,
    onSourceTrimChange: (Long, Long) -> Unit,
    sourceFilterStack: List<String>,
    sourceFilterContrast: Float,
    sourceFilterSaturation: Float,
    sourceFilterBrightness: Float,
    sourceVideoEffects: List<Effect>,
    sourceFilterKey: String,
    onFilterStackChange: (List<String>) -> Unit,
    onToneAdjustChange: (Float, Float, Float) -> Unit,
    onOpenSourceTrimChat: () -> Unit,
    onAddSourceTrimToBatch: () -> Unit,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onOpenYoutubeSearch: (liveOnly: Boolean) -> Unit,
    selectedYoutubeVideo: YouTubeSearchResult?,
    urlTrimStartSec: Double,
    urlTrimEndSec: Double,
    onOpenYoutubeTrimChat: () -> Unit,
    liveStreamUrlInput: String,
    onLiveStreamUrlChange: (String) -> Unit,
    selectedLiveSkill: LiveShortsSkillPreset,
    onSelectLiveSkill: (LiveShortsSkillPreset) -> Unit,
    liveLookbackMinutes: Int,
    onLiveLookbackChange: (Int) -> Unit,
    liveCueNotes: String,
    onLiveCueNotesChange: (String) -> Unit,
    guidanceInput: String,
    onGuidanceInputChange: (String) -> Unit,
    maxClips: Float,
    onMaxClipsChange: (Float) -> Unit,
    generationState: UiState<String>,
    onGenerateClick: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = sourceMode == "video",
                    onClick = { onSourceModeChange("video") },
                    leadingIcon = { Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Device Video") },
                    modifier = Modifier.testTag("source_mode_video_chip")
                )
                FilterChip(
                    selected = sourceMode == "url",
                    onClick = { onSourceModeChange("url") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("YouTube / Search") },
                    modifier = Modifier.testTag("source_mode_youtube_chip")
                )
                FilterChip(
                    selected = sourceMode == "live",
                    onClick = { onSourceModeChange("live") },
                    leadingIcon = { Icon(Icons.Default.LiveTv, contentDescription = stringResource(R.string.cd_live_stream), modifier = Modifier.size(18.dp)) },
                    label = { Text("Live Video Skill") },
                    modifier = Modifier.testTag("source_mode_live_chip")
                )
            }

            when (sourceMode) {
                "video" -> {
                    OutlinedButton(
                        onClick = onPickVideo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pick_video_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = "Pick video from device")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = pickedVideoUri?.lastPathSegment ?: "Select Video via Android Photo Picker",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (pickedVideoUri != null) {
                        VideoPlayer(
                            uri = pickedVideoUri,
                            autoplay = false,
                            repeat = true,
                            trimEnabled = true,
                            trimStartMs = sourceTrimStartMs,
                            trimEndMs = sourceTrimEndMs,
                            videoEffects = sourceVideoEffects,
                            videoEffectsKey = sourceFilterKey,
                            onTrimRangeChange = onSourceTrimChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                        VideoFilterStackPanel(
                            filterStack = sourceFilterStack,
                            onFilterStackChange = onFilterStackChange,
                            contrastAdjust = sourceFilterContrast,
                            saturationAdjust = sourceFilterSaturation,
                            brightnessAdjust = sourceFilterBrightness,
                            onToneAdjustChange = onToneAdjustChange,
                            compact = true,
                            testTagPrefix = "clip_source_filter"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onOpenSourceTrimChat,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_source_trim_chat_button")
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Trim & Chat", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onAddSourceTrimToBatch,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_source_trim_to_batch_button")
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.cd_add_to_batch), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add to Batch", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                "url" -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChange,
                            label = { Text("YouTube or Video URL") },
                            placeholder = { Text("https://youtube.com/watch?v=...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_url_input")
                        )
                        OutlinedButton(
                            onClick = { onOpenYoutubeSearch(false) },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("open_youtube_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_youtube))
                            Spacer(Modifier.width(4.dp))
                            Text("Search")
                        }
                    }

                    selectedYoutubeVideo?.let { yt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = yt.thumbnailUrl,
                                        contentDescription = yt.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(yt.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${yt.channelName} • ${yt.durationText} • Trim ${formatSeconds(urlTrimStartSec)}–${formatSeconds(urlTrimEndSec)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onOpenYoutubeTrimChat,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("youtube_trim_chat_review_button")
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Visual Trim Handles & Chat Review")
                                }
                            }
                        }
                    }
                }

                else -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color(0xFFEF4444))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live Video Shorts Skill", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFCA5A5))
                                Text(
                                    "Clip active YouTube Live streams or .m3u8 HLS broadcasts with rolling DVR windows & AI skill presets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = liveStreamUrlInput,
                            onValueChange = onLiveStreamUrlChange,
                            label = { Text("Live Stream URL (YouTube Live or .m3u8)") },
                            placeholder = { Text("https://youtube.com/watch?v=... or .m3u8") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_stream_url_input")
                        )
                        OutlinedButton(
                            onClick = { onOpenYoutubeSearch(true) },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("search_live_streams_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Live")
                        }
                    }

                    if (liveStreamUrlInput.contains(".m3u8", ignoreCase = true)) {
                        VideoPlayer(
                            uri = Uri.parse(liveStreamUrlInput.trim()),
                            autoplay = false,
                            isLiveStream = true,
                            trimEnabled = true,
                            trimStartMs = sourceTrimStartMs,
                            trimEndMs = sourceTrimEndMs,
                            videoEffects = sourceVideoEffects,
                            videoEffectsKey = sourceFilterKey,
                            onTrimRangeChange = onSourceTrimChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Live Skill Focus", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DEFAULT_LIVE_SHORTS_SKILLS.forEach { preset ->
                            FilterChip(
                                selected = selectedLiveSkill.id == preset.id,
                                onClick = { onSelectLiveSkill(preset) },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("live_skill_${preset.id}")
                            )
                        }
                    }
                    Text(
                        text = selectedLiveSkill.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text("DVR Rolling Lookback Window", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 15, 30, 60).forEach { mins ->
                            FilterChip(
                                selected = liveLookbackMinutes == mins,
                                onClick = { onLiveLookbackChange(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = liveCueNotes,
                        onValueChange = onLiveCueNotesChange,
                        label = { Text("Live Moment / Chat Spike Notes (Optional)") },
                        placeholder = { Text("e.g. Big demo around 8:20, chat went wild on pricing announcement") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_cue_notes_input")
                    )
                }
            }

            if (sourceMode != "live") {
                OutlinedTextField(
                    value = guidanceInput,
                    onValueChange = onGuidanceInputChange,
                    label = { Text("AI Moment Guidance (Optional)") },
                    placeholder = { Text("e.g. Focus on founder story & bold contrarian takes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                "Target Clips: ${maxClips.toInt()} viral moments",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = maxClips,
                onValueChange = onMaxClipsChange,
                valueRange = 3f..8f,
                steps = 4
            )

            Button(
                onClick = onGenerateClick,
                enabled = generationState !is UiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_clips_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (generationState is UiState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (sourceMode == "live") "Clipping Live Broadcast..." else "Detecting Viral Moments...")
                } else {
                    Icon(
                        imageVector = if (sourceMode == "live") Icons.Default.LiveTv else Icons.Default.AutoAwesome,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (sourceMode == "live") "Make Shorts from Live Video" else "Analyze & Generate Clips")
                }
            }
        }
    }
}
