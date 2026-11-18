package com.example.ui.clips

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.OpenShortsAiRepository
import com.example.data.automation.PublishAutomationScheduler
import com.example.data.export.BatchExportManager
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.data.local.EditingSession
import com.example.data.local.EditingSessionStorageManager
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.IntegrationSettings
import com.example.data.model.LiveShortsSkillPreset
import com.example.data.model.PublishMetadataDraft
import com.example.data.model.TrimChatMessage
import com.example.data.model.UiState
import com.example.data.model.YouTubeSearchResult
import com.example.data.network.YouTubeSearchService
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
import com.example.ui.components.PublishAutomationRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ClipGenVM"

@OptIn(ExperimentalCoroutinesApi::class)
class ClipGeneratorViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val settingsStore: SettingsDataStore,
    private val scheduler: PublishAutomationScheduler,
    private val youtubeSearchService: YouTubeSearchService = YouTubeSearchService(),
    private val sessionStorageManager: EditingSessionStorageManager,
    private val batchExportManager: BatchExportManager
) : ViewModel() {

    private val sessionAndBatchDelegate = ClipSessionAndBatchDelegate(
        currentUserId = currentUserId,
        sessionStorageManager = sessionStorageManager,
        batchExportManager = batchExportManager
    )

    private val publishAndEditDelegate = ClipPublishAndEditDelegate(
        currentUserId = currentUserId,
        firestoreRepo = firestoreRepo,
        aiRepo = aiRepo,
        scheduler = scheduler
    )

    private val _selectedJobId = MutableStateFlow<String?>(null)
    val selectedJobId: StateFlow<String?> = _selectedJobId.asStateFlow()

    private val _generationState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val generationState: StateFlow<UiState<String>> = _generationState.asStateFlow()

    private val _editorFeedback = MutableStateFlow<String?>(null)
    val editorFeedback: StateFlow<String?> = _editorFeedback.asStateFlow()

    private val _isEditorBusy = MutableStateFlow(false)
    val isEditorBusy: StateFlow<Boolean> = _isEditorBusy.asStateFlow()

    private val _youtubeSearchState = MutableStateFlow<UiState<List<YouTubeSearchResult>>>(UiState.Idle)
    val youtubeSearchState: StateFlow<UiState<List<YouTubeSearchResult>>> = _youtubeSearchState.asStateFlow()

    private val _trimChatMessages = MutableStateFlow<List<TrimChatMessage>>(emptyList())
    val trimChatMessages: StateFlow<List<TrimChatMessage>> = _trimChatMessages.asStateFlow()

    private val _isTrimChatBusy = MutableStateFlow(false)
    val isTrimChatBusy: StateFlow<Boolean> = _isTrimChatBusy.asStateFlow()

    private val _activeLocalSessionId = MutableStateFlow<String?>(null)
    val activeLocalSessionId: StateFlow<String?> = _activeLocalSessionId.asStateFlow()

    val editingSessions: StateFlow<List<EditingSession>> = sessionStorageManager.observeSessions(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    val pendingAutomationCount: StateFlow<Int> = firestoreRepo.observePublishJobs(currentUserId)
        .map { list -> list.count { it.status in listOf("queued", "scheduled", "uploading") } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

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

    private fun unwrapFirestoreError(error: Throwable, logContext: String, fallback: String): String {
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, logContext, root)
        return root.message ?: fallback
    }

    val jobsState: StateFlow<UiState<List<ClipJob>>> = firestoreRepo.observeClipJobs(currentUserId)
        .map<List<ClipJob>, UiState<List<ClipJob>>> { jobs ->
            if (_selectedJobId.value == null && jobs.isNotEmpty()) {
                _selectedJobId.value = jobs.first().id
            } else if (_selectedJobId.value != null && jobs.none { it.id == _selectedJobId.value }) {
                _selectedJobId.value = jobs.firstOrNull()?.id
            }
            UiState.Success(jobs)
        }
        .catch { emit(UiState.Error(unwrapFirestoreError(it, "Error observing clip jobs", "Failed to load clip jobs."))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeClipsState: StateFlow<UiState<List<GeneratedClip>>> = _selectedJobId
        .flatMapLatest { jobId ->
            if (jobId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                firestoreRepo.observeClipsForJob(currentUserId, jobId)
                    .map<List<GeneratedClip>, UiState<List<GeneratedClip>>> { UiState.Success(it) }
                    .catch { emit(UiState.Error(unwrapFirestoreError(it, "Error observing clips for job $jobId", "Failed to load clips."))) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun selectJob(jobId: String) { _selectedJobId.value = jobId }
    fun clearFeedback() { _editorFeedback.value = null }
    fun clearGenerationError() { if (_generationState.value is UiState.Error) _generationState.value = UiState.Idle }
    fun restoreTrimChat(messages: List<TrimChatMessage>) { _trimChatMessages.value = messages }

    fun saveClipStudioSession(
        title: String, saveAsNew: Boolean = false, sourceMode: String, sourceLabel: String,
        videoUri: String?, thumbnailUrl: String?, totalDurationMs: Long, trimStartMs: Long, trimEndMs: Long,
        guidanceNotes: String, liveSkillId: String, liveLookbackMinutes: Int, targetClipsCount: Int,
        activeJobId: String?, burnStyle: CaptionBurnStyle = CaptionBurnStyle()
    ) {
        viewModelScope.launch {
            sessionAndBatchDelegate.saveClipStudioSession(
                _activeLocalSessionId.value, _trimChatMessages.value, title, saveAsNew, sourceMode, sourceLabel,
                videoUri, thumbnailUrl, totalDurationMs, trimStartMs, trimEndMs, guidanceNotes, liveSkillId,
                liveLookbackMinutes, targetClipsCount, activeJobId, burnStyle
            ).fold(
                onSuccess = { saved ->
                    _activeLocalSessionId.value = saved.id
                    _editorFeedback.value = "Saved session \"${saved.title}\" locally."
                },
                onFailure = { _editorFeedback.value = it.localizedMessage ?: "Failed to save editing session." }
            )
        }
    }

    suspend fun getLocalSessionById(sessionId: String): EditingSession? =
        sessionAndBatchDelegate.getLocalSessionById(sessionId)

    fun markLocalSessionResumed(session: EditingSession) {
        _activeLocalSessionId.value = session.id
        _trimChatMessages.value = session.trimChatHistory
        if (!session.activeJobId.isNullOrBlank()) _selectedJobId.value = session.activeJobId
        _editorFeedback.value = "Resumed editing session \"${session.title}\"."
    }

    fun deleteLocalSession(sessionId: String) {
        viewModelScope.launch {
            sessionAndBatchDelegate.deleteLocalSession(sessionId).fold(
                onSuccess = {
                    if (_activeLocalSessionId.value == sessionId) _activeLocalSessionId.value = null
                    _editorFeedback.value = "Deleted saved session."
                },
                onFailure = { _editorFeedback.value = it.localizedMessage ?: "Failed to delete session." }
            )
        }
    }

    fun addTrimmedClipToBatch(
        title: String, videoUriString: String, trimStartMs: Long, trimEndMs: Long,
        clip: GeneratedClip? = null, filterStack: List<String> = emptyList(),
        filterContrast: Float = 0f, filterSaturation: Float = 0f, filterBrightness: Float = 0f,
        startImmediately: Boolean = false
    ) {
        if (videoUriString.isBlank()) {
            _editorFeedback.value = "Attach a local device video file to add trimmed clips to the export batch."
            return
        }
        viewModelScope.launch {
            sessionAndBatchDelegate.addTrimmedClipToBatch(
                title, videoUriString, trimStartMs, trimEndMs, clip, filterStack,
                filterContrast, filterSaturation, filterBrightness, startImmediately
            ).fold(
                onSuccess = { _editorFeedback.value = it },
                onFailure = { _editorFeedback.value = it.localizedMessage ?: "Failed to queue clip for export." }
            )
        }
    }

    fun addAllClipsToBatch(
        clips: List<GeneratedClip>, videoUriString: String?, filterStack: List<String> = emptyList(),
        filterContrast: Float = 0f, filterSaturation: Float = 0f, filterBrightness: Float = 0f,
        startImmediately: Boolean = false
    ) {
        val uri = videoUriString?.takeIf { it.isNotBlank() }
        if (uri == null || clips.isEmpty()) {
            _editorFeedback.value = "Generate clips from a local device video to batch export."
            return
        }
        viewModelScope.launch {
            val addedCount = sessionAndBatchDelegate.addAllClipsToBatch(
                clips, uri, filterStack, filterContrast, filterSaturation, filterBrightness, startImmediately
            )
            _editorFeedback.value = if (addedCount > 0) {
                "Added $addedCount trimmed clips to sequential export batch."
            } else {
                "No clips could be added to the export batch."
            }
        }
    }

    fun searchYouTube(query: String, liveOnly: Boolean) {
        viewModelScope.launch {
            _youtubeSearchState.value = UiState.Loading
            val res = youtubeSearchService.searchVideos(query = query, liveOnly = liveOnly)
            _youtubeSearchState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "YouTube search failed.") }
            )
        }
    }

    fun resetTrimChat(sessionKey: String? = null) {
        _trimChatMessages.value = emptyList()
    }

    fun sendTrimChatReview(
        videoTitle: String,
        sourceLabel: String,
        totalDurationSec: Double,
        currentStartSec: Double,
        currentEndSec: Double,
        transcriptOrContext: String,
        userMessage: String
    ) {
        val cleanMsg = userMessage.trim()
        if (cleanMsg.isBlank()) return
        val userEntry = TrimChatMessage(id = UUID.randomUUID().toString(), fromUser = true, text = cleanMsg)
        val updatedHistory = _trimChatMessages.value + userEntry
        _trimChatMessages.value = updatedHistory

        viewModelScope.launch {
            _isTrimChatBusy.value = true
            try {
                val aiEntry = publishAndEditDelegate.reviewTrimChat(
                    videoTitle = videoTitle,
                    sourceLabel = sourceLabel,
                    totalDurationSec = totalDurationSec,
                    currentStartSec = currentStartSec,
                    currentEndSec = currentEndSec,
                    transcriptOrContext = transcriptOrContext,
                    chatHistory = updatedHistory,
                    userRequest = cleanMsg
                )
                _trimChatMessages.value = _trimChatMessages.value + aiEntry
            } finally {
                _isTrimChatBusy.value = false
            }
        }
    }

    fun analyzeLiveStreamForShorts(
        liveStreamUrl: String,
        lookbackMinutes: Int,
        skillPreset: LiveShortsSkillPreset,
        liveCueNotes: String,
        maxClips: Int
    ) {
        if (liveStreamUrl.isBlank()) {
            _generationState.value = UiState.Error("Enter or search a Live Stream URL (YouTube Live or .m3u8) first.")
            return
        }
        viewModelScope.launch {
            _generationState.value = UiState.Loading
            publishAndEditDelegate.runLiveStreamAnalysis(
                liveStreamUrl, lookbackMinutes, skillPreset, liveCueNotes, maxClips
            ).fold(
                onSuccess = { (createdJobId, clipCount) ->
                    _selectedJobId.value = createdJobId
                    _generationState.value = UiState.Success(
                        "Live Video Shorts (${skillPreset.label}) extracted $clipCount viral clips."
                    )
                },
                onFailure = { e ->
                    _generationState.value = UiState.Error(e.localizedMessage ?: "Live stream shorts failed.")
                }
            )
        }
    }

    fun analyzeAndGenerateClips(
        sourceType: String,
        sourceLabel: String,
        videoUri: Uri?,
        clipGuidance: String,
        maxClips: Int
    ) {
        if (sourceLabel.isBlank()) {
            _generationState.value = UiState.Error("Select a video or enter a video URL first.")
            return
        }
        viewModelScope.launch {
            _generationState.value = UiState.Loading
            publishAndEditDelegate.runVideoClipAnalysis(
                sourceType, sourceLabel, videoUri, clipGuidance, maxClips, settings.value
            ).fold(
                onSuccess = { (createdJobId, clipCount, queuedCount) ->
                    _selectedJobId.value = createdJobId
                    _generationState.value = UiState.Success(
                        if (queuedCount > 0) {
                            "Generated $clipCount clips & auto-queued $queuedCount for YouTube/Instagram."
                        } else {
                            "Generated $clipCount viral clips."
                        }
                    )
                },
                onFailure = { e ->
                    _generationState.value = UiState.Error(e.localizedMessage ?: "AI analysis failed.")
                }
            )
        }
    }

    fun buildYoutubeDescriptionFromClip(clip: GeneratedClip, includeTranscript: Boolean): String =
        ClipMetadataFormatter.buildYoutubeDescriptionFromClip(clip, includeTranscript)

    fun buildInstagramCaptionFromClip(clip: GeneratedClip): String =
        ClipMetadataFormatter.buildInstagramCaptionFromClip(clip)

    suspend fun optimizeMetadataForClip(
        clip: GeneratedClip,
        titleHint: String,
        ytDesc: String,
        igCaption: String
    ): Result<PublishMetadataDraft> = publishAndEditDelegate.optimizeMetadataForClip(clip, titleHint, ytDesc, igCaption)

    fun saveClipTrim(clip: GeneratedClip, startSec: Double, endSec: Double) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.saveClipTrim(clip, startSec, endSec)
            _isEditorBusy.value = false
        }
    }

    fun automateClipPublish(clip: GeneratedClip, request: PublishAutomationRequest) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.automateClipPublish(clip, request)
            _isEditorBusy.value = false
        }
    }

    fun automateWeekQueue(
        clips: List<GeneratedClip>,
        videoUriString: String?,
        youtubeEnabled: Boolean,
        instagramEnabled: Boolean,
        startDayIsoDate: String,
        postTimeHour: Int
    ) {
        if (!youtubeEnabled && !instagramEnabled) {
            _editorFeedback.value = "Select YouTube and/or Instagram first."
            return
        }
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.automateWeekQueue(
                clips, videoUriString, youtubeEnabled, instagramEnabled,
                startDayIsoDate, postTimeHour, settings.value
            )
            _isEditorBusy.value = false
        }
    }

    fun saveSubtitles(
        clip: GeneratedClip,
        position: String,
        animation: String,
        font: String,
        colorHex: String,
        highlightHex: String,
        text: String
    ) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.saveSubtitles(
                clip, position, animation, font, colorHex, highlightHex, text
            )
            _isEditorBusy.value = false
        }
    }

    fun saveHook(
        clip: GeneratedClip,
        hookText: String,
        position: String,
        size: String,
        animation: String,
        durationSec: Long
    ) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.saveHook(
                clip, hookText, position, size, animation, durationSec
            )
            _isEditorBusy.value = false
        }
    }

    fun translateAndDubClip(clip: GeneratedClip, targetLanguage: String) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            _editorFeedback.value = publishAndEditDelegate.translateAndDubClip(clip, targetLanguage)
            _isEditorBusy.value = false
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteClipJob(currentUserId, jobId).onFailure {
                _editorFeedback.value = it.localizedMessage ?: "Failed to delete job."
            }
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ClipGeneratorViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = OpenShortsFirestoreRepository(db),
                    aiRepo = OpenShortsAiRepository(app),
                    settingsStore = SettingsDataStore(app),
                    scheduler = PublishAutomationScheduler(app),
                    sessionStorageManager = EditingSessionStorageManager(app),
                    batchExportManager = BatchExportManager(app)
                )
            }
        }
    }
}
