package com.example.ui.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.YouTubeSearchResult
import com.example.ui.components.ErrorBanner
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeSearchSheet(
    searchState: UiState<List<YouTubeSearchResult>>,
    initialQuery: String = "",
    initialLiveOnly: Boolean = false,
    onSearch: (query: String, liveOnly: Boolean) -> Unit,
    onSelectVideo: (YouTubeSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(initialQuery) }
    var liveOnly by remember { mutableStateOf(initialLiveOnly) }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (liveOnly) Icons.Default.LiveTv else Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search_youtube),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (liveOnly) "Search YouTube Live Streams" else "Search YouTube Videos",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !liveOnly,
                    onClick = {
                        liveOnly = false
                        if (query.isNotBlank()) onSearch(query, false)
                    },
                    label = { Text("All Videos") },
                    modifier = Modifier.testTag("yt_search_filter_all")
                )
                FilterChip(
                    selected = liveOnly,
                    onClick = {
                        liveOnly = true
                        if (query.isNotBlank()) onSearch(query, true)
                    },
                    leadingIcon = { Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Live Streams Only") },
                    modifier = Modifier.testTag("yt_search_filter_live")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (liveOnly) "Search live broadcasts..." else "Search YouTube topic, creator, or title...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) onSearch(query, liveOnly)
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("yt_search_query_input")
                )
                Button(
                    onClick = { if (query.isNotBlank()) onSearch(query, liveOnly) },
                    enabled = query.isNotBlank() && searchState !is UiState.Loading,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("yt_search_execute_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_youtube))
                }
            }

            when (searchState) {
                is UiState.Idle -> {
                    Text(
                        text = "Enter keywords above to search real YouTube videos or live broadcasts, then select one to trim with visual handles or generate Shorts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Searching YouTube...")
                    }
                }
                is UiState.Error -> {
                    ErrorBanner(message = searchState.message)
                }
                is UiState.Success -> {
                    if (searchState.data.isEmpty()) {
                        Text(
                            text = "No matching videos returned. Try a broader query.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        searchState.data.forEach { video ->
                            YouTubeSearchResultCard(video = video, onClick = { onSelectVideo(video) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun YouTubeSearchResultCard(
    video: YouTubeSearchResult,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ObsidianBorder),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("yt_search_result_${video.videoId}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (video.liveBroadcast) Color(0xFFDC2626) else Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.durationText,
                        fontFamily = JetBrainsMonoFont,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        video.channelName.takeIf { it.isNotBlank() },
                        video.viewCountText.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to load & trim",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
