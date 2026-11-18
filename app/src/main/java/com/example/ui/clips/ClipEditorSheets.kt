package com.example.ui.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.GeneratedClip
import com.example.data.model.IntegrationSettings
import com.example.ui.components.parseSafeColor
import com.example.ui.components.shareTextContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtitleEditorSheet(
    clip: GeneratedClip,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (position: String, animation: String, font: String, colorHex: String, highlightHex: String, text: String) -> Unit
) {
    var position by remember(clip.id) { mutableStateOf(clip.subtitlePosition) }
    var animation by remember(clip.id) { mutableStateOf(clip.subtitleAnimation) }
    var font by remember(clip.id) { mutableStateOf(clip.subtitleFont) }
    var colorHex by remember(clip.id) { mutableStateOf(clip.subtitleColorHex) }
    var highlightHex by remember(clip.id) { mutableStateOf(clip.subtitleHighlightHex) }
    var text by remember(clip.id) { mutableStateOf(clip.subtitleText.orEmpty()) }

    val highlightPresets = listOf("#3B82F6", "#F59E0B", "#10B981", "#EC4899", "#8B5CF6")
    val basePresets = listOf("#FFFFFF", "#FDE047", "#E4E4E7")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit Subtitles & Style", style = MaterialTheme.typography.headlineSmall)

            Text("Position", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("top", "middle", "bottom").forEach { pos ->
                    FilterChip(
                        selected = position == pos,
                        onClick = { position = pos },
                        label = { Text(pos.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Animation", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "pop" to "Pop",
                    "word-highlight" to "Word Highlight",
                    "karaoke" to "Karaoke",
                    "none" to "None"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = animation == key,
                        onClick = { animation = key },
                        label = { Text(label) }
                    )
                }
            }

            Text("Font Family", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SpaceGrotesk", "NotoSerif", "PlusJakartaSans", "JetBrainsMono").forEach { f ->
                    FilterChip(
                        selected = font == f,
                        onClick = { font = f },
                        label = { Text(f) }
                    )
                }
            }

            Text("Text & Highlight Colors", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                basePresets.forEach { hex ->
                    ColorSwatchDot(
                        hex = hex,
                        selected = colorHex == hex,
                        onClick = { colorHex = hex }
                    )
                }
                Spacer(Modifier.size(8.dp))
                highlightPresets.forEach { hex ->
                    ColorSwatchDot(
                        hex = hex,
                        selected = highlightHex == hex,
                        onClick = { highlightHex = hex }
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Subtitle Transcript") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSave(position, animation, font, colorHex, highlightHex, text) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_subtitles_button")
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Apply Subtitle Style")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorSwatchDot(hex: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(parseSafeColor(hex, Color.White))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HookEditorSheet(
    clip: GeneratedClip,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, position: String, size: String, animation: String, durationSec: Long) -> Unit
) {
    var hookText by remember(clip.id) { mutableStateOf(clip.viralHookText.orEmpty()) }
    var position by remember(clip.id) { mutableStateOf(clip.hookPosition) }
    var size by remember(clip.id) { mutableStateOf(clip.hookSize) }
    var animation by remember(clip.id) { mutableStateOf(clip.hookAnimation) }
    var duration by remember(clip.id) { mutableFloatStateOf(clip.hookDurationSec.toFloat().coerceIn(2f, 15f)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Viral Hook Overlay", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = hookText,
                onValueChange = { hookText = it },
                label = { Text("Hook Headline") },
                placeholder = { Text("Wait until you see the ending...") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Position", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("top", "center", "bottom").forEach { p ->
                    FilterChip(selected = position == p, onClick = { position = p }, label = { Text(p.uppercase()) })
                }
            }

            Text("Size & Motion", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S", "M", "L").forEach { s ->
                    FilterChip(selected = size == s, onClick = { size = s }, label = { Text("Size $s") })
                }
                listOf("spring", "fade", "slide-up", "none").forEach { a ->
                    FilterChip(selected = animation == a, onClick = { animation = a }, label = { Text(a) })
                }
            }

            Text("Display Duration: ${duration.toInt()}s", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 2f..15f,
                steps = 12
            )

            Button(
                onClick = { onSave(hookText, position, size, animation, duration.toLong()) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_hook_button")
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Save Hook Overlay")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslateDubSheet(
    clip: GeneratedClip,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onTranslate: (language: String) -> Unit
) {
    val languages = remember {
        listOf(
            "Spanish", "French", "German", "Portuguese", "Italian", "Japanese",
            "Korean", "Chinese (Mandarin)", "Hindi", "Arabic", "Dutch", "Turkish",
            "Polish", "Indonesian", "Vietnamese", "Thai", "Swedish", "Norwegian",
            "Danish", "Finnish", "Greek", "Czech", "Romanian", "Ukrainian",
            "Hebrew", "Malay", "Tagalog", "Hungarian", "Bulgarian", "English"
        )
    }
    var selectedLang by remember { mutableStateOf(clip.translatedLanguage ?: "Spanish") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Dub & Localize (30+ Languages)", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Gemini localizes your subtitles, viral hook, YouTube title, and TikTok hashtags natively.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                languages.forEach { lang ->
                    FilterChip(
                        selected = selectedLang == lang,
                        onClick = { selectedLang = lang },
                        label = { Text(lang) }
                    )
                }
            }

            Button(
                onClick = { onTranslate(selectedLang) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("run_translation_button")
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Localize to $selectedLang")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostOrScheduleSheet(
    clip: GeneratedClip,
    settings: IntegrationSettings,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onPublishUploadPost: (title: String, platforms: List<String>, scheduledIso: String?) -> Unit
) {
    val context = LocalContext.current
    var captionText by remember(clip.id) {
        mutableStateOf("${clip.youtubeTitle}\n\n${clip.tiktokCaption.orEmpty()}")
    }
    val selectedPlatforms = remember { mutableStateListOf("youtube", "tiktok") }
    var scheduleDate by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Publish or Export Clip", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = captionText,
                onValueChange = { captionText = it },
                label = { Text("Post Title & Caption") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Target Platforms", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("youtube", "tiktok", "instagram").forEach { plat ->
                    val active = plat in selectedPlatforms
                    FilterChip(
                        selected = active,
                        onClick = {
                            if (active && selectedPlatforms.size > 1) selectedPlatforms.remove(plat)
                            else if (!active) selectedPlatforms.add(plat)
                        },
                        label = { Text(plat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (settings.uploadPostApiKey.isNotBlank() && settings.uploadPostUsername.isNotBlank()) {
                OutlinedTextField(
                    value = scheduleDate,
                    onValueChange = { scheduleDate = it },
                    label = { Text("Optional ISO Schedule (e.g. 2026-04-01T15:00:00Z)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onPublishUploadPost(
                            captionText,
                            selectedPlatforms.toList(),
                            scheduleDate.takeIf { it.isNotBlank() }
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Publish via Upload-Post (${settings.uploadPostUsername})")
                }
            }

            OutlinedButton(
                onClick = {
                    shareTextContent(
                        context = context,
                        title = clip.youtubeTitle,
                        text = buildString {
                            appendLine(captionText)
                            if (!clip.viralHookText.isNullOrBlank()) appendLine("\nHook: ${clip.viralHookText}")
                            if (!clip.subtitleText.isNullOrBlank()) appendLine("\nTranscript:\n${clip.subtitleText}")
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Share Clip Script & Captions")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleWeekSheet(
    clips: List<GeneratedClip>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onScheduleWeek: (platforms: List<String>, startDate: String, hourUtc: Int) -> Unit
) {
    val selectedPlatforms = remember { mutableStateListOf("youtube", "tiktok", "instagram") }
    var startDate by remember { mutableStateOf("2026-04-01") }
    var hour by remember { mutableFloatStateOf(16f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Schedule Week (${clips.size} Clips)", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Queue daily multi-platform uploads via Upload-Post.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("youtube", "tiktok", "instagram").forEach { plat ->
                    val active = plat in selectedPlatforms
                    FilterChip(
                        selected = active,
                        onClick = {
                            if (active && selectedPlatforms.size > 1) selectedPlatforms.remove(plat)
                            else if (!active) selectedPlatforms.add(plat)
                        },
                        label = { Text(plat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("Start Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Daily Publish Hour (UTC): ${hour.toInt()}:00")
            Slider(value = hour, onValueChange = { hour = it }, valueRange = 0f..23f, steps = 22)

            Button(
                onClick = { onScheduleWeek(selectedPlatforms.toList(), startDate, hour.toInt()) },
                enabled = !isBusy && clips.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Queue ${clips.take(7).size} Daily Posts")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
