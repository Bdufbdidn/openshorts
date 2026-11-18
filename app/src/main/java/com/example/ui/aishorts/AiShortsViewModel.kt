package com.example.ui.aishorts

import android.app.Application
import android.graphics.Bitmap
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
import com.example.data.model.AiShortProject
import com.example.data.model.GeneratedUgcScript
import com.example.data.model.IntegrationSettings
import com.example.data.model.ProductResearchResult
import com.example.data.model.PublishAutomationJob
import com.example.data.model.PublishMetadataDraft
import com.example.data.model.UgcAvatar
import com.example.data.model.UiState
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
import com.example.ui.components.PublishAutomationRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AiShortsVM"

enum class AiShortsStep(val index: Int, val label: String) {
    SETUP(0, "1. Setup"),
    ANALYSIS(1, "2. Research"),
    CONFIGURE(2, "3. Script & Actor"),
    GENERATE(3, "4. Produce"),
    RESULT(4, "5. Storyboard")
}

class AiShortsViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val settingsStore: SettingsDataStore,
    private val scheduler: PublishAutomationScheduler
) : ViewModel() {

    private val _step = MutableStateFlow(AiShortsStep.SETUP)
    val step: StateFlow<AiShortsStep> = _step.asStateFlow()

    private val _researchState = MutableStateFlow<UiState<ProductResearchResult>>(UiState.Idle)
    val researchState: StateFlow<UiState<ProductResearchResult>> = _researchState.asStateFlow()

    private val _scriptsState = MutableStateFlow<UiState<List<GeneratedUgcScript>>>(UiState.Idle)
    val scriptsState: StateFlow<UiState<List<GeneratedUgcScript>>> = _scriptsState.asStateFlow()

    private val _selectedScriptIndex = MutableStateFlow(0)
    val selectedScriptIndex: StateFlow<Int> = _selectedScriptIndex.asStateFlow()

    private val _actorImageState = MutableStateFlow<UiState<Pair<Bitmap, String>>>(UiState.Idle)
    val actorImageState: StateFlow<UiState<Pair<Bitmap, String>>> = _actorImageState.asStateFlow()

    private val _finalSaveState = MutableStateFlow<UiState<AiShortProject>>(UiState.Idle)
    val finalSaveState: StateFlow<UiState<AiShortProject>> = _finalSaveState.asStateFlow()

    private val _isAutomatingPost = MutableStateFlow(false)
    val isAutomatingPost: StateFlow<Boolean> = _isAutomatingPost.asStateFlow()

    private val _automationFeedback = MutableStateFlow<String?>(null)
    val automationFeedback: StateFlow<String?> = _automationFeedback.asStateFlow()

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    val savedAvatars: StateFlow<List<UgcAvatar>> = firestoreRepo.observeAvatars(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe avatars", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun goToStep(target: AiShortsStep) {
        _step.value = target
    }

    fun stepBack(): Boolean {
        val prev = AiShortsStep.entries.getOrNull(_step.value.index - 1) ?: return false
        _step.value = prev
        return true
    }

    fun selectScript(index: Int) {
        _selectedScriptIndex.value = index
    }

    fun researchProduct(url: String, description: String, language: String) {
        if (url.isBlank() && description.isBlank()) {
            _researchState.value = UiState.Error("Enter a product URL or description.")
            return
        }
        viewModelScope.launch {
            _researchState.value = UiState.Loading
            try {
                val res = aiRepo.researchProductForUgc(url, description, language)
                _researchState.value = UiState.Success(res)
                _step.value = AiShortsStep.ANALYSIS
            } catch (e: TimeoutCancellationException) {
                _researchState.value = UiState.Error("Product research timed out after 45s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Product research failed", e)
                _researchState.value = UiState.Error(e.localizedMessage ?: "Research failed.")
            }
        }
    }

    fun generateScripts(
        style: String,
        language: String,
        actorGender: String,
        videoMode: String,
        count: Int
    ) {
        val research = (_researchState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _scriptsState.value = UiState.Loading
            try {
                val scripts = aiRepo.generateUgcScripts(
                    research = research,
                    style = style,
                    language = language,
                    actorGender = actorGender,
                    videoMode = videoMode,
                    count = count
                )
                _selectedScriptIndex.value = 0
                _scriptsState.value = UiState.Success(scripts)
                _step.value = AiShortsStep.CONFIGURE
            } catch (e: TimeoutCancellationException) {
                _scriptsState.value = UiState.Error("Script generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Script generation failed", e)
                _scriptsState.value = UiState.Error(e.localizedMessage ?: "Script generation failed.")
            }
        }
    }

    fun generateActorPortrait(prompt: String, gender: String, actorName: String) {
        viewModelScope.launch {
            _actorImageState.value = UiState.Loading
            try {
                val (bitmap, path) = aiRepo.generateActorPortrait(prompt, gender)
                _actorImageState.value = UiState.Success(bitmap to path)
                firestoreRepo.createAvatar(
                    currentUserId,
                    UgcAvatar(
                        userId = currentUserId,
                        name = actorName.ifBlank { "AI Creator" },
                        prompt = prompt.ifBlank { "Studio UGC portrait" },
                        gender = gender,
                        localImagePath = path
                    )
                )
            } catch (e: TimeoutCancellationException) {
                _actorImageState.value = UiState.Error("Actor generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Actor portrait generation failed", e)
                _actorImageState.value = UiState.Error(e.localizedMessage ?: "Portrait generation failed.")
            }
        }
    }

    fun setPickedActorUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = aiRepo.decodeUriToBitmap(uri)
            if (bitmap != null) {
                val path = aiRepo.saveBitmapToAppStorage(bitmap, "picked_actor_${System.currentTimeMillis()}.jpg")
                _actorImageState.value = UiState.Success(bitmap to path)
            } else {
                _actorImageState.value = UiState.Error("Could not load selected image.")
            }
        }
    }

    fun saveCampaign(
        productUrl: String,
        productDescription: String,
        videoMode: String,
        style: String,
        language: String,
        actorGender: String,
        editedNarration: String,
        selectedActorPath: String?
    ) {
        val research = (_researchState.value as? UiState.Success)?.data ?: return
        val scripts = (_scriptsState.value as? UiState.Success)?.data.orEmpty()
        val script = scripts.getOrNull(_selectedScriptIndex.value) ?: return

        viewModelScope.launch {
            _finalSaveState.value = UiState.Loading
            _step.value = AiShortsStep.GENERATE
            val estimatedCost = if (videoMode == "premium") 2.00 else 0.80
            val finalActorPath = selectedActorPath
                ?: (_actorImageState.value as? UiState.Success)?.data?.second

            val project = AiShortProject(
                userId = currentUserId,
                productName = research.productName,
                productUrl = productUrl.takeIf { it.isNotBlank() },
                productDescription = productDescription.takeIf { it.isNotBlank() },
                industry = research.industry,
                oneLiner = research.oneLiner,
                videoMode = videoMode,
                style = style,
                language = language,
                actorGender = actorGender,
                scriptTitle = script.title,
                hookText = script.hookText,
                fullNarration = editedNarration.ifBlank { script.fullNarration },
                actorDescription = script.actorDescription,
                voiceName = script.voiceName,
                actorImagePath = finalActorPath,
                caption = script.caption,
                hashtags = script.hashtags,
                durationSeconds = script.durationSeconds.toLong(),
                estimatedCostUsd = estimatedCost,
                status = "completed"
            )
            val res = firestoreRepo.createAiShort(currentUserId, project)
            res.fold(
                onSuccess = { id ->
                    _finalSaveState.value = UiState.Success(project.copy(id = id))
                    _step.value = AiShortsStep.RESULT
                },
                onFailure = { e ->
                    _finalSaveState.value = UiState.Error(e.localizedMessage ?: "Failed to save AI Short.")
                }
            )
        }
    }

    fun resetWizard() {
        _step.value = AiShortsStep.SETUP
        _researchState.value = UiState.Idle
        _scriptsState.value = UiState.Idle
        _actorImageState.value = UiState.Idle
        _finalSaveState.value = UiState.Idle
    }

    fun clearAutomationFeedback() {
        _automationFeedback.value = null
    }

    suspend fun optimizePublishMetadata(
        project: AiShortProject,
        titleHint: String,
        ytDesc: String,
        igCaption: String
    ): Result<PublishMetadataDraft> {
        return try {
            val draft = aiRepo.generatePublishMetadata(
                titleHint = titleHint.ifBlank { project.scriptTitle },
                contextText = igCaption.ifBlank { project.hookText.orEmpty() },
                transcript = ytDesc.ifBlank { project.fullNarration },
                language = if (project.language == "es") "Spanish" else "English"
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun automateAiShortPublish(project: AiShortProject, request: PublishAutomationRequest) {
        viewModelScope.launch {
            _isAutomatingPost.value = true
            val isScheduled = !request.scheduledIso.isNullOrBlank()
            val job = PublishAutomationJob(
                userId = currentUserId,
                sourceType = "ai_short",
                sourceId = project.id.takeIf { it.isNotBlank() },
                youtubeEnabled = request.youtubeEnabled,
                instagramEnabled = request.instagramEnabled,
                youtubeTitle = request.youtubeTitle,
                youtubeDescription = request.youtubeDescription,
                youtubePrivacy = request.youtubePrivacy,
                instagramCaption = request.instagramCaption,
                videoUri = request.videoUri,
                scheduledIso = request.scheduledIso,
                status = if (isScheduled) "scheduled" else "queued",
                resultMessage = if (isScheduled) "Scheduled AI Short (${request.scheduledIso})" else "Queued AI Short for upload"
            )
            val res = firestoreRepo.createPublishJob(currentUserId, job)
            _isAutomatingPost.value = false
            res.fold(
                onSuccess = { pubId ->
                    scheduler.enqueuePublishJob(currentUserId, pubId, request.scheduledIso)
                    _automationFeedback.value = if (isScheduled) {
                        "Scheduled AI Short post for YouTube & Instagram."
                    } else {
                        "Queued AI Short auto-post to YouTube & Instagram."
                    }
                },
                onFailure = { e ->
                    _automationFeedback.value = e.localizedMessage ?: "Failed to queue AI Short."
                }
            )
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AiShortsViewModel(
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
