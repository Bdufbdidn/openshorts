package com.example.ui.clips

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GeneratedClip
import com.example.ui.components.ScoreBadge
import com.example.ui.components.ShortClipPreviewPlayer
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.copyToClipboard
import com.example.ui.components.formatSeconds
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ViralAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneratedClipResultCard(
    clip: GeneratedClip,
    videoUriString: String?,
    onTrimAndChat: () -> Unit,
    onEditSubtitles: () -> Unit,
    onEditHook: () -> Unit,
    onTranslate: () -> Unit,
    onPost: () -> Unit,
    onBurnCaptions: () -> Unit,
    onAddToBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    StudioGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clip #${clip.clipIndex + 1} • ${formatSeconds(clip.startSec)}–${formatSeconds(clip.endSec)}",
                    fontFamily = JetBrainsMonoFont,
                    style = MaterialTheme.typography.labelSmall,
                    color = ViralAmber
                )
                Text(
                    text = clip.youtubeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            ScoreBadge(score = clip.viralScore.toInt())
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShortClipPreviewPlayer(
                clip = clip,
                videoUriString = videoUriString,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!clip.translatedLanguage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "Localized: ${clip.translatedLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!clip.tiktokCaption.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.tiktokCaption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        copyToClipboard(context, "Caption", clip.tiktokCaption)
                        Toast.makeText(context, "Caption copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy caption")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onTrimAndChat, modifier = Modifier.testTag("trim_chat_clip_button_${clip.clipIndex}")) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp), tint = ViralAmber)
                Spacer(Modifier.width(6.dp))
                Text("Trim & Chat")
            }
            OutlinedButton(onClick = onEditSubtitles, modifier = Modifier.testTag("edit_subtitles_button_${clip.clipIndex}")) {
                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Subtitles")
            }
            OutlinedButton(onClick = onEditHook, modifier = Modifier.testTag("edit_hook_button_${clip.clipIndex}")) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hook")
            }
            OutlinedButton(onClick = onTranslate, modifier = Modifier.testTag("translate_clip_button_${clip.clipIndex}")) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dub / Lang")
            }
            OutlinedButton(onClick = onBurnCaptions, modifier = Modifier.testTag("burn_captions_button_${clip.clipIndex}")) {
                Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Transcribe & Burn MP4")
            }
            if (!videoUriString.isNullOrBlank()) {
                OutlinedButton(onClick = onAddToBatch, modifier = Modifier.testTag("add_clip_to_batch_button_${clip.clipIndex}")) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Batch")
                }
            }
            Button(onClick = onPost, modifier = Modifier.testTag("post_clip_button_${clip.clipIndex}")) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Auto-Post YT & IG")
            }
        }
    }
}
