package com.example.ui.clips

import android.util.Log
import com.example.data.ai.OpenShortsAiRepository
import com.example.data.automation.PublishAutomationScheduler
import com.example.data.model.GeneratedClip
import com.example.data.model.IntegrationSettings
import com.example.data.model.PublishAutomationJob
import com.example.data.model.PublishMetadataDraft
import com.example.data.repository.OpenShortsFirestoreRepository
import com.example.ui.components.PublishAutomationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.time.LocalDate
import java.time.ZoneOffset

private const val DELEGATE_TAG = "ClipPublishDelegate"

internal class ClipPublishAndEditDelegate(
    private val currentUserId: String,
    private val firestoreRepo: OpenShortsFirestoreRepository,
    private val aiRepo: OpenShortsAiRepository,
    private val scheduler: PublishAutomationScheduler
) {

    suspend fun autoQueueClips(
        clips: List<GeneratedClip>,
        videoUriString: String?,
        currentSettings: IntegrationSettings
    ): Int {
        var queued = 0
        val startDate = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        clips.take(7).forEachIndexed { idx, clip ->
            val postDay = startDate.plusDays(idx.toLong())
            val iso = "${postDay}T%02d:00:00Z".format(currentSettings.defaultPostHourUtc)
            val publishJob = PublishAutomationJob(
                userId = currentUserId,
                sourceType = "clip",
                sourceId = clip.id.takeIf { it.isNotBlank() },
                youtubeEnabled = currentSettings.defaultAutoYouTube,
                instagramEnabled = currentSettings.defaultAutoInstagram,
                youtubeTitle = clip.youtubeTitle,
                youtubeDescription = ClipMetadataFormatter.buildYoutubeDescriptionFromClip(
                    clip,
                    currentSettings.includeTranscriptInCaptions
                ),
                youtubePrivacy = currentSettings.defaultYoutubePrivacy,
                instagramCaption = ClipMetadataFormatter.buildInstagramCaptionFromClip(clip),
                videoUri = videoUriString,
                scheduledIso = iso,
                status = "scheduled",
                resultMessage = "Auto-queued on clip generation for $iso"
            )
            firestoreRepo.createPublishJob(currentUserId, publishJob).onSuccess { pubId ->
                scheduler.enqueuePublishJob(currentUserId, pubId, iso)
                queued++
            }
        }
        return queued
    }

    suspend fun optimizeMetadataForClip(
        clip: GeneratedClip,
        titleHint: String,
        ytDesc: String,
        igCaption: String
    ): Result<PublishMetadataDraft> {
        return try {
            val draft = aiRepo.generatePublishMetadata(
                titleHint = titleHint.ifBlank { clip.youtubeTitle },
                contextText = igCaption.ifBlank { clip.viralHookText.orEmpty() },
                transcript = ytDesc.ifBlank { clip.subtitleText.orEmpty() },
                language = clip.translatedLanguage ?: "English"
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun automateClipPublish(
        clip: GeneratedClip,
        request: PublishAutomationRequest
    ): String {
        val isScheduled = !request.scheduledIso.isNullOrBlank()
        val publishJob = PublishAutomationJob(
            userId = currentUserId,
            sourceType = "clip",
            sourceId = clip.id.takeIf { it.isNotBlank() },
            youtubeEnabled = request.youtubeEnabled,
            instagramEnabled = request.instagramEnabled,
            youtubeTitle = request.youtubeTitle,
            youtubeDescription = request.youtubeDescription,
            youtubePrivacy = request.youtubePrivacy,
            instagramCaption = request.instagramCaption,
            videoUri = request.videoUri,
            scheduledIso = request.scheduledIso,
            status = if (isScheduled) "scheduled" else "queued",
            resultMessage = if (isScheduled) "Scheduled for ${request.scheduledIso}" else "Queued for background upload"
        )
        return firestoreRepo.createPublishJob(currentUserId, publishJob).fold(
            onSuccess = { pubJobId ->
                scheduler.enqueuePublishJob(currentUserId, pubJobId, request.scheduledIso)
                val channels = buildList {
                    if (request.youtubeEnabled) add("YouTube")
                    if (request.instagramEnabled) add("Instagram")
                }.joinToString(" & ")
                if (isScheduled) {
                    "Scheduled $channels auto-post (${request.scheduledIso})."
                } else {
                    "Automated post started for $channels. Track live status in Queue."
                }
            },
            onFailure = { e -> e.localizedMessage ?: "Failed to queue automation." }
        )
    }

    suspend fun automateWeekQueue(
        clips: List<GeneratedClip>,
        videoUriString: String?,
        youtubeEnabled: Boolean,
        instagramEnabled: Boolean,
        startDayIsoDate: String,
        postTimeHour: Int,
        currentSettings: IntegrationSettings
    ): String {
        val parsedStart = try {
            LocalDate.parse(startDayIsoDate.trim())
        } catch (_: Exception) {
            LocalDate.now(ZoneOffset.UTC).plusDays(1)
        }

        var successCount = 0
        var lastError: String? = null
        clips.take(7).forEachIndexed { idx, clip ->
            val day = parsedStart.plusDays(idx.toLong())
            val iso = "${day}T%02d:00:00Z".format(postTimeHour.coerceIn(0, 23))
            val publishJob = PublishAutomationJob(
                userId = currentUserId,
                sourceType = "clip",
                sourceId = clip.id.takeIf { it.isNotBlank() },
                youtubeEnabled = youtubeEnabled,
                instagramEnabled = instagramEnabled,
                youtubeTitle = clip.youtubeTitle,
                youtubeDescription = ClipMetadataFormatter.buildYoutubeDescriptionFromClip(
                    clip,
                    currentSettings.includeTranscriptInCaptions
                ),
                youtubePrivacy = currentSettings.defaultYoutubePrivacy,
                instagramCaption = ClipMetadataFormatter.buildInstagramCaptionFromClip(clip),
                videoUri = videoUriString,
                scheduledIso = iso,
                status = "scheduled",
                resultMessage = "Day ${idx + 1} of automated week queue ($iso)"
            )
            firestoreRepo.createPublishJob(currentUserId, publishJob).fold(
                onSuccess = { pubId ->
                    scheduler.enqueuePublishJob(currentUserId, pubId, iso)
                    successCount++
                },
                onFailure = { e -> lastError = e.localizedMessage }
            )
        }

        val targetStr = buildList {
            if (youtubeEnabled) add("YouTube")
            if (instagramEnabled) add("Instagram")
        }.joinToString(" & ")

        return if (successCount > 0) {
            "Automated $successCount daily post(s) to $targetStr starting $parsedStart."
        } else {
            lastError ?: "Scheduling failed."
        }
    }

    suspend fun reviewTrimChat(
        videoTitle: String,
        sourceLabel: String,
        totalDurationSec: Double,
        currentStartSec: Double,
        currentEndSec: Double,
        transcriptOrContext: String,
        chatHistory: List<com.example.data.model.TrimChatMessage>,
        userRequest: String
    ): com.example.data.model.TrimChatMessage {
        return try {
            val res = aiRepo.reviewAndSuggestTrim(
                videoTitle = videoTitle,
                sourceLabel = sourceLabel,
                totalDurationSec = totalDurationSec,
                currentStartSec = currentStartSec,
                currentEndSec = currentEndSec,
                transcriptOrContext = transcriptOrContext,
                chatHistory = chatHistory,
                userRequest = userRequest
            )
            com.example.data.model.TrimChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                fromUser = false,
                text = res.assistantMessage,
                suggestedStartSec = if (res.shouldApplyTrim) res.suggestedStartSec else null,
                suggestedEndSec = if (res.shouldApplyTrim) res.suggestedEndSec else null,
                appliedTrim = res.shouldApplyTrim
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DELEGATE_TAG, "Trim chat review failed", e)
            com.example.data.model.TrimChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                fromUser = false,
                text = e.localizedMessage ?: "Could not evaluate trim review. Try dragging handles manually."
            )
        }
    }

    suspend fun runLiveStreamAnalysis(
        liveStreamUrl: String,
        lookbackMinutes: Int,
        skillPreset: com.example.data.model.LiveShortsSkillPreset,
        liveCueNotes: String,
        maxClips: Int
    ): Result<Pair<String, Int>> {
        return try {
            val (durationSec, analysis) = aiRepo.analyzeLiveVideoForShorts(
                liveStreamLabel = liveStreamUrl,
                lookbackMinutes = lookbackMinutes,
                skillPreset = skillPreset,
                liveCueNotes = liveCueNotes,
                maxClips = maxClips
            )
            val job = com.example.data.model.ClipJob(
                userId = currentUserId,
                sourceType = "live",
                sourceLabel = liveStreamUrl,
                videoUri = liveStreamUrl.takeIf { it.contains(".m3u8", ignoreCase = true) },
                durationSec = durationSec,
                status = "completed",
                summary = "[Live Shorts: ${skillPreset.label}] ${analysis.summary}",
                clipsCount = analysis.clips.size.toLong()
            )
            val clips = ClipMetadataFormatter.buildGeneratedClips(currentUserId, analysis)
            firestoreRepo.createClipJobWithClips(currentUserId, job, clips).map { it to clips.size }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Live stream analysis timed out. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DELEGATE_TAG, "Live video shorts failed", e)
            Result.failure(e)
        }
    }

    suspend fun runVideoClipAnalysis(
        sourceType: String,
        sourceLabel: String,
        videoUri: android.net.Uri?,
        clipGuidance: String,
        maxClips: Int,
        currentSettings: IntegrationSettings
    ): Result<Triple<String, Int, Int>> {
        return try {
            val (durationSec, analysis) = aiRepo.analyzeVideoForClips(
                sourceLabel = sourceLabel,
                videoUri = videoUri,
                clipGuidance = clipGuidance,
                maxClips = maxClips
            )
            val job = com.example.data.model.ClipJob(
                userId = currentUserId,
                sourceType = sourceType,
                sourceLabel = sourceLabel,
                videoUri = videoUri?.toString(),
                durationSec = durationSec,
                status = "completed",
                summary = analysis.summary,
                clipsCount = analysis.clips.size.toLong()
            )
            val clips = ClipMetadataFormatter.buildGeneratedClips(currentUserId, analysis)
            firestoreRepo.createClipJobWithClips(currentUserId, job, clips).map { createdJobId ->
                val queuedCount = if (currentSettings.autoPostEnabled &&
                    (currentSettings.defaultAutoYouTube || currentSettings.defaultAutoInstagram)
                ) {
                    autoQueueClips(clips, job.videoUri, currentSettings)
                } else 0
                Triple(createdJobId, clips.size, queuedCount)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(DELEGATE_TAG, "Clip analysis timed out", e)
            Result.failure(Exception("Analysis timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DELEGATE_TAG, "Clip generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveClipTrim(clip: GeneratedClip, startSec: Double, endSec: Double): String =
        firestoreRepo.updateClipTrim(currentUserId, clip.jobId, clip.id, startSec, endSec).fold(
            onSuccess = { "Saved clip trim (${startSec.toInt()}s → ${endSec.toInt()}s)." },
            onFailure = { it.localizedMessage ?: "Failed to save trim." }
        )

    suspend fun saveSubtitles(
        clip: GeneratedClip,
        position: String,
        animation: String,
        font: String,
        colorHex: String,
        highlightHex: String,
        text: String
    ): String = firestoreRepo.updateClipSubtitles(
        currentUserId, clip.jobId, clip.id, position, animation, font, colorHex, highlightHex, text
    ).fold(
        onSuccess = { "Subtitles updated on clip." },
        onFailure = { it.localizedMessage ?: "Failed to update subtitles." }
    )

    suspend fun saveHook(
        clip: GeneratedClip,
        hookText: String,
        position: String,
        size: String,
        animation: String,
        durationSec: Long
    ): String = firestoreRepo.updateClipHook(
        currentUserId, clip.jobId, clip.id, hookText, position, size, animation, durationSec
    ).fold(
        onSuccess = { "Viral hook updated on clip." },
        onFailure = { it.localizedMessage ?: "Failed to save hook." }
    )

    suspend fun translateAndDubClip(clip: GeneratedClip, targetLanguage: String): String {
        return try {
            val translated = aiRepo.translateClipContent(
                targetLanguage = targetLanguage,
                subtitleText = clip.subtitleText.orEmpty(),
                hookText = clip.viralHookText.orEmpty(),
                youtubeTitle = clip.youtubeTitle,
                tiktokCaption = clip.tiktokCaption.orEmpty()
            )
            firestoreRepo.updateClipTranslation(
                currentUserId,
                clip.jobId,
                clip.id,
                translated.translatedLanguage,
                translated.translatedSubtitles,
                translated.translatedHook,
                translated.translatedYoutubeTitle,
                translated.translatedTiktokCaption
            ).fold(
                onSuccess = { "Translated clip into ${translated.translatedLanguage}." },
                onFailure = { it.localizedMessage ?: "Failed to save translation." }
            )
        } catch (e: TimeoutCancellationException) {
            "Translation timed out. Please retry."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DELEGATE_TAG, "Translation failed", e)
            e.localizedMessage ?: "Translation failed."
        }
    }
}
