package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.IntegrationSettings
import com.example.data.model.PublishMetadataDraft
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class PublishAutomationRequest(
    val youtubeEnabled: Boolean,
    val instagramEnabled: Boolean,
    val youtubeTitle: String,
    val youtubeDescription: String,
    val youtubePrivacy: String,
    val instagramCaption: String,
    val videoUri: String?,
    val scheduledIso: String?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublishAutomationSheet(
    sheetTitle: String = "Automate YouTube & Instagram Post",
    initialYoutubeTitle: String,
    initialYoutubeDescription: String,
    initialInstagramCaption: String,
    initialVideoUri: String?,
    settings: IntegrationSettings,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAiOptimize: (suspend (titleHint: String, ytDesc: String, igCaption: String) -> Result<PublishMetadataDraft>)? = null,
    onSubmitAutomation: (PublishAutomationRequest) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var youtubeEnabled by remember { mutableStateOf(settings.defaultAutoYouTube) }
    var instagramEnabled by remember { mutableStateOf(settings.defaultAutoInstagram) }
    var youtubeTitle by remember { mutableStateOf(initialYoutubeTitle.take(100)) }
    var youtubeDescription by remember { mutableStateOf(initialYoutubeDescription) }
    var instagramCaption by remember { mutableStateOf(initialInstagramCaption) }
    var youtubePrivacy by remember { mutableStateOf(settings.defaultYoutubePrivacy) }
    var attachedVideoUri by remember { mutableStateOf(initialVideoUri.orEmpty()) }
    var scheduleMode by remember { mutableStateOf("now") }
    var scheduledIso by remember {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        mutableStateOf("${tomorrow}T%02d:00:00Z".format(settings.defaultPostHourUtc))
    }
    var isAiOptimizing by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedVideoUri = uri.toString()
        }
    }

    val hasUploadPost = settings.uploadPostApiKey.isNotBlank() && settings.uploadPostUsername.isNotBlank()
    val hasDirectIg = settings.directInstagramUserId.isNotBlank() && settings.directInstagramAccessToken.isNotBlank()
    val isChannelReady = hasUploadPost || (instagramEnabled && !youtubeEnabled && hasDirectIg)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(sheetTitle, style = MaterialTheme.typography.headlineSmall)

            // Connection Status Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (isChannelReady) ViralEmerald else ViralAmber).copy(alpha = 0.14f),
                border = BorderStroke(
                    1.dp,
                    (if (isChannelReady) ViralEmerald else ViralAmber).copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        hasUploadPost -> "Connected: @${settings.uploadPostUsername} via Upload-Post (YouTube & Instagram)"
                        hasDirectIg -> "Connected: Direct Instagram Graph API (Add Upload-Post in Settings for YouTube uploads)"
                        else -> "Setup needed: Connect Upload-Post in Settings to auto-upload videos to YouTube & Instagram. You can still optimize captions, queue drafts, or share natively."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Text("Target Channels", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = youtubeEnabled,
                    onClick = {
                        if (!(youtubeEnabled && !instagramEnabled)) {
                            youtubeEnabled = !youtubeEnabled
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("YouTube Channel") },
                    modifier = Modifier.testTag("auto_channel_youtube")
                )
                FilterChip(
                    selected = instagramEnabled,
                    onClick = {
                        if (!(instagramEnabled && !youtubeEnabled)) {
                            instagramEnabled = !instagramEnabled
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("Instagram Reels") },
                    modifier = Modifier.testTag("auto_channel_instagram")
                )
            }

            // Video Attachment
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = attachedVideoUri.ifBlank { "No video attached (pick Short .mp4)" }
                                .substringAfterLast('/')
                                .take(32),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.testTag("auto_pick_video_button")
                    ) {
                        Text(if (attachedVideoUri.isBlank()) "Attach Video" else "Change")
                    }
                }
            }

            if (onAiOptimize != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isAiOptimizing = true
                            val result = onAiOptimize(youtubeTitle, youtubeDescription, instagramCaption)
                            isAiOptimizing = false
                            result.fold(
                                onSuccess = { draft ->
                                    youtubeTitle = draft.youtubeTitle.take(100)
                                    youtubeDescription = draft.youtubeDescription
                                    instagramCaption = draft.instagramCaption
                                    Toast.makeText(context, "AI optimized YouTube title & IG captions", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, err.localizedMessage ?: "AI optimization failed", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = !isAiOptimizing && !isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("auto_ai_optimize_button")
                ) {
                    if (isAiOptimizing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Polishing Title & Captions...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Optimize YouTube Title & IG Captions")
                    }
                }
            }

            if (youtubeEnabled) {
                OutlinedTextField(
                    value = youtubeTitle,
                    onValueChange = { if (it.length <= 100) youtubeTitle = it },
                    label = { Text("YouTube Short Title (${youtubeTitle.length}/100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auto_youtube_title_input")
                )

                OutlinedTextField(
                    value = youtubeDescription,
                    onValueChange = { youtubeDescription = it },
                    label = { Text("YouTube Description & Captions (#Shorts)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("auto_youtube_desc_input")
                )

                Text("YouTube Visibility", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("public" to "Public", "unlisted" to "Unlisted", "private" to "Private").forEach { (key, label) ->
                        FilterChip(
                            selected = youtubePrivacy == key,
                            onClick = { youtubePrivacy = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (instagramEnabled) {
                OutlinedTextField(
                    value = instagramCaption,
                    onValueChange = { instagramCaption = it },
                    label = { Text("Instagram Reels Caption & Hashtags") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("auto_instagram_caption_input")
                )
            }

            Text("Automation Timing", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scheduleMode == "now",
                    onClick = { scheduleMode = "now" },
                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Post Now") },
                    modifier = Modifier.testTag("auto_timing_now")
                )
                FilterChip(
                    selected = scheduleMode == "schedule",
                    onClick = { scheduleMode = "schedule" },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Schedule Later") },
                    modifier = Modifier.testTag("auto_timing_schedule")
                )
            }

            if (scheduleMode == "schedule") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scheduledIso = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).toString()
                    }) {
                        Text("+1 Hour")
                    }
                    OutlinedButton(onClick = {
                        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1)
                        scheduledIso = "${tomorrow}T%02d:00:00Z".format(settings.defaultPostHourUtc)
                    }) {
                        Text("Tomorrow ${settings.defaultPostHourUtc}:00 UTC")
                    }
                }
                OutlinedTextField(
                    value = scheduledIso,
                    onValueChange = { scheduledIso = it },
                    label = { Text("UTC Schedule Time (ISO-8601)") },
                    placeholder = { Text("2026-11-20T16:00:00Z") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auto_schedule_iso_input")
                )
            }

            Button(
                onClick = {
                    if (youtubeEnabled && youtubeTitle.isBlank()) {
                        Toast.makeText(context, "Enter a YouTube Short title first.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSubmitAutomation(
                        PublishAutomationRequest(
                            youtubeEnabled = youtubeEnabled,
                            instagramEnabled = instagramEnabled,
                            youtubeTitle = youtubeTitle.ifBlank { instagramCaption.take(90).ifBlank { "Viral Short #Shorts" } },
                            youtubeDescription = youtubeDescription,
                            youtubePrivacy = youtubePrivacy,
                            instagramCaption = instagramCaption,
                            videoUri = attachedVideoUri.takeIf { it.isNotBlank() },
                            scheduledIso = if (scheduleMode == "schedule") scheduledIso.trim().takeIf { it.isNotBlank() } else null
                        )
                    )
                },
                enabled = !isBusy && (youtubeEnabled || instagramEnabled),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auto_submit_post_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Queueing Automation...")
                } else {
                    Text(
                        if (scheduleMode == "schedule") "Schedule Auto-Post to Channels"
                        else "Automate Post Now"
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val combined = buildString {
                        if (youtubeTitle.isNotBlank()) appendLine(youtubeTitle)
                        if (instagramCaption.isNotBlank()) {
                            appendLine()
                            appendLine(instagramCaption)
                        } else if (youtubeDescription.isNotBlank()) {
                            appendLine()
                            appendLine(youtubeDescription)
                        }
                    }.trim()
                    if (attachedVideoUri.isNotBlank()) {
                        shareVideoUri(context, attachedVideoUri, youtubeTitle, combined)
                    } else {
                        shareTextContent(context, youtubeTitle, combined)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Video & Captions to Apps")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
