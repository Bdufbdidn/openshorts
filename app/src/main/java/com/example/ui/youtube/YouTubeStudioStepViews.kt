package com.example.ui.youtube

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TitleRecommendation
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoPlayer
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareImageFile
import com.example.ui.components.shareTextContent
import java.io.File

@Composable
internal fun YouTubeInputStep(
    mode: String,
    onModeChange: (String) -> Unit,
    videoUri: Uri?,
    topicNotes: String,
    onTopicNotesChange: (String) -> Unit,
    selectedTitle: String,
    onSelectedTitleChange: (String) -> Unit,
    titlesState: UiState<TitleRecommendation>,
    onPickVideo: () -> Unit,
    onSearchYouTube: () -> Unit,
    onGenerateTitles: () -> Unit,
    onManualContinue: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "video", onClick = { onModeChange("video") }, label = { Text("Analyze Video") })
                FilterChip(selected = mode == "manual", onClick = { onModeChange("manual") }, label = { Text("Manual Title") })
            }

            if (mode == "video") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickVideo,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("yt_pick_video_button")
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(videoUri?.lastPathSegment ?: "Pick Video", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onSearchYouTube,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("yt_studio_search_youtube_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Search YouTube")
                    }
                }
                if (videoUri != null) {
                    VideoPlayer(
                        uri = videoUri,
                        autoplay = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = topicNotes,
                    onValueChange = onTopicNotesChange,
                    label = { Text("Video Topic / Key Angle") },
                    placeholder = { Text("How I automated 30 YouTube Shorts in 10 minutes...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("yt_topic_input")
                )

                if (titlesState is UiState.Error) ErrorBanner(titlesState.message)

                Button(
                    onClick = onGenerateTitles,
                    enabled = titlesState !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("yt_suggest_titles_button")
                ) {
                    if (titlesState is UiState.Loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Scoring 10 Title Angles...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Suggest 10 Viral Titles")
                    }
                }
            } else {
                OutlinedTextField(
                    value = selectedTitle,
                    onValueChange = onSelectedTitleChange,
                    label = { Text("Enter Your YouTube Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("yt_manual_title_input")
                )
                Button(
                    onClick = onManualContinue,
                    enabled = selectedTitle.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Continue to 16:9 Thumbnail")
                }
            }
        }
    }
}

@Composable
internal fun YouTubeTitlesStep(
    rec: TitleRecommendation,
    selectedTitle: String,
    onSelectedTitleChange: (String) -> Unit,
    chatMessages: List<ChatBubble>,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    isChatBusy: Boolean,
    onSendChat: () -> Unit,
    onNextStep: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Top 2 CTR Recommendations", style = MaterialTheme.typography.titleLarge)
            Text(rec.reasoning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            rec.titles.forEach { t ->
                val isTop = t == rec.topPick1 || t == rec.topPick2
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        if (selectedTitle == t) 2.dp else 1.dp,
                        if (selectedTitle == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    color = if (selectedTitle == t) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { onSelectedTitleChange(t) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (isTop) {
                            Spacer(Modifier.width(8.dp))
                            Text("TOP PICK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = selectedTitle,
                onValueChange = onSelectedTitleChange,
                label = { Text("Selected / Custom Title (${selectedTitle.length}/70)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Refine Titles with Gemini Chat", style = MaterialTheme.typography.titleMedium)
            chatMessages.forEach { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (msg.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (if (msg.isUser) "You: " else "Gemini: ") + msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = onChatInputChange,
                    label = { Text("Ask for shorter, bolder, or curiosity-gap variants") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSendChat,
                    enabled = !isChatBusy && chatInput.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send refinement")
                }
            }

            Button(
                onClick = onNextStep,
                enabled = selectedTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Next: Generate 16:9 Thumbnail")
            }
        }
    }
}

@Composable
internal fun YouTubeThumbnailStep(
    selectedTitle: String,
    faceUri: Uri?,
    bgUri: Uri?,
    extraThumbPrompt: String,
    onExtraThumbPromptChange: (String) -> Unit,
    thumbCount: Float,
    onThumbCountChange: (Float) -> Unit,
    thumbnailsState: UiState<List<Pair<Bitmap, String>>>,
    selectedThumbnailIndex: Int,
    onPickFace: () -> Unit,
    onPickBg: () -> Unit,
    onGenerateThumbnails: () -> Unit,
    onSelectThumbnail: (Int) -> Unit,
    onNextStep: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("16:9 AI Thumbnail Studio", style = MaterialTheme.typography.titleLarge)
            Text("Title: $selectedTitle", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickFace, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (faceUri != null) "Face Added" else "Face Photo")
                }
                OutlinedButton(onClick = onPickBg, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (bgUri != null) "BG Added" else "BG Photo")
                }
            }

            OutlinedTextField(
                value = extraThumbPrompt,
                onValueChange = onExtraThumbPromptChange,
                label = { Text("Art Direction (Optional)") },
                placeholder = { Text("Shocked expression, glowing dashboard chart, red arrow...") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("yt_thumb_prompt_input")
            )

            Text("Variations: ${thumbCount.toInt()}")
            Slider(value = thumbCount, onValueChange = onThumbCountChange, valueRange = 1f..3f, steps = 1)

            Button(
                onClick = onGenerateThumbnails,
                enabled = thumbnailsState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("yt_generate_thumb_button")
            ) {
                if (thumbnailsState is UiState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating 16:9 Thumbnails...")
                } else {
                    Text("Generate Thumbnail")
                }
            }
        }
    }

    when (thumbnailsState) {
        is UiState.Loading -> ScannerProcessingCard(
            title = "Nano Banana 2.1 Composing 16:9 Art",
            subtitle = "Synthesizing high-CTR YouTube thumbnail (30-60s)..."
        )
        is UiState.Error -> ErrorBanner(thumbnailsState.message)
        is UiState.Success -> {
            thumbnailsState.data.forEachIndexed { idx, (bmp, _) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (selectedThumbnailIndex == idx) 3.dp else 1.dp,
                        if (selectedThumbnailIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    onClick = { onSelectThumbnail(idx) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Generated 16:9 Thumbnail ${idx + 1}",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        UiState.Idle -> Unit
    }

    Button(
        onClick = onNextStep,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("Next: SEO Chapter Description")
    }
}

@Composable
internal fun YouTubeDescriptionStep(
    descriptionState: UiState<String>,
    descriptionText: String,
    onDescriptionTextChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onNextStep: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YouTube SEO Description & Chapters", style = MaterialTheme.typography.titleLarge)

            if (descriptionState is UiState.Loading) {
                ScannerProcessingCard(
                    title = "Writing Chaptered Description",
                    subtitle = "Generating hook lines, timestamps & hashtags..."
                )
            } else if (descriptionState is UiState.Error) {
                ErrorBanner(descriptionState.message)
            }

            OutlinedTextField(
                value = descriptionText,
                onValueChange = onDescriptionTextChange,
                label = { Text("Video Description") },
                minLines = 8,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Regenerate")
                }
                Button(
                    onClick = onNextStep,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Review & Export")
                }
            }
        }
    }
}

@Composable
internal fun YouTubePublishStep(
    context: Context,
    selectedTitle: String,
    descriptionText: String,
    selectedThumb: Pair<Bitmap, String>?,
    saveState: UiState<String>,
    onSaveProject: () -> Unit,
    onOpenAutoPost: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ready to Publish", style = MaterialTheme.typography.titleLarge)
            Text(selectedTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            selectedThumb?.let { (bmp, _) ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected Thumbnail",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            if (descriptionText.isNotBlank()) {
                Text(descriptionText, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }

            if (saveState is UiState.Error) ErrorBanner(saveState.message)

            Button(
                onClick = onSaveProject,
                enabled = saveState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("yt_save_project_button")
            ) {
                Text(if (saveState is UiState.Loading) "Saving..." else "Save Project to Cloud")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, "YouTube Metadata", "$selectedTitle\n\n$descriptionText")
                        Toast.makeText(context, "Title & description copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Text")
                }
                Button(
                    onClick = {
                        val thumbPath = selectedThumb?.second
                        if (!thumbPath.isNullOrBlank() && File(thumbPath).exists()) {
                            shareImageFile(context, thumbPath, "$selectedTitle\n\n$descriptionText")
                        } else {
                            shareTextContent(context, selectedTitle, descriptionText)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }

            Button(
                onClick = onOpenAutoPost,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("yt_studio_automate_post_button")
            ) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Automate Post to YouTube & IG")
            }
        }
    }
}
