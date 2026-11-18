package com.example.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig

internal object OpenShortsAiSchemas {

    fun buildClipAnalysisModel(): GenerativeModel {
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
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    fun buildTranslationModel(): GenerativeModel {
        val schema = Schema.obj(
            properties = mapOf(
                "translatedLanguage" to Schema.string("Target language display name"),
                "translatedSubtitles" to Schema.string("Translated spoken subtitles keeping timing flow"),
                "translatedHook" to Schema.string("Translated short punchy viral hook"),
                "translatedYoutubeTitle" to Schema.string("Translated YouTube Shorts title under 90 chars"),
                "translatedTiktokCaption" to Schema.string("Translated TikTok caption with localized hashtags")
            )
        )
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun buildProductResearchModel(): GenerativeModel {
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
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun buildUgcScriptModel(): GenerativeModel {
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
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    fun buildYoutubeTitlesModel(): GenerativeModel {
        val schema = Schema.obj(
            properties = mapOf(
                "titles" to Schema.array(Schema.string(), "10 clickable YouTube titles under 70 characters"),
                "topPick1" to Schema.string("Best overall title from the list"),
                "topPick2" to Schema.string("Strong runner-up title from the list"),
                "reasoning" to Schema.string("Brief explanation of why the top 2 picks maximize CTR")
            )
        )
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun buildPublishMetadataModel(): GenerativeModel {
        val schema = Schema.obj(
            properties = mapOf(
                "youtubeTitle" to Schema.string("High-CTR YouTube Shorts title under 95 characters including #Shorts"),
                "youtubeDescription" to Schema.string("YouTube Shorts SEO description with hook, key captions/transcript summary, CTA, and hashtags"),
                "instagramCaption" to Schema.string("Engaging Instagram Reels caption with opening hook, value summary, CTA, and 6-8 hashtags")
            )
        )
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun buildTrimReviewModel(): GenerativeModel {
        val schema = Schema.obj(
            properties = mapOf(
                "assistantMessage" to Schema.string("Concise 1-3 sentence chat review explaining why these trim points boost hook retention"),
                "suggestedStartSec" to Schema.double("Recommended trim start timestamp in seconds (>= 0)"),
                "suggestedEndSec" to Schema.double("Recommended trim end timestamp in seconds (> suggestedStartSec)"),
                "shouldApplyTrim" to Schema.boolean("True if the chat review recommends updating the visual trim handles now"),
                "suggestedTitle" to Schema.string("Optional punchier Short title for the trimmed segment")
            )
        )
        return Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun buildThumbnailImageModel(): GenerativeModel {
        return Firebase.ai.generativeModel(
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

    fun buildPortraitImageModel(): GenerativeModel {
        return Firebase.ai.generativeModel(
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
}
