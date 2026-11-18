package com.example.ui.clips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.ui.components.StudioGlassCard

@Composable
internal fun ClipGeneratorHeaderSection(
    editingSessionsCount: Int,
    pendingAutomationCount: Int,
    pendingBatchExportCount: Int,
    onOpenSavedSessions: () -> Unit,
    onOpenAutomationQueue: () -> Unit,
    onOpenCaptionExport: () -> Unit,
    onOpenBatchExportQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Clip Generator", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenSavedSessions,
                    modifier = Modifier.height(48.dp).testTag("open_saved_sessions_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_saved_sessions), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (editingSessionsCount > 0) "Sessions ($editingSessionsCount)" else "Sessions")
                }
                OutlinedButton(
                    onClick = onOpenAutomationQueue,
                    modifier = Modifier.height(48.dp).testTag("open_automation_queue_button")
                ) {
                    Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (pendingAutomationCount > 0) "Queue ($pendingAutomationCount)" else "Queue")
                }
            }
        }
        Text(
            "Search YouTube, clip live streams, visually trim with seek bar handles or AI chat review, and save sessions to resume later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenCaptionExport,
                modifier = Modifier.weight(1f).height(48.dp).testTag("open_caption_export_studio_button")
            ) {
                Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Caption & Burn MP4", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onOpenBatchExportQueue,
                modifier = Modifier.height(48.dp).testTag("open_batch_export_queue_button")
            ) {
                Icon(Icons.Default.QueuePlayNext, contentDescription = stringResource(R.string.cd_batch_export), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (pendingBatchExportCount > 0) "Batch ($pendingBatchExportCount)" else "Batch")
            }
            OutlinedButton(
                onClick = onOpenSavedSessions,
                modifier = Modifier.height(48.dp).testTag("save_clip_studio_session_button")
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.cd_save_session), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Draft")
            }
        }
    }
}

@Composable
internal fun RecentClipProjectsSection(
    jobs: List<ClipJob>,
    selectedJobId: String?,
    selectedJob: ClipJob?,
    clips: List<GeneratedClip>,
    onSelectJob: (String) -> Unit,
    onDeleteJob: (String) -> Unit,
    onBatchAllClips: () -> Unit,
    onScheduleWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Clip Projects", style = MaterialTheme.typography.titleLarge)
            if (clips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!selectedJob?.videoUri.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onBatchAllClips,
                            modifier = Modifier.testTag("batch_export_all_clips_button")
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Batch All (${clips.size})")
                        }
                    }
                    OutlinedButton(
                        onClick = onScheduleWeek,
                        modifier = Modifier.testTag("schedule_week_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Schedule Week")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs, key = { it.id }) { job ->
                FilterChip(
                    selected = job.id == selectedJobId,
                    onClick = { onSelectJob(job.id) },
                    leadingIcon = if (job.sourceType == "live") {
                        { Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444)) }
                    } else null,
                    label = { Text("${job.sourceLabel.take(22)} (${job.clipsCount})", maxLines = 1) },
                    trailingIcon = {
                        IconButton(onClick = { onDeleteJob(job.id) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete project", modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun ClipJobSummaryCard(
    job: ClipJob,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(modifier = modifier) {
        Text(
            text = if (job.sourceType == "live") "Live Broadcast AI Summary" else "AI Video Summary",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(job.summary.orEmpty(), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun buildRestoredSessionVideo(
    session: com.example.data.local.EditingSession,
    liveBroadcast: Boolean
): com.example.data.model.YouTubeSearchResult? {
    val hasValidSource = if (liveBroadcast) !session.thumbnailUrl.isNullOrBlank() else session.sourceLabel.isNotBlank()
    if (!hasValidSource) return null
    val fallbackSec = if (liveBroadcast) 60.0 else 30.0
    return com.example.data.model.YouTubeSearchResult(
        videoId = session.id,
        title = session.title,
        channelName = if (liveBroadcast) "Live Stream" else "Saved Session",
        url = session.sourceLabel,
        thumbnailUrl = session.thumbnailUrl.orEmpty(),
        durationText = if (liveBroadcast) "LIVE" else com.example.ui.components.formatSeconds(session.totalDurationMs / 1000.0),
        durationSec = (session.totalDurationMs / 1000.0).coerceAtLeast(fallbackSec),
        liveBroadcast = liveBroadcast,
        viewCountText = ""
    )
}

internal fun buildCaptionExportArgsForClip(
    clip: GeneratedClip,
    videoUri: String,
    filterStack: List<String>,
    filterContrast: Float,
    filterSaturation: Float,
    filterBrightness: Float
) = com.example.ui.captions.CaptionExportInitArgs(
    initialVideoUri = videoUri,
    jobId = clip.jobId,
    clipId = clip.id,
    clipStartMs = (clip.startSec * 1000.0).toLong().coerceAtLeast(0L),
    clipEndMs = (clip.endSec * 1000.0).toLong().coerceAtLeast(1000L),
    initialTranscript = clip.subtitleText.orEmpty(),
    initialTitle = clip.youtubeTitle,
    initialHook = clip.viralHookText.orEmpty(),
    subtitlePosition = clip.subtitlePosition,
    subtitleAnimation = clip.subtitleAnimation,
    subtitleFont = clip.subtitleFont,
    subtitleColorHex = clip.subtitleColorHex,
    subtitleHighlightHex = clip.subtitleHighlightHex,
    initialFilterStackCsv = com.example.data.media.Media3VideoFilterFactory.toCsv(filterStack),
    filterContrast = filterContrast,
    filterSaturation = filterSaturation,
    filterBrightness = filterBrightness
)

internal fun buildCombinedClipGuidance(
    sourceMode: String,
    selectedYoutubeVideo: com.example.data.model.YouTubeSearchResult?,
    urlTrimStartSec: Double,
    urlTrimEndSec: Double,
    sourceTrimStartMs: Long,
    sourceTrimEndMs: Long,
    guidanceInput: String
): String = buildString {
    if (sourceMode == "url" && selectedYoutubeVideo != null) {
        append("Selected YouTube video '${selectedYoutubeVideo.title}' trimmed to ${com.example.ui.components.formatSeconds(urlTrimStartSec)}-${com.example.ui.components.formatSeconds(urlTrimEndSec)}. ")
    } else if (sourceMode == "video" && sourceTrimEndMs > sourceTrimStartMs && sourceTrimStartMs > 0L) {
        append("Focus on user-trimmed range ${com.example.ui.components.formatSeconds(sourceTrimStartMs / 1000.0)}-${com.example.ui.components.formatSeconds(sourceTrimEndMs / 1000.0)}. ")
    }
    append(guidanceInput)
}.trim()

internal fun buildCaptionExportArgsForSource(
    pickedVideoUri: android.net.Uri?,
    fallbackVideoUri: String?,
    sourceTrimStartMs: Long,
    sourceTrimEndMs: Long,
    filterStack: List<String>,
    filterContrast: Float,
    filterSaturation: Float,
    filterBrightness: Float
) = com.example.ui.captions.CaptionExportInitArgs(
    initialVideoUri = pickedVideoUri?.toString() ?: fallbackVideoUri.orEmpty(),
    clipStartMs = if (pickedVideoUri != null) sourceTrimStartMs else 0L,
    clipEndMs = if (pickedVideoUri != null && sourceTrimEndMs > sourceTrimStartMs) sourceTrimEndMs else -1L,
    initialFilterStackCsv = com.example.data.media.Media3VideoFilterFactory.toCsv(filterStack),
    filterContrast = filterContrast,
    filterSaturation = filterSaturation,
    filterBrightness = filterBrightness
)
