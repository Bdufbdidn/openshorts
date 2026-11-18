package com.example.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.caption.CaptionBurnInExportService
import com.example.data.local.BatchExportStatus
import com.example.data.local.CloudUploadStatus
import com.example.data.local.EditedVideoExportSource
import com.example.data.local.EditedVideoMetadataRepository
import com.example.data.media.Media3VideoFilterFactory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

private const val TAG = "BatchExportWorker"

/**
 * WorkManager worker that processes queued trimmed video clips strictly sequentially:
 * 1) Renders/trims each clip to MP4 with Media3 Transformer
 * 2) Optionally saves to device Gallery
 * 3) Uploads exported MP4 to Firebase Storage while streaming real-time progress to Room
 */
@OptIn(UnstableApi::class)
class BatchExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        if (userId.isBlank()) return Result.failure()

        val manager = BatchExportManager(applicationContext)
        val exportService = CaptionBurnInExportService(applicationContext)
        val metadataRepository = EditedVideoMetadataRepository(applicationContext)

        manager.recoverInterruptedWork(userId)

        var processedCount = 0
        val workerJob = Job()
        val progressScope = CoroutineScope(Dispatchers.IO + workerJob)

        try {
            while (!isStopped) {
                val nextEncodeItem = manager.getNextQueuedItem(userId)
                if (nextEncodeItem != null) {
                    manager.updateProgress(
                        userId = userId,
                        itemId = nextEncodeItem.id,
                        status = BatchExportStatus.PROCESSING,
                        progress = 0.02f
                    )

                    var lastSavedProgress = 0.02f
                    var lastSavedTimeMs = System.currentTimeMillis()

                    val sourceUri = try {
                        Uri.parse(nextEncodeItem.sourceVideoUri)
                    } catch (_: Exception) {
                        null
                    }

                    if (sourceUri == null || nextEncodeItem.sourceVideoUri.isBlank()) {
                        manager.markFailed(userId, nextEncodeItem.id, "Invalid or empty source video URI.")
                        continue
                    }

                    val clipEnd = nextEncodeItem.trimEndMs.takeIf { it > nextEncodeItem.trimStartMs }
                    val exportRes = exportService.exportVideoWithBurnedCaptions(
                        sourceVideoUri = sourceUri,
                        cues = nextEncodeItem.cues,
                        style = nextEncodeItem.burnStyle,
                        clipStartMs = nextEncodeItem.trimStartMs.coerceAtLeast(0L),
                        clipEndMs = clipEnd,
                        onProgress = { progress ->
                            val now = System.currentTimeMillis()
                            if (abs(progress - lastSavedProgress) >= 0.05f || now - lastSavedTimeMs >= 450L) {
                                lastSavedProgress = progress
                                lastSavedTimeMs = now
                                progressScope.launch {
                                    manager.updateProgress(
                                        userId = userId,
                                        itemId = nextEncodeItem.id,
                                        status = BatchExportStatus.PROCESSING,
                                        progress = progress
                                    )
                                }
                            }
                        }
                    )

                    exportRes.fold(
                        onSuccess = { exported ->
                            var savedGalleryUri: String? = null
                            if (nextEncodeItem.saveToGallery) {
                                savedGalleryUri = exportService.saveExportToGallery(
                                    filePath = exported.outputFilePath,
                                    displayName = nextEncodeItem.title
                                ).getOrNull()?.toString()
                            }
                            manager.markCompleted(
                                userId = userId,
                                itemId = nextEncodeItem.id,
                                outputFilePath = exported.outputFilePath,
                                outputContentUri = exported.contentUri,
                                galleryUri = savedGalleryUri
                            )
                            val filterSummary = Media3VideoFilterFactory.describeStack(
                                filterStack = nextEncodeItem.burnStyle.filterStack,
                                contrastAdjust = nextEncodeItem.burnStyle.filterContrast,
                                saturationAdjust = nextEncodeItem.burnStyle.filterSaturation,
                                brightnessAdjust = nextEncodeItem.burnStyle.filterBrightness
                            )
                            val generatedDescription = buildString {
                                if (nextEncodeItem.burnStyle.hookText.isNotBlank()) {
                                    append(nextEncodeItem.burnStyle.hookText.trim())
                                }
                                val cueSnippet = nextEncodeItem.cues
                                    .joinToString(" ") { it.text.trim() }
                                    .trim()
                                    .take(220)
                                if (cueSnippet.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(cueSnippet)
                                }
                                if (isBlank()) {
                                    append("Batch exported MP4 clip")
                                    if (filterSummary != "Original") {
                                        append(" • Filters: $filterSummary")
                                    }
                                }
                            }
                            metadataRepository.recordExportedVideo(
                                userId = userId,
                                title = nextEncodeItem.title,
                                description = generatedDescription,
                                videoUri = exported.contentUri,
                                outputFilePath = exported.outputFilePath,
                                galleryUri = savedGalleryUri,
                                sourceVideoUri = nextEncodeItem.sourceVideoUri,
                                trimStartMs = nextEncodeItem.trimStartMs,
                                trimEndMs = nextEncodeItem.trimEndMs,
                                durationMs = exported.durationMs,
                                appliedFiltersSummary = filterSummary,
                                captionCount = nextEncodeItem.cues.size,
                                exportSource = EditedVideoExportSource.BATCH_EXPORT
                            )
                            processedCount++

                            if (nextEncodeItem.uploadToFirebaseStorage && !isStopped) {
                                uploadToFirebaseStorage(
                                    manager = manager,
                                    userId = userId,
                                    itemId = nextEncodeItem.id,
                                    title = nextEncodeItem.title,
                                    outputFilePath = exported.outputFilePath,
                                    progressScope = progressScope
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Batch item ${nextEncodeItem.id} failed", error)
                            manager.markFailed(
                                userId = userId,
                                itemId = nextEncodeItem.id,
                                errorMessage = error.localizedMessage ?: "Export failed."
                            )
                        }
                    )
                    continue
                }

                // Also process any completed local exports queued specifically for Firebase Storage upload retry
                val pendingUploadItem = manager.getNextPendingUploadItem(userId) ?: break
                val path = pendingUploadItem.outputFilePath
                if (path.isNullOrBlank()) {
                    manager.markUploadFailed(userId, pendingUploadItem.id, "Exported MP4 file is missing.")
                    continue
                }
                uploadToFirebaseStorage(
                    manager = manager,
                    userId = userId,
                    itemId = pendingUploadItem.id,
                    title = pendingUploadItem.title,
                    outputFilePath = path,
                    progressScope = progressScope
                )
            }
            return Result.success()
        } catch (e: CancellationException) {
            manager.recoverInterruptedWork(userId)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Batch export worker encountered error after $processedCount items", e)
            manager.recoverInterruptedWork(userId)
            return Result.failure()
        } finally {
            workerJob.cancel()
        }
    }

    private suspend fun uploadToFirebaseStorage(
        manager: BatchExportManager,
        userId: String,
        itemId: String,
        title: String,
        outputFilePath: String,
        progressScope: CoroutineScope
    ) {
        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid != userId) {
            manager.markUploadFailed(
                userId = userId,
                itemId = itemId,
                errorMessage = "Sign in with Google to upload exports to Firebase Storage."
            )
            return
        }

        val file = File(outputFilePath)
        if (!file.exists()) {
            manager.markUploadFailed(userId, itemId, "Local exported MP4 file not found.")
            return
        }

        val safeTitle = title.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "short" }.take(40)
        val storagePath = "users/$userId/exports/${itemId}_${safeTitle}.mp4"
        val storageRef = Firebase.storage.reference.child(storagePath)
        val fileBytes = file.length().coerceAtLeast(1L)

        manager.updateUploadProgress(
            userId = userId,
            itemId = itemId,
            uploadStatus = CloudUploadStatus.UPLOADING,
            uploadProgress = 0.01f,
            uploadedBytes = 0L,
            totalBytes = fileBytes,
            storagePath = storagePath
        )

        var lastSavedProgress = 0.01f
        var lastSavedTimeMs = System.currentTimeMillis()

        try {
            val metadata = storageMetadata {
                contentType = "video/mp4"
                setCustomMetadata("title", title.take(100))
                setCustomMetadata("exportId", itemId)
            }

            suspendCancellableCoroutine { cont ->
                val uploadTask = storageRef.putFile(Uri.fromFile(file), metadata)
                uploadTask.addOnProgressListener { snapshot ->
                    val total = snapshot.totalByteCount.coerceAtLeast(fileBytes)
                    val transferred = snapshot.bytesTransferred.coerceAtLeast(0L)
                    val fraction = (transferred.toFloat() / total.toFloat()).coerceIn(0.01f, 0.99f)
                    val now = System.currentTimeMillis()
                    if (abs(fraction - lastSavedProgress) >= 0.04f || now - lastSavedTimeMs >= 400L) {
                        lastSavedProgress = fraction
                        lastSavedTimeMs = now
                        progressScope.launch {
                            manager.updateUploadProgress(
                                userId = userId,
                                itemId = itemId,
                                uploadStatus = CloudUploadStatus.UPLOADING,
                                uploadProgress = fraction,
                                uploadedBytes = transferred,
                                totalBytes = total,
                                storagePath = storagePath
                            )
                        }
                    }
                }.addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }.addOnFailureListener { exc ->
                    if (cont.isActive) cont.resumeWithException(exc)
                }

                cont.invokeOnCancellation {
                    uploadTask.cancel()
                }
            }

            val downloadUrl = storageRef.downloadUrl.await().toString()
            manager.markUploadCompleted(
                userId = userId,
                itemId = itemId,
                totalBytes = fileBytes,
                storagePath = storagePath,
                downloadUrl = downloadUrl
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            Log.w(TAG, "Firebase Storage upload failed for $itemId (code=${e.errorCode})", e)
            val hint = when (e.errorCode) {
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Storage permission denied. Enable Firebase Storage & security rules for users/{uid}/exports in Firebase Console."
                StorageException.ERROR_OBJECT_NOT_FOUND,
                StorageException.ERROR_BUCKET_NOT_FOUND ->
                    "Firebase Storage bucket is not initialized yet in Firebase Console."
                else -> e.localizedMessage ?: "Firebase Storage upload failed."
            }
            manager.markUploadFailed(userId, itemId, hint)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Storage upload failed for $itemId", e)
            manager.markUploadFailed(
                userId = userId,
                itemId = itemId,
                errorMessage = e.localizedMessage ?: "Firebase Storage upload failed."
            )
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
    }
}
