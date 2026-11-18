package com.example.ui.youtube

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
import com.example.data.model.TitleRecommendation
import com.example.data.model.UiState
import com.example.data.model.YoutubeStudioProject
import com.example.data.repository.OpenShortsFirestoreRepository
import com.google.firebase.ai.Chat
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

private const val TAG = "YouTubeStudioVM"

enum class YouTubeStep(val index: Int, val label: String) {
    INPUT(0, "1. Source"),
    TITLES(1, "2. Titles & Chat"),
    THUMBNAIL(2, "3. 16:9 Thumbnail"),
    DESCRIPTION(3, "4. Description"),
    PUBLISH(4, "5. Export & Save")
}

data class ChatBubble(
    val isUser: Boolean,
    val text: String
)

class YouTubeStudioViewModel(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository
) : ViewModel() {

    private val _step = MutableStateFlow(YouTubeStep.INPUT)
    val step: StateFlow<YouTubeStep> = _step.asStateFlow()

    private val _titlesState = MutableStateFlow<UiState<TitleRecommendation>>(UiState.Idle)
    val titlesState: StateFlow<UiState<TitleRecommendation>> = _titlesState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatBubble>>(emptyList())
    val chatMessages: StateFlow<List<ChatBubble>> = _chatMessages.asStateFlow()

    private val _isChatBusy = MutableStateFlow(false)
    val isChatBusy: StateFlow<Boolean> = _isChatBusy.asStateFlow()

    private val _thumbnailsState = MutableStateFlow<UiState<List<Pair<Bitmap, String>>>>(UiState.Idle)
    val thumbnailsState: StateFlow<UiState<List<Pair<Bitmap, String>>>> = _thumbnailsState.asStateFlow()

    private val _selectedThumbnailIndex = MutableStateFlow(0)
    val selectedThumbnailIndex: StateFlow<Int> = _selectedThumbnailIndex.asStateFlow()

    private val _descriptionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val descriptionState: StateFlow<UiState<String>> = _descriptionState.asStateFlow()

    private val _saveState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val saveState: StateFlow<UiState<String>> = _saveState.asStateFlow()

    val savedProjectsState: StateFlow<UiState<List<YoutubeStudioProject>>> =
        firestoreRepo.observeYoutubeProjects(currentUserId)
            .map<List<YoutubeStudioProject>, UiState<List<YoutubeStudioProject>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Failed to observe YouTube projects", root)
                emit(UiState.Error(root.message ?: "Failed to load saved projects."))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private var titleChat: Chat? = null

    fun goToStep(target: YouTubeStep) {
        _step.value = target
    }

    fun stepBack(): Boolean {
        val prev = YouTubeStep.entries.getOrNull(_step.value.index - 1) ?: return false
        _step.value = prev
        return true
    }

    fun selectThumbnail(index: Int) {
        _selectedThumbnailIndex.value = index
    }

    fun generateViralTitles(videoUri: Uri?, topicOrNotes: String) {
        if (videoUri == null && topicOrNotes.isBlank()) {
            _titlesState.value = UiState.Error("Pick a video or describe your video topic first.")
            return
        }
        viewModelScope.launch {
            _titlesState.value = UiState.Loading
            try {
                val rec = aiRepo.suggestYoutubeTitles(videoUri, topicOrNotes)
                _titlesState.value = UiState.Success(rec)
                titleChat = aiRepo.startTitleRefinementChat(
                    "Topic: $topicOrNotes\nTop picks: ${rec.topPick1} / ${rec.topPick2}"
                )
                _chatMessages.value = emptyList()
                _step.value = YouTubeStep.TITLES
            } catch (e: TimeoutCancellationException) {
                _titlesState.value = UiState.Error("Title generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Title suggestion failed", e)
                _titlesState.value = UiState.Error(e.localizedMessage ?: "Failed to generate titles.")
            }
        }
    }

    fun sendTitleChatMessage(message: String) {
        if (message.isBlank()) return
        val activeChat = titleChat ?: aiRepo.startTitleRefinementChat("YouTube video title refinement").also {
            titleChat = it
        }
        viewModelScope.launch {
            _chatMessages.value = _chatMessages.value + ChatBubble(isUser = true, text = message.trim())
            _isChatBusy.value = true
            try {
                val reply = aiRepo.sendChatMessage(activeChat, message.trim())
                _chatMessages.value = _chatMessages.value + ChatBubble(isUser = false, text = reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatBubble(
                    isUser = false,
                    text = "Error: ${e.localizedMessage ?: "Chat request failed."}"
                )
            } finally {
                _isChatBusy.value = false
            }
        }
    }

    fun generateThumbnails(
        title: String,
        extraPrompt: String,
        faceUri: Uri?,
        bgUri: Uri?,
        count: Int
    ) {
        if (title.isBlank()) {
            _thumbnailsState.value = UiState.Error("Enter or select a title first.")
            return
        }
        viewModelScope.launch {
            _thumbnailsState.value = UiState.Loading
            try {
                val faceBitmap = faceUri?.let { aiRepo.decodeUriToBitmap(it) }
                val bgBitmap = bgUri?.let { aiRepo.decodeUriToBitmap(it) }
                val generated = buildList {
                    repeat(count.coerceIn(1, 3)) { idx ->
                        val variantPrompt = if (count > 1) {
                            "$extraPrompt (Composition variation #${idx + 1})"
                        } else extraPrompt
                        add(aiRepo.generateYoutubeThumbnail(title, variantPrompt, faceBitmap, bgBitmap))
                    }
                }
                _selectedThumbnailIndex.value = 0
                _thumbnailsState.value = UiState.Success(generated)
            } catch (e: TimeoutCancellationException) {
                _thumbnailsState.value = UiState.Error("Thumbnail generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail generation failed", e)
                _thumbnailsState.value = UiState.Error(e.localizedMessage ?: "Thumbnail generation failed.")
            }
        }
    }

    fun generateDescription(title: String, notes: String) {
        if (title.isBlank()) {
            _descriptionState.value = UiState.Error("Select a video title first.")
            return
        }
        viewModelScope.launch {
            _descriptionState.value = UiState.Loading
            try {
                val desc = aiRepo.generateYoutubeDescription(title, notes)
                _descriptionState.value = UiState.Success(desc)
            } catch (e: TimeoutCancellationException) {
                _descriptionState.value = UiState.Error("Description generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Description generation failed", e)
                _descriptionState.value = UiState.Error(e.localizedMessage ?: "Failed to generate description.")
            }
        }
    }

    fun saveProject(
        mode: String,
        selectedTitle: String,
        description: String,
        thumbnailPrompt: String
    ) {
        if (selectedTitle.isBlank()) {
            _saveState.value = UiState.Error("Select a title before saving.")
            return
        }
        val thumbs = (_thumbnailsState.value as? UiState.Success)?.data.orEmpty()
        val chosenPath = thumbs.getOrNull(_selectedThumbnailIndex.value)?.second
        val suggested = (_titlesState.value as? UiState.Success)?.data?.titles.orEmpty()

        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val project = YoutubeStudioProject(
                userId = currentUserId,
                mode = mode,
                selectedTitle = selectedTitle.trim(),
                suggestedTitles = suggested,
                description = description.takeIf { it.isNotBlank() },
                thumbnailPrompt = thumbnailPrompt.takeIf { it.isNotBlank() },
                thumbnailPath = chosenPath,
                publishedStatus = "draft"
            )
            val res = firestoreRepo.saveYoutubeProject(currentUserId, project)
            _saveState.value = res.fold(
                onSuccess = { UiState.Success("YouTube Studio project saved.") },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to save project.") }
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteYoutubeProject(currentUserId, projectId)
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                YouTubeStudioViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = OpenShortsFirestoreRepository(db),
                    aiRepo = OpenShortsAiRepository(app)
                )
            }
        }
    }
}
