package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.compose.ui.text.AnnotatedString // Necesario para UiChatMessage
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@ViewModelScoped
class ChatStateManager @Inject constructor() {

    companion object {
        const val SERVER_TARGET_ID = "Servidor" // ID constante para el target del servidor
        private const val MAX_MESSAGES_PER_TARGET = 150
    }

    private val _activeTarget = MutableStateFlow<String?>(null)
    val activeTarget: StateFlow<String?> = _activeTarget.asStateFlow()

    private val _chatTargets = MutableStateFlow<List<String>>(emptyList())
    val chatTargets: StateFlow<List<String>> = _chatTargets.asStateFlow()

    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    val unreadTargets: StateFlow<Set<String>> = _unreadTargets.asStateFlow()

    private val _usersInChannel = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val usersInChannel: StateFlow<Map<String, List<String>>> = _usersInChannel.asStateFlow()

    private val _showUserList = MutableStateFlow(false)
    val showUserList: StateFlow<Boolean> = _showUserList.asStateFlow()

    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())
    val allMessages: StateFlow<Map<String, List<UiChatMessage>>> = _allMessages.asStateFlow()

    private val historyLoadedTargets = mutableSetOf<String>()

    val currentChannelUserListFlow: Flow<List<String>> = combine(
        _activeTarget,
        _usersInChannel
    ) { activeTarget, usersMap ->
        usersMap[activeTarget] ?: emptyList()
    }

    // --- Funciones privadas de ayuda ---
    private fun getSortedUserList(users: List<String>): List<String> {
        return users.sortedWith(
            compareBy<String> {
                when {
                    it.startsWith("@") -> 0
                    it.startsWith("+") -> 1
                    else -> 2
                }
            }.thenBy { it.lowercase() }
        )
    }

    private fun appendMessageToTargetInternal(target: String, message: UiChatMessage) {
        _allMessages.update { currentAllMessages ->
            val currentMessagesForTarget = currentAllMessages[target] ?: emptyList()
            currentAllMessages + (target to (currentMessagesForTarget + message).takeLast(MAX_MESSAGES_PER_TARGET))
        }
    }

    // --- Funciones para añadir mensajes ---
    fun addSystemMessageToTarget(target: String, text: String, isError: Boolean = false) { // Parámetro isError añadido
        val systemMessage = UiChatMessage(
            fullText = text,
            annotatedString = AnnotatedString(text),
            type = UiMessageType.SYSTEM_MESSAGE // De momento, isError no cambia el tipo aquí
        )
        appendMessageToTargetInternal(target, systemMessage)
        Log.d("ChatStateManager", "System message added to '$target' (isError: $isError): $text")
    }

    fun addLocalUiMessageToTarget(target: String, uiMessage: UiChatMessage) {
        val messageToAdd = if (uiMessage.annotatedString == null && uiMessage.type != UiMessageType.SYSTEM_MESSAGE) {
            uiMessage.copy(annotatedString = AnnotatedString(uiMessage.fullText))
        } else {
            uiMessage
        }
        appendMessageToTargetInternal(target, messageToAdd)
        Log.d("ChatStateManager", "Local UI message added to '$target': ${uiMessage.fullText}")
    }

    fun clearMessagesForTarget(target: String) {
        _allMessages.update { currentAllMessages ->
            if (currentAllMessages.containsKey(target)) {
                Log.d("ChatStateManager", "Messages cleared for target: $target")
                currentAllMessages + (target to emptyList())
            } else {
                Log.w("ChatStateManager", "Attempted to clear messages for non-existent target: $target")
                currentAllMessages
            }
        }
    }
    
    fun prependHistoryMessages(target: String, history: List<UiChatMessage>) {
        _allMessages.update { currentAllMessages ->
            val currentMessages = currentAllMessages[target] ?: emptyList()
            val updatedMessages = (history + currentMessages).distinctBy { it.fullText + it.timestamp }.takeLast(MAX_MESSAGES_PER_TARGET)
            currentAllMessages + (target to updatedMessages)
        }
    }
    
    fun checkAndMarkHistoryAsLoaded(target:String): Boolean {
        return if (historyLoadedTargets.contains(target)) {
            true // Ya cargado
        } else {
            historyLoadedTargets.add(target)
            false // No estaba cargado, pero ahora lo está
        }
    }

    fun clearHistoryLoadedTargets() {
        historyLoadedTargets.clear()
    }

    // --- Funciones de gestión de estado existentes ---
    fun toggleUserListVisibility() {
        _showUserList.update { !it }
        Log.d("ChatStateManager", "User list visibility toggled to: ${_showUserList.value}")
    }

    fun setActiveTarget(targetName: String, currentOwnNickname: String) {
        if (_chatTargets.value.any { it.equals(targetName, ignoreCase = true) } || targetName == SERVER_TARGET_ID) {
            _activeTarget.value = targetName
            _unreadTargets.update { it - targetName } // Mark as read
            Log.d("ChatStateManager", "Target '$targetName' activated and marked as read.")
        } else {
            Log.w("ChatStateManager", "Attempt to activate non-existent target: $targetName. Current targets: ${_chatTargets.value.joinToString()}")
            _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        }
        if (_showUserList.value) { // If the list is currently shown, hide it
            _showUserList.value = false
            Log.d("ChatStateManager", "User list hidden due to active target change/selection.")
        }
    }

    fun openPrivateMessageTarget(nick: String, currentOwnNickname: String) {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentOwnNickname, ignoreCase = true)) {
            Log.w("ChatStateManager", "Invalid nick for PM: $nick")
            return
        }
        _chatTargets.update { currentTargets ->
            if (!currentTargets.any { it.equals(nick, ignoreCase = true) }) {
                ensureServerTargetIsFirstLocal((currentTargets + nick).distinct())
            } else {
                currentTargets
            }
        }
        _allMessages.update { currentAll -> // Ensure message list exists
            if (currentAll[nick] == null) currentAll + (nick to emptyList()) else currentAll
        }
        _activeTarget.value = nick
        _unreadTargets.update { it - nick }
        Log.d("ChatStateManager", "PM Target opened/activated: '$nick'")
        if (_showUserList.value) { // If the list is currently shown, hide it
            _showUserList.value = false
            Log.d("ChatStateManager", "User list hidden due to PM target opening.")
        }
    }

    fun ensurePmTargetExists(nick: String, currentOwnNickname: String): Boolean {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentOwnNickname, ignoreCase = true)) {
            Log.w("ChatStateManager", "[ensurePmTargetExists] Invalid nick for PM: $nick")
            return false
        }
        _chatTargets.update { currentTargets ->
            if (!currentTargets.any { it.equals(nick, ignoreCase = true) }) {
                Log.d("ChatStateManager", "[ensurePmTargetExists] PM Target added to chatTargets: '$nick'")
                ensureServerTargetIsFirstLocal((currentTargets + nick).distinct())
            } else {
                currentTargets
            }
        }
        _allMessages.update { currentAll ->
            if (currentAll[nick] == null) {
                Log.d("ChatStateManager", "[ensurePmTargetExists] Message list initialized for PM Target: '$nick'")
                currentAll + (nick to emptyList()) 
            } else currentAll
        }
        return true
    }

    fun addChannelTarget(channelName: String) {
        if (channelName.startsWith("#")) {
            _chatTargets.update { currentTargets ->
                 if (!currentTargets.any { it.equals(channelName, ignoreCase = true) }) {
                    Log.d("ChatStateManager", "Channel target added: $channelName")
                    ensureServerTargetIsFirstLocal((currentTargets + channelName).distinct())
                 } else {
                    currentTargets
                 }
            }
        }
    }

    fun closeTarget(targetName: String, currentActiveTargetFromVM: String?, currentOwnNickname: String): String? {
        if (targetName == SERVER_TARGET_ID) {
            Log.w("ChatStateManager", "Attempt to close SERVER_TARGET_ID.")
            return null
        }

        var newActiveTargetToSuggest: String? = null

        if (!targetName.startsWith("#")) { // Es un PM
            _chatTargets.update { ensureServerTargetIsFirstLocal(it.filterNot { t -> t.equals(targetName, ignoreCase = true) }) }
            _unreadTargets.update { it - targetName }
            historyLoadedTargets.remove(targetName)

            if (currentActiveTargetFromVM?.equals(targetName, ignoreCase = true) == true) {
                val nextTarget = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                _activeTarget.value = nextTarget
                newActiveTargetToSuggest = nextTarget
                _unreadTargets.update { it - nextTarget } // Mark new active as read
                 if (_showUserList.value) { // Also hide user list if active target changed due to closing current PM
                    _showUserList.value = false
                    Log.d("ChatStateManager", "User list hidden due to active target change from closing PM.")
                }
            }
            Log.d("ChatStateManager", "PM target '$targetName' closed. New active: $newActiveTargetToSuggest")
        } else { // Es un canal
            // For channels, closing doesn't automatically change active target unless it WAS the active one.
            // The MainViewModel calls partChannelUseCase, which might then lead to an activeTarget change via server messages if PART is successful.
            // Hiding user list if the channel being closed IS the active one AND the list is shown:
            if (currentActiveTargetFromVM == targetName && _showUserList.value) {
                 _showUserList.value = false
                 Log.d("ChatStateManager", "User list hidden because active channel '$targetName' is being closed.")
            }
             _unreadTargets.update { it - targetName}
             Log.d("ChatStateManager", "Channel target '$targetName' marked as read due to active close initiation.")
        }
        return newActiveTargetToSuggest
    }

    fun updateUsersForChannel(channel: String, users: List<String>) {
        val sortedUsers = getSortedUserList(users)
        _usersInChannel.update { it + (channel to sortedUsers) }
        Log.d("ChatStateManager", "Users updated for channel '$channel': ${sortedUsers.size} users.")
    }

    fun resetStateForConnection() {
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _unreadTargets.value = emptySet()
        _usersInChannel.value = emptyMap()
        _allMessages.value = mapOf(SERVER_TARGET_ID to emptyList()) 
        _showUserList.value = false // Also hide on new connection
        Log.d("ChatStateManager", "State reset for new connection.")
    }

    fun resetStateForDisconnection() {
        clearHistoryLoadedTargets()
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _unreadTargets.value = emptySet()
        _usersInChannel.value = emptyMap()
        val serverMessages = _allMessages.value[SERVER_TARGET_ID]
        _allMessages.value = if (serverMessages != null) {
            mapOf(SERVER_TARGET_ID to serverMessages)
        } else {
            mapOf(SERVER_TARGET_ID to emptyList())
        }
        _showUserList.value = false // Also hide on disconnection
        Log.d("ChatStateManager", "State reset for disconnection, server messages preserved if any.")
    }

    fun updateStateFromHandlerResult(result: ChatUpdateResult, currentOwnNickProvider: () -> String) {
        val oldNick = currentOwnNickProvider()
        val oldActiveTarget = _activeTarget.value

        result.newCurrentNickname?.let {
            if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it) 
            }
        }
        result.newActiveTarget?.let { 
            _activeTarget.value = it 
            if (oldActiveTarget != it && _showUserList.value) { // If active target actually changed AND list was shown
                 _showUserList.value = false
                 Log.d("ChatStateManager", "User list hidden due to active target change from handler result.")
            }
        }
        result.newChatTargets?.let { _chatTargets.value = ensureServerTargetIsFirstLocal(it) }
        result.newUnreadTargets?.let { _unreadTargets.value = it }
        result.newUsersInChannel?.let { newMap ->
            _usersInChannel.value = newMap.mapValues { (_, userList) -> getSortedUserList(userList) }
        }
        result.ownNickChangedTo?.let {
             if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it)
             }
        }

        if (result.newAllMessages != null) {
            _allMessages.value = result.newAllMessages.mapValues { entry -> 
                entry.value.takeLast(MAX_MESSAGES_PER_TARGET) 
            }
            Log.d("ChatStateManager", "Messages updated from newAllMessages in handler result.")
        } else if (result.uiMessageToAdd != null && result.targetForUiMessage != null) {
            appendMessageToTargetInternal(result.targetForUiMessage, result.uiMessageToAdd)
            Log.d("ChatStateManager", "Single message added from uiMessageToAdd in handler result.")
        }

        Log.d("ChatStateManager", "State updated from IrcMessageHandler result. Active: ${_activeTarget.value}, Targets: ${_chatTargets.value.joinToString()}, Unread: ${_unreadTargets.value.joinToString()}, Messages keys: ${_allMessages.value.keys.joinToString()}")
    }

    private fun renameUserInAllChannelsInternal(oldNick: String, newNick: String) {
        _usersInChannel.update { currentUsersInChannel ->
            val updatedMap = currentUsersInChannel.toMutableMap()
            var changed = false
            currentUsersInChannel.forEach { (channel, users) ->
                val newUsersList = users.map { user ->
                    val baseNick = user.removePrefix("@").removePrefix("+")
                    val prefix = user.takeWhile { it == '@' || it == '+' }
                    if (baseNick.equals(oldNick, ignoreCase = true)) {
                        changed = true
                        prefix + newNick
                    } else {
                        user
                    }
                }
                if (newUsersList != users) {
                    updatedMap[channel] = getSortedUserList(newUsersList) 
                }
            }
            if (changed) {
                 Log.d("ChatStateManager", "User '$oldNick' renamed to '$newNick' in channels.")
                 updatedMap
            } else {
                 currentUsersInChannel
            }
        }
    }

    private fun ensureServerTargetIsFirstLocal(targets: List<String>): List<String> {
        val distinctTargets = targets.distinctBy { it.lowercase() }
        val serverTargetPresent = distinctTargets.any { it == SERVER_TARGET_ID }
        val otherTargets = distinctTargets.filterNot { it == SERVER_TARGET_ID }
        return if (serverTargetPresent) {
            listOf(SERVER_TARGET_ID) + otherTargets
        } else {
            listOf(SERVER_TARGET_ID) + otherTargets
        }
    }
}
