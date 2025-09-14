package com.jfaf.irc.ui.viewmodels

import android.util.Log
import androidx.compose.ui.text.AnnotatedString // Necesario para UiChatMessage
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// Asegúrate de que UiChatMessage y UiMessageType estén accesibles o defínelas aquí si es necesario.
// Asumo que están definidas en el mismo paquete o importadas correctamente.

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

    // Gestión de mensajes movida aquí
    private val _allMessages = MutableStateFlow<Map<String, List<UiChatMessage>>>(emptyMap())
    val allMessages: StateFlow<Map<String, List<UiChatMessage>>> = _allMessages.asStateFlow()

    val currentChannelUserListFlow: Flow<List<String>> = combine(
        _activeTarget,
        _usersInChannel
    ) { activeTarget, usersMap ->
        usersMap[activeTarget] ?: emptyList()
    }

    // --- Funciones para añadir mensajes (movidas desde MainViewModel) ---
    fun addSystemMessageToTarget(target: String, text: String) {
        val systemMessage = UiChatMessage(
            fullText = text,
            annotatedString = AnnotatedString(text),
            type = UiMessageType.SYSTEM_MESSAGE
        )
        val currentMessages = _allMessages.value[target] ?: emptyList()
        _allMessages.value = _allMessages.value + (target to (currentMessages + systemMessage).takeLast(MAX_MESSAGES_PER_TARGET))
        Log.d("ChatStateManager", "System message added to '$target': $text")
    }

    fun addLocalUiMessageToTarget(target: String, uiMessage: UiChatMessage) {
        val messageToAdd = if (uiMessage.annotatedString == null && uiMessage.type != UiMessageType.SYSTEM_MESSAGE) {
            // Los mensajes del sistema ya crean su AnnotatedString en addSystemMessageToTarget
            uiMessage.copy(annotatedString = AnnotatedString(uiMessage.fullText))
        } else {
            uiMessage
        }
        val currentMessagesForTarget = _allMessages.value[target] ?: emptyList()
        _allMessages.value = _allMessages.value + (target to (currentMessagesForTarget + messageToAdd).takeLast(MAX_MESSAGES_PER_TARGET))
        Log.d("ChatStateManager", "Local UI message added to '$target': ${uiMessage.fullText}")
    }

    // --- Funciones de gestión de estado existentes (modificadas si es necesario) ---
    fun toggleUserListVisibility() {
        _showUserList.value = !_showUserList.value
        Log.d("ChatStateManager", "User list visibility toggled to: ${_showUserList.value}")
    }

    fun setActiveTarget(targetName: String, currentOwnNickname: String) {
        if (_chatTargets.value.any { it.equals(targetName, ignoreCase = true) } || targetName == SERVER_TARGET_ID) {
            _activeTarget.value = targetName
            if (_unreadTargets.value.contains(targetName)) {
                _unreadTargets.value = _unreadTargets.value - targetName
                Log.d("ChatStateManager", "Target '$targetName' marked as read.")
            }
        } else {
            Log.w("ChatStateManager", "Attempt to activate non-existent target: $targetName. Current targets: ${_chatTargets.value.joinToString()}")
            _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        }
    }

    fun openPrivateMessageTarget(nick: String, currentOwnNickname: String) {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentOwnNickname, ignoreCase = true)) {
            Log.w("ChatStateManager", "Invalid nick for PM: $nick")
            // MainViewModel se encargará del feedback si es necesario, CSM solo actualiza estado.
            return
        }
        if (!_chatTargets.value.any { it.equals(nick, ignoreCase = true) }) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + nick).distinct())
        }
        // Inicializar mensajes para el nuevo target de PM si no existen
        if (_allMessages.value[nick] == null) {
            _allMessages.value = _allMessages.value + (nick to emptyList())
        }
        _activeTarget.value = nick
        if (_unreadTargets.value.contains(nick)) {
            _unreadTargets.value = _unreadTargets.value - nick
        }
        Log.d("ChatStateManager", "PM Target opened/activated: '$nick'")
    }

    fun addChannelTarget(channelName: String) {
        if (channelName.startsWith("#") && !_chatTargets.value.any { it.equals(channelName, ignoreCase = true) }) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + channelName).distinct())
            Log.d("ChatStateManager", "Channel target added: $channelName")
        }
    }

    fun closeTarget(targetName: String, currentActiveTargetFromVM: String?, currentOwnNickname: String): String? {
        if (targetName == SERVER_TARGET_ID) {
            Log.w("ChatStateManager", "Attempt to close SERVER_TARGET_ID.")
            return null
        }

        var newActiveTargetToSuggest: String? = null

        if (!targetName.startsWith("#")) { // Es un PM
            _chatTargets.value = ensureServerTargetIsFirstLocal(_chatTargets.value.filterNot { it.equals(targetName, ignoreCase = true) })
            _unreadTargets.value = _unreadTargets.value - targetName
            _usersInChannel.value = _usersInChannel.value - targetName 
            _allMessages.value = _allMessages.value - targetName 

            if (currentActiveTargetFromVM?.equals(targetName, ignoreCase = true) == true) {
                val nextTarget = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                _activeTarget.value = nextTarget
                newActiveTargetToSuggest = nextTarget
                if (_unreadTargets.value.contains(nextTarget)) {
                    _unreadTargets.value = _unreadTargets.value - nextTarget
                }
            }
            Log.d("ChatStateManager", "PM target '$targetName' closed. New active: $newActiveTargetToSuggest")
        } else { // Es un canal
            if (currentActiveTargetFromVM == targetName && _unreadTargets.value.contains(targetName)){
                 _unreadTargets.value = _unreadTargets.value - targetName
                 Log.d("ChatStateManager", "Channel target '$targetName' marked as read due to active close initiation.")
            }
        }
        return newActiveTargetToSuggest
    }

    fun updateUsersForChannel(channel: String, users: List<String>) {
        val sortedUsers = users.sortedWith(
            compareBy<String> {
                when {
                    it.startsWith("@") -> 0
                    it.startsWith("+") -> 1
                    else -> 2
                }
            }.thenBy { it.lowercase() }
        )
        _usersInChannel.value = _usersInChannel.value + (channel to sortedUsers)
        Log.d("ChatStateManager", "Users updated for channel '$channel': ${sortedUsers.size} users.")
    }

    fun resetStateForConnection() {
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _unreadTargets.value = emptySet()
        _usersInChannel.value = emptyMap()
        _allMessages.value = mapOf(SERVER_TARGET_ID to emptyList()) 
        Log.d("ChatStateManager", "State reset for new connection.")
    }

    fun resetStateForDisconnection() {
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
        Log.d("ChatStateManager", "State reset for disconnection, server messages preserved if any.")
    }

    fun updateStateFromHandlerResult(result: ChatUpdateResult, currentOwnNickProvider: () -> String) {
        val oldNick = currentOwnNickProvider()

        result.newCurrentNickname?.let {
            if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it)
            }
        }
        result.newActiveTarget?.let { _activeTarget.value = it }
        result.newChatTargets?.let { _chatTargets.value = ensureServerTargetIsFirstLocal(it) }
        result.newUnreadTargets?.let { _unreadTargets.value = it }
        result.newUsersInChannel?.let { newMap ->
            val sortedMap = newMap.mapValues { (_, userList) ->
                userList.sortedWith(compareBy<String> {
                    when {
                        it.startsWith("@") -> 0
                        it.startsWith("+") -> 1
                        else -> 2
                    }
                }.thenBy { it.lowercase() })
            }
            _usersInChannel.value = sortedMap
        }
        result.ownNickChangedTo?.let {
             if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it)
             }
        }

        // --- Lógica de actualización de mensajes MODIFICADA ---
        if (result.newAllMessages != null) {
            // Si newAllMessages está presente, es la fuente autoritativa.
            _allMessages.value = result.newAllMessages.mapValues { entry -> 
                entry.value.takeLast(MAX_MESSAGES_PER_TARGET) 
            }
            Log.d("ChatStateManager", "Messages updated from newAllMessages in handler result.")
        } else if (result.uiMessageToAdd != null && result.targetForUiMessage != null) {
            // Si no hay newAllMessages, pero sí un uiMessageToAdd, procesarlo.
            addLocalUiMessageToTarget(result.targetForUiMessage, result.uiMessageToAdd)
            Log.d("ChatStateManager", "Single message added from uiMessageToAdd in handler result.")
        }
        // Si ambos son null, no se hace nada con los mensajes en este paso.

        Log.d("ChatStateManager", "State updated from IrcMessageHandler result. Active: ${_activeTarget.value}, Targets: ${_chatTargets.value.joinToString()}, Unread: ${_unreadTargets.value.joinToString()}, Messages keys: ${_allMessages.value.keys.joinToString()}")
    }

    private fun renameUserInAllChannelsInternal(oldNick: String, newNick: String) {
        val updatedUsersInChannel = _usersInChannel.value.toMutableMap()
        var ownNickAffected = false
        _usersInChannel.value.forEach { (channel, users) ->
            val newUsersList = users.map { user ->
                val baseNick = user.removePrefix("@").removePrefix("+")
                val prefix = user.takeWhile { it == '@' || it == '+' }
                if (baseNick.equals(oldNick, ignoreCase = true)) {
                    if (baseNick == oldNick) ownNickAffected = true
                    prefix + newNick
                } else {
                    user
                }
            }
            if (newUsersList != users) {
                updatedUsersInChannel[channel] = newUsersList.sortedWith(compareBy<String> {
                    when {
                        it.startsWith("@") -> 0
                        it.startsWith("+") -> 1
                        else -> 2
                    }
                }.thenBy { it.lowercase() })
            }
        }
        if (updatedUsersInChannel.keys.isNotEmpty() || ownNickAffected) {
            _usersInChannel.value = updatedUsersInChannel
            Log.d("ChatStateManager", "User '$oldNick' renamed to '$newNick' in channels.")
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
