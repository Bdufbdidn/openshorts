package com.example.data.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import com.example.data.model.CaptionBurnStyle
import kotlin.math.abs

/**
 * Curated real-time Media3 video filter presets that can be applied individually
 * or layered together in an ordered Media3 Effects pipeline for live ExoPlayer preview
 * and hardware-accelerated Transformer MP4 export.
 */
enum class VideoFilterPreset(
    val id: String,
    val label: String,
    val tagline: String,
    val swatchPrimaryHex: Long,
    val swatchSecondaryHex: Long
) {
    GRAYSCALE(
        id = "grayscale",
        label = "Grayscale",
        tagline = "Monochrome B&W look",
        swatchPrimaryHex = 0xFFD4D4D8,
        swatchSecondaryHex = 0xFF3F3F46
    ),
    VINTAGE(
        id = "vintage",
        label = "Vintage",
        tagline = "Warm retro film fade",
        swatchPrimaryHex = 0xFFD97706,
        swatchSecondaryHex = 0xFF78350F
    ),
    HIGH_CONTRAST(
        id = "high_contrast",
        label = "High Contrast",
        tagline = "Punchy blacks & crisp pop",
        swatchPrimaryHex = 0xFFF8FAFC,
        swatchSecondaryHex = 0xFF09090B
    ),
    CINEMATIC(
        id = "cinematic",
        label = "Cinematic",
        tagline = "Filmic depth & warm highlights",
        swatchPrimaryHex = 0xFFEA580C,
        swatchSecondaryHex = 0xFF0F766E
    ),
    WARM_GLOW(
        id = "warm_glow",
        label = "Warm Glow",
        tagline = "Golden hour warmth",
        swatchPrimaryHex = 0xFFFBBF24,
        swatchSecondaryHex = 0xFFB45309
    ),
    COOL_NOIR(
        id = "cool_noir",
        label = "Cool Noir",
        tagline = "Crisp icy slate shadows",
        swatchPrimaryHex = 0xFF38BDF8,
        swatchSecondaryHex = 0xFF1E293B
    ),
    VIVID(
        id = "vivid",
        label = "Vivid",
        tagline = "Bold saturated social feed",
        swatchPrimaryHex = 0xFFEC4899,
        swatchSecondaryHex = 0xFF3B82F6
    );

    companion object {
        fun fromId(id: String): VideoFilterPreset? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

@OptIn(UnstableApi::class)
object Media3VideoFilterFactory {

    fun buildVideoEffects(style: CaptionBurnStyle): List<Effect> = buildVideoEffects(
        filterStack = style.filterStack,
        contrastAdjust = style.filterContrast,
        saturationAdjust = style.filterSaturation,
        brightnessAdjust = style.filterBrightness
    )

    fun buildVideoEffects(
        filterStack: List<String>,
        contrastAdjust: Float = 0f,
        saturationAdjust: Float = 0f,
        brightnessAdjust: Float = 0f
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        filterStack.forEach { presetId ->
            when (VideoFilterPreset.fromId(presetId)) {
                VideoFilterPreset.GRAYSCALE -> {
                    effects += RgbFilter.createGrayscaleFilter()
                }
                VideoFilterPreset.VINTAGE -> {
                    effects += HslAdjustment.Builder()
                        .adjustHue(-6f)
                        .adjustSaturation(-34f)
                        .build()
                    effects += RgbAdjustment.Builder()
                        .setRedScale(1.16f)
                        .setGreenScale(1.03f)
                        .setBlueScale(0.76f)
                        .build()
                    effects += Contrast(0.12f)
                }
                VideoFilterPreset.HIGH_CONTRAST -> {
                    effects += Contrast(0.46f)
                    effects += HslAdjustment.Builder()
                        .adjustSaturation(14f)
                        .build()
                }
                VideoFilterPreset.CINEMATIC -> {
                    effects += Contrast(0.28f)
                    effects += HslAdjustment.Builder()
                        .adjustSaturation(16f)
                        .adjustLightness(-4f)
                        .build()
                    effects += RgbAdjustment.Builder()
                        .setRedScale(1.06f)
                        .setGreenScale(1.0f)
                        .setBlueScale(0.90f)
                        .build()
                }
                VideoFilterPreset.WARM_GLOW -> {
                    effects += RgbAdjustment.Builder()
                        .setRedScale(1.14f)
                        .setGreenScale(1.04f)
                        .setBlueScale(0.84f)
                        .build()
                    effects += Brightness(0.06f)
                }
                VideoFilterPreset.COOL_NOIR -> {
                    effects += Contrast(0.30f)
                    effects += RgbAdjustment.Builder()
                        .setRedScale(0.86f)
                        .setGreenScale(0.98f)
                        .setBlueScale(1.16f)
                        .build()
                    effects += HslAdjustment.Builder()
                        .adjustSaturation(-22f)
                        .build()
                }
                VideoFilterPreset.VIVID -> {
                    effects += HslAdjustment.Builder()
                        .adjustSaturation(38f)
                        .build()
                    effects += Contrast(0.18f)
                }
                null -> Unit
            }
        }

        val clampedContrast = contrastAdjust.coerceIn(-0.6f, 0.8f)
        if (abs(clampedContrast) >= 0.02f) {
            effects += Contrast(clampedContrast)
        }

        val clampedSaturation = saturationAdjust.coerceIn(-100f, 100f)
        if (abs(clampedSaturation) >= 1f) {
            effects += HslAdjustment.Builder()
                .adjustSaturation(clampedSaturation)
                .build()
        }

        val clampedBrightness = brightnessAdjust.coerceIn(-0.5f, 0.5f)
        if (abs(clampedBrightness) >= 0.02f) {
            effects += Brightness(clampedBrightness)
        }

        return effects
    }

    fun cacheKey(style: CaptionBurnStyle): String = cacheKey(
        filterStack = style.filterStack,
        contrastAdjust = style.filterContrast,
        saturationAdjust = style.filterSaturation,
        brightnessAdjust = style.filterBrightness
    )

    fun cacheKey(
        filterStack: List<String>,
        contrastAdjust: Float = 0f,
        saturationAdjust: Float = 0f,
        brightnessAdjust: Float = 0f
    ): String {
        val stackKey = filterStack.joinToString("+")
        val c = (contrastAdjust * 100).toInt()
        val s = saturationAdjust.toInt()
        val b = (brightnessAdjust * 100).toInt()
        return "$stackKey|c=$c|s=$s|b=$b"
    }

    fun hasActiveFilters(
        filterStack: List<String>,
        contrastAdjust: Float = 0f,
        saturationAdjust: Float = 0f,
        brightnessAdjust: Float = 0f
    ): Boolean {
        return filterStack.any { VideoFilterPreset.fromId(it) != null } ||
            abs(contrastAdjust) >= 0.02f ||
            abs(saturationAdjust) >= 1f ||
            abs(brightnessAdjust) >= 0.02f
    }

    fun describeStack(
        filterStack: List<String>,
        contrastAdjust: Float = 0f,
        saturationAdjust: Float = 0f,
        brightnessAdjust: Float = 0f
    ): String {
        val presetLabels = filterStack.mapNotNull { VideoFilterPreset.fromId(it)?.label }
        val customTweaks = buildList {
            if (abs(contrastAdjust) >= 0.02f) add("Contrast")
            if (abs(saturationAdjust) >= 1f) add("Saturation")
            if (abs(brightnessAdjust) >= 0.02f) add("Brightness")
        }
        val all = presetLabels + customTweaks
        return if (all.isEmpty()) "Original" else all.joinToString(" → ")
    }

    fun togglePresetInStack(currentStack: List<String>, presetId: String): List<String> {
        val clean = presetId.trim().lowercase()
        if (VideoFilterPreset.fromId(clean) == null) return currentStack
        return if (currentStack.any { it.equals(clean, ignoreCase = true) }) {
            currentStack.filterNot { it.equals(clean, ignoreCase = true) }
        } else {
            currentStack + clean
        }
    }

    fun movePresetInStack(currentStack: List<String>, index: Int, moveEarlier: Boolean): List<String> {
        if (index !in currentStack.indices) return currentStack
        val targetIndex = if (moveEarlier) index - 1 else index + 1
        if (targetIndex !in currentStack.indices) return currentStack
        val mutable = currentStack.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        return mutable
    }

    fun parseCsv(csv: String): List<String> =
        csv.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && VideoFilterPreset.fromId(it) != null }
            .distinct()

    fun toCsv(stack: List<String>): String = stack.joinToString(",")
}
