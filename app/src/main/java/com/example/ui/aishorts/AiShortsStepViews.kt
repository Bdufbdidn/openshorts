package com.example.ui.aishorts

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AiShortProject
import com.example.data.model.GeneratedUgcScript
import com.example.data.model.ProductResearchResult
import com.example.data.model.UgcAvatar
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ScannerProcessingCard
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.copyToClipboard
import com.example.ui.components.shareImageFile
import com.example.ui.components.shareTextContent
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiShortsSetupStep(
    videoMode: String,
    onVideoModeChange: (String) -> Unit,
    productUrl: String,
    onProductUrlChange: (String) -> Unit,
    productDescription: String,
    onProductDescriptionChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    actorGender: String,
    onActorGenderChange: (String) -> Unit,
    researchState: UiState<ProductResearchResult>,
    onResearchClick: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Production Tier", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = videoMode == "lowcost",
                    onClick = { onVideoModeChange("lowcost") },
                    label = { Text("Low Cost (~$0.80)") }
                )
                FilterChip(
                    selected = videoMode == "premium",
                    onClick = { onVideoModeChange("premium") },
                    label = { Text("Premium (~$2.00)") }
                )
            }

            OutlinedTextField(
                value = productUrl,
                onValueChange = onProductUrlChange,
                label = { Text("Product or Business URL (Optional)") },
                placeholder = { Text("https://myproduct.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ai_shorts_url_input")
            )

            OutlinedTextField(
                value = productDescription,
                onValueChange = onProductDescriptionChange,
                label = { Text("What does your product do?") },
                placeholder = { Text("Describe benefits, target audience, or offer...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("ai_shorts_desc_input")
            )

            Text("Language & Presenter", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "en", onClick = { onLanguageChange("en") }, label = { Text("English") })
                FilterChip(selected = language == "es", onClick = { onLanguageChange("es") }, label = { Text("Spanish") })
                FilterChip(selected = actorGender == "female", onClick = { onActorGenderChange("female") }, label = { Text("Female Actor") })
                FilterChip(selected = actorGender == "male", onClick = { onActorGenderChange("male") }, label = { Text("Male Actor") })
            }

            if (researchState is UiState.Error) {
                ErrorBanner(researchState.message)
            }

            Button(
                onClick = onResearchClick,
                enabled = researchState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("ai_shorts_research_button")
            ) {
                if (researchState is UiState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Researching Angles...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze Product & Hooks")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiShortsAnalysisStep(
    research: ProductResearchResult,
    style: String,
    onStyleChange: (String) -> Unit,
    scriptCount: Float,
    onScriptCountChange: (Float) -> Unit,
    scriptsState: UiState<List<GeneratedUgcScript>>,
    onGenerateScripts: () -> Unit
) {
    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(research.productName, style = MaterialTheme.typography.titleLarge)
            Text("${research.industry} • ${research.targetAudience}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(research.oneLiner, style = MaterialTheme.typography.bodyMedium)
            Text("Pain Points", style = MaterialTheme.typography.labelLarge)
            research.painPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Text("Emotional Hooks", style = MaterialTheme.typography.labelLarge)
            research.emotionalHooks.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        }
    }

    StudioGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose UGC Format", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ugc" to "UGC Natural",
                    "educational" to "Educational",
                    "shock" to "Shock / Hook",
                    "story" to "Storytelling",
                    "comparison" to "Before / After"
                ).forEach { (key, label) ->
                    FilterChip(selected = style == key, onClick = { onStyleChange(key) }, label = { Text(label) })
                }
            }

            Text("Scripts to Generate: ${scriptCount.toInt()}")
            Slider(value = scriptCount, onValueChange = onScriptCountChange, valueRange = 1f..3f, steps = 1)

            if (scriptsState is UiState.Error) {
                ErrorBanner(scriptsState.message)
            }

            Button(
                onClick = onGenerateScripts,
                enabled = scriptsState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("ai_shorts_generate_scripts_button")
            ) {
                if (scriptsState is UiState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Writing Scripts...")
                } else {
                    Text("Generate UGC Scripts")
                }
            }
        }
    }
}

