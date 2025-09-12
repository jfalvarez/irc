package com.jfaf.irc.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
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

    // StateFlow for Ignored Users
    val ignoredUsers: StateFlow<Set<String>> =
        userPreferencesRepository.ignoredUsersFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet() // Default to an empty set
        )

    fun addIgnoredUser(nick: String) {
        if (nick.isNotBlank()) {
            viewModelScope.launch {
                userPreferencesRepository.addIgnoredUser(nick)
            }
        }
    }

    fun removeIgnoredUser(nick: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeIgnoredUser(nick)
        }
    }
}
