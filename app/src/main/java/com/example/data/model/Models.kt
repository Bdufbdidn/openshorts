package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class ClipJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = "video",
    val sourceLabel: String = "",
    val videoUri: String? = null,
    val durationSec: Double = 0.0,
    val status: String = "completed",
    val summary: String? = null,
    val clipsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "sourceType" to if (sourceType in listOf("video", "url")) sourceType else "video",
        "sourceLabel" to sourceLabel.ifBlank { "Untitled Video" }.take(500),
        "videoUri" to videoUri?.take(500),
        "durationSec" to durationSec.coerceAtLeast(0.0),
        "status" to if (status in listOf("processing", "completed", "failed")) status else "completed",
        "summary" to summary?.take(2000),
        "clipsCount" to clipsCount.coerceIn(0L, 100L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class GeneratedClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val jobId: String = "",
    val clipIndex: Long = 0L,
    val startSec: Double = 0.0,
    val endSec: Double = 15.0,
    val youtubeTitle: String = "",
    val tiktokCaption: String? = null,
    val instagramCaption: String? = null,
    val viralHookText: String? = null,
    val hookPosition: String = "top",
    val hookSize: String = "M",
    val hookAnimation: String = "spring",
    val hookDurationSec: Long = 5L,
    val subtitlePosition: String = "bottom",
    val subtitleAnimation: String = "word-highlight",
    val subtitleFont: String = "SpaceGrotesk",
    val subtitleColorHex: String = "#FFFFFF",
    val subtitleHighlightHex: String = "#3B82F6",
    val subtitleText: String? = null,
    val effectsSummary: String? = null,
    val translatedLanguage: String? = null,
    val viralScore: Long = 85L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val safeStart = startSec.coerceAtLeast(0.0)
        val safeEnd = if (endSec > safeStart) endSec else safeStart + 15.0
        return mapOf(
            "userId" to userId,
            "jobId" to jobId,
            "clipIndex" to clipIndex.coerceIn(0L, 100L),
            "startSec" to safeStart,
            "endSec" to safeEnd,
            "youtubeTitle" to youtubeTitle.ifBlank { "Viral Clip #${clipIndex + 1}" }.take(150),
            "tiktokCaption" to tiktokCaption?.take(1000),
            "instagramCaption" to instagramCaption?.take(1000),
            "viralHookText" to viralHookText?.take(160),
            "hookPosition" to if (hookPosition in listOf("top", "center", "bottom")) hookPosition else "top",
            "hookSize" to if (hookSize in listOf("S", "M", "L")) hookSize else "M",
            "hookAnimation" to if (hookAnimation in listOf("spring", "fade", "slide-up", "none")) hookAnimation else "spring",
            "hookDurationSec" to hookDurationSec.coerceIn(1L, 60L),
            "subtitlePosition" to if (subtitlePosition in listOf("top", "middle", "bottom")) subtitlePosition else "bottom",
            "subtitleAnimation" to if (subtitleAnimation in listOf("pop", "word-highlight", "karaoke", "none")) subtitleAnimation else "word-highlight",
            "subtitleFont" to subtitleFont.take(40),
            "subtitleColorHex" to subtitleColorHex.take(12),
            "subtitleHighlightHex" to subtitleHighlightHex.take(12),
            "subtitleText" to subtitleText?.take(3000),
            "effectsSummary" to effectsSummary?.take(1000),
            "translatedLanguage" to translatedLanguage?.take(30),
            "viralScore" to viralScore.coerceIn(0L, 100L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }.mapValues { it.value!! }
    }
}

