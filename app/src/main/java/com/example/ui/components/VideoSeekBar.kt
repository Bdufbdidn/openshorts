package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ViralAmber
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MIN_TRIM_GAP_MS = 1000L

internal enum class SeekDragTarget {
    NONE,
    SCRUB,
    TRIM_START,
    TRIM_END
}

/**
 * Custom Canvas-drawn seek bar supporting tap-to-seek, drag scrubbing, buffered progress,
 * animated thumb expansion, floating timestamp pill, and optional draggable In/Out visual trim handles.
 */
@Composable
fun VideoSeekBar(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onScrubStart: (Long) -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trimEnabled: Boolean = false,
    trimStartMs: Long = 0L,
    trimEndMs: Long = durationMs,
    onTrimStartChange: ((Long) -> Unit)? = null,
    onTrimEndChange: ((Long) -> Unit)? = null,
    onTrimRangeCommit: ((Long, Long) -> Unit)? = null
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val safeMinGap = MIN_TRIM_GAP_MS.coerceAtMost(safeDuration / 4L).coerceAtLeast(250L)
    val safeTrimStartMs = trimStartMs.coerceIn(0L, (safeDuration - safeMinGap).coerceAtLeast(0L))
    val safeTrimEndMs = trimEndMs.coerceIn((safeTrimStartMs + safeMinGap).coerceAtMost(safeDuration), safeDuration)

    val playedFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val trimStartFraction = (safeTrimStartMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val trimEndFraction = (safeTrimEndMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var pillWidthPx by remember { mutableIntStateOf(0) }
    var dragTarget by remember { mutableStateOf(SeekDragTarget.NONE) }

    val density = LocalDensity.current
    val handleHitRadiusPx = remember(density) { with(density) { 26.dp.toPx() } }
    val horizontalPadPx = remember(density) { with(density) { 10.dp.toPx() } }

    val latestDuration by rememberUpdatedState(safeDuration)
    val latestTrimEnabled by rememberUpdatedState(trimEnabled)
    val latestTrimStartMs by rememberUpdatedState(safeTrimStartMs)
    val latestTrimEndMs by rememberUpdatedState(safeTrimEndMs)
    val latestSafeMinGap by rememberUpdatedState(safeMinGap)
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)
    val latestOnScrubMove by rememberUpdatedState(onScrubMove)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val latestOnTrimStartChange by rememberUpdatedState(onTrimStartChange)
    val latestOnTrimEndChange by rememberUpdatedState(onTrimEndChange)
    val latestOnTrimCommit by rememberUpdatedState(onTrimRangeCommit)

    val isDragging = dragTarget != SeekDragTarget.NONE
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging || trimEnabled) 6.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "trackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (dragTarget == SeekDragTarget.SCRUB) 9.dp else 6.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbRadius"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trimAccentColor = ViralAmber

    fun usableWidth(): Float = (barWidthPx - 2f * horizontalPadPx).coerceAtLeast(1f)

    fun xToMillis(offsetX: Float): Long {
        val fraction = ((offsetX - horizontalPadPx) / usableWidth()).coerceIn(0f, 1f)
        return (fraction * latestDuration).toLong()
    }

    fun millisToX(ms: Long): Float {
        val frac = (ms.toFloat() / latestDuration.toFloat()).coerceIn(0f, 1f)
        return horizontalPadPx + frac * usableWidth()
    }

    val activePillFraction: Float
    val activePillLabel: String
    when (dragTarget) {
        SeekDragTarget.TRIM_START -> {
            activePillFraction = trimStartFraction
            activePillLabel = "IN ${formatMillis(safeTrimStartMs)}"
        }
        SeekDragTarget.TRIM_END -> {
            activePillFraction = trimEndFraction
            activePillLabel = "OUT ${formatMillis(safeTrimEndMs)}"
        }
        else -> {
            activePillFraction = playedFraction
            activePillLabel = formatMillis(positionMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (trimEnabled) 56.dp else 48.dp)
            .onSizeChanged { size ->
                barWidthPx = size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(trimEnabled) {
                detectTapGestures { offset ->
                    val target = xToMillis(offset.x)
                    latestOnScrubStart(target)
                    latestOnScrubEnd(target)
                }
            }
            .pointerInput(trimEnabled) {
                var currentDragX = 0f
                var activeMode = SeekDragTarget.SCRUB
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        currentDragX = startOffset.x
                        activeMode = if (latestTrimEnabled) {
                            val startX = millisToX(latestTrimStartMs)
                            val endX = millisToX(latestTrimEndMs)
                            val distStart = abs(startOffset.x - startX)
                            val distEnd = abs(startOffset.x - endX)
                            when {
                                distStart <= handleHitRadiusPx && distStart <= distEnd -> SeekDragTarget.TRIM_START
                                distEnd <= handleHitRadiusPx -> SeekDragTarget.TRIM_END
                                else -> SeekDragTarget.SCRUB
                            }
                        } else {
                            SeekDragTarget.SCRUB
                        }
                        dragTarget = activeMode
                        val ms = xToMillis(currentDragX)
                        when (activeMode) {
                            SeekDragTarget.TRIM_START -> {
                                val clamped = ms.coerceIn(0L, (latestTrimEndMs - latestSafeMinGap).coerceAtLeast(0L))
                                latestOnTrimStartChange?.invoke(clamped)
                            }
                            SeekDragTarget.TRIM_END -> {
                                val clamped = ms.coerceIn(
                                    (latestTrimStartMs + latestSafeMinGap).coerceAtMost(latestDuration),
                                    latestDuration
                                )
                                latestOnTrimEndChange?.invoke(clamped)
                            }
                            else -> latestOnScrubStart(ms)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        currentDragX = (currentDragX + dragAmount).coerceIn(0f, barWidthPx)
                        val ms = xToMillis(currentDragX)
                        when (activeMode) {
                            SeekDragTarget.TRIM_START -> {
                                val clamped = ms.coerceIn(0L, (latestTrimEndMs - latestSafeMinGap).coerceAtLeast(0L))
                                latestOnTrimStartChange?.invoke(clamped)
                            }
                            SeekDragTarget.TRIM_END -> {
                                val clamped = ms.coerceIn(
                                    (latestTrimStartMs + latestSafeMinGap).coerceAtMost(latestDuration),
                                    latestDuration
                                )
                                latestOnTrimEndChange?.invoke(clamped)
                            }
                            else -> latestOnScrubMove(ms)
                        }
                    },
                    onDragEnd = {
                        val ms = xToMillis(currentDragX)
                        when (activeMode) {
                            SeekDragTarget.TRIM_START -> {
                                val clamped = ms.coerceIn(0L, (latestTrimEndMs - latestSafeMinGap).coerceAtLeast(0L))
                                latestOnTrimCommit?.invoke(clamped, latestTrimEndMs)
                            }
                            SeekDragTarget.TRIM_END -> {
                                val clamped = ms.coerceIn(
                                    (latestTrimStartMs + latestSafeMinGap).coerceAtMost(latestDuration),
                                    latestDuration
                                )
                                latestOnTrimCommit?.invoke(latestTrimStartMs, clamped)
                            }
                            else -> latestOnScrubEnd(ms)
                        }
                        dragTarget = SeekDragTarget.NONE
                    },
                    onDragCancel = {
                        val ms = xToMillis(currentDragX)
                        when (activeMode) {
                            SeekDragTarget.TRIM_START,
                            SeekDragTarget.TRIM_END -> latestOnTrimCommit?.invoke(latestTrimStartMs, latestTrimEndMs)
                            else -> latestOnScrubEnd(ms)
                        }
                        dragTarget = SeekDragTarget.NONE
                    }
                )
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = playedFraction,
                    range = 0f..1f
                )
                setProgress { targetFraction ->
                    val targetMs = (targetFraction.coerceIn(0f, 1f) * latestDuration).toLong()
                    latestOnScrubStart(targetMs)
                    latestOnScrubEnd(targetMs)
                    true
                }
            }
            .testTag(if (trimEnabled) "video_player_trim_seek_bar" else "video_player_seek_bar")
    ) {
        // Floating scrub/trim timestamp pill above active handle during drag
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { pillWidthPx = it.width }
                .offset {
                    val thumbX = (horizontalPadPx + activePillFraction * usableWidth()).roundToInt()
                    val maxOffset = (barWidthPx.toInt() - pillWidthPx).coerceAtLeast(0)
                    val clampedX = (thumbX - pillWidthPx / 2).coerceIn(0, maxOffset)
                    IntOffset(x = clampedX, y = 0)
                }
        ) {
            val pillBorderColor = if (dragTarget == SeekDragTarget.TRIM_START || dragTarget == SeekDragTarget.TRIM_END) {
                trimAccentColor
            } else {
                primaryColor.copy(alpha = 0.7f)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, pillBorderColor),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = activePillLabel,
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Custom track, visual trim bracket handles & playhead thumb
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val padX = horizontalPadPx.coerceAtMost(size.width * 0.1f)
            val usableW = (size.width - 2f * padX).coerceAtLeast(1f)
            val trackHeightPx = trackHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()
            val centerY = size.height / 2f
            val trackTop = centerY - trackHeightPx / 2f
            val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = if (trimEnabled) 0.18f else 0.25f),
                topLeft = Offset(padX, trackTop),
                size = Size(usableW, trackHeightPx),
                cornerRadius = cornerRadius
            )

            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.36f),
                    topLeft = Offset(padX, trackTop),
                    size = Size(usableW * bufferedFraction, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            if (trimEnabled) {
                val startX = padX + trimStartFraction * usableW
                val endX = (padX + trimEndFraction * usableW).coerceAtLeast(startX + 4f)
                val bandHeight = 20.dp.toPx()
                val bandTop = centerY - bandHeight / 2f

                if (startX > padX) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(padX, bandTop),
                        size = Size(startX - padX, bandHeight)
                    )
                }
                if (endX < padX + usableW) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(endX, bandTop),
                        size = Size((padX + usableW) - endX, bandHeight)
                    )
                }

                drawRect(
                    color = trimAccentColor.copy(alpha = 0.22f),
                    topLeft = Offset(startX, bandTop),
                    size = Size(endX - startX, bandHeight)
                )
                val railThickness = 2.5.dp.toPx()
                drawRect(
                    color = trimAccentColor,
                    topLeft = Offset(startX, bandTop),
                    size = Size(endX - startX, railThickness)
                )
                drawRect(
                    color = trimAccentColor,
                    topLeft = Offset(startX, bandTop + bandHeight - railThickness),
                    size = Size(endX - startX, railThickness)
                )
            }

            val playedWidth = usableW * playedFraction
            if (playedWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
                    topLeft = Offset(padX, trackTop),
                    size = Size(playedWidth, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            if (trimEnabled) {
                val handleW = 10.dp.toPx()
                val handleH = 26.dp.toPx()
                val handleTop = centerY - handleH / 2f
                val handleCorner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                val startX = padX + trimStartFraction * usableW
                val endX = (padX + trimEndFraction * usableW).coerceAtLeast(startX + 4f)

                if (dragTarget == SeekDragTarget.TRIM_START) {
                    drawCircle(
                        color = trimAccentColor.copy(alpha = 0.35f),
                        radius = 18.dp.toPx(),
                        center = Offset(startX, centerY)
                    )
                }
                drawRoundRect(
                    color = trimAccentColor,
                    topLeft = Offset(startX - handleW / 2f, handleTop),
                    size = Size(handleW, handleH),
                    cornerRadius = handleCorner
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.78f),
                    topLeft = Offset(startX - 1.2.dp.toPx(), centerY - 6.dp.toPx()),
                    size = Size(2.4.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )

                if (dragTarget == SeekDragTarget.TRIM_END) {
                    drawCircle(
                        color = trimAccentColor.copy(alpha = 0.35f),
                        radius = 18.dp.toPx(),
                        center = Offset(endX, centerY)
                    )
                }
                drawRoundRect(
                    color = trimAccentColor,
                    topLeft = Offset(endX - handleW / 2f, handleTop),
                    size = Size(handleW, handleH),
                    cornerRadius = handleCorner
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.78f),
                    topLeft = Offset(endX - 1.2.dp.toPx(), centerY - 6.dp.toPx()),
                    size = Size(2.4.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            val thumbX = (padX + playedWidth).coerceIn(
                padX,
                (padX + usableW).coerceAtLeast(padX)
            )
            if (dragTarget == SeekDragTarget.SCRUB) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.32f),
                    radius = thumbRadiusPx * 1.75f,
                    center = Offset(thumbX, centerY)
                )
            }
            drawCircle(
                color = Color.White,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = primaryColor,
                radius = thumbRadiusPx * 0.55f,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

internal fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
