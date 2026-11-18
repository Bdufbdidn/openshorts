package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue
import com.example.data.model.TrimChatMessage

const val WORKSPACE_CLIP_STUDIO = "clip_studio"
const val WORKSPACE_CAPTION_EXPORT = "caption_export"

@Entity(tableName = "editing_sessions")
data class EditingSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val workspaceType: String,
    val sourceMode: String,
    val sourceLabel: String,
    val videoUri: String?,
    val thumbnailUrl: String?,
    val totalDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val guidanceNotes: String,
    val liveSkillId: String,
    val liveLookbackMinutes: Int,
    val targetClipsCount: Int,
    val activeJobId: String?,
    val activeClipId: String?,
    val transcriptText: String,
    val detectedLanguage: String,
    val cuesJson: String,
    val burnStyleJson: String,
    val trimChatHistoryJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class EditingSession(
    val id: String,
    val userId: String,
    val title: String,
    val workspaceType: String = WORKSPACE_CLIP_STUDIO,
    val sourceMode: String = "video",
    val sourceLabel: String = "",
    val videoUri: String? = null,
    val thumbnailUrl: String? = null,
    val totalDurationMs: Long = 60_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 30_000L,
    val guidanceNotes: String = "",
    val liveSkillId: String = "hype",
    val liveLookbackMinutes: Int = 15,
    val targetClipsCount: Int = 4,
    val activeJobId: String? = null,
    val activeClipId: String? = null,
    val transcriptText: String = "",
    val detectedLanguage: String = "",
    val cues: List<TimedCaptionCue> = emptyList(),
    val burnStyle: CaptionBurnStyle = CaptionBurnStyle(),
    val trimChatHistory: List<TrimChatMessage> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
