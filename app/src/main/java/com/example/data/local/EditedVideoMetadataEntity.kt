package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object EditedVideoExportSource {
    const val CAPTION_EXPORT = "caption_export"
    const val BATCH_EXPORT = "batch_export"
    const val MANUAL_ENTRY = "manual_entry"
}

/**
 * Room Entity storing local metadata for edited/exported videos,
 * including title, description, source/output references, and timestamps.
 */
@Entity(
    tableName = "edited_videos_metadata",
    indices = [
        Index(value = ["userId", "exportedAtMs"]),
        Index(value = ["videoUri"])
    ]
)
data class EditedVideoMetadataEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val videoUri: String,
    val outputFilePath: String?,
    val galleryUri: String?,
    val sourceVideoUri: String?,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val durationMs: Long,
    val appliedFiltersSummary: String,
    val captionCount: Int,
    val exportSource: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val exportedAtMs: Long
)

data class EditedVideoMetadata(
    val id: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val videoUri: String,
    val outputFilePath: String? = null,
    val galleryUri: String? = null,
    val sourceVideoUri: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val durationMs: Long = 0L,
    val appliedFiltersSummary: String = "Original",
    val captionCount: Int = 0,
    val exportSource: String = EditedVideoExportSource.CAPTION_EXPORT,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val exportedAtMs: Long = System.currentTimeMillis()
)

fun EditedVideoMetadataEntity.toDomain(): EditedVideoMetadata = EditedVideoMetadata(
    id = id,
    userId = userId,
    title = title,
    description = description,
    videoUri = videoUri,
    outputFilePath = outputFilePath,
    galleryUri = galleryUri,
    sourceVideoUri = sourceVideoUri,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    durationMs = durationMs,
    appliedFiltersSummary = appliedFiltersSummary,
    captionCount = captionCount,
    exportSource = exportSource,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    exportedAtMs = exportedAtMs
)

fun EditedVideoMetadata.toEntity(): EditedVideoMetadataEntity = EditedVideoMetadataEntity(
    id = id,
    userId = userId,
    title = title,
    description = description,
    videoUri = videoUri,
    outputFilePath = outputFilePath,
    galleryUri = galleryUri,
    sourceVideoUri = sourceVideoUri,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    durationMs = durationMs,
    appliedFiltersSummary = appliedFiltersSummary,
    captionCount = captionCount,
    exportSource = exportSource,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    exportedAtMs = exportedAtMs
)
