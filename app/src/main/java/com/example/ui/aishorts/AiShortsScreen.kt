package com.example.ui.aishorts

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UiState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.PublishAutomationSheet
import com.example.ui.components.ScannerProcessingCard

@Composable
fun AiShortsScreen(
    currentUserId: String,
    onOpenAutomationQueue: () -> Unit = {},
    viewModel: AiShortsViewModel = viewModel(
        key = currentUserId,
        factory = AiShortsViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsStateWithLifecycle()
    val researchState by viewModel.researchState.collectAsStateWithLifecycle()
    val scriptsState by viewModel.scriptsState.collectAsStateWithLifecycle()
    val selectedScriptIndex by viewModel.selectedScriptIndex.collectAsStateWithLifecycle()
    val actorImageState by viewModel.actorImageState.collectAsStateWithLifecycle()
    val finalSaveState by viewModel.finalSaveState.collectAsStateWithLifecycle()
    val savedAvatars by viewModel.savedAvatars.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isAutomatingPost by viewModel.isAutomatingPost.collectAsStateWithLifecycle()
    val automationFeedback by viewModel.automationFeedback.collectAsStateWithLifecycle()

    var showAutoPostSheet by remember { mutableStateOf(false) }

    LaunchedEffect(automationFeedback) {
        automationFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAutomationFeedback()
        }
    }

    var videoMode by remember { mutableStateOf("lowcost") }
    var productUrl by remember { mutableStateOf("") }
    var productDescription by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var actorGender by remember { mutableStateOf("female") }
    var style by remember { mutableStateOf("ugc") }
    var scriptCount by remember { mutableFloatStateOf(2f) }
    var editedNarration by remember { mutableStateOf("") }
    var selectedExistingActorPath by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedExistingActorPath = null
            viewModel.setPickedActorUri(uri)
        }
    }

    val currentScripts = (scriptsState as? UiState.Success)?.data.orEmpty()
    val activeScript = currentScripts.getOrNull(selectedScriptIndex)
    LaunchedEffect(activeScript?.title, activeScript?.fullNarration) {
        if (activeScript != null) {
            editedNarration = activeScript.fullNarration
        }
    }

    BackHandler(enabled = step.index > 0) {
        viewModel.stepBack()
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (step.index > 0 && step != AiShortsStep.RESULT) {
                        IconButton(
                            onClick = { viewModel.stepBack() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Shorts UGC Creator", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            step.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    AiShortsStep.SETUP -> AiShortsSetupStep(
                        videoMode = videoMode,
                        onVideoModeChange = { videoMode = it },
                        productUrl = productUrl,
                        onProductUrlChange = { productUrl = it },
                        productDescription = productDescription,
                        onProductDescriptionChange = { productDescription = it },
                        language = language,
                        onLanguageChange = { language = it },
                        actorGender = actorGender,
                        onActorGenderChange = { actorGender = it },
                        researchState = researchState,
                        onResearchClick = { viewModel.researchProduct(productUrl, productDescription, language) }
                    )

                    AiShortsStep.ANALYSIS -> {
                        val research = (researchState as? UiState.Success)?.data
                        if (research != null) {
                            AiShortsAnalysisStep(
                                research = research,
                                style = style,
                                onStyleChange = { style = it },
                                scriptCount = scriptCount,
                                onScriptCountChange = { scriptCount = it },
                                scriptsState = scriptsState,
                                onGenerateScripts = {
                                    viewModel.generateScripts(style, language, actorGender, videoMode, scriptCount.toInt())
                                }
                            )
                        }
                    }

                    AiShortsStep.CONFIGURE -> AiShortsConfigureStep(
                        currentScripts = currentScripts,
                        selectedScriptIndex = selectedScriptIndex,
                        onSelectScript = { viewModel.selectScript(it) },
                        activeScript = activeScript,
                        editedNarration = editedNarration,
                        onEditedNarrationChange = { editedNarration = it },
                        actorImageState = actorImageState,
                        savedAvatars = savedAvatars,
                        selectedExistingActorPath = selectedExistingActorPath,
                        onSelectExistingActorPath = { selectedExistingActorPath = it },
                        onGeneratePortrait = { script ->
                            selectedExistingActorPath = null
                            viewModel.generateActorPortrait(
                                prompt = script.actorDescription,
                                gender = actorGender,
                                actorName = "${(researchState as? UiState.Success)?.data?.productName.orEmpty()} Actor"
                            )
                        },
                        onPickPhoto = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSaveCampaign = {
                            viewModel.saveCampaign(
                                productUrl = productUrl,
                                productDescription = productDescription,
                                videoMode = videoMode,
                                style = style,
                                language = language,
                                actorGender = actorGender,
                                editedNarration = editedNarration,
                                selectedActorPath = selectedExistingActorPath
                            )
                        }
                    )

                    AiShortsStep.GENERATE -> {
                        ScannerProcessingCard(
                            title = "Saving UGC Campaign",
                            subtitle = "Persisting storyboard & actor assets to cloud..."
                        )
                        if (finalSaveState is UiState.Error) {
                            ErrorBanner((finalSaveState as UiState.Error).message)
                        }
                    }

                    AiShortsStep.RESULT -> {
                        val project = (finalSaveState as? UiState.Success)?.data
                        if (project != null) {
                            AiShortsResultStep(
                                context = context,
                                project = project,
                                onOpenAutoPost = { showAutoPostSheet = true },
                                onResetWizard = { viewModel.resetWizard() }
                            )

                            if (showAutoPostSheet) {
                                val tags = project.hashtags.joinToString(" ").ifBlank { "#shorts #reels" }
                                PublishAutomationSheet(
                                    sheetTitle = "Auto-Post AI Short to YT & IG",
                                    initialYoutubeTitle = "${project.scriptTitle.take(85)} #Shorts",
                                    initialYoutubeDescription = buildString {
                                        if (!project.hookText.isNullOrBlank()) appendLine(project.hookText).appendLine()
                                        appendLine(project.fullNarration)
                                        appendLine()
                                        append(tags)
                                    }.trim(),
                                    initialInstagramCaption = "${project.caption.orEmpty().ifBlank { project.fullNarration }}\n\n$tags".trim(),
                                    initialVideoUri = null,
                                    settings = settings,
                                    isBusy = isAutomatingPost,
                                    onDismiss = { showAutoPostSheet = false },
                                    onAiOptimize = { title, ytDesc, igCap ->
                                        viewModel.optimizePublishMetadata(project, title, ytDesc, igCap)
                                    },
                                    onSubmitAutomation = { req ->
                                        viewModel.automateAiShortPublish(project, req)
                                        showAutoPostSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
