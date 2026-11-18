package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EditingSessionDao {

    @Query("SELECT * FROM editing_sessions WHERE userId = :userId ORDER BY updatedAtMs DESC")
    fun observeSessionsForUser(userId: String): Flow<List<EditingSessionEntity>>

    @Query(
        "SELECT * FROM editing_sessions WHERE userId = :userId AND workspaceType = :workspaceType ORDER BY updatedAtMs DESC"
    )
    fun observeSessionsByWorkspace(
        userId: String,
        workspaceType: String
    ): Flow<List<EditingSessionEntity>>

    @Query("SELECT * FROM editing_sessions WHERE id = :sessionId AND userId = :userId LIMIT 1")
    suspend fun getSessionById(userId: String, sessionId: String): EditingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: EditingSessionEntity)

    @Query("DELETE FROM editing_sessions WHERE id = :sessionId AND userId = :userId")
    suspend fun deleteSessionById(userId: String, sessionId: String)

    @Query("DELETE FROM editing_sessions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
