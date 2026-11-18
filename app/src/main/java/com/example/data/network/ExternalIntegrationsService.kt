package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.data.model.ConnectedAccount
import com.example.data.model.IntegrationSettings
import com.example.data.model.PublishAutomationJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ExternalIntegrationsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun fetchUploadPostUsers(apiKey: String): Result<List<ConnectedAccount>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Upload-Post API key is empty."))
        }
        try {
            val request = Request.Builder()
                .url("https://api.upload-post.com/api/uploadposts/users")
                .addHeader("Authorization", "Apikey $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Upload-Post API error (${response.code}): ${body.take(180)}")
                    )
                }
                val json = JSONObject(body)
                val profiles = json.optJSONArray("profiles") ?: JSONArray()
                val accounts = buildList {
                    for (i in 0 until profiles.length()) {
                        val item = profiles.optJSONObject(i) ?: continue
                        val username = item.optString("username", "").ifBlank { continue }
                        val socialObj = item.optJSONObject("social_accounts")
                        val platforms = buildList {
                            if (socialObj != null) {
                                val keys = socialObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    val v = socialObj.opt(k)
                                    if (v != null && v != JSONObject.NULL && v.toString().isNotBlank() && v.toString() != "false") {
                                        add(k)
                                    }
                                }
                            }
                        }
                        add(ConnectedAccount(username = username, platforms = platforms))
                    }
                }
                Result.success(accounts)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Automates publishing a short video with YouTube Title + Description/Captions and Instagram Reels Caption.
     * Supports:
     * 1) Upload-Post multi-platform video upload (`POST https://api.upload-post.com/api/upload`) for YouTube + Instagram
     * 2) Direct Meta Instagram Graph API v21.0 Reels publish when Direct IG credentials + public https video URL are supplied
     */
    suspend fun publishShort(
        context: Context,
        settings: IntegrationSettings,
        job: PublishAutomationJob
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetPlatforms = buildList {
            if (job.youtubeEnabled) add("youtube")
            if (job.instagramEnabled) add("instagram")
        }
        if (targetPlatforms.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Select at least YouTube or Instagram."))
        }

        val hasUploadPost = settings.uploadPostApiKey.isNotBlank() && settings.uploadPostUsername.isNotBlank()
        val hasDirectIg = settings.directInstagramUserId.isNotBlank() && settings.directInstagramAccessToken.isNotBlank()

        if (hasUploadPost) {
            return@withContext publishViaUploadPostVideo(
                context = context,
                apiKey = settings.uploadPostApiKey,
                username = settings.uploadPostUsername,
                job = job,
                platforms = targetPlatforms
            )
        }

        if (job.instagramEnabled && !job.youtubeEnabled && hasDirectIg) {
            val videoUrl = job.videoUri.orEmpty()
            if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Direct Instagram Graph API requires a public https:// video URL, or connect Upload-Post in Settings for local device video uploads.")
                )
            }
            return@withContext publishInstagramReelDirect(
                igUserId = settings.directInstagramUserId,
                accessToken = settings.directInstagramAccessToken,
                videoUrl = videoUrl,
                caption = buildString {
                    if (job.youtubeTitle.isNotBlank()) appendLine(job.youtubeTitle)
                    if (job.instagramCaption.isNotBlank()) append(job.instagramCaption)
                }.trim()
            )
        }

        Result.failure(
            IllegalStateException("Connect Upload-Post in Settings (with YouTube & Instagram linked) to automate uploads.")
        )
    }

    private fun publishViaUploadPostVideo(
        context: Context,
        apiKey: String,
        username: String,
        job: PublishAutomationJob,
        platforms: List<String>
    ): Result<String> {
        val videoUriStr = job.videoUri.orEmpty().trim()
        var tempFile: File? = null
        try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user", username)
                .addFormDataPart("title", job.youtubeTitle.ifBlank { "Viral Short #Shorts" })
                .addFormDataPart("youtube_title", job.youtubeTitle.ifBlank { "Viral Short #Shorts" })
                .addFormDataPart("youtube_description", job.youtubeDescription)
                .addFormDataPart("privacyStatus", job.youtubePrivacy)
                .addFormDataPart("instagram_title", job.instagramCaption.ifBlank { job.youtubeTitle })
                .addFormDataPart("description", job.instagramCaption.ifBlank { job.youtubeDescription })

            platforms.forEach { platform ->
                multipart.addFormDataPart("platform[]", platform)
            }
            if ("instagram" in platforms) {
                multipart.addFormDataPart("media_type", "REELS")
            }
            if (!job.scheduledIso.isNullOrBlank()) {
                multipart.addFormDataPart("scheduled_date", job.scheduledIso)
            }

            val endpointUrl: String
            when {
                videoUriStr.startsWith("content://") || videoUriStr.startsWith("file://") -> {
                    val file = copyUriToTempVideoFile(context, Uri.parse(videoUriStr))
                        ?: return Result.failure(IllegalStateException("Could not read attached video file for upload."))
                    tempFile = file
                    multipart.addFormDataPart(
                        name = "video",
                        filename = file.name,
                        body = file.asRequestBody("video/mp4".toMediaType())
                    )
                    endpointUrl = "https://api.upload-post.com/api/upload"
                }
                videoUriStr.startsWith("http://") || videoUriStr.startsWith("https://") -> {
                    multipart.addFormDataPart("video", videoUriStr)
                    endpointUrl = "https://api.upload-post.com/api/upload"
                }
                else -> {
                    return Result.failure(
                        IllegalArgumentException("Attach a Short video file or video URL before posting to YouTube / Instagram.")
                    )
                }
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Apikey $apiKey")
                .post(multipart.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("Upload-Post returned ${response.code}: ${body.take(220)}")
                    )
                }
                val actionWord = if (job.scheduledIso.isNullOrBlank()) "Published" else "Scheduled (${job.scheduledIso})"
                val channelList = platforms.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } }
                return Result.success("$actionWord to $channelList for @$username")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            try {
                if (tempFile != null && tempFile.parentFile == context.cacheDir) {
                    tempFile.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun publishInstagramReelDirect(
        igUserId: String,
        accessToken: String,
        videoUrl: String,
        caption: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val createBody = FormBody.Builder()
                .add("media_type", "REELS")
                .add("video_url", videoUrl)
                .add("caption", caption.take(2200))
                .add("share_to_feed", "true")
                .add("access_token", accessToken)
                .build()

            val createRequest = Request.Builder()
                .url("https://graph.facebook.com/v21.0/$igUserId/media")
                .post(createBody)
                .build()

            val creationId = client.newCall(createRequest).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Instagram Graph error (${resp.code}): ${text.take(200)}")
                    )
                }
                JSONObject(text).optString("id").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(IllegalStateException("Instagram did not return a media container ID."))
            }

            // Poll container readiness up to 30 seconds
            var ready = false
            repeat(10) {
                delay(3000L)
                val statusReq = Request.Builder()
                    .url("https://graph.facebook.com/v21.0/$creationId?fields=status_code&access_token=$accessToken")
                    .get()
                    .build()
                client.newCall(statusReq).execute().use { resp ->
                    val statusText = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val code = JSONObject(statusText).optString("status_code")
                        if (code == "FINISHED") ready = true
                        if (code == "ERROR") {
                            return@withContext Result.failure(IllegalStateException("Instagram container processing failed."))
                        }
                    }
                }
                if (ready) return@repeat
            }

            val publishBody = FormBody.Builder()
                .add("creation_id", creationId)
                .add("access_token", accessToken)
                .build()

            val publishReq = Request.Builder()
                .url("https://graph.facebook.com/v21.0/$igUserId/media_publish")
                .post(publishBody)
                .build()

            client.newCall(publishReq).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Instagram publish failed (${resp.code}): ${text.take(200)}")
                    )
                }
                val publishedId = JSONObject(text).optString("id", creationId)
                Result.success("Published Instagram Reel (ID: $publishedId)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scheduleUploadPostTextOrStatus(
        apiKey: String,
        username: String,
        title: String,
        platforms: List<String>,
        scheduledIsoDate: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || username.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Configure Upload-Post API Key & Account in Settings first."))
        }
        try {
            val payload = JSONObject().apply {
                put("user", username)
                put("title", title)
                put("platform", JSONArray(platforms))
                if (!scheduledIsoDate.isNullOrBlank()) {
                    put("scheduled_date", scheduledIsoDate)
                }
            }
            val request = Request.Builder()
                .url("https://api.upload-post.com/api/upload_text")
                .addHeader("Authorization", "Apikey $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Upload-Post returned ${response.code}: ${body.take(180)}")
                    )
                }
                Result.success("Published/scheduled via Upload-Post for $username")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyUriToTempVideoFile(context: Context, uri: Uri): File? {
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val src = File(uri.path!!)
                if (src.exists()) return src
            }
            val outFile = File(context.cacheDir, "upload_short_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null
            if (outFile.length() > 0L) outFile else null
        } catch (_: Exception) {
            null
        }
    }
}
