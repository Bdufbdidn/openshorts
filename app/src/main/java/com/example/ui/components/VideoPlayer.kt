package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.ui.theme.ObsidianBorder
import kotlinx.coroutines.delay

/**
 * Reusable Media3 ExoPlayer composable supporting play/pause, +/-10s skip,
 * speed selector, buffering spinner, LIVE stream indicator, and interactive
 * visual start/end trim handles on the seek bar.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri?,
    modifier: Modifier = Modifier,
    autoplay: Boolean = false,
    repeat: Boolean = false,
    aspectRatio: Float = 16f / 9f,
    startPositionMs: Long = 0L,
    endPositionMs: Long? = null,
    trimEnabled: Boolean = false,
    trimStartMs: Long = 0L,
    trimEndMs: Long? = null,
    onTrimRangeChange: ((Long, Long) -> Unit)? = null,
    isLiveStream: Boolean = false,
    videoEffects: List<Effect> = emptyList(),
    videoEffectsKey: String = "",
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (uri == null) {
        VideoPlayerEmptyState(modifier = modifier, aspectRatio = aspectRatio)
        return
    }

    var isPlaying by remember(uri) { mutableStateOf(autoplay) }
    var isBuffering by remember(uri) { mutableStateOf(true) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var currentPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var isScrubbing by remember(uri) { mutableStateOf(false) }
    var scrubPositionMs by remember(uri) { mutableLongStateOf(0L) }
    var showControls by remember(uri, trimEnabled) { mutableStateOf(trimEnabled || !autoplay) }
    var playbackSpeed by remember(uri) { mutableFloatStateOf(1.0f) }
    var speedMenuExpanded by remember(uri) { mutableStateOf(false) }
    var playerError by remember(uri) { mutableStateOf<String?>(null) }

    var activeTrimStartMs by remember(uri, trimStartMs) { mutableLongStateOf(trimStartMs.coerceAtLeast(0L)) }
    var activeTrimEndMs by remember(uri, trimEndMs) { mutableLongStateOf(trimEndMs ?: Long.MAX_VALUE) }

    val exoPlayer = remember(
        uri,
        trimEnabled,
        if (trimEnabled) 0L else startPositionMs,
        if (trimEnabled) null else endPositionMs,
        repeat,
        videoEffectsKey
    ) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (!trimEnabled && (startPositionMs > 0L || endPositionMs != null)) {
                val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startPositionMs.coerceAtLeast(0L))
                if (endPositionMs != null && endPositionMs > startPositionMs) {
                    clipBuilder.setEndPositionMs(endPositionMs)
                }
                mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
            }
            setMediaItem(mediaItemBuilder.build())
            if (videoEffects.isNotEmpty()) {
                setVideoEffects(videoEffects)
            }
            repeatMode = if (repeat && !trimEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            val resumePos = currentPositionMs.coerceAtLeast(0L)
            if (resumePos > 0L) {
                seekTo(resumePos)
            }
            playWhenReady = isPlaying
            setPlaybackSpeed(playbackSpeed)
            prepare()
        }
    }

    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
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
                    if (activeTrimEndMs == Long.MAX_VALUE || activeTrimEndMs > total) {
                        activeTrimEndMs = total
                    }
                    if (trimEnabled && exoPlayer.currentPosition < activeTrimStartMs) {
                        exoPlayer.seekTo(activeTrimStartMs)
                    }
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

    LaunchedEffect(showControls, isPlaying, isScrubbing, speedMenuExpanded, trimEnabled) {
        if (trimEnabled) {
            showControls = true
        } else if (!showControls) {
            speedMenuExpanded = false
        } else if (isPlaying && !isScrubbing && !speedMenuExpanded) {
            delay(3000L)
            showControls = false
        }
    }

    val safeDurationMs = durationMs.coerceAtLeast(1L)
    val effectiveTrimStartMs = if (durationMs > 0L) {
        activeTrimStartMs.coerceIn(0L, (durationMs - MIN_TRIM_GAP_MS).coerceAtLeast(0L))
    } else {
        activeTrimStartMs.coerceAtLeast(0L)
    }
    val effectiveTrimEndMs = if (durationMs > 0L) {
        activeTrimEndMs.coerceIn((effectiveTrimStartMs + MIN_TRIM_GAP_MS).coerceAtMost(durationMs), durationMs)
    } else {
        activeTrimEndMs
    }

    LaunchedEffect(exoPlayer, isPlaying, isScrubbing, trimEnabled, effectiveTrimStartMs, effectiveTrimEndMs, repeat) {
        while (true) {
            if (!isScrubbing) {
                val total = exoPlayer.duration
                if (total > 0L) durationMs = total
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)

                if (trimEnabled && isPlaying && total > 0L && pos >= effectiveTrimEndMs) {
                    if (repeat) {
                        exoPlayer.seekTo(effectiveTrimStartMs)
                    } else {
                        exoPlayer.pause()
                        exoPlayer.seekTo(effectiveTrimStartMs)
                    }
                }
            }
            delay(if (isPlaying) 100L else 400L)
        }
    }

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
            ) {
                showControls = if (!trimEnabled) !showControls else true
            }
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

        if (isLiveStream && playerError == null) {
            VideoLiveBadge(modifier = Modifier.align(Alignment.TopStart))
        }

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

        if (!showControls && playerError == null && durationMs > 0L) {
            VideoHairlineProgress(
                displayedPositionMs = displayedPositionMs,
                safeDurationMs = safeDurationMs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        AnimatedVisibility(
            visible = showControls && playerError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            VideoPlayerControlsOverlay(
                playbackSpeed = playbackSpeed,
                speedOptions = speedOptions,
                speedMenuExpanded = speedMenuExpanded,
                onOpenSpeedMenu = { speedMenuExpanded = true },
                onDismissSpeedMenu = { speedMenuExpanded = false },
                onSelectSpeed = { speed ->
                    playbackSpeed = speed
                    exoPlayer.setPlaybackSpeed(speed)
                    speedMenuExpanded = false
                },
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                trimEnabled = trimEnabled,
                durationMs = durationMs,
                safeDurationMs = safeDurationMs,
                displayedPositionMs = displayedPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                effectiveTrimStartMs = effectiveTrimStartMs,
                effectiveTrimEndMs = effectiveTrimEndMs,
                onSeekBack = {
                    val minPos = if (trimEnabled) effectiveTrimStartMs else 0L
                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(minPos)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                },
                onTogglePlayPause = {
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(if (trimEnabled) effectiveTrimStartMs else 0L)
                        exoPlayer.play()
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (trimEnabled &&
                            (exoPlayer.currentPosition < effectiveTrimStartMs ||
                                exoPlayer.currentPosition >= effectiveTrimEndMs)
                        ) {
                            exoPlayer.seekTo(effectiveTrimStartMs)
                        }
                        exoPlayer.play()
                    }
                },
                onSeekForward = {
                    val maxPos = if (trimEnabled && durationMs > 0L) effectiveTrimEndMs else safeDurationMs
                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxPos)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                },
                onScrubStart = { targetMs ->
                    isScrubbing = true
                    scrubPositionMs = targetMs
                },
                onScrubMove = { targetMs -> scrubPositionMs = targetMs },
                onScrubEnd = { targetMs ->
                    exoPlayer.seekTo(targetMs)
                    currentPositionMs = targetMs
                    isScrubbing = false
                },
                onTrimStartChange = { newStart ->
                    activeTrimStartMs = newStart
                    isScrubbing = true
                    scrubPositionMs = newStart
                    exoPlayer.seekTo(newStart)
                    currentPositionMs = newStart
                    onTrimRangeChange?.invoke(newStart, effectiveTrimEndMs)
                },
                onTrimEndChange = { newEnd ->
                    activeTrimEndMs = newEnd
                    isScrubbing = true
                    scrubPositionMs = newEnd
                    exoPlayer.seekTo(newEnd)
                    currentPositionMs = newEnd
                    onTrimRangeChange?.invoke(effectiveTrimStartMs, newEnd)
                },
                onTrimRangeCommit = { committedStart, committedEnd ->
                    activeTrimStartMs = committedStart
                    activeTrimEndMs = committedEnd
                    isScrubbing = false
                    onTrimRangeChange?.invoke(committedStart, committedEnd)
                }
            )
        }

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

@Composable
private fun VideoPlayerEmptyState(
    modifier: Modifier,
    aspectRatio: Float
) {
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
}
