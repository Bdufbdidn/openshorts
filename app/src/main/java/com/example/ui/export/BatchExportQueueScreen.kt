package com.example.ui.export

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareVideoUri
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchExportQueueScreen(
    currentUserId: String,
    onNavigateBack: () -> Unit,
    viewModel: BatchExportQueueViewModel = viewModel(
        key = currentUserId,
        factory = BatchExportQueueViewModel.provideFactory(currentUserId)
    )
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val items by viewModel.queueItems.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    var showAddClipSheet by remember { mutableStateOf(false) }
    var previewingItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    val queuedCount = items.count { it.status == BatchExportStatus.QUEUED }
    val processingItem = items.firstOrNull { it.status == BatchExportStatus.PROCESSING }
    val uploadingItem = items.firstOrNull { it.uploadStatus == CloudUploadStatus.UPLOADING }
    val queuedUploadCount = items.count {
        it.status == BatchExportStatus.COMPLETED && it.uploadStatus == CloudUploadStatus.QUEUED
    }
    val completedCount = items.count { it.status == BatchExportStatus.COMPLETED }
    val uploadedCount = items.count { it.uploadStatus == CloudUploadStatus.UPLOADED }
    val failedCount = items.count {
        it.status == BatchExportStatus.FAILED || it.uploadStatus == CloudUploadStatus.FAILED
    }
    val isBatchRunning = processingItem != null || uploadingItem != null || queuedUploadCount > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_export_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp).testTag("batch_export_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                },
                actions = {
                    if (completedCount > 0) {
                        TextButton(
                            onClick = { viewModel.clearFinished() },
                            modifier = Modifier.testTag("batch_clear_finished_button")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear Done")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddClipSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("batch_add_clip_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_to_batch)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = stringResource(R.string.cd_batch_export),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sequential Batch & Cloud Upload", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = when {
                                        uploadingItem != null ->
                                            "Uploading \"${uploadingItem.title}\" to Firebase Storage (${(uploadingItem.uploadProgress * 100).toInt()}%)..."
                                        processingItem != null ->
                                            "Rendering \"${processingItem.title}\" (${(processingItem.progress * 100).toInt()}%)..."
                                        queuedUploadCount > 0 ->
                                            "Finishing Firebase Storage upload queue ($queuedUploadCount remaining)..."
                                        else ->
                                            "Renders queued trimmed clips sequentially and streams each export to Firebase Storage."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BatchCountBadge("Queued: $queuedCount", ViralAmber)
                            if (processingItem != null) {
                                BatchCountBadge("Rendering: 1", MaterialTheme.colorScheme.primary)
                            }
                            if (uploadingItem != null) {
                                BatchCountBadge("Uploading: 1", MaterialTheme.colorScheme.secondary)
                            }
                            BatchCountBadge("Rendered: $completedCount", ViralEmerald)
                            BatchCountBadge("Cloud Uploaded: $uploadedCount", MaterialTheme.colorScheme.secondary)
                            if (failedCount > 0) {
                                BatchCountBadge("Attention: $failedCount", MaterialTheme.colorScheme.error)
                            }
                        }

                        if (items.isNotEmpty() && (isBatchRunning || completedCount > 0 || uploadedCount > 0)) {
                            val totalWeight = items.size.toFloat().coerceAtLeast(1f)
                            val itemFractionSum = items.sumOf { item ->
                                val encodePart = when (item.status) {
                                    BatchExportStatus.COMPLETED -> 1.0
                                    BatchExportStatus.PROCESSING -> item.progress.toDouble().coerceIn(0.0, 1.0)
                                    else -> 0.0
                                }
                                if (item.uploadToFirebaseStorage) {
                                    val uploadPart = when (item.uploadStatus) {
                                        CloudUploadStatus.UPLOADED -> 1.0
                                        CloudUploadStatus.UPLOADING -> item.uploadProgress.toDouble().coerceIn(0.0, 1.0)
                                        else -> 0.0
                                    }
                                    (encodePart * 0.5) + (uploadPart * 0.5)
                                } else {
                                    encodePart
                                }
                            }.toFloat()
                            val overallProgress = (itemFractionSum / totalWeight).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Overall Batch Progress",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(overallProgress * 100).toInt()}%",
                                        fontFamily = JetBrainsMonoFont,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { overallProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.startBatchProcessing() },
                                enabled = queuedCount > 0 && !isBatchRunning,
                                modifier = Modifier.weight(1f).height(50.dp).testTag("start_batch_export_button")
                            ) {
                                if (isBatchRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (uploadingItem != null) "Uploading..." else "Processing Batch...")
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (queuedCount > 0) "Start Batch ($queuedCount)" else "Queue Empty")
                                }
                            }
                            if (isBatchRunning) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelBatchProcessing() },
                                    modifier = Modifier.height(50.dp).testTag("cancel_batch_export_button")
                                ) {
                                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pause")
                                }
                            }
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("No Clips in Export Batch", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Add trimmed clips from Clip Generator or Caption Studio, then monitor both local MP4 encoding and live Firebase Storage upload progress per item.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showAddClipSheet = true },
                                modifier = Modifier.height(48.dp).testTag("empty_batch_add_clip_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Trimmed Clip to Batch")
                            }
                        }
                    }
                }
            }

            val queuedItems = items.filter { it.status == BatchExportStatus.QUEUED }
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val queuedIdx = queuedItems.indexOfFirst { it.id == item.id }
                BatchExportQueueCard(
                    positionNumber = index + 1,
                    item = item,
                    canMoveUp = item.status == BatchExportStatus.QUEUED && queuedIdx > 0,
                    canMoveDown = item.status == BatchExportStatus.QUEUED && queuedIdx >= 0 && queuedIdx < queuedItems.lastIndex,
                    isPreviewExpanded = previewingItemId == item.id,
                    onTogglePreview = {
                        previewingItemId = if (previewingItemId == item.id) null else item.id
                    },
                    onMoveUp = { viewModel.moveItem(item.id, moveUp = true) },
                    onMoveDown = { viewModel.moveItem(item.id, moveUp = false) },
                    onRetry = { viewModel.retryItem(item.id) },
                    onRetryUpload = { viewModel.retryCloudUpload(item.id) },
                    onCopyCloudUrl = { url ->
                        copyToClipboard(context, "Firebase Storage URL", url)
                        Toast.makeText(context, "Copied Firebase Storage URL", Toast.LENGTH_SHORT).show()
                    },
                    onSaveToGallery = { viewModel.saveCompletedToGallery(item) },
                    onShare = {
                        val uri = item.outputContentUri ?: return@BatchExportQueueCard
                        shareVideoUri(
                            context = context,
                            videoUriString = uri,
                            title = item.title,
                            caption = item.cues.joinToString(" ") { it.text }
                        )
                    },
                    onRemove = { viewModel.removeItem(item.id) },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }
    }

    if (showAddClipSheet) {
        AddTrimmedClipToBatchSheet(
            onDismiss = { showAddClipSheet = false },
            onAddClip = { title, uri, startMs, endMs, burnStyle, saveToGallery, uploadToCloud, startNow ->
                viewModel.enqueueClip(
                    title = title,
                    videoUri = uri,
                    trimStartMs = startMs,
                    trimEndMs = endMs,
                    burnStyle = burnStyle,
                    saveToGallery = saveToGallery,
                    uploadToFirebaseStorage = uploadToCloud,
                    startImmediately = startNow
                )
                showAddClipSheet = false
            }
        )
    }
}
