package com.example.ui.captions

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.example.R
import com.example.data.ai.OpenShortsAiRepository
import com.example.data.automation.PublishAutomationScheduler
import com.example.data.caption.CaptionBurnInExportService
import com.example.data.caption.GeminiCaptionTranscriptionService
import com.example.data.export.BatchExportManager
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.data.local.EditedVideoExportSource
import com.example.data.local.EditedVideoMetadata
import com.example.data.local.EditedVideoMetadataRepository
import com.example.data.local.EditingSession
import com.example.data.local.EditingSessionStorageManager
import com.example.data.local.WORKSPACE_CAPTION_EXPORT
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.CaptionExportResult
import com.example.data.model.IntegrationSettings
import com.example.data.model.PublishAutomationJob
import com.example.data.model.PublishMetadataDraft
import com.example.data.model.TimedCaptionCue
import com.example.data.model.UiState
import com.example.data.model.VideoTranscriptionResult
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
import com.example.ui.components.PublishAutomationRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CaptionExportVM"

data class CaptionExportInitArgs(
    val initialVideoUri: String = "",
    val jobId: String = "",
    val clipId: String = "",
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = -1L,
    val initialTranscript: String = "",
    val initialTitle: String = "",
    val initialHook: String = "",
    val subtitlePosition: String = "bottom",
    val subtitleAnimation: String = "word-highlight",
    val subtitleFont: String = "SpaceGrotesk",
    val subtitleColorHex: String = "#FFFFFF",
    val subtitleHighlightHex: String = "#3B82F6",
    val initialFilterStackCsv: String = "",
    val filterContrast: Float = 0f,
    val filterSaturation: Float = 0f,
    val filterBrightness: Float = 0f,
    val localSessionId: String = ""
)

