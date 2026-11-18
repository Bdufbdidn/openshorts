package com.example.ui.clips

import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.data.local.EditingSession
import com.example.data.local.WORKSPACE_CAPTION_EXPORT
import com.example.data.media.Media3VideoFilterFactory
import com.example.data.model.CaptionBurnStyle
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.IntegrationSettings
import com.example.data.model.LiveShortsSkillPreset
import com.example.data.model.TrimChatMessage
import com.example.data.model.UiState
import com.example.data.model.YouTubeSearchResult
import com.example.ui.captions.CaptionExportInitArgs
import com.example.ui.components.PublishAutomationSheet

internal enum class ActiveClipSheet {
    SUBTITLES, HOOK, TRANSLATE, POST, TRIM_CHAT
}

@Composable
internal fun ClipEditorModalHost(
    activeSheet: ActiveClipSheet?,
    currentClip: GeneratedClip?,
    selectedJob: ClipJob?,
    selectedYoutubeVideo: YouTubeSearchResult?,
    trimChatMessages: List<TrimChatMessage>,
    isTrimChatBusy: Boolean,
    isEditorBusy: Boolean,
    settings: IntegrationSettings,
    sourceFilterStack: List<String>,
    sourceFilterContrast: Float,
    sourceFilterSaturation: Float,
    sourceFilterBrightness: Float,
    onFiltersChanged: (List<String>, Float, Float, Float) -> Unit,
    viewModel: ClipGeneratorViewModel,
    onOpenCaptionExport: (CaptionExportInitArgs) -> Unit,
    onOpenYouTubeSearch: (liveOnly: Boolean) -> Unit,
    onDismissSheet: () -> Unit
) {
    if (currentClip == null) return
    when (activeSheet) {
        ActiveClipSheet.TRIM_CHAT -> {
            val jobDuration = selectedJob?.durationSec?.takeIf { it > currentClip.endSec }
                ?: (currentClip.endSec + 30.0).coerceAtLeast(60.0)
            val jobVideoUri = selectedJob?.videoUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            TrimAndChatReviewSheet(
                title = currentClip.youtubeTitle,
                sourceLabel = selectedJob?.sourceLabel.orEmpty(),
                playableVideoUri = jobVideoUri,
                thumbnailUrl = selectedYoutubeVideo?.thumbnailUrl,
                totalDurationSec = jobDuration,
                initialStartSec = currentClip.startSec,
                initialEndSec = currentClip.endSec,
                transcriptOrContext = currentClip.subtitleText ?: currentClip.effectsSummary.orEmpty(),
                isLiveSource = selectedJob?.sourceType == "live",
                chatMessages = trimChatMessages,
                isChatBusy = isTrimChatBusy,
                canSaveToClip = true,
                initialFilterStack = sourceFilterStack,
                initialFilterContrast = sourceFilterContrast,
                initialFilterSaturation = sourceFilterSaturation,
                initialFilterBrightness = sourceFilterBrightness,
                onFiltersChanged = onFiltersChanged,
                onSendChatReview = { msg, sSec, eSec, durSec ->
                    viewModel.sendTrimChatReview(
                        videoTitle = currentClip.youtubeTitle,
                        sourceLabel = selectedJob?.sourceLabel.orEmpty(),
                        totalDurationSec = durSec,
                        currentStartSec = sSec,
                        currentEndSec = eSec,
                        transcriptOrContext = currentClip.subtitleText.orEmpty(),
                        userMessage = msg
                    )
                },
                onSaveTrim = { newStart, newEnd ->
                    viewModel.saveClipTrim(currentClip, newStart, newEnd)
                    onDismissSheet()
                },
                onBurnTrimmedMp4 = { sMs, eMs, filterStack, c, s, b ->
                    onDismissSheet()
                    onOpenCaptionExport(
                        CaptionExportInitArgs(
                            initialVideoUri = selectedJob?.videoUri.orEmpty(),
                            jobId = currentClip.jobId,
                            clipId = currentClip.id,
                            clipStartMs = sMs,
                            clipEndMs = eMs,
                            initialTranscript = currentClip.subtitleText.orEmpty(),
                            initialTitle = currentClip.youtubeTitle,
                            initialHook = currentClip.viralHookText.orEmpty(),
                            subtitlePosition = currentClip.subtitlePosition,
                            subtitleAnimation = currentClip.subtitleAnimation,
                            subtitleFont = currentClip.subtitleFont,
                            subtitleColorHex = currentClip.subtitleColorHex,
                            subtitleHighlightHex = currentClip.subtitleHighlightHex,
                            initialFilterStackCsv = Media3VideoFilterFactory.toCsv(filterStack),
                            filterContrast = c,
                            filterSaturation = s,
                            filterBrightness = b
                        )
                    )
                },
                onAddToExportBatch = if (jobVideoUri != null) { sMs, eMs, filterStack, c, s, b ->
                    viewModel.addTrimmedClipToBatch(
                        title = currentClip.youtubeTitle,
                        videoUriString = jobVideoUri.toString(),
                        trimStartMs = sMs,
                        trimEndMs = eMs,
                        clip = currentClip,
                        filterStack = filterStack,
                        filterContrast = c,
                        filterSaturation = s,
                        filterBrightness = b
                    )
                    onDismissSheet()
                } else null,
                onOpenYouTubeSearch = {
                    onOpenYouTubeSearch(selectedJob?.sourceType == "live")
                },
                onDismiss = onDismissSheet
            )
        }
        ActiveClipSheet.SUBTITLES -> SubtitleEditorSheet(
            clip = currentClip,
            isBusy = isEditorBusy,
            onDismiss = onDismissSheet,
            onSave = { pos, anim, font, colorHex, highlightHex, text ->
                viewModel.saveSubtitles(currentClip, pos, anim, font, colorHex, highlightHex, text)
                onDismissSheet()
            }
        )
        ActiveClipSheet.HOOK -> HookEditorSheet(
            clip = currentClip,
            isBusy = isEditorBusy,
            onDismiss = onDismissSheet,
            onSave = { text, pos, size, anim, dur ->
                viewModel.saveHook(currentClip, text, pos, size, anim, dur)
                onDismissSheet()
            }
        )
        ActiveClipSheet.TRANSLATE -> TranslateDubSheet(
            clip = currentClip,
            isBusy = isEditorBusy,
            onDismiss = onDismissSheet,
            onTranslate = { lang ->
                viewModel.translateAndDubClip(currentClip, lang)
                onDismissSheet()
            }
        )
        ActiveClipSheet.POST -> PublishAutomationSheet(
            sheetTitle = "Auto-Post Clip to YT & IG",
            initialYoutubeTitle = currentClip.youtubeTitle,
            initialYoutubeDescription = viewModel.buildYoutubeDescriptionFromClip(
                currentClip,
                settings.includeTranscriptInCaptions
            ),
            initialInstagramCaption = viewModel.buildInstagramCaptionFromClip(currentClip),
            initialVideoUri = selectedJob?.videoUri,
            settings = settings,
            isBusy = isEditorBusy,
            onDismiss = onDismissSheet,
            onAiOptimize = { title, ytDesc, igCap ->
                viewModel.optimizeMetadataForClip(currentClip, title, ytDesc, igCap)
            },
            onSubmitAutomation = { req ->
                viewModel.automateClipPublish(currentClip, req)
                onDismissSheet()
            }
        )
        null -> Unit
    }
}

