package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.media.VideoFilterPreset
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import kotlin.math.roundToInt

/**
 * Real-time Media3 video filter stack selector & shader pipeline visualizer.
 * Users can tap presets (Grayscale, Vintage, High Contrast, Cinematic, etc.) to
 * stack effects in sequence, reorder or remove layers, and fine-tune tone sliders
 * with live ExoPlayer preview before exporting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoFilterStackPanel(
    filterStack: List<String>,
    onFilterStackChange: (List<String>) -> Unit,
    contrastAdjust: Float = 0f,
    saturationAdjust: Float = 0f,
    brightnessAdjust: Float = 0f,
    onToneAdjustChange: ((contrast: Float, saturation: Float, brightness: Float) -> Unit)? = null,
    compact: Boolean = false,
    testTagPrefix: String = "video_filter",
    modifier: Modifier = Modifier
) {
    var showFineTune by remember { mutableStateOf(false) }
    val hasFilters = Media3VideoFilterFactory.hasActiveFilters(
        filterStack = filterStack,
        contrastAdjust = contrastAdjust,
        saturationAdjust = saturationAdjust,
        brightnessAdjust = brightnessAdjust
    )
    val activeSummary = Media3VideoFilterFactory.describeStack(
        filterStack = filterStack,
        contrastAdjust = contrastAdjust,
        saturationAdjust = saturationAdjust,
        brightnessAdjust = brightnessAdjust
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = stringResource(R.string.cd_video_filters),
                    tint = if (hasFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = if (compact) "Media3 Filter Stack" else "Real-Time Media3 Filter Stack",
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Active: $activeSummary",
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasFilters) ViralEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToneAdjustChange != null) {
                    IconButton(
                        onClick = { showFineTune = !showFineTune },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_tune_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Fine-tune contrast, saturation, and brightness",
                            tint = if (showFineTune) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasFilters) {
                    TextButton(
                        onClick = {
                            onFilterStackChange(emptyList())
                            onToneAdjustChange?.invoke(0f, 0f, 0f)
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("${testTagPrefix}_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.cd_clear_filters),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }

        // Preset strip (tap to stack or toggle presets in real time)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "original") {
                val isOriginal = filterStack.isEmpty()
                Surface(
                    onClick = { onFilterStackChange(emptyList()) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOriginal) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isOriginal) 1.5.dp else 1.dp,
                        color = if (isOriginal) MaterialTheme.colorScheme.primary else ObsidianBorder
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_preset_original")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF94A3B8), Color(0xFF334155))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                        Text(
                            text = "Original",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            items(VideoFilterPreset.entries, key = { it.id }) { preset ->
                val stackPosition = filterStack.indexOfFirst { it.equals(preset.id, ignoreCase = true) }
                val isSelected = stackPosition >= 0
                Surface(
                    onClick = {
                        onFilterStackChange(Media3VideoFilterFactory.togglePresetInStack(filterStack, preset.id))
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else ObsidianBorder
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_preset_${preset.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(preset.swatchPrimaryHex),
                                            Color(preset.swatchSecondaryHex)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isSelected && filterStack.size > 1) {
                                    "#${stackPosition + 1} ${preset.label}"
                                } else {
                                    preset.label
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (!compact) {
                                Text(
                                    text = preset.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active ordered Media3 Effect Stack pipeline strip
        AnimatedVisibility(visible = filterStack.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_active_stack")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = ViralAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Stacked Shader Pipeline (${filterStack.size} layer${if (filterStack.size == 1) "" else "s"}) — tap presets to layer more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterStack.forEachIndexed { idx, presetId ->
                            val preset = VideoFilterPreset.fromId(presetId) ?: return@forEachIndexed
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${preset.label}",
                                        fontFamily = JetBrainsMonoFont,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (filterStack.size > 1 && idx > 0) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Move filter earlier in stack",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onFilterStackChange(
                                                        Media3VideoFilterFactory.movePresetInStack(
                                                            filterStack,
                                                            idx,
                                                            moveEarlier = true
                                                        )
                                                    )
                                                }
                                        )
                                    }
                                    if (filterStack.size > 1 && idx < filterStack.lastIndex) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Move filter later in stack",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onFilterStackChange(
                                                        Media3VideoFilterFactory.movePresetInStack(
                                                            filterStack,
                                                            idx,
                                                            moveEarlier = false
                                                        )
                                                    )
                                                }
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${preset.label} filter",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onFilterStackChange(filterStack.filterIndexed { i, _ -> i != idx })
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Optional Fine-Tune Tone Adjustments layered onto Media3 stack
        AnimatedVisibility(visible = showFineTune && onToneAdjustChange != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_fine_tune_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToneSliderRow(
                        label = "Contrast",
                        valueText = "${(contrastAdjust * 100).roundToInt()}%",
                        value = contrastAdjust,
                        valueRange = -0.5f..0.7f,
                        onValueChange = { c ->
                            onToneAdjustChange?.invoke(c, saturationAdjust, brightnessAdjust)
                        }
                    )
                    ToneSliderRow(
                        label = "Saturation",
                        valueText = "${saturationAdjust.roundToInt()}%",
                        value = saturationAdjust,
                        valueRange = -80f..80f,
                        onValueChange = { s ->
                            onToneAdjustChange?.invoke(contrastAdjust, s, brightnessAdjust)
                        }
                    )
                    ToneSliderRow(
                        label = "Brightness",
                        valueText = "${(brightnessAdjust * 100).roundToInt()}%",
                        value = brightnessAdjust,
                        valueRange = -0.35f..0.35f,
                        onValueChange = { b ->
                            onToneAdjustChange?.invoke(contrastAdjust, saturationAdjust, b)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToneSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = valueText,
                fontFamily = JetBrainsMonoFont,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
