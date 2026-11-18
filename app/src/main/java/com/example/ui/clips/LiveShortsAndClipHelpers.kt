package com.example.ui.clips

import com.example.data.caption.GeminiCaptionTranscriptionService
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.TimedCaptionCue
import com.example.data.model.ClipAnalysisResult
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.LiveShortsSkillPreset

val DEFAULT_LIVE_SHORTS_SKILLS = listOf(
    LiveShortsSkillPreset(
        id = "hype",
        label = "Hype & Clutch",
        tagline = "Peak crowd energy, wins & reactions",
        promptInstruction = "Focus on adrenaline peaks, breakthroughs, high-energy vocal inflections, and fast 15-30s payoff clips."
    ),
    LiveShortsSkillPreset(
        id = "qa",
        label = "Q&A Soundbites",
        tagline = "Sharp audience answers & hot takes",
        promptInstruction = "Isolate complete question-and-answer exchanges with punchy hooks, crisp advice, and zero rambling."
    ),
    LiveShortsSkillPreset(
        id = "breaking",
        label = "Breaking Reveal",
        tagline = "News drops, demos & announcements",
        promptInstruction = "Extract key announcements, live product demos, and headline takeaways structured as urgent #Shorts updates."
    ),
    LiveShortsSkillPreset(
        id = "story",
        label = "Stream Story",
        tagline = "Funny fails & candid backstage arcs",
        promptInstruction = "Capture self-contained 25-45s narrative moments with setup, twist, and comedic or emotional payoff."
    )
)

internal object ClipMetadataFormatter {

    fun buildYoutubeDescriptionFromClip(clip: GeneratedClip, includeTranscript: Boolean): String {
        return buildString {
            if (!clip.viralHookText.isNullOrBlank()) {
                appendLine(clip.viralHookText)
                appendLine()
            }
            if (includeTranscript && !clip.subtitleText.isNullOrBlank()) {
                appendLine("Captions:")
                appendLine(clip.subtitleText)
                appendLine()
            }
            append("#Shorts #Viral #Creator")
        }.trim()
    }

    fun buildInstagramCaptionFromClip(clip: GeneratedClip): String {
        val raw = clip.instagramCaption?.takeIf { it.isNotBlank() }
            ?: clip.tiktokCaption?.takeIf { it.isNotBlank() }
            ?: clip.youtubeTitle
        return if (raw.contains("#")) raw else "$raw\n\n#reels #viral #shorts"
    }

    fun buildBurnStyle(
        clip: GeneratedClip?,
        filterStack: List<String>,
        filterContrast: Float,
        filterSaturation: Float,
        filterBrightness: Float
    ): CaptionBurnStyle {
        return if (clip != null) {
            CaptionBurnStyle(
                position = clip.subtitlePosition,
                animation = clip.subtitleAnimation,
                fontName = clip.subtitleFont,
                textColorHex = clip.subtitleColorHex,
                highlightColorHex = clip.subtitleHighlightHex,
                hookText = clip.viralHookText.orEmpty(),
                hookPosition = clip.hookPosition,
                hookSize = clip.hookSize,
                hookDurationSec = clip.hookDurationSec,
                filterStack = filterStack,
                filterContrast = filterContrast,
                filterSaturation = filterSaturation,
                filterBrightness = filterBrightness
            )
        } else {
            CaptionBurnStyle(
                filterStack = filterStack,
                filterContrast = filterContrast,
                filterSaturation = filterSaturation,
                filterBrightness = filterBrightness
            )
        }
    }

    fun buildCuesForClip(
        clip: GeneratedClip?,
        trimStartMs: Long,
        trimEndMs: Long
    ): List<TimedCaptionCue> {
        val text = clip?.subtitleText
        return if (!text.isNullOrBlank()) {
            val durMs = (trimEndMs - trimStartMs).coerceAtLeast(3000L)
            GeminiCaptionTranscriptionService.buildOverlayCuesFromTranscript(text, durMs)
        } else {
            emptyList()
        }
    }

    fun buildGeneratedClips(
        userId: String,
        analysis: ClipAnalysisResult
    ): List<GeneratedClip> {
        return analysis.clips.mapIndexed { idx, moment ->
            GeneratedClip(
                userId = userId,
                clipIndex = idx.toLong(),
                startSec = moment.startSec,
                endSec = moment.endSec,
                youtubeTitle = moment.youtubeTitle,
                tiktokCaption = moment.tiktokCaption,
                instagramCaption = moment.instagramCaption,
                viralHookText = moment.viralHookText,
                subtitleText = moment.subtitleText,
                effectsSummary = moment.effectsSummary,
                viralScore = moment.viralScore.toLong()
            )
        }
    }
}
