package com.example.ui.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TrimChatMessage
import com.example.ui.components.VideoSeekBar
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald

@Composable
internal fun TrimThumbnailTimelineFallback(
    title: String,
    thumbnailUrl: String?,
    isLiveSource: Boolean,
    previewPositionMs: Long,
    safeTotalMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onPreviewPositionChange: (Long) -> Unit,
    onTrimChange: (Long, Long) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Text(
                            text = if (isLiveSource) "Live Broadcast Timeline" else "YouTube Clip Timeline",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }

            VideoSeekBar(
                positionMs = previewPositionMs,
                bufferedPositionMs = safeTotalMs,
                durationMs = safeTotalMs,
                trimEnabled = true,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                onScrubStart = onPreviewPositionChange,
                onScrubMove = onPreviewPositionChange,
                onScrubEnd = onPreviewPositionChange,
                onTrimStartChange = { s ->
                    onTrimChange(s, trimEndMs)
                    onPreviewPositionChange(s)
                },
                onTrimEndChange = { e ->
                    onTrimChange(trimStartMs, e)
                    onPreviewPositionChange(e)
                },
                onTrimRangeCommit = onTrimChange
            )
        }
    }
}

@Composable
internal fun TrimTimingNudgeCard(
    trimStartMs: Long,
    trimEndMs: Long,
    safeTotalMs: Long,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit
) {
    val lengthSec = ((trimEndMs - trimStartMs).coerceAtLeast(1000L)) / 1000.0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("START", style = MaterialTheme.typography.labelSmall, color = ViralAmber)
                Text(
                    text = formatSecondsLabel(trimStartMs / 1000.0),
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onTrimStartChange((trimStartMs - 1000L).coerceAtLeast(0L)) },
                        modifier = Modifier.testTag("trim_start_minus_1s")
                    ) { Text("-1s") }
                    TextButton(
                        onClick = {
                            onTrimStartChange((trimStartMs + 1000L).coerceAtMost((trimEndMs - 1000L).coerceAtLeast(0L)))
                        },
                        modifier = Modifier.testTag("trim_start_plus_1s")
                    ) { Text("+1s") }
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "${lengthSec.toInt()}s clip",
                    fontFamily = JetBrainsMonoFont,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("END", style = MaterialTheme.typography.labelSmall, color = ViralAmber)
                Text(
                    text = formatSecondsLabel(trimEndMs / 1000.0),
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onTrimEndChange((trimEndMs - 1000L).coerceAtLeast((trimStartMs + 1000L).coerceAtMost(safeTotalMs)))
                        },
                        modifier = Modifier.testTag("trim_end_minus_1s")
                    ) { Text("-1s") }
                    TextButton(
                        onClick = { onTrimEndChange((trimEndMs + 1000L).coerceAtMost(safeTotalMs)) },
                        modifier = Modifier.testTag("trim_end_plus_1s")
                    ) { Text("+1s") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrimChatReviewSection(
    chatMessages: List<TrimChatMessage>,
    transcriptOrContext: String,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    isChatBusy: Boolean,
    safeTotalMs: Long,
    onSendPrompt: (String) -> Unit,
    onSnapHandles: (Long, Long) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text("Chat Review Trim Assistant", style = MaterialTheme.typography.titleMedium)
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "Cut the slow first 5s intro",
            "Tighten to the punchiest 20s",
            "End right on the key takeaway",
            "Find a high-energy 15s hook"
        ).forEach { promptChip ->
            AssistChip(
                onClick = { onSendPrompt(promptChip) },
                enabled = !isChatBusy,
                label = { Text(promptChip, maxLines = 1) }
            )
        }
    }

    if (chatMessages.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chatMessages.forEach { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (msg.fromUser) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (msg.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else ObsidianBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = (if (msg.fromUser) "You: " else "Trim AI: ") + msg.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val sSec = msg.suggestedStartSec
                        val eSec = msg.suggestedEndSec
                        if (!msg.fromUser && sSec != null && eSec != null && eSec > sSec) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Suggested: ${formatSecondsLabel(sSec)} → ${formatSecondsLabel(eSec)}",
                                    fontFamily = JetBrainsMonoFont,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ViralEmerald
                                )
                                TextButton(
                                    onClick = {
                                        val ns = (sSec * 1000.0).toLong().coerceIn(0L, (safeTotalMs - 1000L).coerceAtLeast(0L))
                                        val ne = (eSec * 1000.0).toLong().coerceIn((ns + 1000L).coerceAtMost(safeTotalMs), safeTotalMs)
                                        onSnapHandles(ns, ne)
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Snap Handles")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (transcriptOrContext.isNotBlank()) {
        Text(
            text = "Context: ${transcriptOrContext.take(180)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = chatInput,
            onValueChange = onChatInputChange,
            label = { Text("Ask AI to review & trim (e.g. 'start at 0:12, cut ending')") },
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .testTag("trim_chat_input")
        )
        IconButton(
            onClick = {
                val msg = chatInput.trim()
                if (msg.isNotBlank()) {
                    onChatInputChange("")
                    onSendPrompt(msg)
                }
            },
            enabled = !isChatBusy && chatInput.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .testTag("trim_chat_send_button")
        ) {
            if (isChatBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send_trim_chat)
                )
            }
        }
    }
}

internal fun formatSecondsLabel(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return "%02d:%02d".format(mins, secs)
}
