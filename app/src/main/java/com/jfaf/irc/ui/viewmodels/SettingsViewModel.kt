package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.repositories.UserMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userMetadataRepository: UserMetadataRepository
) : ViewModel() {

    val showJoinPartQuitMessages: StateFlow<Boolean> =
        userPreferencesRepository.showJoinPartQuitFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Default value
        )

    fun setShowJoinPartQuitMessages(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowJoinPartQuit(show)
        }
    }

    val showNickChanges: StateFlow<Boolean> =
        userPreferencesRepository.showNickChangesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Default value
        )

    fun setShowNickChanges(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowNickChanges(show)
        }
    }

    val showModeChanges: StateFlow<Boolean> =
        userPreferencesRepository.showModeChangesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Default value
        )

    fun setShowModeChanges(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowModeChanges(show)
        }
    }

    val showPingPongMessages: StateFlow<Boolean> =
        userPreferencesRepository.showPingPongMessagesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false // Default value (no mostrar PINGs)
        )

    fun setShowPingPongMessages(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowPingPongMessages(show)
        }
    }

    // --- Preferencia para Previsualizaciones de Medios ---
    val showMediaPreviews: StateFlow<Boolean> =
        userPreferencesRepository.showMediaPreviewsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Coincide con el predeterminado en el repositorio
        )

    fun setShowMediaPreviews(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowMediaPreviews(show)
        }
    }
    // --- Fin Preferencia para Previsualizaciones de Medios ---

    val ignoredUsers: StateFlow<Set<String>> =
        userMetadataRepository.ignoredUsersFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet() // Default to an empty set
        )

    fun addIgnoredUser(nick: String) {
        if (nick.isNotBlank()) {
            viewModelScope.launch {
                userMetadataRepository.addIgnoredUser(nick)
            }
        }
    }

    fun removeIgnoredUser(nick: String) {
        viewModelScope.launch {
            userMetadataRepository.removeIgnoredUser(nick)
        }
    }

    val friends: StateFlow<Set<String>> =
        userMetadataRepository.friendsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun addFriend(nick: String) {
        if (nick.isNotBlank()) {
            viewModelScope.launch {
                userMetadataRepository.addFriend(nick)
            }
        }
    }

    fun removeFriend(nick: String) {
        viewModelScope.launch {
            userMetadataRepository.removeFriend(nick)
        }
    }

    // --- NickServ Password Preference (REMOVED) ---
    // val nickServPassword: StateFlow<String> =
    //     userPreferencesRepository.nickServPasswordFlow.stateIn(
    //         scope = viewModelScope,
    //         started = SharingStarted.WhileSubribed(5000),
    //         initialValue = "" // Default to empty string
    //     )

    // fun setNickServPassword(password: String) {
    //     viewModelScope.launch {
    //         userPreferencesRepository.updateNickServPassword(password)
    //     }
    // }
}
