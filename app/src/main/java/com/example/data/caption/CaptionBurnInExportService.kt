package com.example.data.caption

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.CaptionExportResult
import com.example.data.model.TimedCaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

@UnstableApi
class CaptionBurnInExportService(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Burns timed Gemini overlay captions (and optional viral hook banner) directly into the
     * video frames using Jetpack Media3 Transformer + OverlayEffect, exporting an H.264/MP4 file.
     */
    suspend fun exportVideoWithBurnedCaptions(
        sourceVideoUri: Uri,
        cues: List<TimedCaptionCue>,
        style: CaptionBurnStyle,
        clipStartMs: Long = 0L,
        clipEndMs: Long? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<CaptionExportResult> = withContext(Dispatchers.Main.immediate) {
        val exportsDir = File(appContext.filesDir, "generated/exports").apply { mkdirs() }
        val outputFile = File(exportsDir, "openshorts_captioned_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        try {
            val mediaItemBuilder = MediaItem.Builder().setUri(sourceVideoUri)
            if (clipStartMs > 0L || (clipEndMs != null && clipEndMs > clipStartMs)) {
                val clipCfg = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
                if (clipEndMs != null && clipEndMs > clipStartMs) {
                    clipCfg.setEndPositionMs(clipEndMs)
                }
                mediaItemBuilder.setClippingConfiguration(clipCfg.build())
            }

            val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
            val videoEffects = mutableListOf<Effect>()
            videoEffects += Media3VideoFilterFactory.buildVideoEffects(style)
            if (cues.isNotEmpty() || style.hookText.isNotBlank()) {
                val overlay = CaptionBitmapOverlay(
                    context = appContext,
                    cues = cues,
                    style = style
                )
                videoEffects += OverlayEffect(listOf(overlay))
            }
            if (videoEffects.isNotEmpty()) {
                editedMediaItemBuilder.setEffects(Effects(emptyList(), videoEffects))
            }
            val editedMediaItem = editedMediaItemBuilder.build()

            val exportDurationMs = suspendCancellableCoroutine<Long> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) {
                            onProgress(1f)
                            cont.resume(exportResult.durationMs)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(exportException)
                        }
                    }
                }

                val transformer = Transformer.Builder(appContext)
                    .setLooper(Looper.getMainLooper())
                    .addListener(listener)
                    .build()

                val progressHolder = ProgressHolder()
                val mainHandler = Handler(Looper.getMainLooper())
                val progressJob = launch {
                    while (isActive && cont.isActive) {
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                        }
                        delay(200L)
                    }
                }

                cont.invokeOnCancellation {
                    progressJob.cancel()
                    mainHandler.post {
                        try {
                            transformer.cancel()
                        } catch (_: Exception) {
                        }
                    }
                }

                transformer.start(editedMediaItem, outputFile.absolutePath)
            }

            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                outputFile
            )

            Result.success(
                CaptionExportResult(
                    outputFilePath = outputFile.absolutePath,
                    contentUri = contentUri.toString(),
                    fileSizeBytes = outputFile.length(),
                    durationMs = exportDurationMs.coerceAtLeast(0L)
                )
            )
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Saves an exported captioned MP4 into the device's Movies/OpenShorts collection
     * using scoped MediaStore storage (zero broad storage permissions required on Android 10+).
     */
    suspend fun saveExportToGallery(filePath: String, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalStateException("Exported video file not found."))
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val contentUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
                return@withContext Result.success(contentUri)
            }

            val resolver = appContext.contentResolver
            val cleanName = displayName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "openshorts_export" }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${cleanName}_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OpenShorts")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore video entry."))

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to open gallery output stream.")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
                Result.success(itemUri)
            } catch (e: CancellationException) {
                resolver.delete(itemUri, null, null)
                throw e
            } catch (e: Exception) {
                resolver.delete(itemUri, null, null)
                Result.failure(e)
            }
        }
}