@Composable
internal fun ClipUtilityModalsHost(
    showSourceTrimChatSheet: Boolean,
    showYoutubeSearchSheet: Boolean,
    showScheduleWeek: Boolean,
    showSavedSessionsSheet: Boolean,
    sourceMode: String,
    pickedVideoUri: Uri?,
    urlInput: String,
    liveStreamUrlInput: String,
    selectedYoutubeVideo: YouTubeSearchResult?,
    sourceTrimStartMs: Long,
    sourceTrimEndMs: Long,
    urlTrimStartSec: Double,
    urlTrimEndSec: Double,
    guidanceInput: String,
    liveCueNotes: String,
    selectedLiveSkill: LiveShortsSkillPreset,
    liveLookbackMinutes: Int,
    maxClips: Float,
    sourceFilterStack: List<String>,
    sourceFilterContrast: Float,
    sourceFilterSaturation: Float,
    sourceFilterBrightness: Float,
    onFiltersChanged: (List<String>, Float, Float, Float) -> Unit,
    searchLiveOnly: Boolean,
    youtubeSearchState: UiState<List<YouTubeSearchResult>>,
    trimChatMessages: List<TrimChatMessage>,
    isTrimChatBusy: Boolean,
    isEditorBusy: Boolean,
    clips: List<GeneratedClip>,
    selectedJob: ClipJob?,
    selectedJobId: String?,
    editingSessions: List<EditingSession>,
    activeLocalSessionId: String?,
    viewModel: ClipGeneratorViewModel,
    onApplyUrlTrim: (Double, Double) -> Unit,
    onApplySourceTrim: (Long, Long) -> Unit,
    onSelectYoutubeVideo: (YouTubeSearchResult) -> Unit,
    onResumeClipStudioSession: (EditingSession) -> Unit,
    onOpenCaptionExport: (CaptionExportInitArgs) -> Unit,
    onOpenYoutubeSearchFromTrim: (Boolean) -> Unit,
    onDismissSourceTrim: () -> Unit,
    onDismissYoutubeSearch: () -> Unit,
    onDismissScheduleWeek: () -> Unit,
    onDismissSavedSessions: () -> Unit
) {
    if (showSourceTrimChatSheet) {
        val isYtUrl = sourceMode == "url" || sourceMode == "live"
        val durSec = if (isYtUrl) {
            selectedYoutubeVideo?.durationSec ?: 180.0
        } else {
            (sourceTrimEndMs / 1000.0).coerceAtLeast(60.0)
        }
        val playable = when (sourceMode) {
            "video" -> pickedVideoUri
            "live" -> liveStreamUrlInput.trim().takeIf { it.contains(".m3u8", ignoreCase = true) }?.let { Uri.parse(it) }
            else -> null
        }
        TrimAndChatReviewSheet(
            title = selectedYoutubeVideo?.title ?: (pickedVideoUri?.lastPathSegment ?: "Selected Video"),
            sourceLabel = when (sourceMode) {
                "url" -> urlInput
                "live" -> liveStreamUrlInput
                else -> pickedVideoUri?.lastPathSegment.orEmpty()
            },
            playableVideoUri = playable,
            thumbnailUrl = selectedYoutubeVideo?.thumbnailUrl,
            totalDurationSec = durSec,
            initialStartSec = if (isYtUrl) urlTrimStartSec else sourceTrimStartMs / 1000.0,
            initialEndSec = if (isYtUrl) urlTrimEndSec else (sourceTrimEndMs / 1000.0).coerceAtLeast(10.0),
            transcriptOrContext = guidanceInput.ifBlank { liveCueNotes },
            isLiveSource = sourceMode == "live" || (selectedYoutubeVideo?.liveBroadcast == true),
            chatMessages = trimChatMessages,
            isChatBusy = isTrimChatBusy,
            canSaveToClip = false,
            initialFilterStack = sourceFilterStack,
            initialFilterContrast = sourceFilterContrast,
            initialFilterSaturation = sourceFilterSaturation,
            initialFilterBrightness = sourceFilterBrightness,
            onFiltersChanged = onFiltersChanged,
            onSendChatReview = { msg, sSec, eSec, totalDur ->
                viewModel.sendTrimChatReview(
                    videoTitle = selectedYoutubeVideo?.title ?: (pickedVideoUri?.lastPathSegment ?: "Video"),
                    sourceLabel = if (isYtUrl) urlInput.ifBlank { liveStreamUrlInput } else pickedVideoUri?.lastPathSegment.orEmpty(),
                    totalDurationSec = totalDur,
                    currentStartSec = sSec,
                    currentEndSec = eSec,
                    transcriptOrContext = guidanceInput.ifBlank { liveCueNotes },
                    userMessage = msg
                )
            },
            onSaveTrim = { newStart, newEnd ->
                if (isYtUrl) {
                    onApplyUrlTrim(newStart, newEnd)
                } else {
                    onApplySourceTrim((newStart * 1000.0).toLong(), (newEnd * 1000.0).toLong())
                }
                onDismissSourceTrim()
            },
            onBurnTrimmedMp4 = if (pickedVideoUri != null) { sMs, eMs, filterStack, c, s, b ->
                onDismissSourceTrim()
                onOpenCaptionExport(
                    CaptionExportInitArgs(
                        initialVideoUri = pickedVideoUri.toString(),
                        clipStartMs = sMs,
                        clipEndMs = eMs,
                        initialTitle = selectedYoutubeVideo?.title.orEmpty(),
                        initialFilterStackCsv = Media3VideoFilterFactory.toCsv(filterStack),
                        filterContrast = c,
                        filterSaturation = s,
                        filterBrightness = b
                    )
                )
            } else null,
            onAddToExportBatch = if (pickedVideoUri != null) { sMs, eMs, filterStack, c, s, b ->
                viewModel.addTrimmedClipToBatch(
                    title = selectedYoutubeVideo?.title ?: (pickedVideoUri.lastPathSegment ?: "Trimmed Short"),
                    videoUriString = pickedVideoUri.toString(),
                    trimStartMs = sMs,
                    trimEndMs = eMs,
                    filterStack = filterStack,
                    filterContrast = c,
                    filterSaturation = s,
                    filterBrightness = b
                )
                onDismissSourceTrim()
            } else null,
            onOpenYouTubeSearch = { onOpenYoutubeSearchFromTrim(sourceMode == "live") },
            onDismiss = onDismissSourceTrim
        )
    }

    if (showYoutubeSearchSheet) {
        YouTubeSearchSheet(
            searchState = youtubeSearchState,
            initialLiveOnly = searchLiveOnly,
            onSearch = { q, liveOnly -> viewModel.searchYouTube(q, liveOnly) },
            onSelectVideo = onSelectYoutubeVideo,
            onDismiss = onDismissYoutubeSearch
        )
    }

    if (showScheduleWeek) {
        ScheduleWeekSheet(
            clips = clips,
            isBusy = isEditorBusy,
            onDismiss = onDismissScheduleWeek,
            onScheduleWeek = { ytEnabled, igEnabled, date, hour ->
                viewModel.automateWeekQueue(
                    clips = clips,
                    videoUriString = selectedJob?.videoUri,
                    youtubeEnabled = ytEnabled,
                    instagramEnabled = igEnabled,
                    startDayIsoDate = date,
                    postTimeHour = hour
                )
                onDismissScheduleWeek()
            }
        )
    }

    if (showSavedSessionsSheet) {
        val currentLabel = when (sourceMode) {
            "url" -> urlInput.trim()
            "live" -> liveStreamUrlInput.trim()
            else -> pickedVideoUri?.lastPathSegment.orEmpty()
        }
        val titleSuggestion = when {
            selectedYoutubeVideo != null -> selectedYoutubeVideo.title
            currentLabel.isNotBlank() -> currentLabel.take(42)
            selectedJob != null -> selectedJob.sourceLabel.take(42)
            else -> "Clip Studio Project"
        }
        val currentStartMs = if (sourceMode == "url") (urlTrimStartSec * 1000.0).toLong() else sourceTrimStartMs
        val currentEndMs = if (sourceMode == "url") (urlTrimEndSec * 1000.0).toLong() else sourceTrimEndMs
        val totalDurMs = selectedYoutubeVideo?.durationSec?.let { (it * 1000.0).toLong() }
            ?: selectedJob?.durationSec?.let { (it * 1000.0).toLong() }
            ?: currentEndMs.coerceAtLeast(60_000L)

        SavedEditingSessionsSheet(
            sessions = editingSessions,
            currentSessionTitleSuggestion = titleSuggestion,
            activeSessionId = activeLocalSessionId,
            onSaveCurrentSession = { customTitle, saveAsNew ->
                viewModel.saveClipStudioSession(
                    title = customTitle,
                    saveAsNew = saveAsNew,
                    sourceMode = sourceMode,
                    sourceLabel = currentLabel.ifBlank { selectedJob?.sourceLabel.orEmpty() },
                    videoUri = if (sourceMode == "video") {
                        pickedVideoUri?.toString() ?: selectedJob?.videoUri
                    } else if (sourceMode == "live" && liveStreamUrlInput.contains(".m3u8", ignoreCase = true)) {
                        liveStreamUrlInput.trim()
                    } else null,
                    thumbnailUrl = selectedYoutubeVideo?.thumbnailUrl,
                    totalDurationMs = totalDurMs,
                    trimStartMs = currentStartMs,
                    trimEndMs = currentEndMs,
                    guidanceNotes = if (sourceMode == "live") liveCueNotes else guidanceInput,
                    liveSkillId = selectedLiveSkill.id,
                    liveLookbackMinutes = liveLookbackMinutes,
                    targetClipsCount = maxClips.toInt(),
                    activeJobId = selectedJobId,
                    burnStyle = CaptionBurnStyle(
                        filterStack = sourceFilterStack,
                        filterContrast = sourceFilterContrast,
                        filterSaturation = sourceFilterSaturation,
                        filterBrightness = sourceFilterBrightness
                    )
                )
                onDismissSavedSessions()
            },
            onResumeSession = { session ->
                onDismissSavedSessions()
                if (session.workspaceType == WORKSPACE_CAPTION_EXPORT) {
                    onOpenCaptionExport(
                        CaptionExportInitArgs(
                            initialVideoUri = session.videoUri.orEmpty(),
                            clipStartMs = session.trimStartMs,
                            clipEndMs = session.trimEndMs,
                            initialTranscript = session.transcriptText,
                            initialTitle = session.title,
                            initialHook = session.burnStyle.hookText,
                            subtitlePosition = session.burnStyle.position,
                            subtitleAnimation = session.burnStyle.animation,
                            subtitleFont = session.burnStyle.fontName,
                            subtitleColorHex = session.burnStyle.textColorHex,
                            subtitleHighlightHex = session.burnStyle.highlightColorHex,
                            initialFilterStackCsv = Media3VideoFilterFactory.toCsv(session.burnStyle.filterStack),
                            filterContrast = session.burnStyle.filterContrast,
                            filterSaturation = session.burnStyle.filterSaturation,
                            filterBrightness = session.burnStyle.filterBrightness,
                            localSessionId = session.id
                        )
                    )
                } else {
                    onResumeClipStudioSession(session)
                }
            },
            onDeleteSession = { sessionId -> viewModel.deleteLocalSession(sessionId) },
            onDismiss = onDismissSavedSessions
        )
    }
}
