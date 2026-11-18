package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ViralAmber

@Composable
internal fun VideoLiveBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFDC2626),
        modifier = modifier
            .padding(10.dp)
            .testTag("video_player_live_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
internal fun VideoHairlineProgress(
    displayedPositionMs: Long,
    safeDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val hairlineColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .testTag("video_player_hairline_progress")
    ) {
        val fraction = (displayedPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
        drawRect(color = Color.White.copy(alpha = 0.22f))
        drawRect(
            color = hairlineColor,
            size = Size(size.width * fraction, size.height)
        )
    }
}

@Composable
internal fun VideoPlayerControlsOverlay(
    playbackSpeed: Float,
    speedOptions: List<Float>,
    speedMenuExpanded: Boolean,
    onOpenSpeedMenu: () -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    isPlaying: Boolean,
    isBuffering: Boolean,
    trimEnabled: Boolean,
    durationMs: Long,
    safeDurationMs: Long,
    displayedPositionMs: Long,
    bufferedPositionMs: Long,
    effectiveTrimStartMs: Long,
    effectiveTrimEndMs: Long,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onScrubStart: (Long) -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit,
    onTrimRangeCommit: (Long, Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.86f)
                    )
                )
            )
    ) {
        // Top-right playback speed menu button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Surface(
                onClick = onOpenSpeedMenu,
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.62f),
                border = BorderStroke(
                    1.dp,
                    if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else Color.White.copy(alpha = 0.24f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_player_speed_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.cd_playback_speed),
                        tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatSpeedLabel(playbackSpeed),
                        fontFamily = JetBrainsMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            DropdownMenu(
                expanded = speedMenuExpanded,
                onDismissRequest = onDismissSpeedMenu
            ) {
                speedOptions.forEach { speed ->
                    val isSelected = speed == playbackSpeed
                    val label = formatSpeedLabel(speed)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (speed == 1.0f) "$label (Normal)" else label,
                                fontFamily = JetBrainsMonoFont,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = { onSelectSpeed(speed) },
                        modifier = Modifier.testTag("video_player_speed_option_$label")
                    )
                }
            }
        }

        // Center transport controls: Replay 10s, Play/Pause, Forward 10s
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSeekBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("video_player_seek_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = stringResource(R.string.cd_seek_backward),
                    tint = Color.White
                )
            }

            if (!isBuffering) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), CircleShape)
                        .testTag("video_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.cd_pause_video else R.string.cd_play_video
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }

            IconButton(
                onClick = onSeekForward,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("video_player_seek_forward_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = stringResource(R.string.cd_seek_forward),
                    tint = Color.White
                )
            }
        }

        // Bottom custom seek bar (with optional visual trim handles) & duration timestamps
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            VideoSeekBar(
                positionMs = displayedPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = safeDurationMs,
                trimEnabled = trimEnabled && durationMs > 0L,
                trimStartMs = effectiveTrimStartMs,
                trimEndMs = if (durationMs > 0L) effectiveTrimEndMs else safeDurationMs,
                onScrubStart = onScrubStart,
                onScrubMove = onScrubMove,
                onScrubEnd = onScrubEnd,
                onTrimStartChange = onTrimStartChange,
                onTrimEndChange = onTrimEndChange,
                onTrimRangeCommit = onTrimRangeCommit
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMillis(displayedPositionMs),
                    fontFamily = JetBrainsMonoFont,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.testTag("video_player_current_time")
                )
                if (trimEnabled && durationMs > 0L) {
                    Text(
                        text = "CUT ${formatMillis(effectiveTrimStartMs)} → ${formatMillis(effectiveTrimEndMs)}",
                        fontFamily = JetBrainsMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        color = ViralAmber,
                        modifier = Modifier.testTag("video_player_trim_range_label")
                    )
                }
                Text(
                    text = formatMillis(durationMs),
                    fontFamily = JetBrainsMonoFont,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.testTag("video_player_total_time")
                )
            }
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    return when (speed) {
        0.5f -> "0.5x"
        0.75f -> "0.75x"
        1.0f -> "1x"
        1.25f -> "1.25x"
        1.5f -> "1.5x"
        2.0f -> "2x"
        else -> "${speed}x"
    }
}
