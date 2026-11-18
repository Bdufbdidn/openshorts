package com.example.data.caption

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.data.model.TimedCaptionCue
import com.example.data.model.VideoTranscriptionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.roundToLong

private const val TAG = "GeminiCaptionService"
private const val MAX_INLINE_AUDIO_BYTES = 14 * 1024 * 1024 // Stay safely below Gemini 20MB inline limit

class GeminiCaptionTranscriptionService(context: Context) {
    private val appContext = context.applicationContext

    private val transcriptionModel: GenerativeModel by lazy {
        val cueSchema = Schema.obj(
            properties = mapOf(
                "startSec" to Schema.double("Start timestamp of this caption phrase in seconds relative to 0.0"),
                "endSec" to Schema.double("End timestamp of this caption phrase in seconds, strictly greater than startSec"),
                "text" to Schema.string("Short spoken overlay caption phrase (2 to 6 words) matching exact speech"),
                "highlightWord" to Schema.string("Single highest-impact keyword from this phrase to accent visually")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "detectedLanguage" to Schema.string("Spoken language name, e.g. English or Spanish"),
                "fullTranscript" to Schema.string("Complete verbatim spoken audio transcript"),
                "cues" to Schema.array(
                    items = cueSchema,
                    description = "Sequential timed overlay caption phrases covering all speech in the audio"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    /**
     * Extracts audio from [videoUri] (optionally within [clipStartMs]..[clipEndMs]) and calls
     * Gemini (`gemini-3.8-flash` via Firebase AI Logic) to transcribe speech and generate
     * timed short-form overlay caption cues.
     */
    suspend fun transcribeVideoAudio(
        videoUri: Uri,
        clipStartMs: Long = 0L,
        clipEndMs: Long? = null,
        targetLanguageHint: String = ""
    ): Result<VideoTranscriptionResult> = withContext(Dispatchers.IO) {
        try {
            val extracted = extractAudioPayload(
                videoUri = videoUri,
                clipStartMs = clipStartMs.coerceAtLeast(0L),
                clipEndMs = clipEndMs
            )

            val prompt = buildString {
                appendLine("Listen carefully to the attached audio track and transcribe every spoken word verbatim.")
                appendLine("Audio duration is approximately ${"%.1f".format(Locale.US, extracted.durationSec)} seconds (starting at 0.0s).")
                appendLine("Break the spoken dialogue into crisp, readable vertical-video overlay caption cues of 2 to 6 words each.")
                appendLine("Rules:")
                appendLine("1. Timestamps startSec and endSec must accurately match when each phrase is spoken within 0.0..${"%.1f".format(Locale.US, extracted.durationSec)}.")
                appendLine("2. Cues must be strictly non-overlapping and sequential (endSec > startSec).")
                appendLine("3. Pick one standout keyword from each cue for highlightWord.")
                if (targetLanguageHint.isNotBlank()) {
                    appendLine("4. Output captions in $targetLanguageHint (translate faithfully while keeping tight spoken timing if different from source speech).")
                } else {
                    appendLine("4. Keep captions in the original spoken language.")
                }
            }

            val response = withTimeout(60_000L) {
                transcriptionModel.generateContent(
                    content {
                        inlineData(extracted.bytes, extracted.mimeType)
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text ?: throw IllegalStateException("Gemini returned an empty transcription.")
            val json = JSONObject(rawJson)
            val detectedLanguage = json.optString("detectedLanguage", "English").ifBlank { "English" }
            val fullTranscript = json.optString("fullTranscript", "").trim()
            val cuesArr = json.optJSONArray("cues")

            val durationLimitMs = (extracted.durationSec * 1000.0).roundToLong().coerceAtLeast(1000L)
            val parsedCues = buildList {
                if (cuesArr != null) {
                    var lastEndMs = 0L
                    for (i in 0 until cuesArr.length()) {
                        val obj = cuesArr.optJSONObject(i) ?: continue
                        val text = obj.optString("text", "").trim()
                        if (text.isBlank()) continue
                        val rawStartMs = (obj.optDouble("startSec", i * 2.0) * 1000.0).roundToLong()
                        val rawEndMs = (obj.optDouble("endSec", (i + 1) * 2.0) * 1000.0).roundToLong()
                        val startMs = rawStartMs.coerceAtLeast(lastEndMs).coerceAtMost(durationLimitMs)
                        val endMs = rawEndMs.coerceAtLeast(startMs + 450L)
                        lastEndMs = endMs
                        add(
                            TimedCaptionCue(
                                index = size + 1,
                                startMs = startMs,
                                endMs = endMs,
                                text = text,
                                highlightWord = obj.optString("highlightWord", "").trim()
                            )
                        )
                    }
                }
            }

            val finalCues = if (parsedCues.isNotEmpty()) {
                parsedCues
            } else if (fullTranscript.isNotBlank()) {
                buildOverlayCuesFromTranscript(fullTranscript, durationLimitMs)
            } else {
                throw IllegalStateException("No speech detected in the extracted video audio.")
            }

            val finalTranscript = fullTranscript.ifBlank { finalCues.joinToString(" ") { it.text } }
            val srtText = formatSrt(finalCues)

            Result.success(
                VideoTranscriptionResult(
                    fullTranscript = finalTranscript,
                    detectedLanguage = detectedLanguage,
                    audioTrackInfo = extracted.trackInfo,
                    cues = finalCues,
                    srtText = srtText
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini audio transcription timed out", e)
            Result.failure(IllegalStateException("Audio transcription timed out after 60s. Try a shorter clip range."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            Result.failure(e)
        }
    }

    private data class ExtractedAudioPayload(
        val bytes: ByteArray,
        val mimeType: String,
        val durationSec: Double,
        val trackInfo: String
    )

    private fun extractAudioPayload(
        videoUri: Uri,
        clipStartMs: Long,
        clipEndMs: Long?
    ): ExtractedAudioPayload {
        val videoDurationMs = probeVideoDurationMs(videoUri)
        val effectiveEndMs = clipEndMs?.takeIf { it > clipStartMs } ?: videoDurationMs
        val targetDurationSec = ((effectiveEndMs - clipStartMs).coerceAtLeast(1000L)) / 1000.0

        val extractor = MediaExtractor()
        var tempM4a: File? = null
        try {
            extractor.setDataSource(appContext, videoUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IllegalStateException("This video has no audio track to transcribe.")
            }

            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val trackLabel = "$audioMime • ${sampleRate}Hz • ${"%.1f".format(Locale.US, targetDurationSec)}s"

            // Fast zero-transcode AAC audio extraction to .m4a (usually ~1MB/min)
            if (audioMime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)) {
                extractor.selectTrack(audioTrackIndex)
                val startUs = clipStartMs * 1000L
                val endUs = effectiveEndMs * 1000L
                if (startUs > 0L) {
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }

                tempM4a = File(appContext.cacheDir, "gemini_audio_${System.currentTimeMillis()}.m4a")
                val muxer = MediaMuxer(tempM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var muxerStarted = false
                try {
                    val outTrack = muxer.addTrack(audioFormat)
                    muxer.start()
                    muxerStarted = true

                    val maxInput = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
                    } else 256 * 1024
                    val buffer = ByteBuffer.allocate(maxInput)
                    val info = MediaCodec.BufferInfo()
                    var anchorPtsUs = -1L
                    var writtenBytes = 0L

                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break
                        val ptsUs = extractor.sampleTime
                        if (endUs > 0L && ptsUs > endUs) break

                        if (ptsUs >= startUs) {
                            if (anchorPtsUs < 0L) anchorPtsUs = ptsUs
                            info.offset = 0
                            info.size = sampleSize
                            info.presentationTimeUs = (ptsUs - anchorPtsUs).coerceAtLeast(0L)
                            info.flags = extractor.sampleFlags
                            muxer.writeSampleData(outTrack, buffer, info)
                            writtenBytes += sampleSize
                            if (writtenBytes >= MAX_INLINE_AUDIO_BYTES) break
                        }
                        extractor.advance()
                    }
                } finally {
                    try {
                        if (muxerStarted) muxer.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        muxer.release()
                    } catch (_: Exception) {
                    }
                }

                if (tempM4a.exists() && tempM4a.length() > 512L) {
                    return ExtractedAudioPayload(
                        bytes = tempM4a.readBytes(),
                        mimeType = "audio/mp4",
                        durationSec = targetDurationSec,
                        trackInfo = trackLabel
                    )
                }
            }

            // Fallback for non-AAC audio tracks: read raw file if under inline size threshold
            val rawBytes = readUriBytesUpTo(videoUri, MAX_INLINE_AUDIO_BYTES)
                ?: throw IllegalStateException(
                    "Audio codec ($audioMime) requires a shorter clip or MP4/AAC video under 14 MB for inline transcription."
                )
            val resolvedMime = appContext.contentResolver.getType(videoUri) ?: "video/mp4"
            return ExtractedAudioPayload(
                bytes = rawBytes,
                mimeType = resolvedMime,
                durationSec = targetDurationSec,
                trackInfo = trackLabel
            )
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                tempM4a?.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun probeVideoDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 30_000L
        } catch (_: Exception) {
            30_000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readUriBytesUpTo(uri: Uri, maxBytes: Int): ByteArray? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun buildOverlayCuesFromTranscript(
            transcript: String,
            totalDurationMs: Long = 15_000L
        ): List<TimedCaptionCue> {
            val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return emptyList()
            val chunks = words.chunked(4)
            val safeTotalMs = totalDurationMs.coerceAtLeast(chunks.size * 1200L)
            val stepMs = safeTotalMs / chunks.size.coerceAtLeast(1)
            return chunks.mapIndexed { idx, group ->
                val startMs = idx * stepMs
                val endMs = if (idx == chunks.lastIndex) safeTotalMs else (idx + 1) * stepMs
                val highlight = group.maxByOrNull { it.length }.orEmpty()
                TimedCaptionCue(
                    index = idx + 1,
                    startMs = startMs,
                    endMs = endMs,
                    text = group.joinToString(" "),
                    highlightWord = highlight
                )
            }
        }

        fun formatSrt(cues: List<TimedCaptionCue>): String {
            return cues.mapIndexed { idx, cue ->
                buildString {
                    appendLine(idx + 1)
                    appendLine("${formatSrtTime(cue.startMs)} --> ${formatSrtTime(cue.endMs)}")
                    appendLine(cue.text)
                }
            }.joinToString("\n").trim()
        }

        private fun formatSrtTime(millis: Long): String {
            val safe = millis.coerceAtLeast(0L)
            val hours = safe / 3_600_000L
            val mins = (safe % 3_600_000L) / 60_000L
            val secs = (safe % 60_000L) / 1000L
            val ms = safe % 1000L
            return "%02d:%02d:%02d,%03d".format(Locale.US, hours, mins, secs, ms)
        }
    }
}
