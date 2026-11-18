package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.model.ClipAnalysisResult
import com.example.data.model.DetectedClipMoment
import com.example.data.model.GeneratedUgcScript
import com.example.data.model.ProductResearchResult
import com.example.data.model.TitleRecommendation
import com.example.data.model.TranslatedClipResult
import com.example.data.model.UgcSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "OpenShortsAiRepo"

class OpenShortsAiRepository(context: Context) {
    private val appContext = context.applicationContext

    // Lazy models following Firebase AI Logic rules
    private val clipAnalysisModel: GenerativeModel by lazy {
        val clipMomentSchema = Schema.obj(
            properties = mapOf(
                "startSec" to Schema.double("Clip start time in seconds"),
                "endSec" to Schema.double("Clip end time in seconds, greater than startSec"),
                "youtubeTitle" to Schema.string("Punchy YouTube Shorts title under 90 chars"),
                "tiktokCaption" to Schema.string("Engaging TikTok caption with relevant hashtags"),
                "instagramCaption" to Schema.string("Instagram Reels caption with call-to-action"),
                "viralHookText" to Schema.string("Short 4-8 word hook overlay displayed at top of clip"),
                "subtitleText" to Schema.string("Full spoken subtitle lines or punchy script for this clip"),
                "effectsSummary" to Schema.string("AI auto-edit camera zoom, cut, and pacing notes"),
                "viralScore" to Schema.integer("Predicted viral score between 60 and 99")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string("Concise 2-sentence overview of the video"),
                "clips" to Schema.array(
                    items = clipMomentSchema,
                    description = "List of 3 to 6 high-retention vertical short clip moments"
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

    private val translationModel: GenerativeModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "translatedLanguage" to Schema.string("Target language display name"),
                "translatedSubtitles" to Schema.string("Translated spoken subtitles keeping timing flow"),
                "translatedHook" to Schema.string("Translated short punchy viral hook"),
                "translatedYoutubeTitle" to Schema.string("Translated YouTube Shorts title under 90 chars"),
                "translatedTiktokCaption" to Schema.string("Translated TikTok caption with localized hashtags")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val productResearchModel: GenerativeModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "productName" to Schema.string("Brand or product name"),
                "industry" to Schema.string("Industry or niche"),
                "oneLiner" to Schema.string("Clear 1-sentence value proposition"),
                "painPoints" to Schema.array(Schema.string(), "3-5 audience pain points"),
                "keyBenefits" to Schema.array(Schema.string(), "3-5 standout benefits"),
                "targetAudience" to Schema.string("Primary target audience description"),
                "emotionalHooks" to Schema.array(Schema.string(), "3-5 scroll-stopping emotional angles")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val ugcScriptModel: GenerativeModel by lazy {
        val segmentSchema = Schema.obj(
            properties = mapOf(
                "label" to Schema.string("Segment phase: Hook, Problem, Solution, or CTA"),
                "startSec" to Schema.integer("Segment start second"),
                "endSec" to Schema.integer("Segment end second"),
                "dialogue" to Schema.string("Spoken UGC line"),
                "visualDirection" to Schema.string("Camera framing and action cue")
            )
        )
        val scriptSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Campaign concept title"),
                "hookText" to Schema.string("Opening 3-second hook text overlay"),
                "fullNarration" to Schema.string("Complete voiceover script from start to finish"),
                "actorDescription" to Schema.string("Detailed portrait prompt for the AI actor"),
                "voiceName" to Schema.string("Recommended voice profile"),
                "caption" to Schema.string("Social post caption"),
                "hashtags" to Schema.array(Schema.string(), "Up to 8 hashtags"),
                "durationSeconds" to Schema.integer("Estimated duration in seconds (15 to 45)"),
                "segments" to Schema.array(segmentSchema, "4 timed storyboard segments")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "scripts" to Schema.array(scriptSchema, "List of requested UGC scripts")
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

    private val youtubeTitlesModel: GenerativeModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "titles" to Schema.array(Schema.string(), "10 clickable YouTube titles under 70 characters"),
                "topPick1" to Schema.string("Best overall title from the list"),
                "topPick2" to Schema.string("Strong runner-up title from the list"),
                "reasoning" to Schema.string("Brief explanation of why the top 2 picks maximize CTR")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val generalFlashModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val thumbnailImageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    private val squareImageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.PORTRAIT_3x4)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun analyzeVideoForClips(
        sourceLabel: String,
        videoUri: Uri?,
        clipGuidance: String,
        maxClips: Int
    ): Pair<Double, ClipAnalysisResult> = withTimeout(60_000L) {
        val extracted = if (videoUri != null) {
            extractSampledFrames(videoUri)
        } else {
            SampledVideoData(durationSec = 180.0, frames = emptyList())
        }

        val durationSec = extracted.durationSec.coerceAtLeast(30.0)
        val prompt = buildString {
            appendLine("You are an elite viral short-form video editor (TikTok, YouTube Shorts, Instagram Reels).")
            appendLine("Analyze this source video ($sourceLabel, estimated duration ${durationSec.toInt()}s).")
            if (clipGuidance.isNotBlank()) {
                appendLine("Creator guidance: $clipGuidance")
            }
            appendLine("Identify $maxClips distinct high-retention 15s-55s vertical clip segments within 0..${durationSec.toInt()} seconds.")
            appendLine("For each clip provide exact startSec and endSec (endSec > startSec), a YouTube Short title, TikTok caption, Instagram caption, a short punchy viralHookText, natural subtitleText transcript/captions, dynamic AI auto-edit effectsSummary, and viralScore (70-98).")
        }

        val response = clipAnalysisModel.generateContent(
            content {
                extracted.frames.forEach { bitmap -> image(bitmap) }
                text(prompt)
            }
        )
        val rawJson = response.text ?: throw IllegalStateException("AI returned an empty clip analysis.")
        val json = JSONObject(rawJson)
        val summary = json.optString("summary", "AI analyzed your video for top viral highlights.")
        val clipsArr = json.optJSONArray("clips") ?: throw IllegalStateException("No clips returned in AI response.")
        val clips = buildList {
            for (i in 0 until clipsArr.length()) {
                val obj = clipsArr.getJSONObject(i)
                val start = obj.optDouble("startSec", i * 25.0).coerceIn(0.0, (durationSec - 10.0).coerceAtLeast(0.0))
                val end = obj.optDouble("endSec", start + 25.0).coerceAtLeast(start + 10.0).coerceAtMost(durationSec)
                add(
                    DetectedClipMoment(
                        startSec = start,
                        endSec = end,
                        youtubeTitle = obj.optString("youtubeTitle", "Viral Moment #${i + 1}"),
                        tiktokCaption = obj.optString("tiktokCaption", ""),
                        instagramCaption = obj.optString("instagramCaption", ""),
                        viralHookText = obj.optString("viralHookText", "WAIT FOR THIS"),
                        subtitleText = obj.optString("subtitleText", ""),
                        effectsSummary = obj.optString("effectsSummary", "Dynamic zoom-in on hook + punchy cut pacing"),
                        viralScore = obj.optInt("viralScore", 86).coerceIn(50, 99)
                    )
                )
            }
        }
        durationSec to ClipAnalysisResult(summary = summary, clips = clips)
    }

    suspend fun translateClipContent(
        targetLanguage: String,
        subtitleText: String,
        hookText: String,
        youtubeTitle: String,
        tiktokCaption: String
    ): TranslatedClipResult = withTimeout(45_000L) {
        val prompt = """
            Translate and localize this short-form video clip metadata into $targetLanguage.
            Keep the hook punchy and native, preserve energy, and localize hashtags.
            Subtitles: $subtitleText
            Viral Hook: $hookText
            YouTube Title: $youtubeTitle
            TikTok Caption: $tiktokCaption
        """.trimIndent()
        val response = translationModel.generateContent(prompt)
        val json = JSONObject(response.text ?: throw IllegalStateException("Translation returned empty response."))
        TranslatedClipResult(
            translatedLanguage = json.optString("translatedLanguage", targetLanguage),
            translatedSubtitles = json.optString("translatedSubtitles", subtitleText),
            translatedHook = json.optString("translatedHook", hookText),
            translatedYoutubeTitle = json.optString("translatedYoutubeTitle", youtubeTitle),
            translatedTiktokCaption = json.optString("translatedTiktokCaption", tiktokCaption)
        )
    }

    suspend fun researchProductForUgc(
        productUrl: String,
        productDescription: String,
        language: String
    ): ProductResearchResult = withTimeout(45_000L) {
        val langName = if (language == "es") "Spanish" else "English"
        val prompt = """
            Perform deep marketing and UGC conversion research for this product/business in $langName.
            Product URL: ${productUrl.ifBlank { "Not specified" }}
            Product Description / Context: $productDescription
            Extract the product name, industry, concise 1-liner value prop, target audience, pain points, key benefits, and emotional UGC hooks.
        """.trimIndent()
        val response = productResearchModel.generateContent(prompt)
        val json = JSONObject(response.text ?: throw IllegalStateException("Product research returned empty output."))
        ProductResearchResult(
            productName = json.optString("productName", "Featured Product"),
            industry = json.optString("industry", "Digital / Consumer"),
            oneLiner = json.optString("oneLiner", productDescription.take(120)),
            painPoints = jsonArrayToList(json.optJSONArray("painPoints")),
            keyBenefits = jsonArrayToList(json.optJSONArray("keyBenefits")),
            targetAudience = json.optString("targetAudience", "Modern consumers"),
            emotionalHooks = jsonArrayToList(json.optJSONArray("emotionalHooks"))
        )
    }

    suspend fun generateUgcScripts(
        research: ProductResearchResult,
        style: String,
        language: String,
        actorGender: String,
        videoMode: String,
        count: Int
    ): List<GeneratedUgcScript> = withTimeout(60_000L) {
        val langName = if (language == "es") "Spanish" else "English"
        val prompt = """
            Generate $count distinct UGC short-form video marketing scripts in $langName.
            Product: ${research.productName} (${research.industry})
            Value Prop: ${research.oneLiner}
            Pain Points: ${research.painPoints.joinToString("; ")}
            Benefits: ${research.keyBenefits.joinToString("; ")}
            Hooks: ${research.emotionalHooks.joinToString("; ")}
            Style: $style
            Actor Gender: $actorGender
            Production Mode: $videoMode (keep pacing authentic, conversational, and high-converting for TikTok/Reels).
            Each script should have 4 clear timed segments: Hook, Problem, Solution, CTA.
        """.trimIndent()
        val response = ugcScriptModel.generateContent(prompt)
        val json = JSONObject(response.text ?: throw IllegalStateException("Script generation returned empty output."))
        val arr = json.optJSONArray("scripts") ?: throw IllegalStateException("Missing scripts array.")
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val segArr = obj.optJSONArray("segments")
                val segments = if (segArr != null) {
                    (0 until segArr.length()).map { idx ->
                        val s = segArr.getJSONObject(idx)
                        UgcSegment(
                            label = s.optString("label", "Scene ${idx + 1}"),
                            startSec = s.optInt("startSec", idx * 5),
                            endSec = s.optInt("endSec", (idx + 1) * 5),
                            dialogue = s.optString("dialogue", ""),
                            visualDirection = s.optString("visualDirection", "")
                        )
                    }
                } else emptyList()

                add(
                    GeneratedUgcScript(
                        title = obj.optString("title", "${research.productName} Script #${i + 1}"),
                        hookText = obj.optString("hookText", ""),
                        fullNarration = obj.optString("fullNarration", ""),
                        actorDescription = obj.optString("actorDescription", "Authentic $actorGender creator filming on iPhone"),
                        voiceName = obj.optString("voiceName", if (actorGender == "female") "Aria (Warm UGC)" else "Liam (Natural)"),
                        caption = obj.optString("caption", ""),
                        hashtags = jsonArrayToList(obj.optJSONArray("hashtags")).take(10),
                        durationSeconds = obj.optInt("durationSeconds", 22).coerceIn(10, 90),
                        segments = segments
                    )
                )
            }
        }
    }

    suspend fun generateActorPortrait(
        prompt: String,
        gender: String
    ): Pair<Bitmap, String> = withTimeout(60_000L) {
        val fullPrompt = "Vertical photorealistic 3:4 portrait of a $gender UGC video creator speaking naturally to camera in warm modern lighting, crisp 85mm lens look, authentic expression. $prompt"
        val response = squareImageModel.generateContent(fullPrompt)
        val bitmap = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image
            ?: throw IllegalStateException("AI image model did not return a portrait.")
        val savedPath = saveBitmapToAppStorage(bitmap, "actor_${System.currentTimeMillis()}.jpg")
        bitmap to savedPath
    }

    suspend fun suggestYoutubeTitles(
        videoUri: Uri?,
        topicOrNotes: String
    ): TitleRecommendation = withTimeout(60_000L) {
        val frames = if (videoUri != null) extractSampledFrames(videoUri).frames else emptyList()
        val prompt = """
            You are a YouTube CTR strategist. Generate 10 curiosity-driven, high-CTR YouTube titles (each strictly under 70 characters) for this video topic:
            ${topicOrNotes.ifBlank { "Analyze the provided video frames and craft viral YouTube titles." }}
            Select topPick1 and topPick2 from the 10 titles and explain why they outperform.
        """.trimIndent()
        val response = youtubeTitlesModel.generateContent(
            content {
                frames.forEach { image(it) }
                text(prompt)
            }
        )
        val json = JSONObject(response.text ?: throw IllegalStateException("Title generator returned empty response."))
        val titles = jsonArrayToList(json.optJSONArray("titles")).take(12)
        TitleRecommendation(
            titles = titles,
            topPick1 = json.optString("topPick1", titles.firstOrNull().orEmpty()),
            topPick2 = json.optString("topPick2", titles.getOrNull(1).orEmpty()),
            reasoning = json.optString("reasoning", "")
        )
    }

    fun startTitleRefinementChat(initialContext: String): Chat {
        return generalFlashModel.startChat(
            history = listOf(
                content("user") {
                    text("You are helping me refine YouTube video titles under 70 chars for maximum CTR. Context: $initialContext")
                },
                content("model") {
                    text("Ready! Tell me what vibe, angle, or keywords you want to emphasize and I will craft sharper title variations.")
                }
            )
        )
    }

    suspend fun sendChatMessage(chat: Chat, userMessage: String): String = withTimeout(45_000L) {
        val response = chat.sendMessage(userMessage)
        response.text ?: throw IllegalStateException("Chat returned an empty response.")
    }

    suspend fun generateYoutubeDescription(
        title: String,
        notes: String
    ): String = withTimeout(45_000L) {
        val prompt = """
            Write a complete SEO-optimized YouTube video description for the title: "$title".
            Include:
            1. Hook paragraph (first 2 lines before "Show more")
            2. Key takeaways / what viewers will learn
            3. Timestamp chapter template (00:00 Intro, etc.)
            4. Call to action & relevant hashtags
            Context notes: $notes
        """.trimIndent()
        val response = generalFlashModel.generateContent(prompt)
        response.text ?: throw IllegalStateException("Description generation returned empty text.")
    }

    suspend fun generateYoutubeThumbnail(
        title: String,
        extraPrompt: String,
        faceBitmap: Bitmap?,
        backgroundBitmap: Bitmap?
    ): Pair<Bitmap, String> = withTimeout(60_000L) {
        val prompt = buildString {
            append("Design a bold, high-contrast 16:9 YouTube thumbnail for the video titled \"$title\". ")
            append("Ultra-clean studio composition, expressive focal point, vibrant rim lighting, readable dramatic visual storytelling, no messy clutter. ")
            if (faceBitmap != null) {
                append("Incorporate the person from the provided reference photo as the expressive main subject. ")
            }
            if (backgroundBitmap != null) {
                append("Blend the style/setting from the provided background reference image. ")
            }
            if (extraPrompt.isNotBlank()) {
                append("Additional art direction: $extraPrompt")
            }
        }
        val response = thumbnailImageModel.generateContent(
            content {
                faceBitmap?.let { image(scaleBitmapMaxWidth(it, 768)) }
                backgroundBitmap?.let { image(scaleBitmapMaxWidth(it, 768)) }
                text(prompt)
            }
        )
        val bitmap = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image
            ?: throw IllegalStateException("Thumbnail model did not return an image.")
        val path = saveBitmapToAppStorage(bitmap, "thumb_${System.currentTimeMillis()}.jpg")
        bitmap to path
    }

    fun decodeUriToBitmap(uri: Uri): Bitmap? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { scaleBitmapMaxWidth(it, 1024) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap from URI: $uri", e)
            null
        }
    }

    fun saveBitmapToAppStorage(bitmap: Bitmap, filename: String): String {
        val dir = File(appContext.filesDir, "generated").apply { mkdirs() }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    private data class SampledVideoData(
        val durationSec: Double,
        val frames: List<Bitmap>
    )

    private suspend fun extractSampledFrames(uri: Uri): SampledVideoData = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
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
            Log.w(TAG, "Could not extract frames via MediaMetadataRetriever: ${e.message}")
            SampledVideoData(durationSec = 90.0, frames = emptyList())
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun scaleBitmapMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width.toFloat()
        val targetHeight = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, targetHeight, true)
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx)?.takeIf { it.isNotBlank() }
        }
    }
}
