package com.example.ui.captions

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.EditedVideoMetadata
import com.example.data.model.CaptionExportResult
import com.example.data.model.TimedCaptionCue
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoPlayer
import com.example.ui.components.parseSafeColor
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.NotoSerifFont
import com.example.ui.theme.PlusJakartaSansFont
import com.example.ui.theme.SpaceGroteskFont
import com.example.ui.theme.ViralEmerald
import java.util.Locale

@Composable
internal fun TimedCaptionCueCard(
    cue: TimedCaptionCue,
    onUpdate: (text: String, highlightWord: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "#${cue.index} • ${formatMsShort(cue.startMs)} → ${formatMsShort(cue.endMs)}",
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_caption_cue)
                    )
                }
            }

            OutlinedTextField(
                value = cue.text,
                onValueChange = { onUpdate(it, cue.highlightWord) },
                label = { Text("Overlay Caption Text") },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cue.highlightWord,
                onValueChange = { onUpdate(cue.text, it) },
                label = { Text("Accent / Highlight Keyword") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ExportedVideoResultCard(
    result: CaptionExportResult,
    metadata: EditedVideoMetadata?,
    onEditMetadata: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShareVideo: () -> Unit,
    onOpenAutoPost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeMb = String.format(Locale.US, "%.2f MB", result.fileSizeBytes / (1024f * 1024f))
    StudioGlassCard(
        modifier = modifier,
        borderColor = ViralEmerald.copy(alpha = 0.55f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Export complete", tint = ViralEmerald)
                    Column {
                        Text(
                            text = metadata?.title?.takeIf { it.isNotBlank() } ?: "Final Burned-In MP4 Ready",
                            style = MaterialTheme.typography.titleLarge,
                            color = ViralEmerald,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Size: $sizeMb • Duration: ${formatMsShort(result.durationMs)} • Saved in Room",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (metadata != null) {
                    IconButton(
                        onClick = onEditMetadata,
                        modifier = Modifier.size(48.dp).testTag("edit_exported_video_metadata_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_edit_video_metadata)
                        )
                    }
                }
            }

            if (metadata != null && metadata.description.isNotBlank()) {
                Text(
                    text = metadata.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            VideoPlayer(
                uri = Uri.parse(result.contentUri),
                autoplay = true,
                repeat = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSaveToGallery,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_export_gallery_button")
                ) {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (result.savedToGalleryUri != null) "Saved" else "Save Gallery")
                }
                OutlinedButton(
                    onClick = onShareVideo,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("share_export_video_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share MP4")
                }
            }

            Button(
                onClick = onOpenAutoPost,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("autopost_exported_mp4_button")
            ) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto-Post Exported MP4 to YT & IG")
            }
        }
    }
}

@Composable
internal fun LiveCaptionStyleOverlayPreview(
    sampleCue: TimedCaptionCue?,
    hookText: String,
    position: String,
    animation: String,
    fontName: String,
    textColorHex: String,
    highlightColorHex: String,
    hookPosition: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        if (hookText.isNotBlank()) {
            val hookAlign = when (hookPosition) {
                "center" -> Alignment.Center
                "bottom" -> Alignment.BottomCenter
                else -> Alignment.TopCenter
            }
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.align(hookAlign)
            ) {
                Text(
                    text = hookText,
                    fontFamily = NotoSerifFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        val previewText = sampleCue?.text?.takeIf { it.isNotBlank() } ?: "Preview overlay captions here"
        val words = previewText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val baseColor = parseSafeColor(textColorHex, Color.White)
        val highlightColor = parseSafeColor(highlightColorHex, Color(0xFF3B82F6))
        val font = when (fontName) {
            "NotoSerif" -> NotoSerifFont
            "JetBrainsMono" -> JetBrainsMonoFont
            "PlusJakartaSans" -> PlusJakartaSansFont
            else -> SpaceGroteskFont
        }
        val capAlign = when (position) {
            "top" -> Alignment.TopCenter
            "middle" -> Alignment.Center
            else -> Alignment.BottomCenter
        }
        val highlightWord = sampleCue?.highlightWord.orEmpty()
        val annotated = buildAnnotatedString {
            words.forEachIndexed { idx, word ->
                val isAccent = animation != "none" && (
                    (highlightWord.isNotBlank() && word.equals(highlightWord, ignoreCase = true)) ||
                        (highlightWord.isBlank() && idx == words.size / 2)
                    )
                withStyle(
                    SpanStyle(
                        color = if (isAccent) highlightColor else baseColor,
                        fontWeight = if (isAccent) FontWeight.ExtraBold else FontWeight.Bold,
                        background = if (animation == "karaoke" && isAccent) {
                            highlightColor.copy(alpha = 0.25f)
                        } else Color.Transparent
                    )
                ) {
                    append(word)
                }
                if (idx < words.lastIndex) append(" ")
            }
        }
        Text(
            text = annotated,
            fontFamily = font,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(capAlign)
                .padding(bottom = if (position == "bottom") 34.dp else 0.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun OverlayColorDot(hex: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.background(parseSafeColor(hex, Color.White)))
    }
}

@Composable
internal fun EditExportMetadataDialog(
    metadata: EditedVideoMetadata,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit
) {
    var metaTitle by remember(metadata.id) { mutableStateOf(metadata.title) }
    var metaDescription by remember(metadata.id) { mutableStateOf(metadata.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Saved Video Metadata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = metaTitle,
                    onValueChange = { metaTitle = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("export_metadata_title_input")
                )
                OutlinedTextField(
                    value = metaDescription,
                    onValueChange = { metaDescription = it },
                    label = { Text("Video Description") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("export_metadata_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(metaTitle, metaDescription) },
                enabled = metaTitle.isNotBlank(),
                modifier = Modifier.testTag("export_metadata_save_button")
            ) {
                Text("Save Metadata")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

internal fun formatMsShort(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val secs = safe / 1000L
    val mins = secs / 60L
    val remSecs = secs % 60L
    val tenths = (safe % 1000L) / 100L
    return "%02d:%02d.%d".format(Locale.US, mins, remSecs, tenths)
}
