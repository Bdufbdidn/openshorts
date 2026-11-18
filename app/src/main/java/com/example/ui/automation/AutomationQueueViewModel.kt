package com.example.ui.automation

import android.app.Application
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
import com.example.data.model.IntegrationSettings
import com.example.data.model.PublishAutomationJob
import com.example.data.model.PublishMetadataDraft
import com.example.data.model.UiState
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
import com.example.ui.components.PublishAutomationRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AutomationQueueVM"

class AutomationQueueViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val settingsStore: SettingsDataStore,
    private val scheduler: PublishAutomationScheduler
) : ViewModel() {

    val publishJobsState: StateFlow<UiState<List<PublishAutomationJob>>> =
        firestoreRepo.observePublishJobs(currentUserId)
            .map<List<PublishAutomationJob>, UiState<List<PublishAutomationJob>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Failed to observe publish automation jobs", root)
                emit(UiState.Error(root.message ?: "Failed to load automation queue."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    suspend fun optimizeMetadata(
        titleHint: String,
        ytDesc: String,
        igCaption: String
    ): Result<PublishMetadataDraft> {
        return try {
            val draft = aiRepo.generatePublishMetadata(
                titleHint = titleHint,
                contextText = igCaption,
                transcript = ytDesc
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun submitCustomAutomation(request: PublishAutomationRequest) {
        viewModelScope.launch {
            _isBusy.value = true
            val isScheduled = !request.scheduledIso.isNullOrBlank()
            val job = PublishAutomationJob(
                userId = currentUserId,
                sourceType = "custom",
                youtubeEnabled = request.youtubeEnabled,
                instagramEnabled = request.instagramEnabled,
                youtubeTitle = request.youtubeTitle,
                youtubeDescription = request.youtubeDescription,
                youtubePrivacy = request.youtubePrivacy,
                instagramCaption = request.instagramCaption,
                videoUri = request.videoUri,
                scheduledIso = request.scheduledIso,
                status = if (isScheduled) "scheduled" else "queued",
                resultMessage = if (isScheduled) "Scheduled for ${request.scheduledIso}" else "Queued for background upload"
            )
            val result = firestoreRepo.createPublishJob(currentUserId, job)
            _isBusy.value = false
            result.fold(
                onSuccess = { jobId ->
                    scheduler.enqueuePublishJob(currentUserId, jobId, request.scheduledIso)
                    _feedback.value = if (isScheduled) {
                        "Scheduled YouTube & Instagram post (${request.scheduledIso})."
                    } else {
                        "Started automated post in background queue."
                    }
                },
                onFailure = { e ->
                    _feedback.value = e.localizedMessage ?: "Failed to queue automation."
                }
            )
        }
    }

    fun runJobNow(job: PublishAutomationJob) {
        viewModelScope.launch {
            firestoreRepo.updatePublishJobStatus(
                userId = currentUserId,
                jobId = job.id,
                status = "queued",
                resultMessage = "Triggered immediate upload"
            )
            scheduler.enqueuePublishJob(currentUserId, job.id, scheduledIso = null)
            _feedback.value = "Triggered immediate upload for \"${job.youtubeTitle}\"."
        }
    }

    fun deleteJob(job: PublishAutomationJob) {
        viewModelScope.launch {
            scheduler.cancelPublishJob(job.id)
            firestoreRepo.deletePublishJob(currentUserId, job.id)
            _feedback.value = "Removed automation job."
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AutomationQueueViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = OpenShortsFirestoreRepository(db),
                    aiRepo = OpenShortsAiRepository(app),
                    settingsStore = SettingsDataStore(app),
                    scheduler = PublishAutomationScheduler(app)
                )
            }
        }
    }
}
