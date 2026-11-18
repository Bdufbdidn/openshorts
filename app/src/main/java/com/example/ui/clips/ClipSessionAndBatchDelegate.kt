package com.example.ui.clips

import com.example.data.export.BatchExportManager
import com.example.data.local.EditingSession
import com.example.data.local.EditingSessionStorageManager
import com.example.data.local.WORKSPACE_CLIP_STUDIO
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.GeneratedClip
import com.example.data.model.TrimChatMessage

internal class ClipSessionAndBatchDelegate(
    private val currentUserId: String,
    private val sessionStorageManager: EditingSessionStorageManager,
    private val batchExportManager: BatchExportManager
) {
    suspend fun saveClipStudioSession(
        activeSessionId: String?,
        trimChatHistory: List<TrimChatMessage>,
        title: String,
        saveAsNew: Boolean,
        sourceMode: String,
        sourceLabel: String,
        videoUri: String?,
        thumbnailUrl: String?,
        totalDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        guidanceNotes: String,
        liveSkillId: String,
        liveLookbackMinutes: Int,
        targetClipsCount: Int,
        activeJobId: String?,
        burnStyle: CaptionBurnStyle
    ): Result<EditingSession> {
        val resolvedTitle = title.trim().ifBlank {
            sourceLabel.trim().takeIf { it.isNotBlank() } ?: "Clip Studio Draft"
        }
        val targetId = if (saveAsNew) "" else activeSessionId.orEmpty()
        val session = EditingSession(
            id = targetId,
            userId = currentUserId,
            title = resolvedTitle,
            workspaceType = WORKSPACE_CLIP_STUDIO,
            sourceMode = sourceMode,
            sourceLabel = sourceLabel,
            videoUri = videoUri,
            thumbnailUrl = thumbnailUrl,
            totalDurationMs = totalDurationMs.coerceAtLeast(15_000L),
            trimStartMs = trimStartMs.coerceAtLeast(0L),
            trimEndMs = trimEndMs.coerceAtLeast(trimStartMs + 1000L),
            guidanceNotes = guidanceNotes,
            liveSkillId = liveSkillId,
            liveLookbackMinutes = liveLookbackMinutes,
            targetClipsCount = targetClipsCount,
            activeJobId = activeJobId?.takeIf { it.isNotBlank() },
            burnStyle = burnStyle,
            trimChatHistory = trimChatHistory
        )
        return sessionStorageManager.saveSession(session)
    }

    suspend fun getLocalSessionById(sessionId: String): EditingSession? {
        return sessionStorageManager.getSession(currentUserId, sessionId).getOrNull()
    }

    suspend fun deleteLocalSession(sessionId: String): Result<Unit> {
        return sessionStorageManager.deleteSession(currentUserId, sessionId)
    }

    suspend fun addTrimmedClipToBatch(
        title: String,
        videoUriString: String,
        trimStartMs: Long,
        trimEndMs: Long,
        clip: GeneratedClip?,
        filterStack: List<String>,
        filterContrast: Float,
        filterSaturation: Float,
        filterBrightness: Float,
        startImmediately: Boolean
    ): Result<String> {
        val cues = ClipMetadataFormatter.buildCuesForClip(clip, trimStartMs, trimEndMs)
        val style = ClipMetadataFormatter.buildBurnStyle(
            clip = clip,
            filterStack = filterStack,
            filterContrast = filterContrast,
            filterSaturation = filterSaturation,
            filterBrightness = filterBrightness
        )
        return batchExportManager.enqueueClip(
            userId = currentUserId,
            title = title,
            sourceVideoUri = videoUriString,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            cues = cues,
            burnStyle = style,
            saveToGallery = true,
            startImmediately = startImmediately
        ).map { "Queued \"${it.title}\" for sequential batch export." }
    }

    suspend fun addAllClipsToBatch(
        clips: List<GeneratedClip>,
        videoUriString: String,
        filterStack: List<String>,
        filterContrast: Float,
        filterSaturation: Float,
        filterBrightness: Float,
        startImmediately: Boolean
    ): Int {
        var addedCount = 0
        clips.forEach { clip ->
            val startMs = (clip.startSec * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (clip.endSec * 1000.0).toLong().coerceAtLeast(startMs + 1000L)
            val cues = ClipMetadataFormatter.buildCuesForClip(clip, startMs, endMs)
            val style = ClipMetadataFormatter.buildBurnStyle(
                clip = clip,
                filterStack = filterStack,
                filterContrast = filterContrast,
                filterSaturation = filterSaturation,
                filterBrightness = filterBrightness
            )
            val res = batchExportManager.enqueueClip(
                userId = currentUserId,
                title = clip.youtubeTitle,
                sourceVideoUri = videoUriString,
                trimStartMs = startMs,
                trimEndMs = endMs,
                cues = cues,
                burnStyle = style,
                saveToGallery = true,
                startImmediately = false
            )
            if (res.isSuccess) addedCount++
        }
        if (startImmediately && addedCount > 0) {
            batchExportManager.startBatchProcessing(currentUserId)
        }
        return addedCount
    }
}
