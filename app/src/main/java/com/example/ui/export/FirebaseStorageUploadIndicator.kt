package com.example.ui.export

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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BatchExportItem
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import java.util.Locale

@Composable
internal fun FirebaseStorageUploadIndicator(
    item: BatchExportItem,
    onRetryUpload: () -> Unit,
    onCopyCloudUrl: (String) -> Unit
) {
    val uploadColor = when (item.uploadStatus) {
        CloudUploadStatus.UPLOADED -> ViralEmerald
        CloudUploadStatus.UPLOADING -> MaterialTheme.colorScheme.secondary
        CloudUploadStatus.FAILED -> MaterialTheme.colorScheme.error
        CloudUploadStatus.QUEUED -> MaterialTheme.colorScheme.primary
        CloudUploadStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> ViralAmber
    }

    val uploadLabel = when {
        !item.uploadToFirebaseStorage || item.uploadStatus == CloudUploadStatus.SKIPPED ->
            "Firebase Storage: Local only (upload skipped)"
        item.uploadStatus == CloudUploadStatus.UPLOADING ->
            "Firebase Storage: Uploading ${(item.uploadProgress * 100).toInt()}%"
        item.uploadStatus == CloudUploadStatus.UPLOADED ->
            "Firebase Storage: Uploaded"
        item.uploadStatus == CloudUploadStatus.FAILED ->
            "Firebase Storage: Upload Failed"
        item.uploadStatus == CloudUploadStatus.QUEUED ->
            "Firebase Storage: Queued for upload"
        item.status == BatchExportStatus.PROCESSING ->
            "Firebase Storage: Pending after MP4 render"
        item.status == BatchExportStatus.FAILED ->
            "Firebase Storage: Blocked by render error"
        else ->
            "Firebase Storage: Waiting in queue"
    }

    val barProgress = when (item.uploadStatus) {
        CloudUploadStatus.UPLOADED -> 1f
        CloudUploadStatus.UPLOADING -> item.uploadProgress.coerceIn(0.03f, 0.99f)
        else -> 0f
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, uploadColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firebase_upload_status_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    if (item.uploadStatus == CloudUploadStatus.UPLOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = uploadColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = when (item.uploadStatus) {
                                CloudUploadStatus.UPLOADED -> Icons.Default.CloudDone
                                CloudUploadStatus.FAILED, CloudUploadStatus.SKIPPED -> Icons.Default.CloudOff
                                else -> Icons.Default.CloudUpload
                            },
                            contentDescription = stringResource(R.string.cd_firebase_upload),
                            tint = uploadColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = uploadLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = uploadColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.totalBytes > 0L &&
                    (item.uploadStatus == CloudUploadStatus.UPLOADING || item.uploadStatus == CloudUploadStatus.UPLOADED)
                ) {
                    Text(
                        text = if (item.uploadStatus == CloudUploadStatus.UPLOADING) {
                            "${formatBytesMb(item.uploadedBytes)} / ${formatBytesMb(item.totalBytes)}"
                        } else {
                            formatBytesMb(item.totalBytes)
                        },
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${(barProgress * 100).toInt()}%",
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = uploadColor
                    )
                }
            }

            if (item.uploadToFirebaseStorage && item.uploadStatus != CloudUploadStatus.SKIPPED) {
                LinearProgressIndicator(
                    progress = { barProgress },
                    color = uploadColor,
                    trackColor = ObsidianBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .testTag("firebase_upload_progress_bar_${item.id}")
                )
            }

            if (!item.storagePath.isNullOrBlank() && item.uploadStatus == CloudUploadStatus.UPLOADED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.storagePath,
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val dlUrl = item.storageDownloadUrl
                    if (!dlUrl.isNullOrBlank()) {
                        TextButton(
                            onClick = { onCopyCloudUrl(dlUrl) },
                            modifier = Modifier.testTag("batch_copy_storage_url_${item.id}")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_cloud_url),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy URL")
                        }
                    }
                }
            }

            if (item.uploadStatus == CloudUploadStatus.FAILED) {
                if (!item.uploadErrorMessage.isNullOrBlank()) {
                    Text(
                        text = item.uploadErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (item.status == BatchExportStatus.COMPLETED) {
                    OutlinedButton(
                        onClick = onRetryUpload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_retry_upload_${item.id}")
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = stringResource(R.string.cd_retry_upload),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Firebase Storage Upload")
                    }
                }
            }
        }
    }
}

private fun formatBytesMb(bytes: Long): String {
    val mb = bytes.coerceAtLeast(0L) / (1024f * 1024f)
    return String.format(Locale.US, "%.1f MB", mb)
}
