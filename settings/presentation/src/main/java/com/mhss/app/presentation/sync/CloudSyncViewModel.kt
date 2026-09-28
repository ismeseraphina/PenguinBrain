package com.mhss.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhss.app.domain.repository.CloudSyncRepository
import com.mhss.app.domain.repository.CloudSyncSummary
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.preferences.domain.use_case.SavePreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed interface CloudSyncStatus {
    data object Idle : CloudSyncStatus
    data object Loading : CloudSyncStatus
    data class SignedIn(val login: String) : CloudSyncStatus
    data class Synced(val summary: CloudSyncSummary) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

@KoinViewModel
class CloudSyncViewModel(
    private val cloudSyncRepository: CloudSyncRepository,
    getPreference: GetPreferenceUseCase,
    private val savePreference: SavePreferenceUseCase
) : ViewModel() {

    val login = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LOGIN), "")
    val owner = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_OWNER), "")
    val repo = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_REPO), "")
    val autoSync = getPreference(booleanPreferencesKey(PrefsConstants.CLOUD_SYNC_AUTO), false)
    val lastSync = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_TIME), "")
    val lastError = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), "")
    val externalNotes = getPreference(booleanPreferencesKey(PrefsConstants.EXTERNAL_NOTES_ENABLED), false)

    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status

    fun signIn(token: String, owner: String, repo: String) {
        if (_status.value == CloudSyncStatus.Loading) return
        _status.update { CloudSyncStatus.Loading }
        viewModelScope.launch {
            try {
                val login = cloudSyncRepository.signIn(token, owner, repo)
                _status.update { CloudSyncStatus.SignedIn(login) }
                // first sync right after signing in
                val summary = cloudSyncRepository.sync()
                _status.update { CloudSyncStatus.Synced(summary) }
            } catch (e: Exception) {
                _status.update { CloudSyncStatus.Error(e.message ?: "Unknown error") }
            }
        }
    }

    fun syncNow() {
        if (_status.value == CloudSyncStatus.Loading) return
        _status.update { CloudSyncStatus.Loading }
        viewModelScope.launch {
            try {
                val summary = cloudSyncRepository.sync()
                _status.update { CloudSyncStatus.Synced(summary) }
            } catch (e: Exception) {
                _status.update { CloudSyncStatus.Error(e.message ?: "Unknown error") }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            savePreference(booleanPreferencesKey(PrefsConstants.CLOUD_SYNC_AUTO), enabled)
            cloudSyncRepository.setAutoSync(enabled)
            if (enabled) cloudSyncRepository.requestBackgroundSync()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cloudSyncRepository.signOut()
            _status.update { CloudSyncStatus.Idle }
        }
    }
}
