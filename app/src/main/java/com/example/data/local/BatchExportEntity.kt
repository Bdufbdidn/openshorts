package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue

object BatchExportStatus {
    const val QUEUED = "queued"
    const val PROCESSING = "processing"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

object CloudUploadStatus {
    const val PENDING = "pending"
    const val QUEUED = "queued_upload"
    const val UPLOADING = "uploading"
    const val UPLOADED = "uploaded"
    const val FAILED = "failed"
    const val SKIPPED = "skipped"
}

@Entity(tableName = "batch_export_items")
data class BatchExportItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val sourceVideoUri: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val cuesJson: String,
    val burnStyleJson: String,
    val saveToGallery: Boolean,
    val uploadToFirebaseStorage: Boolean,
    val status: String,
    val progress: Float,
    val outputFilePath: String?,
    val outputContentUri: String?,
    val galleryUri: String?,
    val errorMessage: String?,
    val uploadStatus: String,
    val uploadProgress: Float,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val storagePath: String?,
    val storageDownloadUrl: String?,
    val uploadErrorMessage: String?,
    val queueOrder: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class BatchExportItem(
    val id: String,
    val userId: String,
    val title: String,
    val sourceVideoUri: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val cues: List<TimedCaptionCue> = emptyList(),
    val burnStyle: CaptionBurnStyle = CaptionBurnStyle(),
    val saveToGallery: Boolean = false,
    val uploadToFirebaseStorage: Boolean = true,
    val status: String = BatchExportStatus.QUEUED,
    val progress: Float = 0f,
    val outputFilePath: String? = null,
    val outputContentUri: String? = null,
    val galleryUri: String? = null,
    val errorMessage: String? = null,
    val uploadStatus: String = CloudUploadStatus.PENDING,
    val uploadProgress: Float = 0f,
    val uploadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val storagePath: String? = null,
    val storageDownloadUrl: String? = null,
    val uploadErrorMessage: String? = null,
    val queueOrder: Long = System.currentTimeMillis(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
