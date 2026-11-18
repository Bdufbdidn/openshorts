package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.GeneratedClip
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.NotoSerifFont
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PlusJakartaSansFont
import com.example.ui.theme.SpaceGroteskFont
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralEmerald
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun StudioGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianBorder,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun ScannerProcessingCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanBeam"
    )

    StudioGlassCard(
        modifier = modifier,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        containerColor = ObsidianSurfaceElevated
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                        ),
                        start = Offset(0f, progress * 320f),
                        end = Offset(600f, progress * 320f + 120f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val badgeColor = when {
        score >= 90 -> ViralEmerald
        score >= 80 -> MaterialTheme.colorScheme.primary
        else -> ViralAmber
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = "Viral $score",
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ShortClipPreviewPlayer(
    clip: GeneratedClip,
    videoUriString: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasLocalVideo = remember(videoUriString) {
        !videoUriString.isNullOrBlank() &&
            (videoUriString.startsWith("content://") || videoUriString.startsWith("file://"))
    }
    var isPlaying by remember { mutableStateOf(true) }
    var activeWordIndex by remember(clip.id, clip.subtitleText) { mutableIntStateOf(0) }

    val words = remember(clip.subtitleText) {
        clip.subtitleText.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    LaunchedEffect(isPlaying, words) {
        if (isPlaying && words.isNotEmpty()) {
            while (true) {
                delay(360L)
                activeWordIndex = (activeWordIndex + 1) % words.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1426), Color(0xFF09090B), Color(0xFF141129))
                )
            )
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
    ) {
        if (hasLocalVideo) {
            val exoPlayer = remember(videoUriString, clip.startSec, clip.endSec) {
                ExoPlayer.Builder(context).build().apply {
                    val item = MediaItem.Builder()
                        .setUri(Uri.parse(videoUriString))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((clip.startSec * 1000).toLong())
                                .setEndPositionMs((clip.endSec * 1000).toLong())
                                .build()
                        )
                        .build()
                    setMediaItem(item)
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 1f
                    prepare()
                    playWhenReady = isPlaying
                }
            }

            LaunchedEffect(isPlaying) {
                exoPlayer.playWhenReady = isPlaying
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.release()
                }
            }

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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // URL / Storyboard 9:16 Studio Preview stage
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${formatSeconds(clip.startSec)} — ${formatSeconds(clip.endSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = clip.youtubeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (!clip.effectsSummary.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = clip.effectsSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Viral Hook Overlay
        if (!clip.viralHookText.isNullOrBlank()) {
            val hookAlignment = when (clip.hookPosition) {
                "center" -> Alignment.Center
                "bottom" -> Alignment.BottomCenter
                else -> Alignment.TopCenter
            }
            val hookFontSize = when (clip.hookSize) {
                "S" -> 14.sp
                "L" -> 21.sp
                else -> 17.sp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                contentAlignment = hookAlignment
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = clip.viralHookText,
                            fontFamily = NotoSerifFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = hookFontSize,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Live Subtitle Overlay
        if (words.isNotEmpty()) {
            val subAlignment = when (clip.subtitlePosition) {
                "top" -> Alignment.TopCenter
                "middle" -> Alignment.Center
                else -> Alignment.BottomCenter
            }
            val primaryColor = remember(clip.subtitleColorHex) {
                parseSafeColor(clip.subtitleColorHex, Color.White)
            }
            val highlightColor = remember(clip.subtitleHighlightHex) {
                parseSafeColor(clip.subtitleHighlightHex, Color(0xFF3B82F6))
            }
            val subFont = remember(clip.subtitleFont) {
                when (clip.subtitleFont) {
                    "NotoSerif" -> NotoSerifFont
                    "JetBrainsMono" -> JetBrainsMonoFont
                    "PlusJakartaSans" -> PlusJakartaSansFont
                    else -> SpaceGroteskFont
                }
            }

            val windowStart = (activeWordIndex / 4) * 4
            val visibleWords = words.subList(windowStart, (windowStart + 4).coerceAtMost(words.size))
            val localHighlightIdx = activeWordIndex - windowStart

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 56.dp),
                contentAlignment = subAlignment
            ) {
                val annotated = buildAnnotatedString {
                    visibleWords.forEachIndexed { idx, w ->
                        val isHighlighted = clip.subtitleAnimation != "none" && idx == localHighlightIdx
                        withStyle(
                            SpanStyle(
                                color = if (isHighlighted) highlightColor else primaryColor,
                                fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Bold,
                                background = if (clip.subtitleAnimation == "karaoke" && isHighlighted) {
                                    highlightColor.copy(alpha = 0.22f)
                                } else Color.Transparent
                            )
                        ) {
                            append(w)
                        }
                        if (idx < visibleWords.lastIndex) append(" ")
                    }
                }
                Text(
                    text = annotated,
                    fontFamily = subFont,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom-right play/pause button
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play or pause preview",
                tint = Color.White
            )
        }
    }
}

fun parseSafeColor(hex: String, fallback: Color): Color {
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

fun formatSeconds(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return "%02d:%02d".format(mins, secs)
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareTextContent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

fun shareImageFile(context: Context, filePath: String, caption: String = "") {
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Image"))
}

fun shareVideoUri(context: Context, videoUriString: String, title: String, caption: String) {
    val uri = try {
        val parsed = Uri.parse(videoUriString)
        if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(parsed.path!!))
        } else {
            parsed
        }
    } catch (_: Exception) {
        null
    }
    if (uri != null && (uri.scheme == "content")) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Short Video"))
    } else {
        shareTextContent(context, title, caption)
    }
}
