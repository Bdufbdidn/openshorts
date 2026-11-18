package com.example.ui.gallery

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
import com.example.data.local.EditedVideoExportSource
import com.example.data.local.EditedVideoMetadata
import com.example.data.local.EditedVideoMetadataRepository
import com.example.data.model.AiShortProject
import com.example.data.model.UgcAvatar
import com.example.data.model.UiState
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "UgcGalleryVM"

@OptIn(ExperimentalCoroutinesApi::class)
class UgcGalleryViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val editedVideoMetadataRepo: EditedVideoMetadataRepository
) : ViewModel() {

    val shortsState: StateFlow<UiState<List<AiShortProject>>> = firestoreRepo.observeAiShorts(currentUserId)
        .map<List<AiShortProject>, UiState<List<AiShortProject>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Failed to observe AI Shorts", root)
            emit(UiState.Error(root.message ?: "Failed to load AI Shorts."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val avatarsState: StateFlow<UiState<List<UgcAvatar>>> = firestoreRepo.observeAvatars(currentUserId)
        .map<List<UgcAvatar>, UiState<List<UgcAvatar>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Failed to observe Avatars", root)
            emit(UiState.Error(root.message ?: "Failed to load avatars."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _createAvatarState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val createAvatarState: StateFlow<UiState<String>> = _createAvatarState.asStateFlow()

    private val _editedVideoSearchQuery = MutableStateFlow("")
    val editedVideoSearchQuery: StateFlow<String> = _editedVideoSearchQuery.asStateFlow()

    val editedVideosMetadata: StateFlow<List<EditedVideoMetadata>> = _editedVideoSearchQuery
        .flatMapLatest { query ->
            editedVideoMetadataRepo.searchEditedVideos(currentUserId, query)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _metadataFeedback = MutableStateFlow<String?>(null)
    val metadataFeedback: StateFlow<String?> = _metadataFeedback.asStateFlow()

    fun updateEditedVideoSearchQuery(query: String) {
        _editedVideoSearchQuery.value = query
    }

    fun clearMetadataFeedback() {
        _metadataFeedback.value = null
    }

    fun addManualEditedVideoMetadata(
        title: String,
        description: String,
        videoUri: String
    ) {
        if (title.isBlank()) {
            _metadataFeedback.value = "Enter a video title."
            return
        }
        viewModelScope.launch {
            editedVideoMetadataRepo.recordExportedVideo(
                userId = currentUserId,
                title = title,
                description = description,
                videoUri = videoUri.trim(),
                exportSource = EditedVideoExportSource.MANUAL_ENTRY
            ).fold(
                onSuccess = { _metadataFeedback.value = "Saved video metadata to Room." },
                onFailure = { _metadataFeedback.value = it.localizedMessage ?: "Failed to save video metadata." }
            )
        }
    }

    fun updateEditedVideoMetadata(id: String, title: String, description: String) {
        viewModelScope.launch {
            editedVideoMetadataRepo.updateTitleAndDescription(
                userId = currentUserId,
                id = id,
                title = title,
                description = description
            ).fold(
                onSuccess = { _metadataFeedback.value = "Updated title and description." },
                onFailure = { _metadataFeedback.value = it.localizedMessage ?: "Failed to update metadata." }
            )
        }
    }

    fun deleteEditedVideoMetadata(id: String) {
        viewModelScope.launch {
            editedVideoMetadataRepo.deleteById(currentUserId, id)
        }
    }

    fun createAiAvatar(name: String, prompt: String, gender: String) {
        if (name.isBlank() || prompt.isBlank()) {
            _createAvatarState.value = UiState.Error("Enter both an actor name and visual prompt.")
            return
        }
        viewModelScope.launch {
            _createAvatarState.value = UiState.Loading
            try {
                val (_, path) = aiRepo.generateActorPortrait(prompt, gender)
                val res = firestoreRepo.createAvatar(
                    currentUserId,
                    UgcAvatar(
                        userId = currentUserId,
                        name = name.trim(),
                        prompt = prompt.trim(),
                        gender = gender,
                        localImagePath = path
                    )
                )
                _createAvatarState.value = res.fold(
                    onSuccess = { UiState.Success("AI Actor added to gallery.") },
                    onFailure = { UiState.Error(it.localizedMessage ?: "Failed to save avatar.") }
                )
            } catch (e: TimeoutCancellationException) {
                _createAvatarState.value = UiState.Error("Actor portrait generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Avatar creation failed", e)
                _createAvatarState.value = UiState.Error(e.localizedMessage ?: "Avatar creation failed.")
            }
        }
    }

    fun clearCreateStatus() {
        _createAvatarState.value = UiState.Idle
    }

    fun deleteShort(id: String) {
        viewModelScope.launch {
            firestoreRepo.deleteAiShort(currentUserId, id)
        }
    }

    fun deleteAvatar(id: String) {
        viewModelScope.launch {
            firestoreRepo.deleteAvatar(currentUserId, id)
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                UgcGalleryViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = OpenShortsFirestoreRepository(db),
                    aiRepo = OpenShortsAiRepository(app),
                    editedVideoMetadataRepo = EditedVideoMetadataRepository(app)
                )
            }
        }
    }
}
