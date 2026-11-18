package com.example.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.CaptionBurnStyle
import com.example.ui.components.VideoFilterStackPanel
import com.example.ui.components.VideoPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTrimmedClipToBatchSheet(
    onDismiss: () -> Unit,
    onAddClip: (
        title: String,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        burnStyle: CaptionBurnStyle,
        saveToGallery: Boolean,
        uploadToFirebaseStorage: Boolean,
        startNow: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var clipTitle by remember { mutableStateOf("") }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(30_000L) }
    var saveToGallery by remember { mutableStateOf(true) }
    var uploadToFirebaseStorage by remember { mutableStateOf(true) }
    var filterStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var filterContrast by remember { mutableFloatStateOf(0f) }
    var filterSaturation by remember { mutableFloatStateOf(0f) }
    var filterBrightness by remember { mutableFloatStateOf(0f) }

    val filterEffectsKey = remember(filterStack, filterContrast, filterSaturation, filterBrightness) {
        Media3VideoFilterFactory.cacheKey(filterStack, filterContrast, filterSaturation, filterBrightness)
    }
    val activeEffects = remember(filterEffectsKey) {
        Media3VideoFilterFactory.buildVideoEffects(filterStack, filterContrast, filterSaturation, filterBrightness)
    }
    val currentBurnStyle = remember(filterStack, filterContrast, filterSaturation, filterBrightness) {
        CaptionBurnStyle(
            filterStack = filterStack,
            filterContrast = filterContrast,
            filterSaturation = filterSaturation,
            filterBrightness = filterBrightness
        )
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            trimStartMs = 0L
            trimEndMs = 30_000L
            if (clipTitle.isBlank()) {
                clipTitle = uri.lastPathSegment?.take(40) ?: "Batch Trimmed Clip"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Trimmed Clip to Batch", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick a video, adjust visual trim handles, stack Media3 filters, and queue it for sequential export & Firebase Storage upload.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("batch_sheet_pick_video_button")
            ) {
                Icon(Icons.Default.VideoFile, contentDescription = stringResource(R.string.cd_pick_video))
                Spacer(Modifier.width(8.dp))
                Text(selectedUri?.lastPathSegment ?: "Choose Video File", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            val currentUri = selectedUri
            if (currentUri != null) {
                VideoPlayer(
                    uri = currentUri,
                    autoplay = false,
                    repeat = true,
                    trimEnabled = true,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    videoEffects = activeEffects,
                    videoEffectsKey = filterEffectsKey,
                    onTrimRangeChange = { s, e ->
                        trimStartMs = s
                        trimEndMs = e
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                VideoFilterStackPanel(
                    filterStack = filterStack,
                    onFilterStackChange = { filterStack = it },
                    contrastAdjust = filterContrast,
                    saturationAdjust = filterSaturation,
                    brightnessAdjust = filterBrightness,
                    onToneAdjustChange = { c, s, b ->
                        filterContrast = c
                        filterSaturation = s
                        filterBrightness = b
                    },
                    compact = true,
                    testTagPrefix = "batch_sheet_filter"
                )
            }

            OutlinedTextField(
                value = clipTitle,
                onValueChange = { clipTitle = it },
                label = { Text("Export Clip Title") },
                placeholder = { Text("e.g. Product Demo Hook Cut") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batch_sheet_title_input")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = saveToGallery, onCheckedChange = { saveToGallery = it })
                Text("Auto-save exported MP4 to Movies/OpenShorts", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = uploadToFirebaseStorage,
                    onCheckedChange = { uploadToFirebaseStorage = it },
                    modifier = Modifier.testTag("batch_sheet_upload_checkbox")
                )
                Text("Upload exported MP4 to Firebase Storage", style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        currentUri?.let {
                            onAddClip(
                                clipTitle, it, trimStartMs, trimEndMs,
                                currentBurnStyle, saveToGallery, uploadToFirebaseStorage, false
                            )
                        }
                    },
                    enabled = currentUri != null,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("batch_sheet_queue_only_button")
                ) {
                    Text("Add to Batch")
                }
                Button(
                    onClick = {
                        currentUri?.let {
                            onAddClip(
                                clipTitle, it, trimStartMs, trimEndMs,
                                currentBurnStyle, saveToGallery, uploadToFirebaseStorage, true
                            )
                        }
                    },
                    enabled = currentUri != null,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("batch_sheet_queue_and_start_button")
                ) {
                    Text("Queue & Start")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
