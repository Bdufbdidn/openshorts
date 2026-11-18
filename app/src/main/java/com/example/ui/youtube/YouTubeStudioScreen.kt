package com.example.ui.youtube

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.UiState
import com.example.ui.clips.YouTubeSearchSheet
import com.example.ui.components.PublishAutomationSheet
import com.example.ui.components.StudioGlassCard
import java.io.File

@Composable
fun YouTubeStudioScreen(
    currentUserId: String,
    onOpenAutomationQueue: () -> Unit = {},
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isAutomatingPost by viewModel.isAutomatingPost.collectAsStateWithLifecycle()
    val youtubeSearchState by viewModel.youtubeSearchState.collectAsStateWithLifecycle()

    var showAutoPostSheet by remember { mutableStateOf(false) }
    var showYoutubeSearchSheet by remember { mutableStateOf(false) }

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("YouTube Studio", style = MaterialTheme.typography.headlineMedium)
                        Text(step.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(onClick = onOpenAutomationQueue) {
                        Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Queue")
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
                    YouTubeStep.INPUT -> YouTubeInputStep(
                        mode = mode,
                        onModeChange = { mode = it },
                        videoUri = videoUri,
                        topicNotes = topicNotes,
                        onTopicNotesChange = { topicNotes = it },
                        selectedTitle = selectedTitle,
                        onSelectedTitleChange = { selectedTitle = it },
                        titlesState = titlesState,
                        onPickVideo = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onSearchYouTube = { showYoutubeSearchSheet = true },
                        onGenerateTitles = { viewModel.generateViralTitles(videoUri, topicNotes) },
                        onManualContinue = { viewModel.goToStep(YouTubeStep.THUMBNAIL) }
                    )

                    YouTubeStep.TITLES -> {
                        val rec = (titlesState as? UiState.Success)?.data
                        if (rec != null) {
                            YouTubeTitlesStep(
                                rec = rec,
                                selectedTitle = selectedTitle,
                                onSelectedTitleChange = { selectedTitle = it },
                                chatMessages = chatMessages,
                                chatInput = chatInput,
                                onChatInputChange = { chatInput = it },
                                isChatBusy = isChatBusy,
                                onSendChat = {
                                    val q = chatInput
                                    chatInput = ""
                                    viewModel.sendTitleChatMessage(q)
                                },
                                onNextStep = { viewModel.goToStep(YouTubeStep.THUMBNAIL) }
                            )
                        }
                    }

                    YouTubeStep.THUMBNAIL -> YouTubeThumbnailStep(
                        selectedTitle = selectedTitle,
                        faceUri = faceUri,
                        bgUri = bgUri,
                        extraThumbPrompt = extraThumbPrompt,
                        onExtraThumbPromptChange = { extraThumbPrompt = it },
                        thumbCount = thumbCount,
                        onThumbCountChange = { thumbCount = it },
                        thumbnailsState = thumbnailsState,
                        selectedThumbnailIndex = selectedThumbnailIndex,
                        onPickFace = {
                            facePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickBg = {
                            bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onGenerateThumbnails = {
                            viewModel.generateThumbnails(selectedTitle, extraThumbPrompt, faceUri, bgUri, thumbCount.toInt())
                        },
                        onSelectThumbnail = { viewModel.selectThumbnail(it) },
                        onNextStep = {
                            viewModel.generateDescription(selectedTitle, topicNotes)
                            viewModel.goToStep(YouTubeStep.DESCRIPTION)
                        }
                    )

                    YouTubeStep.DESCRIPTION -> YouTubeDescriptionStep(
                        descriptionState = descriptionState,
                        descriptionText = descriptionText,
                        onDescriptionTextChange = { descriptionText = it },
                        onRegenerate = { viewModel.generateDescription(selectedTitle, topicNotes) },
                        onNextStep = { viewModel.goToStep(YouTubeStep.PUBLISH) }
                    )

                    YouTubeStep.PUBLISH -> {
                        val thumbs = (thumbnailsState as? UiState.Success)?.data.orEmpty()
                        val selectedThumb = thumbs.getOrNull(selectedThumbnailIndex)
                        YouTubePublishStep(
                            context = context,
                            selectedTitle = selectedTitle,
                            descriptionText = descriptionText,
                            selectedThumb = selectedThumb,
                            saveState = saveState,
                            onSaveProject = {
                                viewModel.saveProject(mode, selectedTitle, descriptionText, extraThumbPrompt)
                            },
                            onOpenAutoPost = { showAutoPostSheet = true }
                        )
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

    if (showAutoPostSheet) {
        PublishAutomationSheet(
            sheetTitle = "Auto-Post YouTube & Instagram",
            initialYoutubeTitle = selectedTitle,
            initialYoutubeDescription = descriptionText,
            initialInstagramCaption = "$selectedTitle\n\n${descriptionText.take(600)}\n\n#reels #shorts",
            initialVideoUri = videoUri?.toString(),
            settings = settings,
            isBusy = isAutomatingPost,
            onDismiss = { showAutoPostSheet = false },
            onAiOptimize = { title, ytDesc, igCap ->
                viewModel.optimizePublishMetadata(title, ytDesc, igCap)
            },
            onSubmitAutomation = { req ->
                viewModel.automatePost(req)
                showAutoPostSheet = false
            }
        )
    }

    if (showYoutubeSearchSheet) {
        YouTubeSearchSheet(
            searchState = youtubeSearchState,
            initialQuery = topicNotes,
            initialLiveOnly = false,
            onSearch = { q, liveOnly -> viewModel.searchYouTube(q, liveOnly) },
            onSelectVideo = { video ->
                topicNotes = "YouTube Reference: ${video.title} by ${video.channelName} (${video.url}, ${video.durationText})"
                if (selectedTitle.isBlank()) {
                    selectedTitle = video.title.take(70)
                }
                showYoutubeSearchSheet = false
            },
            onDismiss = { showYoutubeSearchSheet = false }
        )
    }
}
