package com.example.ui.captions

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.local.WORKSPACE_CLIP_STUDIO
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.UiState
import com.example.ui.clips.SavedEditingSessionsSheet
import com.example.ui.components.PublishAutomationSheet
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoFilterStackPanel
import com.example.ui.components.VideoPlayer
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareVideoUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionExportScreen(
    currentUserId: String,
    args: CaptionExportInitArgs,
    onResumeClipStudioSession: (String) -> Unit = {},
    onOpenBatchExportQueue: () -> Unit = {},
    onNavigateBack: () -> Unit,
    viewModel: CaptionExportViewModel = viewModel(
        key = "${currentUserId}_${args.initialVideoUri}_${args.clipId}_${args.clipStartMs}_${args.localSessionId}",
        factory = CaptionExportViewModel.provideFactory(currentUserId, args)
    )
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val sourceVideoUri by viewModel.sourceVideoUri.collectAsStateWithLifecycle()
    val clipStartMs by viewModel.clipStartMs.collectAsStateWithLifecycle()
    val clipEndMs by viewModel.clipEndMs.collectAsStateWithLifecycle()
    val transcriptionState by viewModel.transcriptionState.collectAsStateWithLifecycle()
    val cues by viewModel.cues.collectAsStateWithLifecycle()
    val fullTranscript by viewModel.fullTranscript.collectAsStateWithLifecycle()
    val detectedLanguage by viewModel.detectedLanguage.collectAsStateWithLifecycle()
    val audioTrackInfo by viewModel.audioTrackInfo.collectAsStateWithLifecycle()
    val burnStyle by viewModel.burnStyle.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editingSessions by viewModel.editingSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val sessionTitle by viewModel.sessionTitle.collectAsStateWithLifecycle()
    val pendingBatchExportCount by viewModel.pendingBatchExportCount.collectAsStateWithLifecycle()
    val latestExportMetadata by viewModel.latestExportMetadata.collectAsStateWithLifecycle()

    var languageHint by remember { mutableStateOf("") }
    var showPublishSheet by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showEditExportMetadataDialog by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.selectVideoUri(uri)
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    val parsedSourceUri = remember(sourceVideoUri) {
        sourceVideoUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }
    val filterEffectsKey = remember(
        burnStyle.filterStack,
        burnStyle.filterContrast,
        burnStyle.filterSaturation,
        burnStyle.filterBrightness
    ) {
        Media3VideoFilterFactory.cacheKey(burnStyle)
    }
    val activeVideoEffects = remember(filterEffectsKey) {
        Media3VideoFilterFactory.buildVideoEffects(burnStyle)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.caption_studio_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp).testTag("caption_export_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenBatchExportQueue,
                        modifier = Modifier.testTag("caption_open_batch_queue_button")
                    ) {
                        Icon(Icons.Default.QueuePlayNext, contentDescription = stringResource(R.string.cd_batch_export), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (pendingBatchExportCount > 0) "Batch ($pendingBatchExportCount)" else "Batch")
                    }
                    TextButton(
                        onClick = { showSessionsSheet = true },
                        modifier = Modifier.testTag("caption_open_sessions_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_saved_sessions), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (editingSessions.isNotEmpty()) "Drafts (${editingSessions.size})" else "Drafts")
                    }
                    IconButton(
                        onClick = { viewModel.saveLocalSession(sessionTitle) },
                        modifier = Modifier.size(48.dp).testTag("caption_quick_save_session_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.cd_save_session))
                    }
                    if (viewModel.canSaveToClip && cues.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.saveCaptionsToClip() },
                            modifier = Modifier.testTag("save_captions_to_clip_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Clip")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.caption_studio_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    StudioGlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Source Video & Overlay Preview", style = MaterialTheme.typography.titleLarge)
                                if (clipStartMs > 0L || clipEndMs != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "Segment ${formatMsShort(clipStartMs)}–${formatMsShort(clipEndMs ?: clipStartMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("caption_pick_video_button")
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = stringResource(R.string.cd_pick_video))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = parsedSourceUri?.lastPathSegment ?: "Select Video File (Photo Picker)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (parsedSourceUri != null) {
                                VideoPlayer(
                                    uri = parsedSourceUri,
                                    autoplay = false,
                                    repeat = true,
                                    trimEnabled = true,
                                    trimStartMs = clipStartMs,
                                    trimEndMs = clipEndMs,
                                    onTrimRangeChange = { sMs, eMs -> viewModel.updateTrimRange(sMs, eMs) },
                                    videoEffects = activeVideoEffects,
                                    videoEffectsKey = filterEffectsKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    overlayContent = {
                                        LiveCaptionStyleOverlayPreview(
                                            sampleCue = cues.firstOrNull(),
                                            hookText = burnStyle.hookText,
                                            position = burnStyle.position,
                                            animation = burnStyle.animation,
                                            fontName = burnStyle.fontName,
                                            textColorHex = burnStyle.textColorHex,
                                            highlightColorHex = burnStyle.highlightColorHex,
                                            hookPosition = burnStyle.hookPosition
                                        )
                                    }
                                )
                            }
                        }
                    }

                    StudioGlassCard {
                        VideoFilterStackPanel(
                            filterStack = burnStyle.filterStack,
                            onFilterStackChange = { stack ->
                                viewModel.updateStyle { it.copy(filterStack = stack) }
                            },
                            contrastAdjust = burnStyle.filterContrast,
                            saturationAdjust = burnStyle.filterSaturation,
                            brightnessAdjust = burnStyle.filterBrightness,
                            onToneAdjustChange = { c, s, b ->
                                viewModel.updateStyle {
                                    it.copy(filterContrast = c, filterSaturation = s, filterBrightness = b)
                                }
                            },
                            compact = false,
                            testTagPrefix = "caption_video_filter"
                        )
                    }
                }
            }

            item {
                TranscribeAudioCard(
                    hasVideoSource = parsedSourceUri != null,
                    languageHint = languageHint,
                    onLanguageHintChange = { languageHint = it },
                    transcriptionState = transcriptionState,
                    detectedLanguage = detectedLanguage,
                    audioTrackInfo = audioTrackInfo,
                    fullTranscript = fullTranscript,
                    hasCues = cues.isNotEmpty(),
                    onTranscribe = { viewModel.transcribeAudioWithGemini(languageHint) },
                    onTranscriptChange = { viewModel.updateFullTranscriptText(it) },
                    onSplitCues = { viewModel.rebuildCuesFromTranscript() },
                    onCopySrt = {
                        val srt = viewModel.currentSrtString()
                        if (srt.isNotBlank()) {
                            copyToClipboard(context, "SRT Captions", srt)
                            Toast.makeText(context, "Copied .SRT subtitles", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }

            if (cues.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Timed Overlay Cues (${cues.size})", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(
                            onClick = { viewModel.addCue() },
                            modifier = Modifier.height(48.dp).testTag("add_caption_cue_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_caption_cue))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Cue")
                        }
                    }
                }

                items(cues, key = { "${it.index}_${it.startMs}" }) { cue ->
                    TimedCaptionCueCard(
                        cue = cue,
                        onUpdate = { text, highlight -> viewModel.updateCue(cue.index, text, highlight) },
                        onDelete = { viewModel.deleteCue(cue.index) },
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }
            }

            item {
                OverlayAppearanceStyleCard(
                    burnStyle = burnStyle,
                    onUpdateStyle = { updater -> viewModel.updateStyle(updater) },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }

            item {
                BurnAndExportActionCard(
                    hasVideoSource = parsedSourceUri != null,
                    hasCues = cues.isNotEmpty(),
                    burnStyle = burnStyle,
                    exportState = exportState,
                    exportProgress = exportProgress,
                    pendingBatchExportCount = pendingBatchExportCount,
                    onBurnAndExport = { viewModel.burnCaptionsAndExportMp4() },
                    onAddToBatch = { viewModel.addCurrentToBatchQueue(startImmediately = false) },
                    onOpenBatchQueue = onOpenBatchExportQueue,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }

            val exported = (exportState as? UiState.Success)?.data
            if (exported != null) {
                item {
                    ExportedVideoResultCard(
                        result = exported,
                        metadata = latestExportMetadata,
                        onEditMetadata = { showEditExportMetadataDialog = true },
                        onSaveToGallery = { viewModel.saveExportToDeviceGallery() },
                        onShareVideo = {
                            shareVideoUri(
                                context = context,
                                videoUriString = exported.contentUri,
                                title = latestExportMetadata?.title ?: args.initialTitle.ifBlank { "OpenShorts Captioned Short" },
                                caption = latestExportMetadata?.description ?: fullTranscript
                            )
                        },
                        onOpenAutoPost = { showPublishSheet = true },
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }
            }
        }
    }

    val exportedResult = (exportState as? UiState.Success)?.data
    if (showPublishSheet && exportedResult != null) {
        PublishAutomationSheet(
            sheetTitle = "Auto-Post Captioned MP4",
            initialYoutubeTitle = args.initialTitle.ifBlank { "Captioned Viral Short #Shorts" },
            initialYoutubeDescription = fullTranscript,
            initialInstagramCaption = "${burnStyle.hookText.ifBlank { args.initialTitle }}\n\n#reels #shorts #viral",
            initialVideoUri = exportedResult.contentUri,
            settings = settings,
            isBusy = false,
            onDismiss = { showPublishSheet = false },
            onAiOptimize = { title, ytDesc, igCap -> viewModel.optimizePublishMetadata(title, ytDesc, igCap) },
            onSubmitAutomation = { req ->
                viewModel.automateExportPublish(req)
                showPublishSheet = false
            }
        )
    }

    if (showSessionsSheet) {
        val suggestedTitle = sessionTitle.ifBlank {
            parsedSourceUri?.lastPathSegment?.let { "Caption • $it" } ?: "Caption & Burn Project"
        }
        SavedEditingSessionsSheet(
            sessions = editingSessions,
            currentSessionTitleSuggestion = suggestedTitle,
            activeSessionId = activeSessionId,
            onSaveCurrentSession = { title, saveAsNew ->
                viewModel.saveLocalSession(title, saveAsNew)
                showSessionsSheet = false
            },
            onResumeSession = { session ->
                showSessionsSheet = false
                if (session.workspaceType == WORKSPACE_CLIP_STUDIO) {
                    onResumeClipStudioSession(session.id)
                } else {
                    viewModel.resumeLocalSession(session)
                }
            },
            onDeleteSession = { sessionId -> viewModel.deleteLocalSession(sessionId) },
            onDismiss = { showSessionsSheet = false }
        )
    }

    val currentExportMeta = latestExportMetadata
    if (showEditExportMetadataDialog && currentExportMeta != null) {
        EditExportMetadataDialog(
            metadata = currentExportMeta,
            onDismiss = { showEditExportMetadataDialog = false },
            onSave = { title, desc ->
                viewModel.updateLatestExportMetadata(title, desc)
                showEditExportMetadataDialog = false
            }
        )
    }
}
