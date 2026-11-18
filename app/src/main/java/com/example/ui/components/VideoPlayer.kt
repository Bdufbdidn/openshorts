package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Reusable Media3 ExoPlayer composable supporting play/pause, +/-10s skip, and a custom interactive seek bar.
 */
@Composable
fun VideoPlayer(
    uri: Uri?,
    modifier: Modifier = Modifier,
    autoplay: Boolean = false,
    repeat: Boolean = false,
    aspectRatio: Float = 16f / 9f,
    startPositionMs: Long = 0L,
    endPositionMs: Long? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (uri == null) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .testTag("video_player_empty"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideocamOff,
                    contentDescription = stringResource(R.string.cd_video_empty),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.video_player_no_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var isPlaying by remember(uri) { mutableStateOf(autoplay) }
    var isBuffering by remember(uri) { mutableStateOf(true) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var currentPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var isScrubbing by remember(uri) { mutableStateOf(false) }
    var scrubPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var showControls by remember(uri) { mutableStateOf(!autoplay) }
    var playerError by remember(uri) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(uri, startPositionMs, endPositionMs, repeat) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (startPositionMs > 0L || endPositionMs != null) {
                val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startPositionMs.coerceAtLeast(0L))
                if (endPositionMs != null && endPositionMs > startPositionMs) {
                    clipBuilder.setEndPositionMs(endPositionMs)
                }
                mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
            }
            setMediaItem(mediaItemBuilder.build())
            repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = autoplay
            prepare()
        }
    }

    var previousAutoplay by remember(exoPlayer) { mutableStateOf(autoplay) }
    LaunchedEffect(exoPlayer, autoplay) {
        if (autoplay != previousAutoplay) {
            previousAutoplay = autoplay
            exoPlayer.playWhenReady = autoplay
            if (autoplay && exoPlayer.playbackState != Player.STATE_ENDED) {
                exoPlayer.play()
            } else if (!autoplay) {
                exoPlayer.pause()
            }
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        isPlaying = exoPlayer.isPlaying

        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                val total = exoPlayer.duration
                if (total > 0L) {
                    durationMs = total
                }
                if (playbackState == Player.STATE_ENDED && !repeat) {
                    isPlaying = false
                    showControls = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Unable to play selected video."
                isBuffering = false
                isPlaying = false
                showControls = true
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(showControls, isPlaying, isScrubbing) {
        if (showControls && isPlaying && !isScrubbing) {
            delay(3000L)
            showControls = false
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                val total = exoPlayer.duration
                if (total > 0L) durationMs = total
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
            delay(if (isPlaying) 100L else 400L)
        }
    }

    val safeDurationMs = durationMs.coerceAtLeast(1L)
    val displayedPositionMs = if (isScrubbing) {
        scrubPositionMs.coerceIn(0L, safeDurationMs)
    } else {
        currentPositionMs.coerceIn(0L, safeDurationMs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
            .testTag("video_player_container")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        overlayContent?.invoke(this)

        playerError?.let { errorText ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Subtle persistent hairline progress bar when full controls are hidden
        if (!showControls && playerError == null && durationMs > 0L) {
            val hairlineColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("video_player_hairline_progress")
            ) {
                val fraction = (displayedPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
                drawRect(color = Color.White.copy(alpha = 0.22f))
                drawRect(
                    color = hairlineColor,
                    size = Size(size.width * fraction, size.height)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls && playerError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            ) {
                // Center transport controls: Replay 10s, Play/Pause, Forward 10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_seek_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = stringResource(R.string.cd_seek_backward),
                            tint = Color.White
                        )
                    }

                    if (!isBuffering) {
                        IconButton(
                            onClick = {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                } else if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), CircleShape)
                                .testTag("video_player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.cd_pause_video else R.string.cd_play_video
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(safeDurationMs)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("video_player_seek_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = stringResource(R.string.cd_seek_forward),
                            tint = Color.White
                        )
                    }
                }

                // Bottom custom seek bar & duration timestamps
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    VideoSeekBar(
                        positionMs = displayedPositionMs,
                        bufferedPositionMs = bufferedPositionMs,
                        durationMs = safeDurationMs,
                        onScrubStart = { targetMs ->
                            isScrubbing = true
                            scrubPositionMs = targetMs
                        },
                        onScrubMove = { targetMs ->
                            scrubPositionMs = targetMs
                        },
                        onScrubEnd = { targetMs ->
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
                            isScrubbing = false
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMillis(displayedPositionMs),
                            fontFamily = JetBrainsMonoFont,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.testTag("video_player_current_time")
                        )
                        Text(
                            text = formatMillis(durationMs),
                            fontFamily = JetBrainsMonoFont,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.testTag("video_player_total_time")
                        )
                    }
                }
            }
        }

        // Centered buffering indicator rendered above controls so it is never obscured
        if (isBuffering && playerError == null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .testTag("video_player_buffering_indicator")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas-drawn seek bar supporting tap-to-seek, drag scrubbing, buffered progress,
 * animated thumb expansion, and a floating timestamp preview pill while scrubbing.
 */
@Composable
fun VideoSeekBar(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onScrubStart: (Long) -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val playedFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var pillWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    val latestDuration by rememberUpdatedState(safeDuration)
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)
    val latestOnScrubMove by rememberUpdatedState(onScrubMove)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "trackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 6.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbRadius"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    fun xToMillis(offsetX: Float): Long {
        val fraction = (offsetX / barWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return (fraction * latestDuration).toLong()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { size ->
                barWidthPx = size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val target = xToMillis(offset.x)
                    latestOnScrubStart(target)
                    latestOnScrubEnd(target)
                }
            }
            .pointerInput(Unit) {
                var currentDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        currentDragX = startOffset.x
                        latestOnScrubStart(xToMillis(currentDragX))
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        currentDragX = (currentDragX + dragAmount).coerceIn(0f, barWidthPx)
                        latestOnScrubMove(xToMillis(currentDragX))
                    },
                    onDragEnd = {
                        isDragging = false
                        latestOnScrubEnd(xToMillis(currentDragX))
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnScrubEnd(xToMillis(currentDragX))
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
            .testTag("video_player_seek_bar")
    ) {
        // Floating scrub timestamp pill above thumb during drag
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { pillWidthPx = it.width }
                .offset {
                    val thumbX = (playedFraction * barWidthPx).roundToInt()
                    val maxOffset = (barWidthPx.toInt() - pillWidthPx).coerceAtLeast(0)
                    val clampedX = (thumbX - pillWidthPx / 2).coerceIn(0, maxOffset)
                    IntOffset(x = clampedX, y = 0)
                }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = formatMillis(positionMs),
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Custom track & thumb
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val trackHeightPx = trackHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()
            val centerY = size.height / 2f
            val trackTop = centerY - trackHeightPx / 2f
            val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            // 1. Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeightPx),
                cornerRadius = cornerRadius
            )

            // 2. Buffered progress track
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.42f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width * bufferedFraction, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            // 3. Played gradient progress track
            val playedWidth = size.width * playedFraction
            if (playedWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
                    topLeft = Offset(0f, trackTop),
                    size = Size(playedWidth, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            // 4. Glow halo + crisp thumb
            val thumbX = playedWidth.coerceIn(thumbRadiusPx, (size.width - thumbRadiusPx).coerceAtLeast(thumbRadiusPx))
            if (isDragging) {
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

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
