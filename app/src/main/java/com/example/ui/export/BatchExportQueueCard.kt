package com.example.ui.export

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BatchExportItem
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.data.media.Media3VideoFilterFactory
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import java.util.Locale

@Composable
internal fun BatchCountBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFont,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun BatchExportQueueCard(
    positionNumber: Int,
    item: BatchExportItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isPreviewExpanded: Boolean,
    onTogglePreview: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRetry: () -> Unit,
    onRetryUpload: () -> Unit,
    onCopyCloudUrl: (String) -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActiveItem = item.status == BatchExportStatus.PROCESSING ||
        item.uploadStatus == CloudUploadStatus.UPLOADING

    val statusColor = when {
        item.uploadStatus == CloudUploadStatus.UPLOADING -> MaterialTheme.colorScheme.secondary
        item.status == BatchExportStatus.PROCESSING -> MaterialTheme.colorScheme.primary
        item.status == BatchExportStatus.COMPLETED && item.uploadStatus == CloudUploadStatus.UPLOADED -> ViralEmerald
        item.status == BatchExportStatus.COMPLETED -> ViralEmerald
        item.status == BatchExportStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> ViralAmber
    }

    StudioGlassCard(
        modifier = modifier.testTag("batch_item_card_${item.id}"),
        borderColor = if (isActiveItem) statusColor.copy(alpha = 0.72f) else ObsidianBorder
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "#$positionNumber",
                            fontFamily = JetBrainsMonoFont,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (item.status) {
                                BatchExportStatus.COMPLETED ->
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                                BatchExportStatus.FAILED ->
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = when {
                                    item.status == BatchExportStatus.PROCESSING ->
                                        "RENDERING ${(item.progress * 100).toInt()}%"
                                    item.uploadStatus == CloudUploadStatus.UPLOADING ->
                                        "UPLOADING ${(item.uploadProgress * 100).toInt()}%"
                                    else -> item.status.uppercase()
                                },
                                fontFamily = JetBrainsMonoFont,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canMoveUp) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move clip earlier in batch")
                        }
                    }
                    if (canMoveDown) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move clip later in batch")
                        }
                    }
                    IconButton(
                        onClick = onRemove,
                        enabled = !isActiveItem,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("batch_remove_item_${item.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove from export batch")
                    }
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val trimText = if (item.trimEndMs > item.trimStartMs) {
                "Trim ${formatMs(item.trimStartMs)} → ${formatMs(item.trimEndMs)}"
            } else {
                "Full source video"
            }
            val overlayText = buildList {
                if (item.cues.isNotEmpty()) add("${item.cues.size} caption cues")
                if (item.burnStyle.hookText.isNotBlank()) add("Hook banner")
                if (item.burnStyle.hasVideoFilters()) {
                    add(
                        "Filters: ${
                            Media3VideoFilterFactory.describeStack(
                                item.burnStyle.filterStack,
                                item.burnStyle.filterContrast,
                                item.burnStyle.filterSaturation,
                                item.burnStyle.filterBrightness
                            )
                        }"
                    )
                }
                if (item.cues.isEmpty() && item.burnStyle.hookText.isBlank() && !item.burnStyle.hasVideoFilters()) {
                    add("Direct trim MP4")
                }
            }.joinToString(" • ")

            Text(
                text = "$trimText • $overlayText",
                fontFamily = JetBrainsMonoFont,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.status == BatchExportStatus.PROCESSING) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1. Encoding Trimmed MP4",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            fontFamily = JetBrainsMonoFont,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0.03f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }

            FirebaseStorageUploadIndicator(
                item = item,
                onRetryUpload = onRetryUpload,
                onCopyCloudUrl = onCopyCloudUrl
            )

            if (item.status == BatchExportStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("batch_retry_item_${item.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry Clip Export")
                }
            }

            if (item.status == BatchExportStatus.COMPLETED && !item.outputContentUri.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTogglePreview,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isPreviewExpanded) "Hide" else "Preview")
                    }
                    OutlinedButton(
                        onClick = onSaveToGallery,
                        enabled = item.galleryUri == null,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.galleryUri != null) "Saved" else "Gallery")
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }

                AnimatedVisibility(visible = isPreviewExpanded) {
                    VideoPlayer(
                        uri = Uri.parse(item.outputContentUri),
                        autoplay = true,
                        repeat = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatMs(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val secs = safe / 1000L
    val mins = secs / 60L
    val remSecs = secs % 60L
    val tenths = (safe % 1000L) / 100L
    return "%02d:%02d.%d".format(Locale.US, mins, remSecs, tenths)
}
