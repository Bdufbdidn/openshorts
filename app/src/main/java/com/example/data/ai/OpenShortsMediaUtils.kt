package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

private const val MEDIA_TAG = "OpenShortsMediaUtils"

internal data class SampledVideoData(
    val durationSec: Double,
    val frames: List<Bitmap>
)

internal object OpenShortsMediaUtils {

    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { scaleBitmapMaxWidth(it, 1024) }
            }
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Failed to decode bitmap from URI: $uri", e)
            null
        }
    }

    fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap, filename: String): String {
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    suspend fun extractSampledFrames(context: Context, uri: Uri): SampledVideoData =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 60_000L
                val fractions = listOf(0.10, 0.35, 0.60, 0.85)
                val frames = fractions.mapNotNull { fraction ->
                    val timeUs = (durationMs * fraction * 1000.0).toLong()
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { scaleBitmapMaxWidth(it, 512) }
                }
                SampledVideoData(
                    durationSec = (durationMs / 1000.0).coerceAtLeast(10.0),
                    frames = frames
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(MEDIA_TAG, "Could not extract frames via MediaMetadataRetriever: ${e.message}")
                SampledVideoData(durationSec = 90.0, frames = emptyList())
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

    fun scaleBitmapMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width.toFloat()
        val targetHeight = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, targetHeight, true)
    }

    fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx)?.takeIf { it.isNotBlank() }
        }
    }
}
