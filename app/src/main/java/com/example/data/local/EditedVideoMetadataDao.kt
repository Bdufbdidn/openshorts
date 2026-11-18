package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for storing, querying, searching, updating, and deleting
 * local edited video metadata (titles, descriptions, and timestamps).
 */
@Dao
interface EditedVideoMetadataDao {

    @Query(
        "SELECT * FROM edited_videos_metadata " +
            "WHERE userId = :userId " +
            "ORDER BY exportedAtMs DESC, updatedAtMs DESC"
    )
    fun observeAllForUser(userId: String): Flow<List<EditedVideoMetadataEntity>>

    @Query(
        "SELECT * FROM edited_videos_metadata " +
            "WHERE userId = :userId " +
            "AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') " +
            "ORDER BY exportedAtMs DESC, updatedAtMs DESC"
    )
    fun searchForUser(userId: String, query: String): Flow<List<EditedVideoMetadataEntity>>

    @Query(
        "SELECT * FROM edited_videos_metadata " +
            "WHERE userId = :userId " +
            "AND exportedAtMs BETWEEN :startTimestampMs AND :endTimestampMs " +
            "ORDER BY exportedAtMs DESC"
    )
    fun observeByTimeRange(
        userId: String,
        startTimestampMs: Long,
        endTimestampMs: Long
    ): Flow<List<EditedVideoMetadataEntity>>

    @Query(
        "SELECT * FROM edited_videos_metadata " +
            "WHERE userId = :userId " +
            "ORDER BY exportedAtMs DESC, updatedAtMs DESC " +
            "LIMIT :limit"
    )
    fun observeRecent(userId: String, limit: Int): Flow<List<EditedVideoMetadataEntity>>

    @Query("SELECT COUNT(*) FROM edited_videos_metadata WHERE userId = :userId")
    fun observeCountForUser(userId: String): Flow<Int>

    @Query("SELECT * FROM edited_videos_metadata WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getById(userId: String, id: String): EditedVideoMetadataEntity?

    @Query("SELECT * FROM edited_videos_metadata WHERE userId = :userId AND videoUri = :videoUri LIMIT 1")
    suspend fun getByVideoUri(userId: String, videoUri: String): EditedVideoMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: EditedVideoMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metadataList: List<EditedVideoMetadataEntity>)

    @Update
    suspend fun update(metadata: EditedVideoMetadataEntity)

    @Query(
        "UPDATE edited_videos_metadata " +
            "SET title = :title, description = :description, updatedAtMs = :updatedAtMs " +
            "WHERE userId = :userId AND id = :id"
    )
    suspend fun updateTitleAndDescription(
        userId: String,
        id: String,
        title: String,
        description: String,
        updatedAtMs: Long
    )

    @Query(
        "UPDATE edited_videos_metadata " +
            "SET galleryUri = :galleryUri, updatedAtMs = :updatedAtMs " +
            "WHERE userId = :userId AND id = :id"
    )
    suspend fun updateGalleryUri(
        userId: String,
        id: String,
        galleryUri: String,
        updatedAtMs: Long
    )

    @Delete
    suspend fun delete(metadata: EditedVideoMetadataEntity)

    @Query("DELETE FROM edited_videos_metadata WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: String, id: String)

    @Query("DELETE FROM edited_videos_metadata WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
