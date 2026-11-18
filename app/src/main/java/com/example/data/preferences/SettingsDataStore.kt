package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    }

    val settingsFlow: Flow<IntegrationSettings> = appContext.settingsDataStore.data.map { prefs ->
        IntegrationSettings(
            uploadPostApiKey = prefs[Keys.UPLOAD_POST_API_KEY].orEmpty(),
            uploadPostUsername = prefs[Keys.UPLOAD_POST_USERNAME].orEmpty(),
            elevenLabsApiKey = prefs[Keys.ELEVENLABS_API_KEY].orEmpty(),
            falAiApiKey = prefs[Keys.FAL_AI_API_KEY].orEmpty()
        )
    }

    suspend fun saveUploadPost(apiKey: String, username: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[Keys.UPLOAD_POST_API_KEY] = apiKey.trim()
            prefs[Keys.UPLOAD_POST_USERNAME] = username.trim()
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
