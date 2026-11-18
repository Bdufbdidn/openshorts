package com.example.data.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.BatchExportDao
import com.example.data.local.BatchExportItem
import com.example.data.local.BatchExportItemEntity
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.data.local.OpenShortsLocalDatabase
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Room + WorkManager repository that manages a sequential batch export queue
 * of trimmed clips (with optional burned-in captions, hooks, and Firebase Storage uploads).
 */
class BatchExportManager(
    private val dao: BatchExportDao,
    private val workManager: WorkManager?
) {
    constructor(context: Context) : this(
        dao = OpenShortsLocalDatabase.getInstance(context).batchExportDao(),
        workManager = WorkManager.getInstance(context.applicationContext)
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeQueue(userId: String): Flow<List<BatchExportItem>> =
        dao.observeBatchItems(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun enqueueClip(
        userId: String,
        title: String,
        sourceVideoUri: String,
        trimStartMs: Long,
        trimEndMs: Long,
        cues: List<TimedCaptionCue> = emptyList(),
        burnStyle: CaptionBurnStyle = CaptionBurnStyle(),
        saveToGallery: Boolean = false,
        uploadToFirebaseStorage: Boolean = true,
        startImmediately: Boolean = false
    ): Result<BatchExportItem> = withContext(Dispatchers.IO) {
        try {
            if (sourceVideoUri.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Select a valid video before adding to export batch."))
            }
            val now = System.currentTimeMillis()
            val nextOrder = maxOf(now, dao.getMaxQueueOrder(userId) + 1L)
            val safeStart = trimStartMs.coerceAtLeast(0L)
            val safeEnd = if (trimEndMs > safeStart) trimEndMs else -1L
            val cleanTitle = title.trim().ifBlank { "Trimmed Short" }.take(100)

            val item = BatchExportItem(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = cleanTitle,
                sourceVideoUri = sourceVideoUri,
                trimStartMs = safeStart,
                trimEndMs = safeEnd,
                cues = cues,
                burnStyle = burnStyle,
                saveToGallery = saveToGallery,
                uploadToFirebaseStorage = uploadToFirebaseStorage,
                status = BatchExportStatus.QUEUED,
                progress = 0f,
                uploadStatus = if (uploadToFirebaseStorage) CloudUploadStatus.PENDING else CloudUploadStatus.SKIPPED,
                uploadProgress = 0f,
                queueOrder = nextOrder,
                createdAtMs = now,
                updatedAtMs = now
            )
            dao.upsertItem(item.toEntity())
            if (startImmediately) {
                startBatchProcessing(userId)
            }
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startBatchProcessing(userId: String) {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<BatchExportWorker>()
            .setInputData(
                workDataOf(BatchExportWorker.KEY_USER_ID to userId)
            )
            .addTag("openshorts_batch_export")
            .build()

        // ExistingWorkPolicy.KEEP guarantees only ONE sequential worker executes at a time
        wm.enqueueUniqueWork(
            uniqueWorkName(userId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelBatchProcessing(userId: String) {
        workManager?.cancelUniqueWork(uniqueWorkName(userId))
    }

    suspend fun getNextQueuedItem(userId: String): BatchExportItem? = withContext(Dispatchers.IO) {
        dao.getNextQueuedItem(userId)?.toDomain()
    }

    suspend fun getNextPendingUploadItem(userId: String): BatchExportItem? = withContext(Dispatchers.IO) {
        dao.getNextPendingUploadItem(userId)?.toDomain()
    }

    suspend fun recoverInterruptedWork(userId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.resetInterruptedProcessing(userId, now)
        dao.resetInterruptedUploads(userId, now)
    }

    suspend fun updateProgress(
        userId: String,
        itemId: String,
        status: String,
        progress: Float
    ) = withContext(Dispatchers.IO) {
        dao.updateStatusAndProgress(
            userId = userId,
            itemId = itemId,
            status = status,
            progress = progress.coerceIn(0f, 1f),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun markCompleted(
        userId: String,
        itemId: String,
        outputFilePath: String,
        outputContentUri: String,
        galleryUri: String?
    ) = withContext(Dispatchers.IO) {
        dao.markCompleted(
            userId = userId,
            itemId = itemId,
            outputFilePath = outputFilePath,
            outputContentUri = outputContentUri,
            galleryUri = galleryUri,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun updateUploadProgress(
        userId: String,
        itemId: String,
        uploadStatus: String,
        uploadProgress: Float,
        uploadedBytes: Long,
        totalBytes: Long,
        storagePath: String?
    ) = withContext(Dispatchers.IO) {
        dao.updateUploadProgress(
            userId = userId,
            itemId = itemId,
            uploadStatus = uploadStatus,
            uploadProgress = uploadProgress.coerceIn(0f, 1f),
            uploadedBytes = uploadedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            storagePath = storagePath,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun markUploadCompleted(
        userId: String,
        itemId: String,
        totalBytes: Long,
        storagePath: String,
        downloadUrl: String
    ) = withContext(Dispatchers.IO) {
        dao.markUploadCompleted(
            userId = userId,
            itemId = itemId,
            totalBytes = totalBytes.coerceAtLeast(0L),
            storagePath = storagePath,
            downloadUrl = downloadUrl,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun markUploadFailed(
        userId: String,
        itemId: String,
        errorMessage: String
    ) = withContext(Dispatchers.IO) {
        dao.markUploadFailed(
            userId = userId,
            itemId = itemId,
            errorMessage = errorMessage,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun updateGalleryUri(
        userId: String,
        itemId: String,
        galleryUri: String
    ) = withContext(Dispatchers.IO) {
        dao.updateGalleryUri(
            userId = userId,
            itemId = itemId,
            galleryUri = galleryUri,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun markFailed(
        userId: String,
        itemId: String,
        errorMessage: String
    ) = withContext(Dispatchers.IO) {
        dao.markFailed(
            userId = userId,
            itemId = itemId,
            errorMessage = errorMessage,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun retryItem(userId: String, itemId: String, startProcessing: Boolean = true) = withContext(Dispatchers.IO) {
        dao.updateStatusAndProgress(
            userId = userId,
            itemId = itemId,
            status = BatchExportStatus.QUEUED,
            progress = 0f,
            updatedAtMs = System.currentTimeMillis()
        )
        if (startProcessing) {
            startBatchProcessing(userId)
        }
    }

    suspend fun retryUploadOnly(userId: String, itemId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getItemById(userId, itemId) ?: return@withContext
        dao.upsertItem(
            existing.copy(
                uploadToFirebaseStorage = true,
                uploadStatus = CloudUploadStatus.QUEUED,
                uploadProgress = 0f,
                uploadedBytes = 0L,
                uploadErrorMessage = null,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        startBatchProcessing(userId)
    }

    suspend fun moveItem(userId: String, itemId: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val snapshot = dao.getBatchItemsSnapshot(userId)
            .filter { it.status == BatchExportStatus.QUEUED }
        val index = snapshot.indexOfFirst { it.id == itemId }
        if (index < 0) return@withContext
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in snapshot.indices) return@withContext

        val current = snapshot[index]
        val other = snapshot[targetIndex]
        val now = System.currentTimeMillis()
        dao.updateQueueOrder(userId, current.id, other.queueOrder, now)
        dao.updateQueueOrder(userId, other.id, current.queueOrder, now)
    }

    suspend fun removeItem(userId: String, itemId: String) = withContext(Dispatchers.IO) {
        dao.deleteItem(userId, itemId)
    }

    suspend fun clearFinished(userId: String) = withContext(Dispatchers.IO) {
        dao.clearFinished(userId)
    }

    private fun uniqueWorkName(userId: String): String = "batch_export_$userId"

    private fun BatchExportItem.toEntity(): BatchExportItemEntity {
        val cuesJson = json.encodeToString(
            cues.map {
                CuePayload(
                    index = it.index,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    text = it.text,
                    highlightWord = it.highlightWord
                )
            }
        )
        val styleJson = json.encodeToString(
            StylePayload(
                position = burnStyle.position,
                animation = burnStyle.animation,
                fontName = burnStyle.fontName,
                textColorHex = burnStyle.textColorHex,
                highlightColorHex = burnStyle.highlightColorHex,
                hookText = burnStyle.hookText,
                hookPosition = burnStyle.hookPosition,
                hookSize = burnStyle.hookSize,
                hookDurationSec = burnStyle.hookDurationSec,
                filterStack = burnStyle.filterStack,
                filterContrast = burnStyle.filterContrast,
                filterSaturation = burnStyle.filterSaturation,
                filterBrightness = burnStyle.filterBrightness
            )
        )
        return BatchExportItemEntity(
            id = id,
            userId = userId,
            title = title,
            sourceVideoUri = sourceVideoUri,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            cuesJson = cuesJson,
            burnStyleJson = styleJson,
            saveToGallery = saveToGallery,
            uploadToFirebaseStorage = uploadToFirebaseStorage,
            status = status,
            progress = progress,
            outputFilePath = outputFilePath,
            outputContentUri = outputContentUri,
            galleryUri = galleryUri,
            errorMessage = errorMessage,
            uploadStatus = uploadStatus,
            uploadProgress = uploadProgress,
            uploadedBytes = uploadedBytes,
            totalBytes = totalBytes,
            storagePath = storagePath,
            storageDownloadUrl = storageDownloadUrl,
            uploadErrorMessage = uploadErrorMessage,
            queueOrder = queueOrder,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    private fun BatchExportItemEntity.toDomain(): BatchExportItem {
        val decodedCues = runCatching {
            json.decodeFromString<List<CuePayload>>(cuesJson).map {
                TimedCaptionCue(
                    index = it.index,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    text = it.text,
                    highlightWord = it.highlightWord
                )
            }
        }.getOrDefault(emptyList())

        val decodedStyle = runCatching {
            val s = json.decodeFromString<StylePayload>(burnStyleJson)
            CaptionBurnStyle(
                position = s.position,
                animation = s.animation,
                fontName = s.fontName,
                textColorHex = s.textColorHex,
                highlightColorHex = s.highlightColorHex,
                hookText = s.hookText,
                hookPosition = s.hookPosition,
                hookSize = s.hookSize,
                hookDurationSec = s.hookDurationSec,
                filterStack = s.filterStack,
                filterContrast = s.filterContrast,
                filterSaturation = s.filterSaturation,
                filterBrightness = s.filterBrightness
            )
        }.getOrDefault(CaptionBurnStyle())

        return BatchExportItem(
            id = id,
            userId = userId,
            title = title,
            sourceVideoUri = sourceVideoUri,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            cues = decodedCues,
            burnStyle = decodedStyle,
            saveToGallery = saveToGallery,
            uploadToFirebaseStorage = uploadToFirebaseStorage,
            status = status,
            progress = progress,
            outputFilePath = outputFilePath,
            outputContentUri = outputContentUri,
            galleryUri = galleryUri,
            errorMessage = errorMessage,
            uploadStatus = uploadStatus,
            uploadProgress = uploadProgress,
            uploadedBytes = uploadedBytes,
            totalBytes = totalBytes,
            storagePath = storagePath,
            storageDownloadUrl = storageDownloadUrl,
            uploadErrorMessage = uploadErrorMessage,
            queueOrder = queueOrder,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    @Serializable
    private data class CuePayload(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val highlightWord: String = ""
    )

    @Serializable
    private data class StylePayload(
        val position: String = "bottom",
        val animation: String = "word-highlight",
        val fontName: String = "SpaceGrotesk",
        val textColorHex: String = "#FFFFFF",
        val highlightColorHex: String = "#3B82F6",
        val hookText: String = "",
        val hookPosition: String = "top",
        val hookSize: String = "M",
        val hookDurationSec: Long = 5L,
        val filterStack: List<String> = emptyList(),
        val filterContrast: Float = 0f,
        val filterSaturation: Float = 0f,
        val filterBrightness: Float = 0f
    )
}
