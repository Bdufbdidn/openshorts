package com.example.ui.automation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.PublishAutomationJob
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.PublishAutomationSheet
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutomationQueueScreen(
    currentUserId: String,
    onNavigateBack: () -> Unit,
    viewModel: AutomationQueueViewModel = viewModel(
        key = currentUserId,
        factory = AutomationQueueViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val jobsState by viewModel.publishJobsState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var filterStatus by remember { mutableStateOf("all") }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        feedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
    }

    val allJobs = (jobsState as? UiState.Success)?.data.orEmpty()
    val filteredJobs = remember(allJobs, filterStatus) {
        when (filterStatus) {
            "pending" -> allJobs.filter { it.status in listOf("queued", "scheduled", "uploading") }
            "published" -> allJobs.filter { it.status == "published" }
            "failed" -> allJobs.filter { it.status == "failed" }
            else -> allJobs
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Channel Automation Queue") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("new_automation_post_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule new YouTube and Instagram post")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val hasUploadPost = settings.uploadPostApiKey.isNotBlank() && settings.uploadPostUsername.isNotBlank()
                    StudioGlassCard(
                        borderColor = (if (hasUploadPost) ViralEmerald else ViralAmber).copy(alpha = 0.45f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.ScheduleSend,
                                contentDescription = null,
                                tint = if (hasUploadPost) ViralEmerald else ViralAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasUploadPost) {
                                        "Auto-Publisher Active (@${settings.uploadPostUsername})"
                                    } else {
                                        "Connect Upload-Post in Settings for Hands-Free Uploads"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Auto-Queue on clip generation: ${if (settings.autoPostEnabled) "ON" else "OFF"} • Daily UTC: ${settings.defaultPostHourUtc}:00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "all" to "All (${allJobs.size})",
                            "pending" to "Active (${allJobs.count { it.status in listOf("queued", "scheduled", "uploading") }})",
                            "published" to "Published (${allJobs.count { it.status == "published" }})",
                            "failed" to "Failed (${allJobs.count { it.status == "failed" }})"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = filterStatus == key,
                                onClick = { filterStatus = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            if (jobsState is UiState.Error) {
                item {
                    ErrorBanner(
                        message = (jobsState as UiState.Error).message,
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }
            }

            if (jobsState is UiState.Success && filteredJobs.isEmpty()) {
                item {
                    StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        Text("No Automated Posts Yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap + to attach a Short video with YouTube Title & Instagram Captions, or use Post / Auto-Post from Clip Generator, AI Shorts, or YouTube Studio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showCreateSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create YouTube & Instagram Post")
                        }
                    }
                }
            }

            items(filteredJobs, key = { it.id }) { job ->
                PublishAutomationJobCard(
                    job = job,
                    onRunNow = { viewModel.runJobNow(job) },
                    onDelete = { viewModel.deleteJob(job) },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }
    }

    if (showCreateSheet) {
        PublishAutomationSheet(
            sheetTitle = "New YouTube & Instagram Auto-Post",
            initialYoutubeTitle = "",
            initialYoutubeDescription = "",
            initialInstagramCaption = "",
            initialVideoUri = null,
            settings = settings,
            isBusy = isBusy,
            onDismiss = { showCreateSheet = false },
            onAiOptimize = { title, ytDesc, igCap ->
                viewModel.optimizeMetadata(title, ytDesc, igCap)
            },
            onSubmitAutomation = { req ->
                viewModel.submitCustomAutomation(req)
                showCreateSheet = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublishAutomationJobCard(
    job: PublishAutomationJob,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (job.status) {
        "published" -> ViralEmerald
        "failed" -> MaterialTheme.colorScheme.error
        "uploading" -> MaterialTheme.colorScheme.secondary
        else -> ViralAmber
    }

    StudioGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = job.status.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete scheduled post")
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = job.youtubeTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (job.youtubeEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("YouTube (${job.youtubePrivacy})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (job.instagramEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text("Instagram Reels", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        if (job.youtubeDescription.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "YT Description: ${job.youtubeDescription}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (job.instagramCaption.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "IG Caption: ${job.instagramCaption}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!job.scheduledIso.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scheduled: ${job.scheduledIso}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (!job.resultMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = job.resultMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (job.status != "published" && job.status != "uploading") {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRunNow,
                modifier = Modifier.fillMaxWidth().testTag("run_automation_now_${job.id}")
            ) {
                Icon(
                    imageVector = if (job.status == "failed") Icons.Default.Refresh else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (job.status == "failed") "Retry Auto-Post Now" else "Run Auto-Post Now")
            }
        }
    }
}
