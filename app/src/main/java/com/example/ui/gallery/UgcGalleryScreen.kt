package com.example.ui.gallery

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.EditedVideoMetadata
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareImageFile
import com.example.ui.components.shareTextContent
import com.example.ui.components.shareVideoUri
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralEmerald
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UgcGalleryScreen(
    currentUserId: String,
    viewModel: UgcGalleryViewModel = viewModel(
        key = currentUserId,
        factory = UgcGalleryViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val shortsState by viewModel.shortsState.collectAsStateWithLifecycle()
    val avatarsState by viewModel.avatarsState.collectAsStateWithLifecycle()
    val createAvatarState by viewModel.createAvatarState.collectAsStateWithLifecycle()
    val editedVideos by viewModel.editedVideosMetadata.collectAsStateWithLifecycle()
    val editedSearchQuery by viewModel.editedVideoSearchQuery.collectAsStateWithLifecycle()
    val metadataFeedback by viewModel.metadataFeedback.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateAvatarDialog by remember { mutableStateOf(false) }
    var editingMetadata by remember { mutableStateOf<EditedVideoMetadata?>(null) }
    var showAddMetadataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(createAvatarState) {
        if (createAvatarState is UiState.Success) {
            Toast.makeText(context, (createAvatarState as UiState.Success).data, Toast.LENGTH_SHORT).show()
            viewModel.clearCreateStatus()
            showCreateAvatarDialog = false
        }
    }

    LaunchedEffect(metadataFeedback) {
        metadataFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMetadataFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showCreateAvatarDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_avatar_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create AI Actor")
                }
            } else if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = { showAddMetadataDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_edited_video_metadata_fab")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_edited_video_metadata)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("UGC Studio Gallery", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Campaigns", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("gallery_videos_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("AI Actors", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("gallery_avatars_tab")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                if (editedVideos.isNotEmpty()) "Edited (${editedVideos.size})" else "Edited",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("gallery_edited_videos_tab")
                    )
                }
            }

            if (selectedTab == 0) {
                val shorts = (shortsState as? UiState.Success)?.data.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (shortsState is UiState.Error) {
                        item {
                            ErrorBanner(
                                message = (shortsState as UiState.Error).message,
                                modifier = Modifier.widthIn(max = 760.dp)
                            )
                        }
                    }
                    if (shortsState is UiState.Success && shorts.isEmpty()) {
                        item {
                            StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                                Text("No AI Shorts Campaigns Yet", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Open the AI Shorts tab to research any product and save storyboards with AI actors.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(shorts, key = { it.id }) { item ->
                        StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${item.productName} • ${item.style.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(item.scriptTitle, style = MaterialTheme.typography.titleLarge)
                                }
                                IconButton(onClick = { viewModel.deleteShort(item.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete campaign")
                                }
                            }
                            if (!item.actorImagePath.isNullOrBlank() && File(item.actorImagePath).exists()) {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(
                                    model = File(item.actorImagePath),
                                    contentDescription = item.scriptTitle,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(item.fullNarration, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, "Script", item.fullNarration)
                                        Toast.makeText(context, "Script copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Script")
                                }
                                Button(
                                    onClick = {
                                        val body = "${item.scriptTitle}\n\n${item.fullNarration}\n\n${item.hashtags.joinToString(" ")}"
                                        val path = item.actorImagePath
                                        if (!path.isNullOrBlank() && File(path).exists()) {
                                            shareImageFile(context, path, body)
                                        } else {
                                            shareTextContent(context, item.scriptTitle, body)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                val avatars = (avatarsState as? UiState.Success)?.data.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (avatarsState is UiState.Error) {
                        item {
                            ErrorBanner(
                                message = (avatarsState as UiState.Error).message,
                                modifier = Modifier.widthIn(max = 760.dp)
                            )
                        }
                    }
                    // Built-in Studio Starter Actors
                    item {
                        StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                            Text("Studio Starter Actors", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Image(
                                        painter = painterResource(R.drawable.img_actor_emma),
                                        contentDescription = "Emma Starter Actor",
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Emma (SaaS UGC)", style = MaterialTheme.typography.labelLarge)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Image(
                                        painter = painterResource(R.drawable.img_actor_marcus),
                                        contentDescription = "Marcus Starter Actor",
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Marcus (Tech Founder)", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    items(avatars, key = { it.id }) { av ->
                        StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(av.name, style = MaterialTheme.typography.titleLarge)
                                    Text(av.gender.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteAvatar(av.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete actor")
                                }
                            }
                            if (!av.localImagePath.isNullOrBlank() && File(av.localImagePath).exists()) {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(
                                    model = File(av.localImagePath),
                                    contentDescription = av.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(av.prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "Actor Prompt", av.prompt)
                                    Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Prompt")
                            }
                        }
                    }
                }
            } else {
                EditedVideosMetadataList(
                    items = editedVideos,
                    searchQuery = editedSearchQuery,
                    onSearchQueryChange = viewModel::updateEditedVideoSearchQuery,
                    onEditMetadata = { editingMetadata = it },
                    onShareMetadata = { meta ->
                        if (meta.videoUri.isNotBlank()) {
                            shareVideoUri(
                                context = context,
                                videoUriString = meta.videoUri,
                                title = meta.title,
                                caption = meta.description
                            )
                        } else {
                            shareTextContent(context, meta.title, meta.description)
                        }
                    },
                    onDeleteMetadata = { viewModel.deleteEditedVideoMetadata(it.id) }
                )
            }
        }
    }

    if (showCreateAvatarDialog) {
        var name by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        var gender by remember { mutableStateOf("female") }

        AlertDialog(
            onDismissRequest = { if (createAvatarState !is UiState.Loading) showCreateAvatarDialog = false },
            title = { Text("Generate New AI Actor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Actor Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Look & Studio Prompt") },
                        placeholder = { Text("Warm cozy home office, casual hoodie, friendly smile...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = gender == "female", onClick = { gender = "female" }, label = { Text("Female") })
                        FilterChip(selected = gender == "male", onClick = { gender = "male" }, label = { Text("Male") })
                    }
                    if (createAvatarState is UiState.Error) {
                        ErrorBanner((createAvatarState as UiState.Error).message)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createAiAvatar(name, prompt, gender) },
                    enabled = createAvatarState !is UiState.Loading
                ) {
                    if (createAvatarState is UiState.Loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Generate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateAvatarDialog = false },
                    enabled = createAvatarState !is UiState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    editingMetadata?.let { target ->
        EditedVideoMetadataDialog(
            dialogTitle = "Edit Video Metadata",
            initialTitle = target.title,
            initialDescription = target.description,
            showUriInput = false,
            onDismiss = { editingMetadata = null },
            onSave = { newTitle, newDescription, _ ->
                viewModel.updateEditedVideoMetadata(target.id, newTitle, newDescription)
                editingMetadata = null
            }
        )
    }

    if (showAddMetadataDialog) {
        EditedVideoMetadataDialog(
            dialogTitle = "Add Edited Video Metadata",
            initialTitle = "",
            initialDescription = "",
            showUriInput = true,
            onDismiss = { showAddMetadataDialog = false },
            onSave = { newTitle, newDescription, uri ->
                viewModel.addManualEditedVideoMetadata(newTitle, newDescription, uri)
                showAddMetadataDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditedVideosMetadataList(
    items: List<EditedVideoMetadata>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEditMetadata: (EditedVideoMetadata) -> Unit,
    onShareMetadata: (EditedVideoMetadata) -> Unit,
    onDeleteMetadata: (EditedVideoMetadata) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("edited_videos_metadata_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search titles & descriptions") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search_edited_videos)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .testTag("edited_videos_search_input")
            )
        }

        if (items.isEmpty()) {
            item {
                StudioGlassCard(modifier = Modifier.widthIn(max = 760.dp)) {
                    Text("No Local Edited Video Metadata", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isBlank()) {
                            "Export any clip in Caption & Burn Export or Batch Export Queue to automatically record its title, description, filters, and timestamps in Room."
                        } else {
                            "No edited video metadata matches \"$searchQuery\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(items, key = { it.id }) { item ->
            StudioGlassCard(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .testTag("edited_video_metadata_card_${item.id}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatTimestampLabel(item.exportedAtMs, prefix = "Exported"),
                                    fontFamily = JetBrainsMonoFont,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ViralEmerald
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { onEditMetadata(item) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("edit_video_metadata_button_${item.id}")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_video_metadata)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMetadata(item) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_video_metadata_button_${item.id}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete edited video metadata"
                                )
                            }
                        }
                    }

                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.durationMs > 0L) {
                            MetadataBadge("${String.format(Locale.US, "%.1fs", item.durationMs / 1000f)}")
                        }
                        if (item.appliedFiltersSummary.isNotBlank() && item.appliedFiltersSummary != "Original") {
                            MetadataBadge("Filters: ${item.appliedFiltersSummary}")
                        }
                        if (item.captionCount > 0) {
                            MetadataBadge("${item.captionCount} captions")
                        }
                        MetadataBadge(item.exportSource.replace('_', ' '))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatTimestampLabel(item.updatedAtMs, prefix = "Updated"),
                                fontFamily = JetBrainsMonoFont,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onShareMetadata(item) },
                            modifier = Modifier.testTag("share_edited_video_button_${item.id}")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFont,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EditedVideoMetadataDialog(
    dialogTitle: String,
    initialTitle: String,
    initialDescription: String,
    showUriInput: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, videoUri: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var videoUri by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_dialog_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_dialog_description_input")
                )
                if (showUriInput) {
                    OutlinedTextField(
                        value = videoUri,
                        onValueChange = { videoUri = it },
                        label = { Text("Local Video URI (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, videoUri) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("metadata_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestampLabel(epochMs: Long, prefix: String): String {
    if (epochMs <= 0L) return "$prefix: —"
    val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
    return "$prefix: $formatted"
}