@UnstableApi
private class CaptionBitmapOverlay(
    private val context: Context,
    private val cues: List<TimedCaptionCue>,
    private val style: CaptionBurnStyle
) : BitmapOverlay() {

    private var overlayWidth = 720
    private var overlayHeight = 1280
    private val frameBitmaps = arrayOfNulls<Bitmap>(2)
    private var bufferIdx = 0
    private var lastRenderKey: String? = null
    private var basePtsUs: Long? = null

    private val captionTypeface: Typeface by lazy {
        val fontRes = when (style.fontName) {
            "NotoSerif" -> R.font.noto_serif
            "JetBrainsMono" -> R.font.jetbrains_mono
            "PlusJakartaSans" -> R.font.plus_jakarta_sans
            else -> R.font.space_grotesk
        }
        ResourcesCompat.getFont(context, fontRes) ?: Typeface.DEFAULT_BOLD
    }

    private val hookTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_serif) ?: Typeface.SERIF
    }

    private val baseColor: Int = parseColorSafe(style.textColorHex, Color.WHITE)
    private val highlightColor: Int = parseColorSafe(style.highlightColorHex, Color.parseColor("#3B82F6"))

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val srcW = videoSize.width.coerceAtLeast(1)
        val srcH = videoSize.height.coerceAtLeast(1)
        val maxDim = maxOf(srcW, srcH)
        val scale = if (maxDim > 1080) 1080f / maxDim else 1f
        overlayWidth = (srcW * scale).roundToInt().coerceAtLeast(360)
        overlayHeight = (srcH * scale).roundToInt().coerceAtLeast(360)
        lastRenderKey = null
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val base = basePtsUs ?: presentationTimeUs.also { basePtsUs = it }
        val relativeMs = ((presentationTimeUs - base).coerceAtLeast(0L)) / 1000L

        val activeCueIndex = cues.indexOfFirst { relativeMs in it.startMs..it.endMs }
        val activeCue = cues.getOrNull(activeCueIndex)
        val words = activeCue?.text?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()

        val activeWordIndex = computeHighlightedWordIndex(activeCue, words, relativeMs)
        val showHook = style.hookText.isNotBlank() && relativeMs <= style.hookDurationSec * 1000L
        val popActive = style.animation == "pop" &&
            activeCue != null &&
            (relativeMs - activeCue.startMs) in 0L..180L

        val renderKey = "$activeCueIndex:$activeWordIndex:$showHook:$popActive:${overlayWidth}x$overlayHeight"
        val currentBitmap = frameBitmaps[bufferIdx]
        if (renderKey == lastRenderKey && currentBitmap != null && !currentBitmap.isRecycled) {
            return currentBitmap
        }

        bufferIdx = 1 - bufferIdx
        val target = ensureBitmap(bufferIdx)
        target.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(target)
        if (showHook) {
            drawHookOverlay(canvas)
        }
        if (activeCue != null && words.isNotEmpty()) {
            drawCaptionOverlay(
                canvas = canvas,
                words = words,
                activeWordIndex = activeWordIndex,
                popScale = if (popActive) 1.07f else 1.0f
            )
        }

        lastRenderKey = renderKey
        return target
    }

    private fun ensureBitmap(slot: Int): Bitmap {
        val existing = frameBitmaps[slot]
        if (existing != null && !existing.isRecycled &&
            existing.width == overlayWidth && existing.height == overlayHeight
        ) {
            return existing
        }
        return Bitmap.createBitmap(overlayWidth, overlayHeight, Bitmap.Config.ARGB_8888).also {
            frameBitmaps[slot] = it
        }
    }

    private fun computeHighlightedWordIndex(
        cue: TimedCaptionCue?,
        words: List<String>,
        relativeMs: Long
    ): Int {
        if (cue == null || words.isEmpty() || style.animation == "none") return -1
        if (style.animation == "pop" && cue.highlightWord.isNotBlank()) {
            val match = words.indexOfFirst {
                it.trim(',', '.', '!', '?', ':', ';').equals(cue.highlightWord.trim(), ignoreCase = true)
            }
            if (match >= 0) return match
        }
        val duration = (cue.endMs - cue.startMs).coerceAtLeast(1L)
        val fraction = ((relativeMs - cue.startMs).toFloat() / duration.toFloat()).coerceIn(0f, 0.999f)
        return (fraction * words.size).toInt().coerceIn(0, words.lastIndex)
    }

    private fun drawHookOverlay(canvas: Canvas) {
        val textSizePx = overlayWidth * when (style.hookSize) {
            "S" -> 0.040f
            "L" -> 0.058f
            else -> 0.048f
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(hookTypeface, Typeface.BOLD)
            textSize = textSizePx
            color = Color.WHITE
            setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
        val maxBoxWidth = overlayWidth * 0.84f
        val lines = wrapText(style.hookText.trim(), paint, maxBoxWidth - 44f)
        if (lines.isEmpty()) return

        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent + 8f
        val padH = 24f
        val padV = 16f
        val maxLineW = lines.maxOf { paint.measureText(it) }
        val boxW = (maxLineW + padH * 2f).coerceAtMost(maxBoxWidth)
        val boxH = lines.size * lineHeight + padV * 2f

        val centerY = when (style.hookPosition) {
            "center" -> overlayHeight * 0.45f
            "bottom" -> overlayHeight * 0.82f
            else -> overlayHeight * 0.13f
        }
        val left = (overlayWidth - boxW) / 2f
        val top = (centerY - boxH / 2f).coerceIn(24f, overlayHeight - boxH - 24f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 10, 10, 14)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(90, 255, 255, 255)
        }
        val rect = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(rect, 22f, 22f, bgPaint)
        canvas.drawRoundRect(rect, 22f, 22f, borderPaint)

        lines.forEachIndexed { idx, line ->
            val w = paint.measureText(line)
            val x = (overlayWidth - w) / 2f
            val y = top + padV + (idx * lineHeight) - fm.ascent
            canvas.drawText(line, x, y, paint)
        }
    }

    private fun drawCaptionOverlay(
        canvas: Canvas,
        words: List<String>,
        activeWordIndex: Int,
        popScale: Float
    ) {
        val textSizePx = overlayWidth * 0.056f * popScale
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(captionTypeface, Typeface.BOLD)
            textSize = textSizePx
            color = baseColor
            setShadowLayer(6f, 0f, 3f, Color.argb(210, 0, 0, 0))
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(captionTypeface, Typeface.BOLD)
            textSize = textSizePx * 1.05f
            color = highlightColor
            setShadowLayer(6f, 0f, 3f, Color.argb(220, 0, 0, 0))
        }

        val spaceW = normalPaint.measureText(" ")
        val maxContentWidth = overlayWidth * 0.84f
        val wrappedLines = wrapWordsWithIndices(words, normalPaint, highlightPaint, activeWordIndex, maxContentWidth)
        if (wrappedLines.isEmpty()) return

        val fm = normalPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.18f
        val padH = 26f
        val padV = 16f

        val lineWidths = wrappedLines.map { line ->
            var w = 0f
            line.forEachIndexed { i, (wordIdx, word) ->
                val p = if (wordIdx == activeWordIndex) highlightPaint else normalPaint
                w += p.measureText(word)
                if (i < line.lastIndex) w += spaceW
            }
            w
        }
        val maxLineWidth = (lineWidths.maxOrNull() ?: 0f).coerceAtMost(maxContentWidth)
        val boxW = maxLineWidth + padH * 2f
        val boxH = wrappedLines.size * lineHeight + padV * 2f

        val anchorY = when (style.position) {
            "top" -> overlayHeight * 0.16f
            "middle" -> overlayHeight * 0.50f
            else -> overlayHeight * 0.78f
        }
        val left = (overlayWidth - boxW) / 2f
        val top = (anchorY - boxH / 2f).coerceIn(24f, overlayHeight - boxH - 24f)

        val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 9, 9, 12)
        }
        canvas.drawRoundRect(RectF(left, top, left + boxW, top + boxH), 20f, 20f, backdropPaint)

        val karaokeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                75,
                Color.red(highlightColor),
                Color.green(highlightColor),
                Color.blue(highlightColor)
            )
        }

        wrappedLines.forEachIndexed { lineIdx, lineWords ->
            val lineW = lineWidths[lineIdx]
            var cursorX = (overlayWidth - lineW) / 2f
            val baselineY = top + padV + (lineIdx * lineHeight) - fm.ascent

            lineWords.forEachIndexed { wordPosInLine, (globalIdx, word) ->
                val isHighlighted = globalIdx == activeWordIndex
                val paint = if (isHighlighted) highlightPaint else normalPaint
                val wordW = paint.measureText(word)

                if (isHighlighted && style.animation == "karaoke") {
                    val pillRect = RectF(
                        cursorX - 8f,
                        baselineY + fm.ascent - 4f,
                        cursorX + wordW + 8f,
                        baselineY + fm.descent + 4f
                    )
                    canvas.drawRoundRect(pillRect, 10f, 10f, karaokeBgPaint)
                }

                canvas.drawText(word, cursorX, baselineY, paint)
                cursorX += wordW + if (wordPosInLine < lineWords.lastIndex) spaceW else 0f
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun wrapWordsWithIndices(
        words: List<String>,
        normalPaint: Paint,
        highlightPaint: Paint,
        activeWordIndex: Int,
        maxWidth: Float
    ): List<List<Pair<Int, String>>> {
        val spaceW = normalPaint.measureText(" ")
        val lines = mutableListOf<MutableList<Pair<Int, String>>>()
        var currentLine = mutableListOf<Pair<Int, String>>()
        var currentWidth = 0f

        words.forEachIndexed { idx, word ->
            val p = if (idx == activeWordIndex) highlightPaint else normalPaint
            val wordW = p.measureText(word)
            val addW = if (currentLine.isEmpty()) wordW else spaceW + wordW
            if (currentWidth + addW <= maxWidth || currentLine.isEmpty()) {
                currentLine.add(idx to word)
                currentWidth += addW
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(idx to word)
                currentWidth = wordW
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }
}
