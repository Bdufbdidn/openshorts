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
import com.example.data.model.AiShortProject
import com.example.data.model.UgcAvatar
import com.example.data.model.UiState
import com.example.data.repository.OpenShortsFirestoreRepository
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

private const val TAG = "UgcGalleryVM"

class UgcGalleryViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository
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
                    aiRepo = OpenShortsAiRepository(app)
                )
            }
        }
    }
}
