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
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentUserId: String,
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
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_out))
                        }
                    }
                }

                // Firebase AI Logic Status Card
                StudioGlassCard(borderColor = ViralEmerald.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "AI Logic Active", tint = ViralEmerald)
                        Column {
                            Text("Firebase AI Logic & Firestore Sync Active", style = MaterialTheme.typography.titleMedium, color = ViralEmerald)
                            Text(
                                "Gemini 3.8 Flash & Nano Banana 2.1 run securely via App Check attestation—no Gemini API key needed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Upload-Post Optional Integration
                StudioGlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Social Publishing (Upload-Post)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Optional: Connect your Upload-Post API key to publish or schedule clips directly to TikTok, Reels & YouTube.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = uploadPostKey,
                            onValueChange = { uploadPostKey = it },
                            label = { Text("Upload-Post API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.fetchUploadPostAccounts(uploadPostKey) },
                                enabled = uploadPostKey.isNotBlank() && accountsState !is UiState.Loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (accountsState is UiState.Loading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Fetch Accounts")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.saveUploadPost(uploadPostKey, uploadPostUser) },
                                modifier = Modifier.weight(1f)
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
                                        FilterChip(
                                            selected = uploadPostUser == acc.username,
                                            onClick = {
                                                uploadPostUser = acc.username
                                                viewModel.saveUploadPost(uploadPostKey, acc.username)
                                            },
                                            label = { Text("${acc.username} (${acc.platforms.joinToString(",")})") }
                                        )
                                    }
                                }
                            }
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
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Save Optional Keys")
                        }
                    }
                }
            }
        }
    }
}
