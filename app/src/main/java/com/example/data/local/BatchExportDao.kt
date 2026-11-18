package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchExportDao {

    @Query("SELECT * FROM batch_export_items WHERE userId = :userId ORDER BY queueOrder ASC")
    fun observeBatchItems(userId: String): Flow<List<BatchExportItemEntity>>

    @Query("SELECT * FROM batch_export_items WHERE userId = :userId ORDER BY queueOrder ASC")
    suspend fun getBatchItemsSnapshot(userId: String): List<BatchExportItemEntity>

    @Query("SELECT * FROM batch_export_items WHERE userId = :userId AND status = 'queued' ORDER BY queueOrder ASC LIMIT 1")
    suspend fun getNextQueuedItem(userId: String): BatchExportItemEntity?

    @Query(
        "SELECT * FROM batch_export_items WHERE userId = :userId AND status = 'completed' " +
            "AND uploadToFirebaseStorage = 1 AND uploadStatus IN ('pending', 'queued_upload') ORDER BY queueOrder ASC LIMIT 1"
    )
    suspend fun getNextPendingUploadItem(userId: String): BatchExportItemEntity?

    @Query("SELECT * FROM batch_export_items WHERE userId = :userId AND id = :itemId LIMIT 1")
    suspend fun getItemById(userId: String, itemId: String): BatchExportItemEntity?

    @Query("SELECT COALESCE(MAX(queueOrder), 0) FROM batch_export_items WHERE userId = :userId")
    suspend fun getMaxQueueOrder(userId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: BatchExportItemEntity)

    @Query("UPDATE batch_export_items SET status = :status, progress = :progress, errorMessage = NULL, updatedAtMs = :updatedAtMs WHERE userId = :userId AND id = :itemId")
    suspend fun updateStatusAndProgress(
        userId: String,
        itemId: String,
        status: String,
        progress: Float,
        updatedAtMs: Long
    )

    @Query(
        "UPDATE batch_export_items SET status = 'completed', progress = 1.0, outputFilePath = :outputFilePath, " +
            "outputContentUri = :outputContentUri, galleryUri = :galleryUri, errorMessage = NULL, updatedAtMs = :updatedAtMs " +
            "WHERE userId = :userId AND id = :itemId"
    )
    suspend fun markCompleted(
        userId: String,
        itemId: String,
        outputFilePath: String,
        outputContentUri: String,
        galleryUri: String?,
        updatedAtMs: Long
    )

    @Query(
        "UPDATE batch_export_items SET uploadStatus = :uploadStatus, uploadProgress = :uploadProgress, " +
            "uploadedBytes = :uploadedBytes, totalBytes = :totalBytes, storagePath = :storagePath, " +
            "uploadErrorMessage = NULL, updatedAtMs = :updatedAtMs WHERE userId = :userId AND id = :itemId"
    )
    suspend fun updateUploadProgress(
        userId: String,
        itemId: String,
        uploadStatus: String,
        uploadProgress: Float,
        uploadedBytes: Long,
        totalBytes: Long,
        storagePath: String?,
        updatedAtMs: Long
    )

    @Query(
        "UPDATE batch_export_items SET uploadStatus = 'uploaded', uploadProgress = 1.0, " +
            "uploadedBytes = :totalBytes, totalBytes = :totalBytes, storagePath = :storagePath, " +
            "storageDownloadUrl = :downloadUrl, uploadErrorMessage = NULL, updatedAtMs = :updatedAtMs " +
            "WHERE userId = :userId AND id = :itemId"
    )
    suspend fun markUploadCompleted(
        userId: String,
        itemId: String,
        totalBytes: Long,
        storagePath: String,
        downloadUrl: String,
        updatedAtMs: Long
    )

    @Query(
        "UPDATE batch_export_items SET uploadStatus = 'failed', uploadErrorMessage = :errorMessage, updatedAtMs = :updatedAtMs " +
            "WHERE userId = :userId AND id = :itemId"
    )
    suspend fun markUploadFailed(
        userId: String,
        itemId: String,
        errorMessage: String,
        updatedAtMs: Long
    )

    @Query("UPDATE batch_export_items SET galleryUri = :galleryUri, updatedAtMs = :updatedAtMs WHERE userId = :userId AND id = :itemId")
    suspend fun updateGalleryUri(
        userId: String,
        itemId: String,
        galleryUri: String,
        updatedAtMs: Long
    )

    @Query("UPDATE batch_export_items SET status = 'failed', errorMessage = :errorMessage, updatedAtMs = :updatedAtMs WHERE userId = :userId AND id = :itemId")
    suspend fun markFailed(
        userId: String,
        itemId: String,
        errorMessage: String,
        updatedAtMs: Long
    )

    @Query("UPDATE batch_export_items SET status = 'queued', progress = 0.0, updatedAtMs = :updatedAtMs WHERE userId = :userId AND status = 'processing'")
    suspend fun resetInterruptedProcessing(userId: String, updatedAtMs: Long)

    @Query("UPDATE batch_export_items SET uploadStatus = 'queued_upload', uploadProgress = 0.0, updatedAtMs = :updatedAtMs WHERE userId = :userId AND uploadStatus = 'uploading'")
    suspend fun resetInterruptedUploads(userId: String, updatedAtMs: Long)

    @Query("UPDATE batch_export_items SET queueOrder = :newOrder, updatedAtMs = :updatedAtMs WHERE userId = :userId AND id = :itemId")
    suspend fun updateQueueOrder(userId: String, itemId: String, newOrder: Long, updatedAtMs: Long)

    @Query("DELETE FROM batch_export_items WHERE userId = :userId AND id = :itemId")
    suspend fun deleteItem(userId: String, itemId: String)

    @Query("DELETE FROM batch_export_items WHERE userId = :userId AND status IN ('completed', 'cancelled')")
    suspend fun clearFinished(userId: String)
}
