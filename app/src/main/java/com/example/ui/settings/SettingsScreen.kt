package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.UiState
import com.example.ui.auth.signOut
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.ViralEmerald
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentUserId: String,
    onOpenAutomationQueue: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(
        key = currentUserId,
        factory = SettingsViewModel.provideFactory()
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = Firebase.auth.currentUser

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()

    var uploadPostKey by remember(settings.uploadPostApiKey) { mutableStateOf(settings.uploadPostApiKey) }
    var uploadPostUser by remember(settings.uploadPostUsername) { mutableStateOf(settings.uploadPostUsername) }
    var elevenLabsKey by remember(settings.elevenLabsApiKey) { mutableStateOf(settings.elevenLabsApiKey) }
    var falAiKey by remember(settings.falAiApiKey) { mutableStateOf(settings.falAiApiKey) }

    var autoPostEnabled by remember(settings.autoPostEnabled) { mutableStateOf(settings.autoPostEnabled) }
    var defaultAutoYouTube by remember(settings.defaultAutoYouTube) { mutableStateOf(settings.defaultAutoYouTube) }
    var defaultAutoInstagram by remember(settings.defaultAutoInstagram) { mutableStateOf(settings.defaultAutoInstagram) }
    var defaultYoutubePrivacy by remember(settings.defaultYoutubePrivacy) { mutableStateOf(settings.defaultYoutubePrivacy) }
    var defaultPostHourUtc by remember(settings.defaultPostHourUtc) { mutableIntStateOf(settings.defaultPostHourUtc) }
    var includeTranscriptInCaptions by remember(settings.includeTranscriptInCaptions) {
        mutableStateOf(settings.includeTranscriptInCaptions)
    }

    var directIgUserId by remember(settings.directInstagramUserId) { mutableStateOf(settings.directInstagramUserId) }
    var directIgToken by remember(settings.directInstagramAccessToken) {
        mutableStateOf(settings.directInstagramAccessToken)
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Studio Settings", style = MaterialTheme.typography.headlineMedium)

                // Account Card
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Google Account", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = user?.displayName ?: "Authenticated Creator",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = user?.email ?: currentUserId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { signOut(credentialManager, scope) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_out))
                        }
                    }
                }

                // Firebase AI Logic Status Card
                StudioGlassCard(borderColor = ViralEmerald.copy(alpha = 0.45f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = "AI Logic Active",
                            tint = ViralEmerald
                        )
                        Column {
                            Text(
                                "Firebase AI Logic & Firestore Sync Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = ViralEmerald
                            )
                            Text(
                                "Gemini 3.8 Flash & Nano Banana 2.1 run securely via App Check attestation—no Gemini API key needed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // YouTube & Instagram Automation Defaults Card
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoMode,
                                contentDescription = "YouTube and Instagram Automation",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "YouTube & Instagram Automation Rules",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            "Configure automated short publishing with AI-optimized YouTube titles, descriptions, and Instagram Reels captions & hashtags.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Auto-Queue Generated Clips",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Automatically enqueue newly detected clips when a source video is attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoPostEnabled,
                                onCheckedChange = { autoPostEnabled = it },
                                modifier = Modifier.testTag("auto_post_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Include Transcript in Captions",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Append generated subtitle lines to YouTube description & IG captions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = includeTranscriptInCaptions,
                                onCheckedChange = { includeTranscriptInCaptions = it },
                                modifier = Modifier.testTag("include_transcript_switch")
                            )
                        }

                        Text("Default Channels", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = defaultAutoYouTube,
                                onClick = {
                                    if (!defaultAutoYouTube || defaultAutoInstagram) {
                                        defaultAutoYouTube = !defaultAutoYouTube
                                    }
                                },
                                label = { Text("YouTube Shorts") },
                                modifier = Modifier.testTag("default_youtube_chip")
                            )
                            FilterChip(
                                selected = defaultAutoInstagram,
                                onClick = {
                                    if (!defaultAutoInstagram || defaultAutoYouTube) {
                                        defaultAutoInstagram = !defaultAutoInstagram
                                    }
                                },
                                label = { Text("Instagram Reels") },
                                modifier = Modifier.testTag("default_instagram_chip")
                            )
                        }

                        Text("Default YouTube Visibility", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("public", "unlisted", "private").forEach { privacy ->
                                FilterChip(
                                    selected = defaultYoutubePrivacy == privacy,
                                    onClick = { defaultYoutubePrivacy = privacy },
                                    label = { Text(privacy.replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.testTag("default_privacy_${privacy}_chip")
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Default Daily Post Hour (UTC): ${"%02d".format(defaultPostHourUtc)}:00 UTC",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = defaultPostHourUtc.toFloat(),
                                onValueChange = { defaultPostHourUtc = it.roundToInt().coerceIn(0, 23) },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.testTag("default_post_hour_slider")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveAutomationSettings(
                                        autoPostEnabled = autoPostEnabled,
                                        defaultAutoYouTube = defaultAutoYouTube,
                                        defaultAutoInstagram = defaultAutoInstagram,
                                        defaultYoutubePrivacy = defaultYoutubePrivacy,
                                        defaultPostHourUtc = defaultPostHourUtc,
                                        includeTranscriptInCaptions = includeTranscriptInCaptions
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_automation_rules_button")
                            ) {
                                Text("Save Automation Rules")
                            }
                            OutlinedButton(
                                onClick = onOpenAutomationQueue,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_automation_queue_button")
                            ) {
                                Icon(Icons.Default.ScheduleSend, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open Queue")
                            }
                        }
                    }
                }

                // Upload-Post Integration (YouTube Channel & Instagram Reels)
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Social Publishing (Upload-Post)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Connect your Upload-Post API key and linked creator profile to publish or schedule vertical shorts directly to your YouTube channel & Instagram Reels.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = uploadPostKey,
                            onValueChange = { uploadPostKey = it },
                            label = { Text("Upload-Post API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_post_key_input")
                        )
                        OutlinedTextField(
                            value = uploadPostUser,
                            onValueChange = { uploadPostUser = it },
                            label = { Text("Upload-Post Profile Username") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_post_username_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.fetchUploadPostAccounts(uploadPostKey) },
                                enabled = uploadPostKey.isNotBlank() && accountsState !is UiState.Loading,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("fetch_accounts_button")
                            ) {
                                if (accountsState is UiState.Loading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Fetch Accounts")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.saveUploadPost(uploadPostKey, uploadPostUser) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_upload_post_button")
                            ) {
                                Text("Save")
                            }
                        }

                        if (accountsState is UiState.Error) {
                            ErrorBanner((accountsState as UiState.Error).message)
                        }
                        if (accountsState is UiState.Success) {
                            val accounts = (accountsState as UiState.Success).data
                            if (accounts.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    accounts.forEach { acc ->
                                        val ytReady = acc.platforms.any { it.contains("youtube", ignoreCase = true) }
                                        val igReady = acc.platforms.any { it.contains("instagram", ignoreCase = true) }
                                        val badgeText = buildString {
                                            append(acc.username)
                                            if (ytReady || igReady) {
                                                append(" • ")
                                                val ready = listOfNotNull(
                                                    "YouTube".takeIf { ytReady },
                                                    "Instagram".takeIf { igReady }
                                                )
                                                append(ready.joinToString(" + "))
                                            } else if (acc.platforms.isNotEmpty()) {
                                                append(" (${acc.platforms.joinToString(",")})")
                                            }
                                        }
                                        FilterChip(
                                            selected = uploadPostUser == acc.username,
                                            onClick = {
                                                uploadPostUser = acc.username
                                                viewModel.saveUploadPost(uploadPostKey, acc.username)
                                            },
                                            leadingIcon = if (ytReady && igReady) {
                                                {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "YouTube and Instagram connected",
                                                        tint = ViralEmerald,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            label = { Text(badgeText) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Direct Meta Instagram Graph API (Optional Reels Fallback/Direct Channel)
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Direct Instagram Graph API (Optional)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Optional for public video URLs: post Reels directly via Meta Graph API v21.0 if you prefer not to route Instagram through Upload-Post.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = directIgUserId,
                            onValueChange = { directIgUserId = it },
                            label = { Text("Instagram Business Account ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_ig_user_id_input")
                        )
                        OutlinedTextField(
                            value = directIgToken,
                            onValueChange = { directIgToken = it },
                            label = { Text("Instagram Graph Access Token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_ig_token_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.saveDirectInstagram(directIgUserId, directIgToken) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_direct_ig_button")
                        ) {
                            Text("Save Direct Instagram Credentials")
                        }
                    }
                }

                // Optional ElevenLabs & fal.ai Keys
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Optional Voice & Video Keys", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = elevenLabsKey,
                            onValueChange = { elevenLabsKey = it },
                            label = { Text("ElevenLabs API Key (Optional Dubbing)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = falAiKey,
                            onValueChange = { falAiKey = it },
                            label = { Text("fal.ai API Key (Optional Avatar Video)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.saveElevenLabs(elevenLabsKey)
                                viewModel.saveFalAi(falAiKey)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Save Optional Keys")
                        }
                    }
                }
            }
        }
    }
}
