package com.example.ui.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.EditingSession
import com.example.data.local.WORKSPACE_CAPTION_EXPORT
import com.example.data.local.WORKSPACE_CLIP_STUDIO
import com.example.ui.components.formatSeconds
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavedEditingSessionsSheet(
    sessions: List<EditingSession>,
    currentSessionTitleSuggestion: String,
    activeSessionId: String?,
    onSaveCurrentSession: (title: String, saveAsNew: Boolean) -> Unit,
    onResumeSession: (EditingSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sessionTitleInput by remember(currentSessionTitleSuggestion) {
        mutableStateOf(currentSessionTitleSuggestion)
    }
    var workspaceFilter by remember { mutableStateOf("all") }

    val filteredSessions = remember(sessions, workspaceFilter) {
        when (workspaceFilter) {
            WORKSPACE_CLIP_STUDIO -> sessions.filter { it.workspaceType == WORKSPACE_CLIP_STUDIO }
            WORKSPACE_CAPTION_EXPORT -> sessions.filter { it.workspaceType == WORKSPACE_CAPTION_EXPORT }
            else -> sessions
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.cd_saved_sessions),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.saved_sessions_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.saved_sessions_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Save current editing session card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.cd_save_session),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (activeSessionId != null) {
                                "Update or Snapshot Current Edit"
                            } else {
                                "Save Current Editing Session"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedTextField(
                        value = sessionTitleInput,
                        onValueChange = { sessionTitleInput = it },
                        label = { Text("Project / Session Title") },
                        placeholder = { Text("e.g. Podcast Ep 42 Viral Hook Cut") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_title_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onSaveCurrentSession(sessionTitleInput, false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_editing_session_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (activeSessionId != null) "Update Draft" else "Save Session")
                        }
                        if (activeSessionId != null) {
                            OutlinedButton(
                                onClick = {
                                    onSaveCurrentSession(sessionTitleInput, true)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_new_editing_session_button")
                            ) {
                                Text("Save as Copy")
                            }
                        }
                    }
                }
            }

            // Filter chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = workspaceFilter == "all",
                    onClick = { workspaceFilter = "all" },
                    label = { Text("All (${sessions.size})") },
                    modifier = Modifier.testTag("filter_sessions_all")
                )
                FilterChip(
                    selected = workspaceFilter == WORKSPACE_CLIP_STUDIO,
                    onClick = { workspaceFilter = WORKSPACE_CLIP_STUDIO },
                    label = {
                        val c = sessions.count { it.workspaceType == WORKSPACE_CLIP_STUDIO }
                        Text("Clip Studio ($c)")
                    },
                    modifier = Modifier.testTag("filter_sessions_clip_studio")
                )
                FilterChip(
                    selected = workspaceFilter == WORKSPACE_CAPTION_EXPORT,
                    onClick = { workspaceFilter = WORKSPACE_CAPTION_EXPORT },
                    label = {
                        val c = sessions.count { it.workspaceType == WORKSPACE_CAPTION_EXPORT }
                        Text("Caption Studio ($c)")
                    },
                    modifier = Modifier.testTag("filter_sessions_caption_studio")
                )
            }

            if (filteredSessions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No Saved Editing Sessions Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Save your trim handles, AI chat review notes, live stream URLs, and timed caption styles to local storage so you can pick up right where you left off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredSessions.forEach { session ->
                        EditingSessionItemCard(
                            session = session,
                            isActive = session.id == activeSessionId,
                            onResume = { onResumeSession(session) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun EditingSessionItemCard(
    session: EditingSession,
    isActive: Boolean,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val isCaptionWorkspace = session.workspaceType == WORKSPACE_CAPTION_EXPORT
    val isLive = session.sourceMode == "live"
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        ObsidianBorder
    }

    val badgeLabel = when {
        isCaptionWorkspace -> "Caption & Burn"
        isLive -> "Live Skill"
        session.sourceMode == "url" -> "YouTube Trim"
        else -> "Clip Studio"
    }
    val badgeColor = when {
        isCaptionWorkspace -> ViralEmerald
        isLive -> MaterialTheme.colorScheme.error
        else -> ViralAmber
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editing_session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!session.thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AsyncImage(
                            model = session.thumbnailUrl,
                            contentDescription = session.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isCaptionWorkspace -> Icons.Default.ClosedCaption
                                    isLive -> Icons.Default.LiveTv
                                    else -> Icons.Default.ContentCut
                                },
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (session.sourceLabel.isNotBlank()) {
                        Text(
                            text = session.sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_session_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metadata chips row + Resume button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val trimStartSec = session.trimStartMs / 1000.0
                    val trimEndSec = session.trimEndMs / 1000.0
                    val details = buildList {
                        if (session.trimEndMs > session.trimStartMs) {
                            add("Trim ${formatSeconds(trimStartSec)}–${formatSeconds(trimEndSec)}")
                        }
                        if (session.cues.isNotEmpty()) {
                            add("${session.cues.size} cues")
                        }
                        if (session.trimChatHistory.isNotEmpty()) {
                            add("${session.trimChatHistory.size} chat")
                        }
                    }.joinToString(" • ")

                    if (details.isNotBlank()) {
                        Text(
                            text = details,
                            fontFamily = JetBrainsMonoFont,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Saved ${formatSessionTimestamp(session.updatedAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("resume_session_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_resume_session),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
    }
}

private fun formatSessionTimestamp(epochMs: Long): String {
    return try {
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        formatter.format(Date(epochMs))
    } catch (_: Exception) {
        "recently"
    }
}
