package com.example.ui.youtube

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.VideoPlayer
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareImageFile
import com.example.ui.components.shareTextContent
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouTubeStudioScreen(
    currentUserId: String,
    viewModel: YouTubeStudioViewModel = viewModel(
        key = currentUserId,
        factory = YouTubeStudioViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsStateWithLifecycle()
    val titlesState by viewModel.titlesState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatBusy by viewModel.isChatBusy.collectAsStateWithLifecycle()
    val thumbnailsState by viewModel.thumbnailsState.collectAsStateWithLifecycle()
    val selectedThumbnailIndex by viewModel.selectedThumbnailIndex.collectAsStateWithLifecycle()
    val descriptionState by viewModel.descriptionState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val savedProjectsState by viewModel.savedProjectsState.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf("video") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var topicNotes by remember { mutableStateOf("") }
    var selectedTitle by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    var faceUri by remember { mutableStateOf<Uri?>(null) }
    var bgUri by remember { mutableStateOf<Uri?>(null) }
    var extraThumbPrompt by remember { mutableStateOf("") }
    var thumbCount by remember { mutableFloatStateOf(1f) }
    var descriptionText by remember { mutableStateOf("") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) videoUri = uri
    }
    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) faceUri = uri
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) bgUri = uri
    }

    LaunchedEffect(titlesState) {
        if (titlesState is UiState.Success && selectedTitle.isBlank()) {
            selectedTitle = (titlesState as UiState.Success).data.topPick1
        }
    }
    LaunchedEffect(descriptionState) {
        if (descriptionState is UiState.Success) {
            descriptionText = (descriptionState as UiState.Success).data
        }
    }
    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) {
            Toast.makeText(context, (saveState as UiState.Success).data, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = step.index > 0) {
        viewModel.stepBack()
    }

    val savedProjects = (savedProjectsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step.index > 0) {
                        IconButton(onClick = { viewModel.stepBack() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step")
                        }
                    }
                    Column {
                        Text("YouTube Studio", style = MaterialTheme.typography.headlineMedium)
                        Text(step.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                LinearProgressIndicator(
                    progress = { (step.index + 1) / 5f },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        }

        item {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (step) {
                    YouTubeStep.INPUT -> {
                        StudioGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = mode == "video", onClick = { mode = "video" }, label = { Text("Analyze Video") })
                                    FilterChip(selected = mode == "manual", onClick = { mode = "manual" }, label = { Text("Manual Title") })
                                }

                                if (mode == "video") {
                                    OutlinedButton(
                                        onClick = {
                                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("yt_pick_video_button")
                                    ) {
                                        Icon(Icons.Default.VideoFile, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(videoUri?.lastPathSegment ?: "Pick Video (Optional)", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                        onValueChange = { topicNotes = it },
                                        label = { Text("Video Topic / Key Angle") },
                                        placeholder = { Text("How I automated 30 YouTube Shorts in 10 minutes...") },
                                        minLines = 3,
                                        modifier = Modifier.fillMaxWidth().testTag("yt_topic_input")
                                    )

                                    if (titlesState is UiState.Error) ErrorBanner((titlesState as UiState.Error).message)

                                    Button(
                                        onClick = { viewModel.generateViralTitles(videoUri, topicNotes) },
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
                                        onValueChange = { selectedTitle = it },
                                        label = { Text("Enter Your YouTube Title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("yt_manual_title_input")
                                    )
                                    Button(
                                        onClick = { viewModel.goToStep(YouTubeStep.THUMBNAIL) },
                                        enabled = selectedTitle.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                    ) {
                                        Text("Continue to 16:9 Thumbnail")
                                    }
                                }
                            }
                        }
                    }

                    YouTubeStep.TITLES -> {
                        val rec = (titlesState as? UiState.Success)?.data
                        if (rec != null) {
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
                                            onClick = { selectedTitle = t },
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
                                        onValueChange = { selectedTitle = it },
                                        label = { Text("Selected / Custom Title (${selectedTitle.length}/70)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Interactive Title Refinement Chat
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
                                            onValueChange = { chatInput = it },
                                            label = { Text("Ask for shorter, bolder, or curiosity-gap variants") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val q = chatInput
                                                chatInput = ""
                                                viewModel.sendTitleChatMessage(q)
                                            },
                                            enabled = !isChatBusy && chatInput.isNotBlank(),
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send refinement")
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.goToStep(YouTubeStep.THUMBNAIL) },
                                        enabled = selectedTitle.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Text("Next: Generate 16:9 Thumbnail")
                                    }
                                }
                            }
                        }
                    }

                    YouTubeStep.THUMBNAIL -> {
                        StudioGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("16:9 AI Thumbnail Studio", style = MaterialTheme.typography.titleLarge)
                                Text("Title: $selectedTitle", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { facePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (faceUri != null) "Face Added" else "Face Photo")
                                    }
                                    OutlinedButton(
                                        onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (bgUri != null) "BG Added" else "BG Photo")
                                    }
                                }

                                OutlinedTextField(
                                    value = extraThumbPrompt,
                                    onValueChange = { extraThumbPrompt = it },
                                    label = { Text("Art Direction (Optional)") },
                                    placeholder = { Text("Shocked expression, glowing dashboard chart, red arrow...") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("yt_thumb_prompt_input")
                                )

                                Text("Variations: ${thumbCount.toInt()}")
                                Slider(value = thumbCount, onValueChange = { thumbCount = it }, valueRange = 1f..3f, steps = 1)

                                Button(
                                    onClick = { viewModel.generateThumbnails(selectedTitle, extraThumbPrompt, faceUri, bgUri, thumbCount.toInt()) },
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

                        when (val tState = thumbnailsState) {
                            is UiState.Loading -> ScannerProcessingCard(
                                title = "Nano Banana 2.1 Composing 16:9 Art",
                                subtitle = "Synthesizing high-CTR YouTube thumbnail (30-60s)..."
                            )
                            is UiState.Error -> ErrorBanner(tState.message)
                            is UiState.Success -> {
                                tState.data.forEachIndexed { idx, (bmp, _) ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            if (selectedThumbnailIndex == idx) 3.dp else 1.dp,
                                            if (selectedThumbnailIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        onClick = { viewModel.selectThumbnail(idx) },
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
                            onClick = {
                                viewModel.generateDescription(selectedTitle, topicNotes)
                                viewModel.goToStep(YouTubeStep.DESCRIPTION)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Next: SEO Chapter Description")
                        }
                    }

                    YouTubeStep.DESCRIPTION -> {
                        StudioGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("YouTube SEO Description & Chapters", style = MaterialTheme.typography.titleLarge)

                                if (descriptionState is UiState.Loading) {
                                    ScannerProcessingCard(
                                        title = "Writing Chaptered Description",
                                        subtitle = "Generating hook lines, timestamps & hashtags..."
                                    )
                                } else if (descriptionState is UiState.Error) {
                                    ErrorBanner((descriptionState as UiState.Error).message)
                                }

                                OutlinedTextField(
                                    value = descriptionText,
                                    onValueChange = { descriptionText = it },
                                    label = { Text("Video Description") },
                                    minLines = 8,
                                    maxLines = 14,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.generateDescription(selectedTitle, topicNotes) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Regenerate")
                                    }
                                    Button(
                                        onClick = { viewModel.goToStep(YouTubeStep.PUBLISH) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Review & Export")
                                    }
                                }
                            }
                        }
                    }

                    YouTubeStep.PUBLISH -> {
                        val thumbs = (thumbnailsState as? UiState.Success)?.data.orEmpty()
                        val selectedThumb = thumbs.getOrNull(selectedThumbnailIndex)

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

                                if (saveState is UiState.Error) ErrorBanner((saveState as UiState.Error).message)

                                Button(
                                    onClick = { viewModel.saveProject(mode, selectedTitle, descriptionText, extraThumbPrompt) },
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
                            }
                        }
                    }
                }
            }
        }

        if (savedProjects.isNotEmpty()) {
            item {
                Text(
                    "Saved YouTube Projects",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                )
            }
            items(savedProjects, key = { it.id }) { proj ->
                StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.selectedTitle, style = MaterialTheme.typography.titleMedium)
                            Text("Mode: ${proj.mode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deleteProject(proj.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete YouTube project")
                        }
                    }
                    if (!proj.thumbnailPath.isNullOrBlank() && File(proj.thumbnailPath).exists()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = File(proj.thumbnailPath),
                            contentDescription = proj.selectedTitle,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
