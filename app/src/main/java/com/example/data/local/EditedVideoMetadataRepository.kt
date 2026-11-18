package com.example.data.local

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository abstracting Room DAO operations for local edited video metadata
 * (titles, descriptions, export details, and creation/update/export timestamps).
 */
class EditedVideoMetadataRepository(
    private val dao: EditedVideoMetadataDao
) {
    constructor(context: Context) : this(
        OpenShortsLocalDatabase.getInstance(context).editedVideoMetadataDao()
    )

    fun observeEditedVideos(userId: String): Flow<List<EditedVideoMetadata>> =
        dao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }

    fun searchEditedVideos(userId: String, query: String): Flow<List<EditedVideoMetadata>> {
        val cleanQuery = query.trim()
        val flow = if (cleanQuery.isBlank()) {
            dao.observeAllForUser(userId)
        } else {
            dao.searchForUser(userId, cleanQuery)
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    fun observeEditedVideosInRange(
        userId: String,
        startTimestampMs: Long,
        endTimestampMs: Long
    ): Flow<List<EditedVideoMetadata>> =
        dao.observeByTimeRange(userId, startTimestampMs, endTimestampMs)
            .map { entities -> entities.map { it.toDomain() } }

    fun observeEditedVideoCount(userId: String): Flow<Int> =
        dao.observeCountForUser(userId)

    suspend fun getById(userId: String, id: String): Result<EditedVideoMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(dao.getById(userId, id)?.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveMetadata(metadata: EditedVideoMetadata): Result<EditedVideoMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val cleanId = metadata.id.ifBlank { UUID.randomUUID().toString() }
                val cleanTitle = metadata.title.trim().ifBlank { "Edited Short" }.take(120)
                val cleanDescription = metadata.description.trim().take(2000)
                val existing = dao.getById(metadata.userId, cleanId)
                val saved = metadata.copy(
                    id = cleanId,
                    title = cleanTitle,
                    description = cleanDescription,
                    createdAtMs = existing?.createdAtMs
                        ?: metadata.createdAtMs.takeIf { it > 0L }
                        ?: now,
                    exportedAtMs = metadata.exportedAtMs.takeIf { it > 0L } ?: now,
                    updatedAtMs = now
                )
                dao.upsert(saved.toEntity())
                Result.success(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun recordExportedVideo(
        userId: String,
        title: String,
        description: String,
        videoUri: String,
        outputFilePath: String? = null,
        galleryUri: String? = null,
        sourceVideoUri: String? = null,
        trimStartMs: Long = 0L,
        trimEndMs: Long = -1L,
        durationMs: Long = 0L,
        appliedFiltersSummary: String = "Original",
        captionCount: Int = 0,
        exportSource: String = EditedVideoExportSource.CAPTION_EXPORT
    ): Result<EditedVideoMetadata> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val existingByUri = if (videoUri.isNotBlank()) dao.getByVideoUri(userId, videoUri) else null
            val item = EditedVideoMetadata(
                id = existingByUri?.id ?: UUID.randomUUID().toString(),
                userId = userId,
                title = title.trim().ifBlank { "Edited Short" }.take(120),
                description = description.trim().take(2000),
                videoUri = videoUri,
                outputFilePath = outputFilePath ?: existingByUri?.outputFilePath,
                galleryUri = galleryUri ?: existingByUri?.galleryUri,
                sourceVideoUri = sourceVideoUri ?: existingByUri?.sourceVideoUri,
                trimStartMs = trimStartMs.coerceAtLeast(0L),
                trimEndMs = trimEndMs,
                durationMs = durationMs.coerceAtLeast(0L),
                appliedFiltersSummary = appliedFiltersSummary.ifBlank { "Original" },
                captionCount = captionCount.coerceAtLeast(0),
                exportSource = exportSource,
                createdAtMs = existingByUri?.createdAtMs ?: now,
                updatedAtMs = now,
                exportedAtMs = now
            )
            dao.upsert(item.toEntity())
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTitleAndDescription(
        userId: String,
        id: String,
        title: String,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim().ifBlank { "Edited Short" }.take(120)
            val cleanDesc = description.trim().take(2000)
            dao.updateTitleAndDescription(
                userId = userId,
                id = id,
                title = cleanTitle,
                description = cleanDesc,
                updatedAtMs = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGalleryUri(
        userId: String,
        id: String,
        galleryUri: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.updateGalleryUri(
                userId = userId,
                id = id,
                galleryUri = galleryUri,
                updatedAtMs = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteById(userId: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteById(userId, id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAll(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteAllForUser(userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