@Composable
internal fun AiShortsConfigureStep(
    currentScripts: List<GeneratedUgcScript>,
    selectedScriptIndex: Int,
    onSelectScript: (Int) -> Unit,
    activeScript: GeneratedUgcScript?,
    editedNarration: String,
    onEditedNarrationChange: (String) -> Unit,
    actorImageState: UiState<Pair<Bitmap, String>>,
    savedAvatars: List<UgcAvatar>,
    selectedExistingActorPath: String?,
    onSelectExistingActorPath: (String?) -> Unit,
    onGeneratePortrait: (GeneratedUgcScript) -> Unit,
    onPickPhoto: () -> Unit,
    onSaveCampaign: () -> Unit
) {
    if (currentScripts.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(currentScripts.size) { idx ->
                FilterChip(
                    selected = selectedScriptIndex == idx,
                    onClick = { onSelectScript(idx) },
                    label = { Text("Script #${idx + 1}") }
                )
            }
        }
    }

    activeScript?.let { script ->
        StudioGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(script.title, style = MaterialTheme.typography.titleLarge)
                Text("Hook: ${script.hookText}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Text("Voice: ${script.voiceName} • ~${script.durationSeconds}s", style = MaterialTheme.typography.labelSmall)

                OutlinedTextField(
                    value = editedNarration,
                    onValueChange = onEditedNarrationChange,
                    label = { Text("Edit Voiceover Narration") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                script.segments.forEach { seg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${seg.label} (${seg.startSec}s-${seg.endSec}s)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(seg.dialogue, style = MaterialTheme.typography.bodyMedium)
                            Text("Cam: ${seg.visualDirection}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        StudioGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI Presenter Portrait", style = MaterialTheme.typography.titleMedium)
                Text(script.actorDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onGeneratePortrait(script) },
                        enabled = actorImageState !is UiState.Loading,
                        modifier = Modifier.weight(1f).testTag("generate_actor_portrait_button")
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate AI Actor")
                    }

                    OutlinedButton(
                        onClick = onPickPhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick Photo")
                    }
                }

                when (actorImageState) {
                    is UiState.Loading -> ScannerProcessingCard(
                        title = "Nano Banana 2.1 Rendering Portrait",
                        subtitle = "Synthesizing photorealistic UGC presenter (30-60s)..."
                    )
                    is UiState.Success -> Image(
                        bitmap = actorImageState.data.first.asImageBitmap(),
                        contentDescription = "Generated AI Actor Portrait",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    is UiState.Error -> ErrorBanner(actorImageState.message)
                    UiState.Idle -> {
                        if (savedAvatars.isNotEmpty()) {
                            Text("Or reuse from UGC Gallery:", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(savedAvatars, key = { it.id }) { av ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            if (selectedExistingActorPath == av.localImagePath) 2.dp else 1.dp,
                                            if (selectedExistingActorPath == av.localImagePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        onClick = { onSelectExistingActorPath(av.localImagePath) }
                                    ) {
                                        Column(Modifier.padding(6.dp).widthIn(max = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            if (!av.localImagePath.isNullOrBlank() && File(av.localImagePath).exists()) {
                                                AsyncImage(
                                                    model = File(av.localImagePath),
                                                    contentDescription = av.name,
                                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Text(av.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onSaveCampaign,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_ai_short_button")
                ) {
                    Text("Save & Finalize Storyboard")
                }
            }
        }
    }
}

@Composable
internal fun AiShortsResultStep(
    context: Context,
    project: AiShortProject,
    onOpenAutoPost: () -> Unit,
    onResetWizard: () -> Unit
) {
    StudioGlassCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Campaign Saved to Gallery", style = MaterialTheme.typography.titleLarge)
            }
            Text(project.scriptTitle, style = MaterialTheme.typography.titleMedium)

            if (!project.actorImagePath.isNullOrBlank() && File(project.actorImagePath).exists()) {
                AsyncImage(
                    model = File(project.actorImagePath),
                    contentDescription = "AI Actor",
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(
                        if (project.actorGender == "male") R.drawable.img_actor_marcus else R.drawable.img_actor_emma
                    ),
                    contentDescription = "Studio Presenter",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Text(project.fullNarration, style = MaterialTheme.typography.bodyMedium)
            Text(project.hashtags.joinToString(" "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, "UGC Script", project.fullNarration)
                        Toast.makeText(context, "Script copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                Button(
                    onClick = {
                        val path = project.actorImagePath
                        val shareBody = "${project.scriptTitle}\n\n${project.fullNarration}\n\n${project.hashtags.joinToString(" ")}"
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            shareImageFile(context, path, shareBody)
                        } else {
                            shareTextContent(context, project.scriptTitle, shareBody)
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
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ai_short_automate_post_button")
            ) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto-Post to YouTube & IG")
            }
        }
    }

    OutlinedButton(
        onClick = onResetWizard,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("Create Another AI Short")
    }
}
