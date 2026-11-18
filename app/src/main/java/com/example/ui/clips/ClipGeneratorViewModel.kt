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
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.IntegrationSettings
import com.example.data.model.UiState
import com.example.data.network.ExternalIntegrationsService
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
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

private const val TAG = "ClipGenVM"

@OptIn(ExperimentalCoroutinesApi::class)
class ClipGeneratorViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val settingsStore: SettingsDataStore,
    private val externalService: ExternalIntegrationsService
) : ViewModel() {

    private val _selectedJobId = MutableStateFlow<String?>(null)
    val selectedJobId: StateFlow<String?> = _selectedJobId.asStateFlow()

    private val _generationState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val generationState: StateFlow<UiState<String>> = _generationState.asStateFlow()

    private val _editorFeedback = MutableStateFlow<String?>(null)
    val editorFeedback: StateFlow<String?> = _editorFeedback.asStateFlow()

    private val _isEditorBusy = MutableStateFlow(false)
    val isEditorBusy: StateFlow<Boolean> = _isEditorBusy.asStateFlow()

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    val jobsState: StateFlow<UiState<List<ClipJob>>> = firestoreRepo.observeClipJobs(currentUserId)
        .map<List<ClipJob>, UiState<List<ClipJob>>> { jobs ->
            if (_selectedJobId.value == null && jobs.isNotEmpty()) {
                _selectedJobId.value = jobs.first().id
            } else if (_selectedJobId.value != null && jobs.none { it.id == _selectedJobId.value }) {
                _selectedJobId.value = jobs.firstOrNull()?.id
            }
            UiState.Success(jobs)
        }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing clip jobs", root)
            emit(UiState.Error(root.message ?: "Failed to load clip jobs."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeClipsState: StateFlow<UiState<List<GeneratedClip>>> = _selectedJobId
        .flatMapLatest { jobId ->
            if (jobId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                firestoreRepo.observeClipsForJob(currentUserId, jobId)
                    .map<List<GeneratedClip>, UiState<List<GeneratedClip>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing clips for job $jobId", root)
                        emit(UiState.Error(root.message ?: "Failed to load clips."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun selectJob(jobId: String) {
        _selectedJobId.value = jobId
    }

    fun clearFeedback() {
        _editorFeedback.value = null
    }

    fun clearGenerationError() {
        if (_generationState.value is UiState.Error) {
            _generationState.value = UiState.Idle
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
            try {
                val (durationSec, analysis) = aiRepo.analyzeVideoForClips(
                    sourceLabel = sourceLabel,
                    videoUri = videoUri,
                    clipGuidance = clipGuidance,
                    maxClips = maxClips
                )
                val job = ClipJob(
                    userId = currentUserId,
                    sourceType = sourceType,
                    sourceLabel = sourceLabel,
                    videoUri = videoUri?.toString(),
                    durationSec = durationSec,
                    status = "completed",
                    summary = analysis.summary,
                    clipsCount = analysis.clips.size.toLong()
                )
                val clips = analysis.clips.mapIndexed { idx, moment ->
                    GeneratedClip(
                        userId = currentUserId,
                        clipIndex = idx.toLong(),
                        startSec = moment.startSec,
                        endSec = moment.endSec,
                        youtubeTitle = moment.youtubeTitle,
                        tiktokCaption = moment.tiktokCaption,
                        instagramCaption = moment.instagramCaption,
                        viralHookText = moment.viralHookText,
                        subtitleText = moment.subtitleText,
                        effectsSummary = moment.effectsSummary,
                        viralScore = moment.viralScore.toLong()
                    )
                }
                val result = firestoreRepo.createClipJobWithClips(currentUserId, job, clips)
                result.fold(
                    onSuccess = { createdJobId ->
                        _selectedJobId.value = createdJobId
                        _generationState.value = UiState.Success("Generated ${clips.size} viral clips.")
                    },
                    onFailure = { e ->
                        _generationState.value = UiState.Error(e.localizedMessage ?: "Failed to save clips.")
                    }
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Clip analysis timed out", e)
                _generationState.value = UiState.Error("Analysis timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clip generation failed", e)
                _generationState.value = UiState.Error(e.localizedMessage ?: "AI analysis failed.")
            }
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
            val res = firestoreRepo.updateClipSubtitles(
                userId = currentUserId,
                jobId = clip.jobId,
                clipId = clip.id,
                subtitlePosition = position,
                subtitleAnimation = animation,
                subtitleFont = font,
                subtitleColorHex = colorHex,
                subtitleHighlightHex = highlightHex,
                subtitleText = text
            )
            _isEditorBusy.value = false
            _editorFeedback.value = res.fold(
                onSuccess = { "Subtitles updated on clip." },
                onFailure = { it.localizedMessage ?: "Failed to update subtitles." }
            )
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
            val res = firestoreRepo.updateClipHook(
                userId = currentUserId,
                jobId = clip.jobId,
                clipId = clip.id,
                viralHookText = hookText,
                hookPosition = position,
                hookSize = size,
                hookAnimation = animation,
                hookDurationSec = durationSec
            )
            _isEditorBusy.value = false
            _editorFeedback.value = res.fold(
                onSuccess = { "Viral hook updated on clip." },
                onFailure = { it.localizedMessage ?: "Failed to save hook." }
            )
        }
    }

    fun translateAndDubClip(clip: GeneratedClip, targetLanguage: String) {
        viewModelScope.launch {
            _isEditorBusy.value = true
            try {
                val translated = aiRepo.translateClipContent(
                    targetLanguage = targetLanguage,
                    subtitleText = clip.subtitleText.orEmpty(),
                    hookText = clip.viralHookText.orEmpty(),
                    youtubeTitle = clip.youtubeTitle,
                    tiktokCaption = clip.tiktokCaption.orEmpty()
                )
                val res = firestoreRepo.updateClipTranslation(
                    userId = currentUserId,
                    jobId = clip.jobId,
                    clipId = clip.id,
                    translatedLanguage = translated.translatedLanguage,
                    subtitleText = translated.translatedSubtitles,
                    viralHookText = translated.translatedHook,
                    youtubeTitle = translated.translatedYoutubeTitle,
                    tiktokCaption = translated.translatedTiktokCaption
                )
                _editorFeedback.value = res.fold(
                    onSuccess = { "Translated clip into ${translated.translatedLanguage}." },
                    onFailure = { it.localizedMessage ?: "Failed to save translation." }
                )
            } catch (e: TimeoutCancellationException) {
                _editorFeedback.value = "Translation timed out. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                _editorFeedback.value = e.localizedMessage ?: "Translation failed."
            } finally {
                _isEditorBusy.value = false
            }
        }
    }

    fun postClipViaUploadPost(
        title: String,
        platforms: List<String>,
        scheduledIso: String?
    ) {
        val currentSettings = settings.value
        viewModelScope.launch {
            _isEditorBusy.value = true
            val res = externalService.scheduleUploadPostTextOrStatus(
                apiKey = currentSettings.uploadPostApiKey,
                username = currentSettings.uploadPostUsername,
                title = title,
                platforms = platforms,
                scheduledIsoDate = scheduledIso
            )
            _isEditorBusy.value = false
            _editorFeedback.value = res.fold(
                onSuccess = { it },
                onFailure = { it.localizedMessage ?: "Publishing failed." }
            )
        }
    }

    fun scheduleWeekViaUploadPost(
        clips: List<GeneratedClip>,
        platforms: List<String>,
        startDayIsoDate: String,
        postTimeHour: Int
    ) {
        val currentSettings = settings.value
        if (currentSettings.uploadPostApiKey.isBlank() || currentSettings.uploadPostUsername.isBlank()) {
            _editorFeedback.value = "Add your Upload-Post API Key & Account in Settings first, or export clips via Share."
            return
        }
        viewModelScope.launch {
            _isEditorBusy.value = true
            var successCount = 0
            var lastError: String? = null
            clips.take(7).forEachIndexed { idx, clip ->
                val iso = "${startDayIsoDate}T%02d:00:00Z".format(postTimeHour.coerceIn(0, 23))
                val res = externalService.scheduleUploadPostTextOrStatus(
                    apiKey = currentSettings.uploadPostApiKey,
                    username = currentSettings.uploadPostUsername,
                    title = "${clip.youtubeTitle}\n\n${clip.tiktokCaption.orEmpty()}",
                    platforms = platforms,
                    scheduledIsoDate = iso
                )
                if (res.isSuccess) successCount++ else lastError = res.exceptionOrNull()?.localizedMessage
            }
            _isEditorBusy.value = false
            _editorFeedback.value = if (successCount > 0) {
                "Scheduled $successCount clip(s) across ${platforms.joinToString(", ")}."
            } else {
                lastError ?: "Scheduling failed."
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            val res = firestoreRepo.deleteClipJob(currentUserId, jobId)
            res.onFailure {
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
                    externalService = ExternalIntegrationsService()
                )
            }
        }
    }
}
