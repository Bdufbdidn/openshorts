package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.ConnectedAccount
import com.example.data.model.IntegrationSettings
import com.example.data.model.UiState
import com.example.data.network.ExternalIntegrationsService
import com.example.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsDataStore,
    private val externalService: ExternalIntegrationsService
) : ViewModel() {

    val settings: StateFlow<IntegrationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), IntegrationSettings())

    private val _accountsState = MutableStateFlow<UiState<List<ConnectedAccount>>>(UiState.Idle)
    val accountsState: StateFlow<UiState<List<ConnectedAccount>>> = _accountsState.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun saveUploadPost(apiKey: String, username: String) {
        viewModelScope.launch {
            settingsStore.saveUploadPost(apiKey, username)
            _saveMessage.value = "Upload-Post settings saved."
        }
    }

    fun fetchUploadPostAccounts(apiKey: String) {
        viewModelScope.launch {
            _accountsState.value = UiState.Loading
            val res = externalService.fetchUploadPostUsers(apiKey.trim())
            _accountsState.value = res.fold(
                onSuccess = { list ->
                    if (list.isNotEmpty()) {
                        settingsStore.saveUploadPost(apiKey.trim(), list.first().username)
                    }
                    UiState.Success(list)
                },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to verify Upload-Post API key.") }
            )
        }
    }

    fun saveElevenLabs(apiKey: String) {
        viewModelScope.launch {
            settingsStore.saveElevenLabsKey(apiKey)
            _saveMessage.value = "ElevenLabs key saved."
        }
    }

    fun saveFalAi(apiKey: String) {
        viewModelScope.launch {
            settingsStore.saveFalAiKey(apiKey)
            _saveMessage.value = "fal.ai key saved."
        }
    }

    fun clearMessage() {
        _saveMessage.value = null
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                SettingsViewModel(
                    settingsStore = SettingsDataStore(app),
                    externalService = ExternalIntegrationsService()
                )
            }
        }
    }
}
