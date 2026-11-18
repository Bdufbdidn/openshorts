package com.example.data.network

import android.util.Log
import com.example.data.model.YouTubeSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeSearchSvc"

class YouTubeSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Searches public YouTube results using real HTML ytInitialData extraction,
     * with a Piped API JSON fallback if YouTube serves a consent wall.
     */
    suspend fun searchVideos(
        query: String,
        liveOnly: Boolean = false,
        maxResults: Int = 12
    ): Result<List<YouTubeSearchResult>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a search query."))
        }
        try {
            val directResults = fetchFromYouTubeHtml(cleanQuery, liveOnly, maxResults)
            if (directResults.isNotEmpty()) {
                return@withContext Result.success(directResults)
            }
            val pipedResults = fetchFromPipedFallback(cleanQuery, liveOnly, maxResults)
            if (pipedResults.isNotEmpty()) {
                return@withContext Result.success(pipedResults)
            }
            Result.failure(
                IllegalStateException(
                    if (liveOnly) "No live YouTube broadcasts found for \"$cleanQuery\"."
                    else "No YouTube videos found for \"$cleanQuery\"."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "YouTube search failed for '$cleanQuery'", e)
            Result.failure(e)
        }
    }

    private fun fetchFromYouTubeHtml(
        query: String,
        liveOnly: Boolean,
        maxResults: Int
    ): List<YouTubeSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // sp=EgJAAQ%3D%3D filters YouTube search to live streams
        val filterParam = if (liveOnly) "&sp=EgJAAQ%253D%253D" else ""
        val url = "https://www.youtube.com/results?search_query=$encoded$filterParam&hl=en&gl=US"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "SOCS=CAI; CONSENT=YES+cb.20210328-17-p0.en+FX+667")
            .get()
            .build()

        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "YouTube HTML search HTTP ${resp.code}")
                return emptyList()
            }
            resp.body?.string().orEmpty()
        }
        if (html.isBlank()) return emptyList()

        val jsonText = extractYtInitialDataJson(html) ?: return emptyList()
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing ytInitialData JSON", e)
            return emptyList()
        }

        val collected = LinkedHashMap<String, YouTubeSearchResult>()
        collectVideoRenderers(root, collected, liveOnly, maxResults)
        return collected.values.toList()
    }

    private fun fetchFromPipedFallback(
        query: String,
        liveOnly: Boolean,
        maxResults: Int
    ): List<YouTubeSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val filter = if (liveOnly) "live" else "videos"
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de"
        )
        for (base in instances) {
            try {
                val request = Request.Builder()
                    .url("$base/search?q=$encoded&filter=$filter")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                val body = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    resp.body?.string().orEmpty()
                }
                if (body.isBlank()) continue
                val obj = JSONObject(body)
                val items = obj.optJSONArray("items") ?: continue
                val results = mutableListOf<YouTubeSearchResult>()
                for (i in 0 until items.length()) {
                    if (results.size >= maxResults) break
                    val item = items.optJSONObject(i) ?: continue
                    val rawUrl = item.optString("url")
                    val videoId = extractVideoIdFromPath(rawUrl)
                    if (videoId.isBlank()) continue
                    val durationSecLong = item.optLong("duration", -1L)
                    val isLive = durationSecLong <= 0L || item.optBoolean("isLive", false)
                    if (liveOnly && !isLive) continue
                    val durationSec = if (durationSecLong > 0L) durationSecLong.toDouble() else 600.0
                    val durationText = if (isLive) "LIVE" else formatSecLabel(durationSecLong)
                    results += YouTubeSearchResult(
                        videoId = videoId,
                        title = item.optString("title", "YouTube Video"),
                        channelName = item.optString("uploaderName", "Creator"),
                        durationText = durationText,
                        durationSec = durationSec,
                        viewCountText = formatViews(item.optLong("views", -1L)),
                        thumbnailUrl = item.optString("thumbnail")
                            .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
                        url = "https://www.youtube.com/watch?v=$videoId",
                        liveBroadcast = isLive
                    )
                }
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                Log.w(TAG, "Piped fallback $base failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf(
            "var ytInitialData = ",
            "window[\"ytInitialData\"] = ",
            "ytInitialData = "
        )
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx < 0) continue
            val start = html.indexOf('{', idx + marker.length)
            if (start < 0) continue
            val json = extractBalancedJsonObject(html, start)
            if (json != null) return json
        }
        return null
    }

    private fun extractBalancedJsonObject(text: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        val limit = (startIndex + 2_500_000).coerceAtMost(text.length)
        for (i in startIndex until limit) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun collectVideoRenderers(
        node: Any?,
        out: LinkedHashMap<String, YouTubeSearchResult>,
        liveOnly: Boolean,
        maxResults: Int
    ) {
        if (out.size >= maxResults || node == null) return
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("videoRenderer")
                    ?: node.optJSONObject("videoWithContextRenderer")
                if (renderer != null) {
                    parseRenderer(renderer)?.let { item ->
                        if (!liveOnly || item.liveBroadcast) {
                            out.putIfAbsent(item.videoId, item)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext() && out.size < maxResults) {
                    val key = keys.next()
                    collectVideoRenderers(node.opt(key), out, liveOnly, maxResults)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= maxResults) break
                    collectVideoRenderers(node.opt(i), out, liveOnly, maxResults)
                }
            }
        }
    }

    private fun parseRenderer(obj: JSONObject): YouTubeSearchResult? {
        val videoId = obj.optString("videoId").trim()
        if (videoId.isBlank()) return null

        val title = readTextField(obj.optJSONObject("title"))
            .ifBlank { readTextField(obj.optJSONObject("headline")) }
        if (title.isBlank()) return null

        val channelName = readTextField(obj.optJSONObject("ownerText"))
            .ifBlank { readTextField(obj.optJSONObject("longBylineText")) }
            .ifBlank { readTextField(obj.optJSONObject("shortBylineText")) }
            .ifBlank { "YouTube Creator" }

        val lengthText = readTextField(obj.optJSONObject("lengthText"))
        val badgeLive = hasLiveBadge(obj.optJSONArray("badges")) ||
            hasLiveOverlay(obj.optJSONArray("thumbnailOverlays")) ||
            lengthText.equals("LIVE", ignoreCase = true)

        val durationSec = if (badgeLive) {
            900.0
        } else {
            parseDurationTextToSeconds(lengthText).takeIf { it > 0.0 } ?: 180.0
        }
        val durationDisplay = if (badgeLive) "LIVE" else lengthText.ifBlank { "03:00" }
        val viewCountText = readTextField(obj.optJSONObject("shortViewCountText"))
            .ifBlank { readTextField(obj.optJSONObject("viewCountText")) }

        val thumbUrl = obj.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return YouTubeSearchResult(
            videoId = videoId,
            title = title,
            channelName = channelName,
            durationText = durationDisplay,
            durationSec = durationSec,
            viewCountText = viewCountText,
            thumbnailUrl = thumbUrl,
            url = "https://www.youtube.com/watch?v=$videoId",
            liveBroadcast = badgeLive
        )
    }

    private fun readTextField(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val part = runs.optJSONObject(i)?.optString("text").orEmpty()
            sb.append(part)
        }
        return sb.toString().trim()
    }

    private fun hasLiveBadge(badges: JSONArray?): Boolean {
        if (badges == null) return false
        for (i in 0 until badges.length()) {
            val meta = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer") ?: continue
            val style = meta.optString("style")
            val label = meta.optString("label")
            if (style.contains("LIVE", ignoreCase = true) || label.equals("LIVE", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun hasLiveOverlay(overlays: JSONArray?): Boolean {
        if (overlays == null) return false
        for (i in 0 until overlays.length()) {
            val status = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
            if (status.optString("style").equals("LIVE", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun extractVideoIdFromPath(path: String): String {
        val vIdx = path.indexOf("v=")
        if (vIdx >= 0) {
            return path.substring(vIdx + 2).substringBefore("&").trim()
        }
        return path.substringAfterLast("/").trim()
    }

    private fun parseDurationTextToSeconds(text: String): Double {
        val parts = text.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]).toDouble()
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]).toDouble()
            else -> 0.0
        }
    }

    private fun formatSecLabel(seconds: Long): String {
        if (seconds <= 0L) return "LIVE"
        val mins = seconds / 60L
        val secs = seconds % 60L
        return "%d:%02d".format(mins, secs)
    }

    private fun formatViews(views: Long): String {
        return when {
            views < 0L -> ""
            views >= 1_000_000L -> "%.1fM views".format(views / 1_000_000.0)
            views >= 1_000L -> "%.1fK views".format(views / 1_000.0)
            else -> "$views views"
        }
    }
}