@OptIn(UnstableApi::class)
class CaptionExportViewModel(
    private val app: Application,
    private val currentUserId: String,
    private val args: CaptionExportInitArgs,
    private val transcriptionService: GeminiCaptionTranscriptionService,
    private val exportService: CaptionBurnInExportService,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val settingsStore: SettingsDataStore,
    private val scheduler: PublishAutomationScheduler,
    private val sessionStorageManager: EditingSessionStorageManager = EditingSessionStorageManager(app),
    private val batchExportManager: BatchExportManager = BatchExportManager(app),
    private val editedVideoMetadataRepo: EditedVideoMetadataRepository = EditedVideoMetadataRepository(app)
) : ViewModel() {

    private val _sourceVideoUri = MutableStateFlow(args.initialVideoUri)
    val sourceVideoUri: StateFlow<String> = _sourceVideoUri.asStateFlow()

    private val _clipStartMs = MutableStateFlow(args.clipStartMs.coerceAtLeast(0L))
    val clipStartMs: StateFlow<Long> = _clipStartMs.asStateFlow()

    private val _clipEndMs = MutableStateFlow(args.clipEndMs.takeIf { it > args.clipStartMs })
    val clipEndMs: StateFlow<Long?> = _clipEndMs.asStateFlow()

    private val _transcriptionState = MutableStateFlow<UiState<VideoTranscriptionResult>>(UiState.Idle)
    val transcriptionState: StateFlow<UiState<VideoTranscriptionResult>> = _transcriptionState.asStateFlow()

    private val _cues = MutableStateFlow(
        if (args.initialTranscript.isNotBlank()) {
            val durMs = if (args.clipEndMs > args.clipStartMs) args.clipEndMs - args.clipStartMs else 18_000L
            GeminiCaptionTranscriptionService.buildOverlayCuesFromTranscript(args.initialTranscript, durMs)
        } else emptyList()
    )
    val cues: StateFlow<List<TimedCaptionCue>> = _cues.asStateFlow()

    private val _fullTranscript = MutableStateFlow(args.initialTranscript)
    val fullTranscript: StateFlow<String> = _fullTranscript.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val _audioTrackInfo = MutableStateFlow("")
    val audioTrackInfo: StateFlow<String> = _audioTrackInfo.asStateFlow()

    private val _burnStyle = MutableStateFlow(
        CaptionBurnStyle(
            position = args.subtitlePosition,
            animation = args.subtitleAnimation,
            fontName = args.subtitleFont,
            textColorHex = args.subtitleColorHex,
            highlightColorHex = args.subtitleHighlightHex,
            hookText = args.initialHook,
            filterStack = Media3VideoFilterFactory.parseCsv(args.initialFilterStackCsv),
            filterContrast = args.filterContrast,
            filterSaturation = args.filterSaturation,
            filterBrightness = args.filterBrightness
        )
    )
    val burnStyle: StateFlow<CaptionBurnStyle> = _burnStyle.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportState = MutableStateFlow<UiState<CaptionExportResult>>(UiState.Idle)
    val exportState: StateFlow<UiState<CaptionExportResult>> = _exportState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(args.localSessionId.takeIf { it.isNotBlank() })
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _sessionTitle = MutableStateFlow(args.initialTitle)
    val sessionTitle: StateFlow<String> = _sessionTitle.asStateFlow()

    private val _latestExportMetadata = MutableStateFlow<EditedVideoMetadata?>(null)
    val latestExportMetadata: StateFlow<EditedVideoMetadata?> = _latestExportMetadata.asStateFlow()

    val editingSessions: StateFlow<List<EditingSession>> = sessionStorageManager.observeSessions(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val pendingBatchExportCount: StateFlow<Int> = batchExportManager.observeQueue(currentUserId)
        .map { list ->
            list.count {
                it.status == BatchExportStatus.QUEUED ||
                    it.status == BatchExportStatus.PROCESSING ||
                    it.uploadStatus == CloudUploadStatus.UPLOADING ||
                    it.uploadStatus == CloudUploadStatus.QUEUED
            }
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    val canSaveToClip: Boolean = args.jobId.isNotBlank() && args.clipId.isNotBlank()

    init {
        if (args.localSessionId.isNotBlank()) {
            viewModelScope.launch {
                sessionStorageManager.getSession(currentUserId, args.localSessionId)
                    .onSuccess { session ->
                        if (session != null) {
                            applyLocalSessionState(session)
                            _feedbackMessage.value = "Resumed \"${session.title}\"."
                        }
                    }
            }
        }
    }

    private fun applyLocalSessionState(session: EditingSession) {
        _activeSessionId.value = session.id
        _sessionTitle.value = session.title
        _sourceVideoUri.value = session.videoUri.orEmpty()
        _clipStartMs.value = session.trimStartMs.coerceAtLeast(0L)
        _clipEndMs.value = session.trimEndMs.takeIf { it > session.trimStartMs }
        _fullTranscript.value = session.transcriptText
        _detectedLanguage.value = session.detectedLanguage
        _cues.value = session.cues
        _burnStyle.value = session.burnStyle
        _transcriptionState.value = UiState.Idle
        _exportState.value = UiState.Idle
        _exportProgress.value = 0f
    }

    fun resumeLocalSession(session: EditingSession) {
        applyLocalSessionState(session)
        _feedbackMessage.value = "Resumed editing session \"${session.title}\"."
    }

    fun saveLocalSession(customTitle: String, saveAsNew: Boolean = false) {
        val uriString = _sourceVideoUri.value
        val sourceLabel = uriString.takeIf { it.isNotBlank() }?.let {
            try {
                Uri.parse(it).lastPathSegment ?: it
            } catch (_: Exception) {
                it
            }
        } ?: "Caption Draft"

        val resolvedTitle = customTitle.trim().ifBlank {
            _sessionTitle.value.ifBlank {
                args.initialTitle.ifBlank { "Captioned Edit • $sourceLabel" }
            }
        }
        val targetId = if (saveAsNew) "" else _activeSessionId.value.orEmpty()

        viewModelScope.launch {
            val session = EditingSession(
                id = targetId,
                userId = currentUserId,
                title = resolvedTitle,
                workspaceType = WORKSPACE_CAPTION_EXPORT,
                sourceMode = "video",
                sourceLabel = sourceLabel,
                videoUri = uriString.takeIf { it.isNotBlank() },
                trimStartMs = _clipStartMs.value,
                trimEndMs = _clipEndMs.value ?: (_clipStartMs.value + 30_000L),
                activeJobId = args.jobId.takeIf { it.isNotBlank() },
                activeClipId = args.clipId.takeIf { it.isNotBlank() },
                transcriptText = _fullTranscript.value,
                detectedLanguage = _detectedLanguage.value,
                cues = _cues.value,
                burnStyle = _burnStyle.value
            )
            sessionStorageManager.saveSession(session).fold(
                onSuccess = { saved ->
                    _activeSessionId.value = saved.id
                    _sessionTitle.value = saved.title
                    _feedbackMessage.value = "Saved session \"${saved.title}\" locally."
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to save session locally."
                }
            )
        }
    }

    fun deleteLocalSession(sessionId: String) {
        viewModelScope.launch {
            sessionStorageManager.deleteSession(currentUserId, sessionId).fold(
                onSuccess = {
                    if (_activeSessionId.value == sessionId) {
                        _activeSessionId.value = null
                    }
                    _feedbackMessage.value = "Deleted saved session."
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to delete session."
                }
            )
        }
    }

    fun addCurrentToBatchQueue(startImmediately: Boolean = false) {
        val uriString = _sourceVideoUri.value
        if (uriString.isBlank()) {
            _feedbackMessage.value = "Select a video file first before adding to batch."
            return
        }
        val title = _sessionTitle.value.ifBlank {
            args.initialTitle.ifBlank {
                try {
                    Uri.parse(uriString).lastPathSegment ?: "Captioned Clip"
                } catch (_: Exception) {
                    "Captioned Clip"
                }
            }
        }
        viewModelScope.launch {
            batchExportManager.enqueueClip(
                userId = currentUserId,
                title = title,
                sourceVideoUri = uriString,
                trimStartMs = _clipStartMs.value,
                trimEndMs = _clipEndMs.value ?: -1L,
                cues = _cues.value,
                burnStyle = _burnStyle.value,
                saveToGallery = true,
                startImmediately = startImmediately
            ).fold(
                onSuccess = { added ->
                    _feedbackMessage.value = if (startImmediately) {
                        "Queued \"${added.title}\" & started sequential batch."
                    } else {
                        "Added \"${added.title}\" to Batch Export Queue."
                    }
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to add to export batch."
                }
            )
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun selectVideoUri(uri: Uri) {
        _sourceVideoUri.value = uri.toString()
        _clipStartMs.value = 0L
        _clipEndMs.value = null
        _transcriptionState.value = UiState.Idle
        _exportState.value = UiState.Idle
        _exportProgress.value = 0f
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 1000L)
        _clipStartMs.value = safeStart
        _clipEndMs.value = safeEnd
    }

    fun transcribeAudioWithGemini(targetLanguageHint: String = "") {
        val uriString = _sourceVideoUri.value
        if (uriString.isBlank()) {
            _transcriptionState.value = UiState.Error("Select a video file first to transcribe its audio.")
            return
        }
        val uri = try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            _transcriptionState.value = UiState.Error("Invalid video URI.")
            return
        }

        viewModelScope.launch {
            _transcriptionState.value = UiState.Loading
            val res = transcriptionService.transcribeVideoAudio(
                videoUri = uri,
                clipStartMs = _clipStartMs.value,
                clipEndMs = _clipEndMs.value,
                targetLanguageHint = targetLanguageHint.trim()
            )
            res.fold(
                onSuccess = { result ->
                    _cues.value = result.cues
                    _fullTranscript.value = result.fullTranscript
                    _detectedLanguage.value = result.detectedLanguage
                    _audioTrackInfo.value = result.audioTrackInfo
                    _transcriptionState.value = UiState.Success(result)
                    _feedbackMessage.value = "Transcribed ${result.cues.size} timed overlay caption cues."
                },
                onFailure = { error ->
                    Log.e(TAG, "Transcription failed", error)
                    _transcriptionState.value = UiState.Error(
                        error.localizedMessage ?: "Gemini audio transcription failed."
                    )
                }
            )
        }
    }

    fun updateFullTranscriptText(text: String) {
        _fullTranscript.value = text
    }

    fun rebuildCuesFromTranscript() {
        val text = _fullTranscript.value.trim()
        if (text.isBlank()) {
            _feedbackMessage.value = "Transcript is empty."
            return
        }
        viewModelScope.launch {
            val durMs = resolveActiveDurationMs()
            _cues.value = GeminiCaptionTranscriptionService.buildOverlayCuesFromTranscript(text, durMs)
            _feedbackMessage.value = "Generated ${_cues.value.size} timed overlay cues from transcript."
        }
    }

    fun updateCue(index: Int, newText: String, newHighlight: String) {
        _cues.value = _cues.value.map { cue ->
            if (cue.index == index) {
                cue.copy(text = newText, highlightWord = newHighlight)
            } else cue
        }
        _fullTranscript.value = _cues.value.joinToString(" ") { it.text.trim() }.trim()
    }

    fun deleteCue(index: Int) {
        val updated = _cues.value.filterNot { it.index == index }
            .mapIndexed { idx, cue -> cue.copy(index = idx + 1) }
        _cues.value = updated
        _fullTranscript.value = updated.joinToString(" ") { it.text.trim() }.trim()
    }

    fun addCue() {
        val current = _cues.value
        val nextStart = current.lastOrNull()?.endMs ?: 0L
        val nextEnd = nextStart + 2500L
        _cues.value = current + TimedCaptionCue(
            index = current.size + 1,
            startMs = nextStart,
            endMs = nextEnd,
            text = "New caption line",
            highlightWord = "caption"
        )
    }

    fun updateStyle(transform: (CaptionBurnStyle) -> CaptionBurnStyle) {
        _burnStyle.value = transform(_burnStyle.value)
    }

    fun currentSrtString(): String = GeminiCaptionTranscriptionService.formatSrt(_cues.value)

    fun saveCaptionsToClip() {
        if (!canSaveToClip) return
        val style = _burnStyle.value
        val transcript = _fullTranscript.value.ifBlank {
            _cues.value.joinToString(" ") { it.text }
        }
        val trimEnd = _clipEndMs.value
        val trimStart = _clipStartMs.value
        viewModelScope.launch {
            if (trimEnd != null && trimEnd > trimStart) {
                firestoreRepo.updateClipTrim(
                    userId = currentUserId,
                    jobId = args.jobId,
                    clipId = args.clipId,
                    startSec = trimStart / 1000.0,
                    endSec = trimEnd / 1000.0
                )
            }
            val res = firestoreRepo.updateClipSubtitles(
                userId = currentUserId,
                jobId = args.jobId,
                clipId = args.clipId,
                subtitlePosition = style.position,
                subtitleAnimation = style.animation,
                subtitleFont = style.fontName,
                subtitleColorHex = style.textColorHex,
                subtitleHighlightHex = style.highlightColorHex,
                subtitleText = transcript
            )
            _feedbackMessage.value = res.fold(
                onSuccess = { "Saved trim range, captions & style to clip." },
                onFailure = { it.localizedMessage ?: "Failed to update clip." }
            )
        }
    }

    fun burnCaptionsAndExportMp4() {
        val uriString = _sourceVideoUri.value
        if (uriString.isBlank()) {
            _exportState.value = UiState.Error("Select a video file first.")
            return
        }
        if (_cues.value.isEmpty() && _burnStyle.value.hookText.isBlank() && !_burnStyle.value.hasVideoFilters()) {
            _exportState.value = UiState.Error("Apply a video filter, transcribe audio with Gemini, or add caption cues before exporting.")
            return
        }
        val uri = try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            _exportState.value = UiState.Error("Invalid video URI.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0f
            _exportState.value = UiState.Loading
            val res = exportService.exportVideoWithBurnedCaptions(
                sourceVideoUri = uri,
                cues = _cues.value,
                style = _burnStyle.value,
                clipStartMs = _clipStartMs.value,
                clipEndMs = _clipEndMs.value,
                onProgress = { progress ->
                    _exportProgress.value = progress
                }
            )
            _exportState.value = res.fold(
                onSuccess = { result ->
                    _exportProgress.value = 1f
                    val style = _burnStyle.value
                    val filterSummary = Media3VideoFilterFactory.describeStack(
                        filterStack = style.filterStack,
                        contrastAdjust = style.filterContrast,
                        saturationAdjust = style.filterSaturation,
                        brightnessAdjust = style.filterBrightness
                    )
                    val resolvedTitle = _sessionTitle.value.trim()
                        .ifBlank { args.initialTitle.trim() }
                        .ifBlank { style.hookText.trim().take(60) }
                        .ifBlank { "OpenShorts Captioned Short" }
                    val resolvedDescription = buildString {
                        if (style.hookText.isNotBlank()) append(style.hookText.trim())
                        val transcriptSnippet = _fullTranscript.value.trim().take(400)
                        if (transcriptSnippet.isNotBlank()) {
                            if (isNotEmpty()) append("\n\n")
                            append(transcriptSnippet)
                        }
                        if (isBlank()) {
                            append("Exported MP4 clip")
                            if (filterSummary != "Original") append(" • Filters: $filterSummary")
                        }
                    }
                    editedVideoMetadataRepo.recordExportedVideo(
                        userId = currentUserId,
                        title = resolvedTitle,
                        description = resolvedDescription,
                        videoUri = result.contentUri,
                        outputFilePath = result.outputFilePath,
                        sourceVideoUri = uriString,
                        trimStartMs = _clipStartMs.value,
                        trimEndMs = _clipEndMs.value ?: -1L,
                        durationMs = result.durationMs,
                        appliedFiltersSummary = filterSummary,
                        captionCount = _cues.value.size,
                        exportSource = EditedVideoExportSource.CAPTION_EXPORT
                    ).onSuccess { savedMeta ->
                        _latestExportMetadata.value = savedMeta
                    }

                    _feedbackMessage.value = if (_cues.value.isNotEmpty() || style.hookText.isNotBlank()) {
                        "Exported MP4 & saved metadata to Room!"
                    } else {
                        "Exported filtered MP4 & saved metadata to Room!"
                    }
                    UiState.Success(result)
                },
                onFailure = { err ->
                    Log.e(TAG, "Burn-in export failed", err)
                    UiState.Error(err.localizedMessage ?: "Failed to burn captions into video export.")
                }
            )
        }
    }

    fun updateLatestExportMetadata(title: String, description: String) {
        val meta = _latestExportMetadata.value ?: return
        viewModelScope.launch {
            editedVideoMetadataRepo.updateTitleAndDescription(
                userId = currentUserId,
                id = meta.id,
                title = title,
                description = description
            ).onSuccess {
                _latestExportMetadata.value = meta.copy(
                    title = title.trim().ifBlank { meta.title },
                    description = description.trim(),
                    updatedAtMs = System.currentTimeMillis()
                )
                _feedbackMessage.value = "Updated video metadata in local Room database."
            }.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Failed to update metadata."
            }
        }
    }

    fun saveExportToDeviceGallery() {
        val current = (_exportState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = exportService.saveExportToGallery(
                filePath = current.outputFilePath,
                displayName = _latestExportMetadata.value?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: args.initialTitle.ifBlank { "OpenShorts_Captioned" }
            )
            res.fold(
                onSuccess = { galleryUri ->
                    val galleryStr = galleryUri.toString()
                    _exportState.value = UiState.Success(current.copy(savedToGalleryUri = galleryStr))
                    _latestExportMetadata.value?.let { meta ->
                        editedVideoMetadataRepo.updateGalleryUri(currentUserId, meta.id, galleryStr)
                        _latestExportMetadata.value = meta.copy(
                            galleryUri = galleryStr,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                    _feedbackMessage.value = "Saved captioned video to Movies/OpenShorts."
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to save to gallery."
                }
            )
        }
    }

    suspend fun optimizePublishMetadata(
        titleHint: String,
        ytDesc: String,
        igCap: String
    ): Result<PublishMetadataDraft> {
        return try {
            val draft = aiRepo.generatePublishMetadata(
                titleHint = titleHint.ifBlank { args.initialTitle.ifBlank { "Captioned Short" } },
                contextText = igCap.ifBlank { _burnStyle.value.hookText },
                transcript = ytDesc.ifBlank { _fullTranscript.value },
                language = _detectedLanguage.value.ifBlank { "English" }
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun automateExportPublish(request: PublishAutomationRequest) {
        viewModelScope.launch {
            val isScheduled = !request.scheduledIso.isNullOrBlank()
            val job = PublishAutomationJob(
                userId = currentUserId,
                sourceType = if (args.clipId.isNotBlank()) "clip" else "custom",
                sourceId = args.clipId.takeIf { it.isNotBlank() },
                youtubeEnabled = request.youtubeEnabled,
                instagramEnabled = request.instagramEnabled,
                youtubeTitle = request.youtubeTitle,
                youtubeDescription = request.youtubeDescription,
                youtubePrivacy = request.youtubePrivacy,
                instagramCaption = request.instagramCaption,
                videoUri = request.videoUri,
                scheduledIso = request.scheduledIso,
                status = if (isScheduled) "scheduled" else "queued",
                resultMessage = if (isScheduled) "Scheduled burned-caption short (${request.scheduledIso})" else "Queued burned-caption short"
            )
            val res = firestoreRepo.createPublishJob(currentUserId, job)
            res.fold(
                onSuccess = { pubId ->
                    scheduler.enqueuePublishJob(currentUserId, pubId, request.scheduledIso)
                    _feedbackMessage.value = if (isScheduled) {
                        "Scheduled captioned short for ${request.scheduledIso}."
                    } else {
                        "Queued captioned short for YouTube/Instagram auto-post."
                    }
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to queue automation."
                }
            )
        }
    }

    private suspend fun resolveActiveDurationMs(): Long = withContext(Dispatchers.IO) {
        val end = _clipEndMs.value
        if (end != null && end > _clipStartMs.value) {
            return@withContext end - _clipStartMs.value
        }
        val uriString = _sourceVideoUri.value
        if (uriString.isBlank()) return@withContext 18_000L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 18_000L
        } catch (_: Exception) {
            18_000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun provideFactory(
            currentUserId: String,
            args: CaptionExportInitArgs
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                CaptionExportViewModel(
                    app = app,
                    currentUserId = currentUserId,
                    args = args,
                    transcriptionService = GeminiCaptionTranscriptionService(app),
                    exportService = CaptionBurnInExportService(app),
                    firestoreRepo = OpenShortsFirestoreRepository(db),
                    aiRepo = OpenShortsAiRepository(app),
                    settingsStore = SettingsDataStore(app),
                    scheduler = PublishAutomationScheduler(app),
                    sessionStorageManager = EditingSessionStorageManager(app),
                    batchExportManager = BatchExportManager(app),
                    editedVideoMetadataRepo = EditedVideoMetadataRepository(app)
                )
            }
        }
    }
}
