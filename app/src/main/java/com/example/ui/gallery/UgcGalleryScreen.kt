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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareImageFile
import com.example.ui.components.shareTextContent
import java.io.File

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

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateAvatarDialog by remember { mutableStateOf(false) }

    LaunchedEffect(createAvatarState) {
        if (createAvatarState is UiState.Success) {
            Toast.makeText(context, (createAvatarState as UiState.Success).data, Toast.LENGTH_SHORT).show()
            viewModel.clearCreateStatus()
            showCreateAvatarDialog = false
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
                        text = { Text("AI Campaigns") },
                        modifier = Modifier.testTag("gallery_videos_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("AI Actors") },
                        modifier = Modifier.testTag("gallery_avatars_tab")
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
            } else {
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
}
