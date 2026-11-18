package com.example.ui.export

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.example.data.caption.CaptionBurnInExportService
import com.example.data.export.BatchExportManager
import com.example.data.local.BatchExportItem
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class BatchExportQueueViewModel(
    private val currentUserId: String,
    private val batchManager: BatchExportManager,
    private val exportService: CaptionBurnInExportService
) : ViewModel() {

    val queueItems: StateFlow<List<BatchExportItem>> = batchManager.observeQueue(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun startBatchProcessing() {
        batchManager.startBatchProcessing(currentUserId)
        _feedbackMessage.value = "Sequential export & Firebase Storage upload started."
    }

    fun cancelBatchProcessing() {
        batchManager.cancelBatchProcessing(currentUserId)
        viewModelScope.launch {
            batchManager.recoverInterruptedWork(currentUserId)
        }
        _feedbackMessage.value = "Batch export paused."
    }

    fun enqueueClip(
        title: String,
        videoUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        cues: List<TimedCaptionCue> = emptyList(),
        burnStyle: CaptionBurnStyle = CaptionBurnStyle(),
        saveToGallery: Boolean = false,
        uploadToFirebaseStorage: Boolean = true,
        startImmediately: Boolean = false
    ) {
        viewModelScope.launch {
            batchManager.enqueueClip(
                userId = currentUserId,
                title = title,
                sourceVideoUri = videoUri.toString(),
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                cues = cues,
                burnStyle = burnStyle,
                saveToGallery = saveToGallery,
                uploadToFirebaseStorage = uploadToFirebaseStorage,
                startImmediately = startImmediately
            ).fold(
                onSuccess = { added ->
                    _feedbackMessage.value = if (startImmediately) {
                        "Added \"${added.title}\" & started sequential batch."
                    } else {
                        "Added \"${added.title}\" to export batch."
                    }
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to add clip to batch."
                }
            )
        }
    }

    fun moveItem(itemId: String, moveUp: Boolean) {
        viewModelScope.launch {
            batchManager.moveItem(currentUserId, itemId, moveUp)
        }
    }

    fun retryItem(itemId: String) {
        viewModelScope.launch {
            batchManager.retryItem(currentUserId, itemId, startProcessing = true)
            _feedbackMessage.value = "Re-queued failed clip for export."
        }
    }

    fun retryCloudUpload(itemId: String) {
        viewModelScope.launch {
            batchManager.retryUploadOnly(currentUserId, itemId)
            _feedbackMessage.value = "Queued Firebase Storage upload retry."
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            batchManager.removeItem(currentUserId, itemId)
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            batchManager.clearFinished(currentUserId)
            _feedbackMessage.value = "Cleared completed exports from queue."
        }
    }

    fun saveCompletedToGallery(item: BatchExportItem) {
        val filePath = item.outputFilePath ?: return
        viewModelScope.launch {
            exportService.saveExportToGallery(filePath, item.title).fold(
                onSuccess = { galleryUri ->
                    batchManager.updateGalleryUri(currentUserId, item.id, galleryUri.toString())
                    _feedbackMessage.value = "Saved \"${item.title}\" to Movies/OpenShorts."
                },
                onFailure = { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Failed to save to gallery."
                }
            )
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                BatchExportQueueViewModel(
                    currentUserId = currentUserId,
                    batchManager = BatchExportManager(app),
                    exportService = CaptionBurnInExportService(app)
                )
            }
        }
    }
}
