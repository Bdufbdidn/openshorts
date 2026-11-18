package com.example.ui.captions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.CaptionExportResult
import com.example.data.model.VideoTranscriptionResult
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.ViralEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranscribeAudioCard(
    hasVideoSource: Boolean,
    languageHint: String,
    onLanguageHintChange: (String) -> Unit,
    transcriptionState: UiState<VideoTranscriptionResult>,
    detectedLanguage: String,
    audioTrackInfo: String,
    fullTranscript: String,
    hasCues: Boolean,
    onTranscribe: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onSplitCues: () -> Unit,
    onCopySrt: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Gemini Audio Transcription",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("1. Transcribe Video Audio (Gemini)", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Extracts the audio track directly from your video and uses Gemini 3.8 Flash to transcribe verbatim speech with word-accented overlay timestamps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = languageHint,
                onValueChange = onLanguageHintChange,
                label = { Text("Caption Output Language (Optional, blank = original speech)") },
                placeholder = { Text("e.g. English, Spanish, French") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("caption_language_hint_input")
            )

            Button(
                onClick = onTranscribe,
                enabled = hasVideoSource && transcriptionState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("transcribe_audio_gemini_button")
            ) {
                if (transcriptionState is UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Transcribing Audio with Gemini...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Transcribe Audio & Generate Captions")
                }
            }

            if (transcriptionState is UiState.Loading) {
                ScannerProcessingCard(
                    title = "Extracting Audio & Running Gemini",
                    subtitle = "Generating timestamped overlay caption cues..."
                )
            }
            if (transcriptionState is UiState.Error) {
                ErrorBanner(transcriptionState.message)
            }

            if (detectedLanguage.isNotBlank() || audioTrackInfo.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detectedLanguage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ViralEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Language: $detectedLanguage",
                                style = MaterialTheme.typography.labelSmall,
                                color = ViralEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (audioTrackInfo.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = audioTrackInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = fullTranscript,
                onValueChange = onTranscriptChange,
                label = { Text("Full Spoken Transcript") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("full_transcript_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSplitCues,
                    enabled = fullTranscript.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Split into Cues")
                }
                OutlinedButton(
                    onClick = onCopySrt,
                    enabled = hasCues,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("copy_srt_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy .SRT")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverlayAppearanceStyleCard(
    burnStyle: CaptionBurnStyle,
    onUpdateStyle: ((CaptionBurnStyle) -> CaptionBurnStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightPresets = listOf("#3B82F6", "#F59E0B", "#10B981", "#EC4899", "#8B5CF6")
    val basePresets = listOf("#FFFFFF", "#FDE047", "#E4E4E7")

    StudioGlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("2. Burn-In Overlay Style", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = burnStyle.hookText,
                onValueChange = { v -> onUpdateStyle { it.copy(hookText = v) } },
                label = { Text("Optional Top Viral Hook Banner") },
                placeholder = { Text("e.g. Wait until you hear this...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("burn_hook_input")
            )

            Text("Caption Position", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("top", "middle", "bottom").forEach { pos ->
                    FilterChip(
                        selected = burnStyle.position == pos,
                        onClick = { onUpdateStyle { it.copy(position = pos) } },
                        label = { Text(pos.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Highlight Style", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "word-highlight" to "Word Highlight",
                    "karaoke" to "Karaoke",
                    "pop" to "Pop",
                    "none" to "Clean"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = burnStyle.animation == key,
                        onClick = { onUpdateStyle { it.copy(animation = key) } },
                        label = { Text(label) }
                    )
                }
            }

            Text("Font Family", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SpaceGrotesk", "NotoSerif", "PlusJakartaSans", "JetBrainsMono").forEach { font ->
                    FilterChip(
                        selected = burnStyle.fontName == font,
                        onClick = { onUpdateStyle { it.copy(fontName = font) } },
                        label = { Text(font) }
                    )
                }
            }

            Text("Base & Accent Colors", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                basePresets.forEach { hex ->
                    OverlayColorDot(
                        hex = hex,
                        selected = burnStyle.textColorHex == hex,
                        onClick = { onUpdateStyle { it.copy(textColorHex = hex) } }
                    )
                }
                Spacer(Modifier.width(8.dp))
                highlightPresets.forEach { hex ->
                    OverlayColorDot(
                        hex = hex,
                        selected = burnStyle.highlightColorHex == hex,
                        onClick = { onUpdateStyle { it.copy(highlightColorHex = hex) } }
                    )
                }
            }
        }
    }
}

@Composable
internal fun BurnAndExportActionCard(
    hasVideoSource: Boolean,
    hasCues: Boolean,
    burnStyle: CaptionBurnStyle,
    exportState: UiState<CaptionExportResult>,
    exportProgress: Float,
    pendingBatchExportCount: Int,
    onBurnAndExport: () -> Unit,
    onAddToBatch: () -> Unit,
    onOpenBatchQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.MovieCreation,
                    contentDescription = "Burn Captions Export",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("3. Burn Captions & Export MP4", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Renders your real-time Media3 video filter stack and styled Gemini caption overlays directly onto every frame with Media3 Transformer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onBurnAndExport,
                enabled = hasVideoSource &&
                    exportState !is UiState.Loading &&
                    (hasCues || burnStyle.hookText.isNotBlank() || burnStyle.hasVideoFilters()),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("burn_export_mp4_button")
            ) {
                if (exportState is UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Exporting with Media3 (${(exportProgress * 100).toInt()}%)...")
                } else {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            hasCues || burnStyle.hookText.isNotBlank() ->
                                if (burnStyle.hasVideoFilters()) "Burn Captions & Filters → MP4" else "Burn Captions & Export MP4"
                            burnStyle.hasVideoFilters() -> "Apply Filters & Export Trimmed MP4"
                            else -> "Burn Captions & Export MP4"
                        }
                    )
                }
            }

            if (exportState is UiState.Loading) {
                LinearProgressIndicator(
                    progress = { exportProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp))
                )
            }
            if (exportState is UiState.Error) {
                ErrorBanner(exportState.message)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddToBatch,
                    enabled = hasVideoSource,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caption_add_to_batch_button")
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.cd_add_to_batch), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Batch")
                }
                OutlinedButton(
                    onClick = onOpenBatchQueue,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caption_view_batch_queue_button")
                ) {
                    Icon(Icons.Default.QueuePlayNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (pendingBatchExportCount > 0) "Batch Queue ($pendingBatchExportCount)" else "Batch Queue")
                }
            }
        }
    }
}
