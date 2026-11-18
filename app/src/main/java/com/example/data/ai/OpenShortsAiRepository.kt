package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.model.ClipAnalysisResult
import com.example.data.model.DetectedClipMoment
import com.example.data.model.GeneratedUgcScript
import com.example.data.model.LiveShortsSkillPreset
import com.example.data.model.ProductResearchResult
import com.example.data.model.PublishMetadataDraft
import com.example.data.model.TitleRecommendation
import com.example.data.model.TranslatedClipResult
import com.example.data.model.TrimChatMessage
import com.example.data.model.TrimChatReviewResult
import com.example.data.model.UgcSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.content
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class OpenShortsAiRepository(context: Context) {
    private val appContext = context.applicationContext

    private val clipAnalysisModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildClipAnalysisModel() }
    private val translationModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildTranslationModel() }
    private val productResearchModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildProductResearchModel() }
    private val ugcScriptModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildUgcScriptModel() }
    private val youtubeTitlesModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildYoutubeTitlesModel() }
    private val publishMetadataModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildPublishMetadataModel() }
    private val trimReviewModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildTrimReviewModel() }
    private val thumbnailImageModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildThumbnailImageModel() }
    private val squareImageModel: GenerativeModel by lazy { OpenShortsAiSchemas.buildPortraitImageModel() }
    private val generalFlashModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun analyzeVideoForClips(
        sourceLabel: String,
        videoUri: Uri?,
        clipGuidance: String,
        maxClips: Int
    ): Pair<Double, ClipAnalysisResult> = withTimeout(60_000L) {
        val extracted = if (videoUri != null) {
            OpenShortsMediaUtils.extractSampledFrames(appContext, videoUri)
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

    suspend fun analyzeLiveVideoForShorts(
        liveStreamLabel: String,
        lookbackMinutes: Int,
        skillPreset: LiveShortsSkillPreset,
        liveCueNotes: String,
        maxClips: Int
    ): Pair<Double, ClipAnalysisResult> = withTimeout(60_000L) {
        val windowDurationSec = (lookbackMinutes.coerceIn(5, 120) * 60).toDouble()
        val prompt = buildString {
            appendLine("You are an expert live-stream vertical shorts producer using the '${skillPreset.label}' skill.")
            appendLine("Live Stream Source: $liveStreamLabel")
            appendLine("DVR Rolling Buffer Window: Last $lookbackMinutes minutes (0 to ${windowDurationSec.toInt()} seconds).")
            appendLine("Skill Directive: ${skillPreset.promptInstruction}")
            if (liveCueNotes.isNotBlank()) {
                appendLine("Creator live timestamps / chat spikes / topic cues: $liveCueNotes")
            }
            appendLine("Extract $maxClips high-energy 15s-55s vertical Shorts moments inside 0..${windowDurationSec.toInt()} seconds.")
            appendLine("Ensure each clip starts right on an attention-grabbing live moment, cleans dead-air lead-in, and includes punchy vertical subtitles, a viralHookText, YouTube #Shorts title, TikTok/Instagram captions, and viralScore.")
        }

        val response = clipAnalysisModel.generateContent(prompt)
        val rawJson = response.text ?: throw IllegalStateException("AI returned empty live-stream analysis.")
        val json = JSONObject(rawJson)
        val summary = json.optString("summary", "Live broadcast analyzed for instant vertical shorts.")
        val clipsArr = json.optJSONArray("clips") ?: throw IllegalStateException("No live clip moments returned.")
        val clips = buildList {
            for (i in 0 until clipsArr.length()) {
                val obj = clipsArr.getJSONObject(i)
                val start = obj.optDouble("startSec", i * 45.0).coerceIn(0.0, (windowDurationSec - 10.0).coerceAtLeast(0.0))
                val end = obj.optDouble("endSec", start + 25.0).coerceAtLeast(start + 8.0).coerceAtMost(windowDurationSec)
                add(
                    DetectedClipMoment(
                        startSec = start,
                        endSec = end,
                        youtubeTitle = obj.optString("youtubeTitle", "Live Highlight #${i + 1} #Shorts"),
                        tiktokCaption = obj.optString("tiktokCaption", ""),
                        instagramCaption = obj.optString("instagramCaption", ""),
                        viralHookText = obj.optString("viralHookText", "LIVE MOMENT"),
                        subtitleText = obj.optString("subtitleText", ""),
                        effectsSummary = obj.optString("effectsSummary", "Live DVR trim + auto-zoom reaction cut"),
                        viralScore = obj.optInt("viralScore", 88).coerceIn(50, 99)
                    )
                )
            }
        }
        windowDurationSec to ClipAnalysisResult(summary = summary, clips = clips)
    }

    suspend fun reviewAndSuggestTrim(
        videoTitle: String,
        sourceLabel: String,
        totalDurationSec: Double,
        currentStartSec: Double,
        currentEndSec: Double,
        transcriptOrContext: String,
        chatHistory: List<TrimChatMessage>,
        userRequest: String
    ): TrimChatReviewResult = withTimeout(45_000L) {
        val safeTotal = totalDurationSec.coerceAtLeast(5.0)
        val historySummary = chatHistory.takeLast(8).joinToString("\n") { msg ->
            val role = if (msg.fromUser) "Creator" else "Editor AI"
            val rangeNote = if (msg.suggestedStartSec != null && msg.suggestedEndSec != null) {
                " [Trim: ${"%.1f".format(msg.suggestedStartSec)}s-${"%.1f".format(msg.suggestedEndSec)}s]"
            } else ""
            "$role: ${msg.text}$rangeNote"
        }

        val prompt = """
            You are an expert short-form video retention editor helping a creator visually trim a clip via chat review.
            Video / Clip Title: ${videoTitle.ifBlank { "Untitled Video" }}
            Source: ${sourceLabel.ifBlank { "Selected Clip" }}
            Total Video Duration: ${"%.1f".format(safeTotal)} seconds
            Current Visual Trim Handles: Start = ${"%.1f".format(currentStartSec)}s, End = ${"%.1f".format(currentEndSec)}s (Clip length: ${"%.1f".format((currentEndSec - currentStartSec).coerceAtLeast(1.0))}s)
            Transcript / Context: ${transcriptOrContext.ifBlank { "No verbatim transcript provided; optimize pacing, hook speed, and clean cut points." }.take(1500)}

            Recent Chat History:
            ${historySummary.ifBlank { "(New review session)" }}

            Latest Creator Chat Review Request:
            "$userRequest"

            Instructions:
            - Evaluate the creator's feedback and adjust suggestedStartSec and suggestedEndSec strictly within [0.0, ${"%.1f".format(safeTotal)}] with suggestedEndSec >= suggestedStartSec + 2.0.
            - If the user mentions timestamps (e.g. "start at 0:12", "cut last 5s", "make it 20s long", "tighten the intro"), honor those exact timings mathematically relative to current handles or total duration.
            - Set shouldApplyTrim = true whenever you propose specific start/end trim timestamps to update the visual trim handles.
            - Keep assistantMessage friendly, specific, and practical (mentioning exact MM:SS bounds).
        """.trimIndent()

        val response = trimReviewModel.generateContent(prompt)
        val json = JSONObject(response.text ?: throw IllegalStateException("AI trim review returned empty response."))
        val minGap = 2.0.coerceAtMost(safeTotal * 0.5)
        val rawStart = json.optDouble("suggestedStartSec", currentStartSec)
            .coerceIn(0.0, (safeTotal - minGap).coerceAtLeast(0.0))
        val rawEnd = json.optDouble("suggestedEndSec", currentEndSec)
            .coerceAtLeast(rawStart + minGap)
            .coerceAtMost(safeTotal)
        TrimChatReviewResult(
            assistantMessage = json.optString(
                "assistantMessage",
                "Updated the trim handles to focus on the strongest opening hook and clean ending."
            ),
            suggestedStartSec = rawStart,
            suggestedEndSec = rawEnd,
            shouldApplyTrim = json.optBoolean("shouldApplyTrim", true),
            suggestedTitle = json.optString("suggestedTitle", "")
        )
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
            painPoints = OpenShortsMediaUtils.jsonArrayToList(json.optJSONArray("painPoints")),
            keyBenefits = OpenShortsMediaUtils.jsonArrayToList(json.optJSONArray("keyBenefits")),
            targetAudience = json.optString("targetAudience", "Modern consumers"),
            emotionalHooks = OpenShortsMediaUtils.jsonArrayToList(json.optJSONArray("emotionalHooks"))
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
                        hashtags = OpenShortsMediaUtils.jsonArrayToList(obj.optJSONArray("hashtags")).take(10),
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
        val savedPath = OpenShortsMediaUtils.saveBitmapToAppStorage(
            appContext,
            bitmap,
            "actor_${System.currentTimeMillis()}.jpg"
        )
        bitmap to savedPath
    }

    suspend fun suggestYoutubeTitles(
        videoUri: Uri?,
        topicOrNotes: String
    ): TitleRecommendation = withTimeout(60_000L) {
        val frames = if (videoUri != null) {
            OpenShortsMediaUtils.extractSampledFrames(appContext, videoUri).frames
        } else {
            emptyList()
        }
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
        val titles = OpenShortsMediaUtils.jsonArrayToList(json.optJSONArray("titles")).take(12)
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
                faceBitmap?.let { image(OpenShortsMediaUtils.scaleBitmapMaxWidth(it, 768)) }
                backgroundBitmap?.let { image(OpenShortsMediaUtils.scaleBitmapMaxWidth(it, 768)) }
                text(prompt)
            }
        )
        val bitmap = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image
            ?: throw IllegalStateException("Thumbnail model did not return an image.")
        val path = OpenShortsMediaUtils.saveBitmapToAppStorage(
            appContext,
            bitmap,
            "thumb_${System.currentTimeMillis()}.jpg"
        )
        bitmap to path
    }

    suspend fun generatePublishMetadata(
        titleHint: String,
        contextText: String,
        transcript: String,
        language: String = "English"
    ): PublishMetadataDraft = withTimeout(45_000L) {
        val prompt = """
            Optimize metadata for automated publishing of a short-form vertical video to BOTH YouTube Shorts and Instagram Reels in $language.
            Current title idea: ${titleHint.ifBlank { "Untitled Short" }}
            Hook / summary context: ${contextText.ifBlank { "High-retention creator short" }}
            Spoken captions / transcript: ${transcript.take(1500)}

            Requirements:
            1. youtubeTitle: Punchy, curiosity-driven YouTube Shorts title strictly <= 95 chars (include #Shorts if helpful).
            2. youtubeDescription: Hook paragraph, cleaned spoken captions / key takeaways, and 5-8 discoverable hashtags.
            3. instagramCaption: Scroll-stopping Reels opening line, concise body with CTA (Save/Share/Comment), and 6-8 targeted hashtags.
        """.trimIndent()
        val response = publishMetadataModel.generateContent(prompt)
        val json = JSONObject(response.text ?: throw IllegalStateException("AI metadata optimization returned empty output."))
        PublishMetadataDraft(
            youtubeTitle = json.optString("youtubeTitle", titleHint).take(100),
            youtubeDescription = json.optString("youtubeDescription", transcript),
            instagramCaption = json.optString("instagramCaption", contextText)
        )
    }

    fun decodeUriToBitmap(uri: Uri): Bitmap? = OpenShortsMediaUtils.decodeUriToBitmap(appContext, uri)

    fun saveBitmapToAppStorage(bitmap: Bitmap, filename: String): String =
        OpenShortsMediaUtils.saveBitmapToAppStorage(appContext, bitmap, filename)
}
