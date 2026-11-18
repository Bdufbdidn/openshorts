package com.example.data.network

import com.example.data.model.ConnectedAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExternalIntegrationsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
}
