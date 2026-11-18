package com.example.ui.clips

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.EditingSession
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.GeneratedClip
import com.example.data.model.UiState
import com.example.data.model.YouTubeSearchResult
import com.example.ui.captions.CaptionExportInitArgs
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard

@Composable
fun ClipGeneratorScreen(
    currentUserId: String,
    pendingResumeSessionId: String? = null,
    onConsumedPendingResume: () -> Unit = {},
    onOpenAutomationQueue: () -> Unit = {},
    onOpenBatchExportQueue: () -> Unit = {},
    onOpenCaptionExport: (CaptionExportInitArgs) -> Unit = {},
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
    val pendingAutomationCount by viewModel.pendingAutomationCount.collectAsStateWithLifecycle()
    val pendingBatchExportCount by viewModel.pendingBatchExportCount.collectAsStateWithLifecycle()
    val youtubeSearchState by viewModel.youtubeSearchState.collectAsStateWithLifecycle()
    val trimChatMessages by viewModel.trimChatMessages.collectAsStateWithLifecycle()
    val isTrimChatBusy by viewModel.isTrimChatBusy.collectAsStateWithLifecycle()
    val editingSessions by viewModel.editingSessions.collectAsStateWithLifecycle()
    val activeLocalSessionId by viewModel.activeLocalSessionId.collectAsStateWithLifecycle()

    var sourceMode by remember { mutableStateOf("video") }
    var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var sourceTrimStartMs by remember { mutableLongStateOf(0L) }
    var sourceTrimEndMs by remember { mutableLongStateOf(60_000L) }
    var sourceFilterStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var sourceFilterContrast by remember { mutableFloatStateOf(0f) }
    var sourceFilterSaturation by remember { mutableFloatStateOf(0f) }
    var sourceFilterBrightness by remember { mutableFloatStateOf(0f) }
    val sourceFilterKey = remember(
        sourceFilterStack,
        sourceFilterContrast,
        sourceFilterSaturation,
        sourceFilterBrightness
    ) {
        Media3VideoFilterFactory.cacheKey(
            sourceFilterStack,
            sourceFilterContrast,
            sourceFilterSaturation,
            sourceFilterBrightness
        )
    }
    val sourceVideoEffects = remember(sourceFilterKey) {
        Media3VideoFilterFactory.buildVideoEffects(
            sourceFilterStack,
            sourceFilterContrast,
            sourceFilterSaturation,
            sourceFilterBrightness
        )
    }

    var urlInput by remember { mutableStateOf("") }
    var selectedYoutubeVideo by remember { mutableStateOf<YouTubeSearchResult?>(null) }
    var urlTrimStartSec by remember { mutableDoubleStateOf(0.0) }
    var urlTrimEndSec by remember { mutableDoubleStateOf(30.0) }

    var liveStreamUrlInput by remember { mutableStateOf("") }
    var liveLookbackMinutes by remember { mutableIntStateOf(15) }
    var selectedLiveSkill by remember { mutableStateOf(DEFAULT_LIVE_SHORTS_SKILLS.first()) }
    var liveCueNotes by remember { mutableStateOf("") }

    var guidanceInput by remember { mutableStateOf("") }
    var maxClips by remember { mutableFloatStateOf(4f) }

    var editingClip by remember { mutableStateOf<GeneratedClip?>(null) }
    var activeSheet by remember { mutableStateOf<ActiveClipSheet?>(null) }
    var showScheduleWeek by remember { mutableStateOf(false) }

    var showYoutubeSearchSheet by remember { mutableStateOf(false) }
    var searchLiveOnly by remember { mutableStateOf(false) }
    var showSourceTrimChatSheet by remember { mutableStateOf(false) }
    var showSavedSessionsSheet by remember { mutableStateOf(false) }

    fun applyClipStudioSession(session: EditingSession) {
        sourceMode = session.sourceMode.ifBlank { "video" }
        pickedVideoUri = session.videoUri?.takeIf { it.isNotBlank() && session.sourceMode == "video" }
            ?.let { Uri.parse(it) }
        sourceTrimStartMs = session.trimStartMs.coerceAtLeast(0L)
        sourceTrimEndMs = session.trimEndMs.coerceAtLeast(session.trimStartMs + 1000L)

        when (session.sourceMode) {
            "url" -> {
                urlInput = session.sourceLabel
                urlTrimStartSec = session.trimStartMs / 1000.0
                urlTrimEndSec = (session.trimEndMs / 1000.0).coerceAtLeast(urlTrimStartSec + 1.0)
                selectedYoutubeVideo = buildRestoredSessionVideo(session, liveBroadcast = false)
                guidanceInput = session.guidanceNotes
            }
            "live" -> {
                liveStreamUrlInput = session.sourceLabel
                liveLookbackMinutes = session.liveLookbackMinutes.coerceIn(5, 60)
                selectedLiveSkill = DEFAULT_LIVE_SHORTS_SKILLS.firstOrNull { it.id == session.liveSkillId }
                    ?: DEFAULT_LIVE_SHORTS_SKILLS.first()
                liveCueNotes = session.guidanceNotes
                selectedYoutubeVideo = buildRestoredSessionVideo(session, liveBroadcast = true)
            }
            else -> guidanceInput = session.guidanceNotes
        }

        maxClips = session.targetClipsCount.coerceIn(3, 8).toFloat()
        sourceFilterStack = session.burnStyle.filterStack
        sourceFilterContrast = session.burnStyle.filterContrast
        sourceFilterSaturation = session.burnStyle.filterSaturation
        sourceFilterBrightness = session.burnStyle.filterBrightness
        viewModel.markLocalSessionResumed(session)
    }

    LaunchedEffect(pendingResumeSessionId) {
        val resumeId = pendingResumeSessionId
        if (!resumeId.isNullOrBlank()) {
            viewModel.getLocalSessionById(resumeId)?.let(::applyClipStudioSession)
            onConsumedPendingResume()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedVideoUri = uri
            sourceTrimStartMs = 0L
            sourceTrimEndMs = 60_000L
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
                ClipGeneratorHeaderSection(
                    editingSessionsCount = editingSessions.size,
                    pendingAutomationCount = pendingAutomationCount,
                    pendingBatchExportCount = pendingBatchExportCount,
                    onOpenSavedSessions = { showSavedSessionsSheet = true },
                    onOpenAutomationQueue = onOpenAutomationQueue,
                    onOpenCaptionExport = {
                        onOpenCaptionExport(
                            buildCaptionExportArgsForSource(
                                pickedVideoUri = pickedVideoUri,
                                fallbackVideoUri = selectedJob?.videoUri,
                                sourceTrimStartMs = sourceTrimStartMs,
                                sourceTrimEndMs = sourceTrimEndMs,
                                filterStack = sourceFilterStack,
                                filterContrast = sourceFilterContrast,
                                filterSaturation = sourceFilterSaturation,
                                filterBrightness = sourceFilterBrightness
                            )
                        )
                    },
                    onOpenBatchExportQueue = onOpenBatchExportQueue
                )

                ClipSourceInputCard(
                    sourceMode = sourceMode,
                    onSourceModeChange = { sourceMode = it },
                    pickedVideoUri = pickedVideoUri,
                    onPickVideo = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    sourceTrimStartMs = sourceTrimStartMs,
                    sourceTrimEndMs = sourceTrimEndMs,
                    onSourceTrimChange = { s, e ->
                        sourceTrimStartMs = s
                        sourceTrimEndMs = e
                    },
                    sourceFilterStack = sourceFilterStack,
                    sourceFilterContrast = sourceFilterContrast,
                    sourceFilterSaturation = sourceFilterSaturation,
                    sourceFilterBrightness = sourceFilterBrightness,
                    sourceVideoEffects = sourceVideoEffects,
                    sourceFilterKey = sourceFilterKey,
                    onFilterStackChange = { sourceFilterStack = it },
                    onToneAdjustChange = { c, s, b ->
                        sourceFilterContrast = c
                        sourceFilterSaturation = s
                        sourceFilterBrightness = b
                    },
                    onOpenSourceTrimChat = {
                        viewModel.resetTrimChat()
                        showSourceTrimChatSheet = true
                    },
                    onAddSourceTrimToBatch = {
                        val uri = pickedVideoUri ?: return@ClipSourceInputCard
                        viewModel.addTrimmedClipToBatch(
                            title = uri.lastPathSegment ?: "Trimmed Clip",
                            videoUriString = uri.toString(),
                            trimStartMs = sourceTrimStartMs,
                            trimEndMs = sourceTrimEndMs,
                            filterStack = sourceFilterStack,
                            filterContrast = sourceFilterContrast,
                            filterSaturation = sourceFilterSaturation,
                            filterBrightness = sourceFilterBrightness
                        )
                    },
                    urlInput = urlInput,
                    onUrlInputChange = {
                        urlInput = it
                        viewModel.clearGenerationError()
                    },
                    onOpenYoutubeSearch = { liveOnly ->
                        searchLiveOnly = liveOnly
                        showYoutubeSearchSheet = true
                    },
                    selectedYoutubeVideo = selectedYoutubeVideo,
                    urlTrimStartSec = urlTrimStartSec,
                    urlTrimEndSec = urlTrimEndSec,
                    onOpenYoutubeTrimChat = {
                        viewModel.resetTrimChat()
                        showSourceTrimChatSheet = true
                    },
                    liveStreamUrlInput = liveStreamUrlInput,
                    onLiveStreamUrlChange = {
                        liveStreamUrlInput = it
                        viewModel.clearGenerationError()
                    },
                    selectedLiveSkill = selectedLiveSkill,
                    onSelectLiveSkill = { selectedLiveSkill = it },
                    liveLookbackMinutes = liveLookbackMinutes,
                    onLiveLookbackChange = { liveLookbackMinutes = it },
                    liveCueNotes = liveCueNotes,
                    onLiveCueNotesChange = { liveCueNotes = it },
                    guidanceInput = guidanceInput,
                    onGuidanceInputChange = { guidanceInput = it },
                    maxClips = maxClips,
                    onMaxClipsChange = { maxClips = it },
                    generationState = generationState,
                    onGenerateClick = {
                        if (sourceMode == "live") {
                            viewModel.analyzeLiveStreamForShorts(
                                liveStreamUrl = liveStreamUrlInput.trim(),
                                lookbackMinutes = liveLookbackMinutes,
                                skillPreset = selectedLiveSkill,
                                liveCueNotes = liveCueNotes,
                                maxClips = maxClips.toInt()
                            )
                        } else {
                            val label = if (sourceMode == "video") pickedVideoUri?.lastPathSegment ?: "" else urlInput.trim()
                            val combinedGuidance = buildCombinedClipGuidance(
                                sourceMode, selectedYoutubeVideo, urlTrimStartSec, urlTrimEndSec,
                                sourceTrimStartMs, sourceTrimEndMs, guidanceInput
                            )
                            viewModel.analyzeAndGenerateClips(
                                sourceType = sourceMode,
                                sourceLabel = label,
                                videoUri = if (sourceMode == "video") pickedVideoUri else null,
                                clipGuidance = combinedGuidance,
                                maxClips = maxClips.toInt()
                            )
                        }
                    }
                )
            }
        }

        if (generationState is UiState.Loading) {
            item {
                ScannerProcessingCard(
                    title = if (sourceMode == "live") "Live Shorts Skill Active (${selectedLiveSkill.label})" else "Gemini 3.8 Flash Analyzing Frames",
                    subtitle = if (sourceMode == "live") "Scanning last ${liveLookbackMinutes}m live DVR window for viral 9:16 moments..." else "Scoring hooks, pacing, and 9:16 vertical cuts...",
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        if (generationState is UiState.Error) {
            item { ErrorBanner((generationState as UiState.Error).message, modifier = Modifier.widthIn(max = 760.dp)) }
        }
        if (jobsState is UiState.Error) {
            item { ErrorBanner((jobsState as UiState.Error).message, modifier = Modifier.widthIn(max = 760.dp)) }
        }

        if (jobs.isNotEmpty()) {
            item {
                RecentClipProjectsSection(
                    jobs = jobs,
                    selectedJobId = selectedJobId,
                    selectedJob = selectedJob,
                    clips = clips,
                    onSelectJob = viewModel::selectJob,
                    onDeleteJob = viewModel::deleteJob,
                    onBatchAllClips = {
                        viewModel.addAllClipsToBatch(
                            clips = clips,
                            videoUriString = selectedJob?.videoUri,
                            filterStack = sourceFilterStack,
                            filterContrast = sourceFilterContrast,
                            filterSaturation = sourceFilterSaturation,
                            filterBrightness = sourceFilterBrightness,
                            startImmediately = false
                        )
                    },
                    onScheduleWeek = { showScheduleWeek = true },
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                )
            }
        }

        if (selectedJob != null && !selectedJob.summary.isNullOrBlank()) {
            item { ClipJobSummaryCard(job = selectedJob, modifier = Modifier.widthIn(max = 760.dp)) }
        }

        if (clipsState is UiState.Error) {
            item { ErrorBanner((clipsState as UiState.Error).message, modifier = Modifier.widthIn(max = 760.dp)) }
        }

        items(clips, key = { it.id }) { clip ->
            GeneratedClipResultCard(
                clip = clip,
                videoUriString = selectedJob?.videoUri,
                onTrimAndChat = {
                    viewModel.resetTrimChat()
                    editingClip = clip
                    activeSheet = ActiveClipSheet.TRIM_CHAT
                },
                onEditSubtitles = { editingClip = clip; activeSheet = ActiveClipSheet.SUBTITLES },
                onEditHook = { editingClip = clip; activeSheet = ActiveClipSheet.HOOK },
                onTranslate = { editingClip = clip; activeSheet = ActiveClipSheet.TRANSLATE },
                onPost = { editingClip = clip; activeSheet = ActiveClipSheet.POST },
                onBurnCaptions = {
                    onOpenCaptionExport(
                        buildCaptionExportArgsForClip(
                            clip = clip,
                            videoUri = selectedJob?.videoUri.orEmpty(),
                            filterStack = sourceFilterStack,
                            filterContrast = sourceFilterContrast,
                            filterSaturation = sourceFilterSaturation,
                            filterBrightness = sourceFilterBrightness
                        )
                    )
                },
                onAddToBatch = {
                    viewModel.addTrimmedClipToBatch(
                        title = clip.youtubeTitle,
                        videoUriString = selectedJob?.videoUri.orEmpty(),
                        trimStartMs = (clip.startSec * 1000.0).toLong().coerceAtLeast(0L),
                        trimEndMs = (clip.endSec * 1000.0).toLong().coerceAtLeast(1000L),
                        clip = clip,
                        filterStack = sourceFilterStack,
                        filterContrast = sourceFilterContrast,
                        filterSaturation = sourceFilterSaturation,
                        filterBrightness = sourceFilterBrightness
                    )
                },
                modifier = Modifier.widthIn(max = 760.dp)
            )
        }
    }

    val currentClip = editingClip?.let { target -> clips.firstOrNull { it.id == target.id } ?: target }
    ClipEditorModalHost(
        activeSheet = activeSheet,
        currentClip = currentClip,
        selectedJob = selectedJob,
        selectedYoutubeVideo = selectedYoutubeVideo,
        trimChatMessages = trimChatMessages,
        isTrimChatBusy = isTrimChatBusy,
        isEditorBusy = isEditorBusy,
        settings = settings,
        sourceFilterStack = sourceFilterStack,
        sourceFilterContrast = sourceFilterContrast,
        sourceFilterSaturation = sourceFilterSaturation,
        sourceFilterBrightness = sourceFilterBrightness,
        onFiltersChanged = { stack, c, s, b ->
            sourceFilterStack = stack
            sourceFilterContrast = c
            sourceFilterSaturation = s
            sourceFilterBrightness = b
        },
        viewModel = viewModel,
        onOpenCaptionExport = onOpenCaptionExport,
        onOpenYouTubeSearch = { liveOnly ->
            searchLiveOnly = liveOnly
            showYoutubeSearchSheet = true
        },
        onDismissSheet = { activeSheet = null }
    )

    ClipUtilityModalsHost(
        showSourceTrimChatSheet = showSourceTrimChatSheet,
        showYoutubeSearchSheet = showYoutubeSearchSheet,
        showScheduleWeek = showScheduleWeek,
        showSavedSessionsSheet = showSavedSessionsSheet,
        sourceMode = sourceMode,
        pickedVideoUri = pickedVideoUri,
        urlInput = urlInput,
        liveStreamUrlInput = liveStreamUrlInput,
        selectedYoutubeVideo = selectedYoutubeVideo,
        sourceTrimStartMs = sourceTrimStartMs,
        sourceTrimEndMs = sourceTrimEndMs,
        urlTrimStartSec = urlTrimStartSec,
        urlTrimEndSec = urlTrimEndSec,
        guidanceInput = guidanceInput,
        liveCueNotes = liveCueNotes,
        selectedLiveSkill = selectedLiveSkill,
        liveLookbackMinutes = liveLookbackMinutes,
        maxClips = maxClips,
        sourceFilterStack = sourceFilterStack,
        sourceFilterContrast = sourceFilterContrast,
        sourceFilterSaturation = sourceFilterSaturation,
        sourceFilterBrightness = sourceFilterBrightness,
        onFiltersChanged = { stack, c, s, b ->
            sourceFilterStack = stack
            sourceFilterContrast = c
            sourceFilterSaturation = s
            sourceFilterBrightness = b
        },
        searchLiveOnly = searchLiveOnly,
        youtubeSearchState = youtubeSearchState,
        trimChatMessages = trimChatMessages,
        isTrimChatBusy = isTrimChatBusy,
        isEditorBusy = isEditorBusy,
        clips = clips,
        selectedJob = selectedJob,
        selectedJobId = selectedJobId,
        editingSessions = editingSessions,
        activeLocalSessionId = activeLocalSessionId,
        viewModel = viewModel,
        onApplyUrlTrim = { s, e -> urlTrimStartSec = s; urlTrimEndSec = e },
        onApplySourceTrim = { s, e -> sourceTrimStartMs = s; sourceTrimEndMs = e },
        onSelectYoutubeVideo = { video ->
            selectedYoutubeVideo = video
            if (video.liveBroadcast || sourceMode == "live") {
                liveStreamUrlInput = video.url
                sourceMode = "live"
            } else {
                urlInput = video.url
                sourceMode = "url"
                urlTrimStartSec = 0.0
                urlTrimEndSec = video.durationSec.coerceAtMost(45.0).coerceAtLeast(15.0)
            }
            viewModel.clearGenerationError()
            showYoutubeSearchSheet = false
            viewModel.resetTrimChat()
            showSourceTrimChatSheet = true
        },
        onResumeClipStudioSession = ::applyClipStudioSession,
        onOpenCaptionExport = onOpenCaptionExport,
        onOpenYoutubeSearchFromTrim = { liveOnly ->
            searchLiveOnly = liveOnly
            showYoutubeSearchSheet = true
        },
        onDismissSourceTrim = { showSourceTrimChatSheet = false },
        onDismissYoutubeSearch = { showYoutubeSearchSheet = false },
        onDismissScheduleWeek = { showScheduleWeek = false },
        onDismissSavedSessions = { showSavedSessionsSheet = false }
    )
}
