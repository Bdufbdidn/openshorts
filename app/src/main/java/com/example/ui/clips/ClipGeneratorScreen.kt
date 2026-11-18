package com.example.ui.clips

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard
import com.example.ui.components.ScoreBadge
import com.example.ui.components.ShortClipPreviewPlayer
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoPlayer
import com.example.ui.components.copyToClipboard
import com.example.ui.components.formatSeconds

private enum class ActiveClipSheet {
    SUBTITLES, HOOK, TRANSLATE, POST
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipGeneratorScreen(
    currentUserId: String,
    viewModel: ClipGeneratorViewModel = viewModel(
        key = currentUserId,
        factory = ClipGeneratorViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val jobsState by viewModel.jobsState.collectAsStateWithLifecycle()
    val clipsState by viewModel.activeClipsState.collectAsStateWithLifecycle()
    val selectedJobId by viewModel.selectedJobId.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val editorFeedback by viewModel.editorFeedback.collectAsStateWithLifecycle()
    val isEditorBusy by viewModel.isEditorBusy.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var sourceMode by remember { mutableStateOf("video") }
    var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var guidanceInput by remember { mutableStateOf("") }
    var maxClips by remember { mutableFloatStateOf(4f) }

    var editingClip by remember { mutableStateOf<GeneratedClip?>(null) }
    var activeSheet by remember { mutableStateOf<ActiveClipSheet?>(null) }
    var showScheduleWeek by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedVideoUri = uri
            viewModel.clearGenerationError()
        }
    }

    LaunchedEffect(editorFeedback) {
        editorFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
    }

