package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.IntegrationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "openshorts_settings")

class SettingsDataStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val UPLOAD_POST_API_KEY = stringPreferencesKey("upload_post_api_key")
        val UPLOAD_POST_USERNAME = stringPreferencesKey("upload_post_username")
        val ELEVENLABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        val FAL_AI_API_KEY = stringPreferencesKey("fal_ai_api_key")
        val AUTO_POST_ENABLED = booleanPreferencesKey("auto_post_enabled")
        val DEFAULT_AUTO_YOUTUBE = booleanPreferencesKey("default_auto_youtube")
        val DEFAULT_AUTO_INSTAGRAM = booleanPreferencesKey("default_auto_instagram")
        val DEFAULT_YOUTUBE_PRIVACY = stringPreferencesKey("default_youtube_privacy")
        val DEFAULT_POST_HOUR_UTC = intPreferencesKey("default_post_hour_utc")
        val INCLUDE_TRANSCRIPT_CAPTIONS = booleanPreferencesKey("include_transcript_captions")
        val DIRECT_IG_USER_ID = stringPreferencesKey("direct_ig_user_id")
        val DIRECT_IG_ACCESS_TOKEN = stringPreferencesKey("direct_ig_access_token")
    }

    val settingsFlow: Flow<IntegrationSettings> = appContext.settingsDataStore.data.map { prefs ->
        IntegrationSettings(
            uploadPostApiKey = prefs[Keys.UPLOAD_POST_API_KEY].orEmpty(),
            uploadPostUsername = prefs[Keys.UPLOAD_POST_USERNAME].orEmpty(),
            elevenLabsApiKey = prefs[Keys.ELEVENLABS_API_KEY].orEmpty(),
            falAiApiKey = prefs[Keys.FAL_AI_API_KEY].orEmpty(),
            autoPostEnabled = prefs[Keys.AUTO_POST_ENABLED] ?: false,
            defaultAutoYouTube = prefs[Keys.DEFAULT_AUTO_YOUTUBE] ?: true,
            defaultAutoInstagram = prefs[Keys.DEFAULT_AUTO_INSTAGRAM] ?: true,
            defaultYoutubePrivacy = prefs[Keys.DEFAULT_YOUTUBE_PRIVACY] ?: "public",
            defaultPostHourUtc = (prefs[Keys.DEFAULT_POST_HOUR_UTC] ?: 16).coerceIn(0, 23),
            includeTranscriptInCaptions = prefs[Keys.INCLUDE_TRANSCRIPT_CAPTIONS] ?: true,
            directInstagramUserId = prefs[Keys.DIRECT_IG_USER_ID].orEmpty(),
            directInstagramAccessToken = prefs[Keys.DIRECT_IG_ACCESS_TOKEN].orEmpty()
        )
    }

    suspend fun saveUploadPost(apiKey: String, username: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.UPLOAD_POST_API_KEY] = apiKey.trim()
            prefs[Keys.UPLOAD_POST_USERNAME] = username.trim()
        }
    }

    suspend fun saveAutomationSettings(
        autoPostEnabled: Boolean,
        defaultAutoYouTube: Boolean,
        defaultAutoInstagram: Boolean,
        defaultYoutubePrivacy: String,
        defaultPostHourUtc: Int,
        includeTranscriptInCaptions: Boolean
    ) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_POST_ENABLED] = autoPostEnabled
            prefs[Keys.DEFAULT_AUTO_YOUTUBE] = defaultAutoYouTube
            prefs[Keys.DEFAULT_AUTO_INSTAGRAM] = defaultAutoInstagram
            prefs[Keys.DEFAULT_YOUTUBE_PRIVACY] = if (defaultYoutubePrivacy in listOf("public", "unlisted", "private")) {
                defaultYoutubePrivacy
            } else "public"
            prefs[Keys.DEFAULT_POST_HOUR_UTC] = defaultPostHourUtc.coerceIn(0, 23)
            prefs[Keys.INCLUDE_TRANSCRIPT_CAPTIONS] = includeTranscriptInCaptions
        }
    }

    suspend fun saveDirectInstagram(userId: String, accessToken: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.DIRECT_IG_USER_ID] = userId.trim()
            prefs[Keys.DIRECT_IG_ACCESS_TOKEN] = accessToken.trim()
        }
    }

    suspend fun saveElevenLabsKey(apiKey: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.ELEVENLABS_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveFalAiKey(apiKey: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.FAL_AI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun clearAll() {
        appContext.settingsDataStore.edit { it.clear() }
    }
}
