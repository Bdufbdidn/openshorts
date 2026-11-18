package com.example.data.local

import android.content.Context
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue
import com.example.data.model.TrimChatMessage
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
 * Local storage manager backed by Room for saving, listing, and resuming
 * on-device video editing sessions across Clip Studio and Caption & Burn Export.
 */
class EditingSessionStorageManager(
    private val dao: EditingSessionDao
) {
    constructor(context: Context) : this(
        OpenShortsLocalDatabase.getInstance(context).editingSessionDao()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeSessions(userId: String): Flow<List<EditingSession>> =
        dao.observeSessionsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeSessionsByWorkspace(
        userId: String,
        workspaceType: String
    ): Flow<List<EditingSession>> =
        dao.observeSessionsByWorkspace(userId, workspaceType).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun saveSession(session: EditingSession): Result<EditingSession> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cleanId = session.id.ifBlank { UUID.randomUUID().toString() }
            val cleanTitle = session.title.trim().ifBlank {
                session.sourceLabel.trim().takeIf { it.isNotBlank() } ?: "Untitled Video Edit"
            }.take(120)
            val saved = session.copy(
                id = cleanId,
                title = cleanTitle,
                createdAtMs = if (session.createdAtMs > 0L) session.createdAtMs else now,
                updatedAtMs = now
            )
            dao.upsertSession(saved.toEntity())
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSession(userId: String, sessionId: String): Result<EditingSession?> = withContext(Dispatchers.IO) {
        try {
            if (sessionId.isBlank()) return@withContext Result.success(null)
            Result.success(dao.getSessionById(userId, sessionId)?.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteSessionById(userId, sessionId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllSessions(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteAllForUser(userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun EditingSession.toEntity(): EditingSessionEntity {
        val serializedCues = json.encodeToString(
            cues.map {
                StoredCuePayload(
                    index = it.index,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    text = it.text,
                    highlightWord = it.highlightWord
                )
            }
        )
        val serializedStyle = json.encodeToString(
            StoredBurnStylePayload(
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
        val serializedChat = json.encodeToString(
            trimChatHistory.map {
                StoredTrimChatPayload(
                    id = it.id,
                    fromUser = it.fromUser,
                    text = it.text,
                    suggestedStartSec = it.suggestedStartSec,
                    suggestedEndSec = it.suggestedEndSec,
                    appliedTrim = it.appliedTrim
                )
            }
        )

        return EditingSessionEntity(
            id = id,
            userId = userId,
            title = title,
            workspaceType = workspaceType,
            sourceMode = sourceMode,
            sourceLabel = sourceLabel,
            videoUri = videoUri,
            thumbnailUrl = thumbnailUrl,
            totalDurationMs = totalDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            guidanceNotes = guidanceNotes,
            liveSkillId = liveSkillId,
            liveLookbackMinutes = liveLookbackMinutes,
            targetClipsCount = targetClipsCount,
            activeJobId = activeJobId,
            activeClipId = activeClipId,
            transcriptText = transcriptText,
            detectedLanguage = detectedLanguage,
            cuesJson = serializedCues,
            burnStyleJson = serializedStyle,
            trimChatHistoryJson = serializedChat,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    private fun EditingSessionEntity.toDomain(): EditingSession {
        val decodedCues = runCatching {
            json.decodeFromString<List<StoredCuePayload>>(cuesJson).map {
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
            val s = json.decodeFromString<StoredBurnStylePayload>(burnStyleJson)
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

        val decodedChat = runCatching {
            json.decodeFromString<List<StoredTrimChatPayload>>(trimChatHistoryJson).map {
                TrimChatMessage(
                    id = it.id,
                    fromUser = it.fromUser,
                    text = it.text,
                    suggestedStartSec = it.suggestedStartSec,
                    suggestedEndSec = it.suggestedEndSec,
                    appliedTrim = it.appliedTrim
                )
            }
        }.getOrDefault(emptyList())

        return EditingSession(
            id = id,
            userId = userId,
            title = title,
            workspaceType = workspaceType,
            sourceMode = sourceMode,
            sourceLabel = sourceLabel,
            videoUri = videoUri,
            thumbnailUrl = thumbnailUrl,
            totalDurationMs = totalDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            guidanceNotes = guidanceNotes,
            liveSkillId = liveSkillId,
            liveLookbackMinutes = liveLookbackMinutes,
            targetClipsCount = targetClipsCount,
            activeJobId = activeJobId,
            activeClipId = activeClipId,
            transcriptText = transcriptText,
            detectedLanguage = detectedLanguage,
            cues = decodedCues,
            burnStyle = decodedStyle,
            trimChatHistory = decodedChat,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    @Serializable
    private data class StoredCuePayload(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val highlightWord: String = ""
    )

    @Serializable
    private data class StoredBurnStylePayload(
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

    @Serializable
    private data class StoredTrimChatPayload(
        val id: String,
        val fromUser: Boolean,
        val text: String,
        val suggestedStartSec: Double? = null,
        val suggestedEndSec: Double? = null,
        val appliedTrim: Boolean = false
    )
}