    val jobs = (jobsState as? UiState.Success)?.data.orEmpty()
    val selectedJob = jobs.firstOrNull { it.id == selectedJobId }
    val clips = (clipsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Clip Generator", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Turn long videos or URLs into 9:16 viral shorts with AI hooks, animated captions & 30+ language dubbing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = sourceMode == "video",
                                onClick = { sourceMode = "video" },
                                leadingIcon = { Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("Device Video") }
                            )
                            FilterChip(
                                selected = sourceMode == "url",
                                onClick = { sourceMode = "url" },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                label = { Text("YouTube / URL") }
                            )
                        }

                        if (sourceMode == "video") {
                            OutlinedButton(
                                onClick = {
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
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
                                    autoplay = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = {
                                    urlInput = it
                                    viewModel.clearGenerationError()
                                },
                                label = { Text("YouTube or Video URL") },
                                placeholder = { Text("https://youtube.com/watch?v=...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("video_url_input")
                            )
                        }

                        OutlinedTextField(
                            value = guidanceInput,
                            onValueChange = { guidanceInput = it },
                            label = { Text("AI Moment Guidance (Optional)") },
                            placeholder = { Text("e.g. Focus on founder story & bold contrarian takes") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Target Clips: ${maxClips.toInt()} viral moments",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = maxClips,
                            onValueChange = { maxClips = it },
                            valueRange = 3f..8f,
                            steps = 4
                        )

                        Button(
                            onClick = {
                                val label = if (sourceMode == "video") {
                                    pickedVideoUri?.lastPathSegment ?: ""
                                } else urlInput.trim()
                                viewModel.analyzeAndGenerateClips(
                                    sourceType = sourceMode,
                                    sourceLabel = label,
                                    videoUri = if (sourceMode == "video") pickedVideoUri else null,
                                    clipGuidance = guidanceInput,
                                    maxClips = maxClips.toInt()
                                )
                            },
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
                                Text("Detecting Viral Moments...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyze & Generate Clips")
                            }
                        }
                    }
                }
            }
        }

        if (generationState is UiState.Loading) {
            item {
                ScannerProcessingCard(
                    title = "Gemini 3.8 Flash Analyzing Frames",
                    subtitle = "Scoring hooks, pacing, and 9:16 vertical cuts...",
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        if (generationState is UiState.Error) {
            item {
                ErrorBanner(
                    message = (generationState as UiState.Error).message,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
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

        // Saved Jobs Strip
        if (jobs.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Clip Projects", style = MaterialTheme.typography.titleLarge)
                        if (clips.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showScheduleWeek = true },
                                modifier = Modifier.testTag("schedule_week_button")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Schedule Week")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(jobs, key = { it.id }) { job ->
                            FilterChip(
                                selected = job.id == selectedJobId,
                                onClick = { viewModel.selectJob(job.id) },
                                label = {
                                    Text(
                                        "${job.sourceLabel.take(22)} (${job.clipsCount})",
                                        maxLines = 1
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.deleteJob(job.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete project",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selectedJob != null && !selectedJob.summary.isNullOrBlank()) {
            item {
                StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Text("AI Video Summary", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(selectedJob.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (clipsState is UiState.Error) {
            item {
                ErrorBanner(
                    message = (clipsState as UiState.Error).message,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        items(clips, key = { it.id }) { clip ->
            GeneratedClipResultCard(
                clip = clip,
                videoUriString = selectedJob?.videoUri,
                onEditSubtitles = {
                    editingClip = clip
                    activeSheet = ActiveClipSheet.SUBTITLES
                },
                onEditHook = {
                    editingClip = clip
                    activeSheet = ActiveClipSheet.HOOK
                },
                onTranslate = {
                    editingClip = clip
                    activeSheet = ActiveClipSheet.TRANSLATE
                },
                onPost = {
                    editingClip = clip
                    activeSheet = ActiveClipSheet.POST
                },
                modifier = Modifier.widthIn(max = 760.dp)
            )
        }
    }

    val currentClip = editingClip?.let { target -> clips.firstOrNull { it.id == target.id } ?: target }
    if (currentClip != null) {
        when (activeSheet) {
            ActiveClipSheet.SUBTITLES -> SubtitleEditorSheet(
                clip = currentClip,
                isBusy = isEditorBusy,
                onDismiss = { activeSheet = null },
                onSave = { pos, anim, font, colorHex, highlightHex, text ->
                    viewModel.saveSubtitles(currentClip, pos, anim, font, colorHex, highlightHex, text)
                    activeSheet = null
                }
            )
            ActiveClipSheet.HOOK -> HookEditorSheet(
                clip = currentClip,
                isBusy = isEditorBusy,
                onDismiss = { activeSheet = null },
                onSave = { text, pos, size, anim, dur ->
                    viewModel.saveHook(currentClip, text, pos, size, anim, dur)
                    activeSheet = null
                }
            )
            ActiveClipSheet.TRANSLATE -> TranslateDubSheet(
                clip = currentClip,
                isBusy = isEditorBusy,
                onDismiss = { activeSheet = null },
                onTranslate = { lang ->
                    viewModel.translateAndDubClip(currentClip, lang)
                    activeSheet = null
                }
            )
            ActiveClipSheet.POST -> PostOrScheduleSheet(
                clip = currentClip,
                settings = settings,
                isBusy = isEditorBusy,
                onDismiss = { activeSheet = null },
                onPublishUploadPost = { title, platforms, iso ->
                    viewModel.postClipViaUploadPost(title, platforms, iso)
                    activeSheet = null
                }
            )
            null -> Unit
        }
    }

    if (showScheduleWeek) {
        ScheduleWeekSheet(
            clips = clips,
            isBusy = isEditorBusy,
            onDismiss = { showScheduleWeek = false },
            onScheduleWeek = { platforms, date, hour ->
                viewModel.scheduleWeekViaUploadPost(clips, platforms, date, hour)
                showScheduleWeek = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratedClipResultCard(
    clip: GeneratedClip,
    videoUriString: String?,
    onEditSubtitles: () -> Unit,
    onEditHook: () -> Unit,
    onTranslate: () -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    StudioGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clip #${clip.clipIndex + 1} • ${formatSeconds(clip.startSec)}–${formatSeconds(clip.endSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = clip.youtubeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            ScoreBadge(score = clip.viralScore.toInt())
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShortClipPreviewPlayer(
                clip = clip,
                videoUriString = videoUriString,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!clip.translatedLanguage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "Localized: ${clip.translatedLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!clip.tiktokCaption.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.tiktokCaption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        copyToClipboard(context, "Caption", clip.tiktokCaption)
                        Toast.makeText(context, "Caption copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy caption")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onEditSubtitles, modifier = Modifier.testTag("edit_subtitles_button_${clip.clipIndex}")) {
                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Subtitles")
            }
            OutlinedButton(onClick = onEditHook, modifier = Modifier.testTag("edit_hook_button_${clip.clipIndex}")) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hook")
            }
            OutlinedButton(onClick = onTranslate, modifier = Modifier.testTag("translate_clip_button_${clip.clipIndex}")) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dub / Lang")
            }
            Button(onClick = onPost, modifier = Modifier.testTag("post_clip_button_${clip.clipIndex}")) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Post / Share")
            }
        }
    }
}