@Keep
@IgnoreExtraProperties
data class AiShortProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productName: String = "",
    val productUrl: String? = null,
    val productDescription: String? = null,
    val industry: String? = null,
    val oneLiner: String? = null,
    val videoMode: String = "lowcost",
    val style: String = "ugc",
    val language: String = "en",
    val actorGender: String = "female",
    val scriptTitle: String = "",
    val hookText: String? = null,
    val fullNarration: String = "",
    val actorDescription: String? = null,
    val voiceName: String? = null,
    val actorImagePath: String? = null,
    val brollImagePath: String? = null,
    val caption: String? = null,
    val hashtags: List<String> = emptyList(),
    val durationSeconds: Long = 20L,
    val estimatedCostUsd: Double = 0.80,
    val status: String = "completed",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "productName" to productName.ifBlank { "My Product" }.take(150),
        "productUrl" to productUrl?.take(500),
        "productDescription" to productDescription?.take(2000),
        "industry" to industry?.take(100),
        "oneLiner" to oneLiner?.take(500),
        "videoMode" to if (videoMode in listOf("lowcost", "premium")) videoMode else "lowcost",
        "style" to if (style in listOf("ugc", "educational", "shock", "story", "comparison")) style else "ugc",
        "language" to if (language in listOf("en", "es")) language else "en",
        "actorGender" to if (actorGender in listOf("female", "male")) actorGender else "female",
        "scriptTitle" to scriptTitle.ifBlank { "UGC Campaign Script" }.take(200),
        "hookText" to hookText?.take(200),
        "fullNarration" to fullNarration.ifBlank { "Discover why creators love this product." }.take(4000),
        "actorDescription" to actorDescription?.take(1000),
        "voiceName" to voiceName?.take(100),
        "actorImagePath" to actorImagePath?.take(500),
        "brollImagePath" to brollImagePath?.take(500),
        "caption" to caption?.take(1000),
        "hashtags" to hashtags.take(15),
        "durationSeconds" to durationSeconds.coerceIn(1L, 180L),
        "estimatedCostUsd" to estimatedCostUsd.coerceAtLeast(0.0),
        "status" to if (status in listOf("draft", "completed")) status else "completed",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class YoutubeStudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "video",
    val selectedTitle: String = "",
    val suggestedTitles: List<String> = emptyList(),
    val description: String? = null,
    val thumbnailPrompt: String? = null,
    val thumbnailPath: String? = null,
    val publishedStatus: String = "draft",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "mode" to if (mode in listOf("video", "manual")) mode else "video",
        "selectedTitle" to selectedTitle.ifBlank { "New YouTube Video" }.take(120),
        "suggestedTitles" to suggestedTitles.take(15),
        "description" to description?.take(5000),
        "thumbnailPrompt" to thumbnailPrompt?.take(1000),
        "thumbnailPath" to thumbnailPath?.take(500),
        "publishedStatus" to if (publishedStatus in listOf("draft", "published")) publishedStatus else "draft",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class UgcAvatar(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val prompt: String = "",
    val gender: String = "female",
    val localImagePath: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.ifBlank { "AI Creator" }.take(100),
        "prompt" to prompt.ifBlank { "Studio UGC presenter portrait" }.take(1000),
        "gender" to if (gender in listOf("female", "male")) gender else "female",
        "localImagePath" to localImagePath?.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

// --- Domain / AI DTOs ---

data class DetectedClipMoment(
    val startSec: Double,
    val endSec: Double,
    val youtubeTitle: String,
    val tiktokCaption: String,
    val instagramCaption: String,
    val viralHookText: String,
    val subtitleText: String,
    val effectsSummary: String,
    val viralScore: Int
)

data class ClipAnalysisResult(
    val summary: String,
    val clips: List<DetectedClipMoment>
)

data class ProductResearchResult(
    val productName: String,
    val industry: String,
    val oneLiner: String,
    val painPoints: List<String>,
    val keyBenefits: List<String>,
    val targetAudience: String,
    val emotionalHooks: List<String>
)

data class UgcSegment(
    val label: String,
    val startSec: Int,
    val endSec: Int,
    val dialogue: String,
    val visualDirection: String
)

data class GeneratedUgcScript(
    val title: String,
    val hookText: String,
    val fullNarration: String,
    val actorDescription: String,
    val voiceName: String,
    val caption: String,
    val hashtags: List<String>,
    val durationSeconds: Int,
    val segments: List<UgcSegment>
)

data class TitleRecommendation(
    val titles: List<String>,
    val topPick1: String,
    val topPick2: String,
    val reasoning: String
)

data class TranslatedClipResult(
    val translatedLanguage: String,
    val translatedSubtitles: String,
    val translatedHook: String,
    val translatedYoutubeTitle: String,
    val translatedTiktokCaption: String
)

data class ConnectedAccount(
    val username: String,
    val platforms: List<String>
)

data class IntegrationSettings(
    val uploadPostApiKey: String = "",
    val uploadPostUsername: String = "",
    val elevenLabsApiKey: String = "",
    val falAiApiKey: String = ""
)
