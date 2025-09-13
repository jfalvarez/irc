package com.jfaf.irc.ui.viewmodels

import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// Asegúrate de que ChatUpdateResult esté definida correctamente en tu proyecto
// Ejemplo:
// data class ChatUpdateResult(
//     val newCurrentNickname: String? = null,
//     val newActiveTarget: String? = null,
//     val newAllMessages: Map<String, List<UiChatMessage>>? = null, // No usado directamente por ChatStateManager
//     val newChatTargets: List<String>? = null,
//     val newUnreadTargets: Set<String>? = null,
//     val newUsersInChannel: Map<String, List<String>>? = null,
//     val uiMessageToAdd: UiChatMessage? = null, // No usado directamente por ChatStateManager
//     val targetForUiMessage: String? = null, // No usado directamente por ChatStateManager
//     val privateMessageEventNick: String? = null, // No usado directamente por ChatStateManager
//     val ownNickChangedTo: String? = null // Para la lógica de renombrado interno
// )

@ViewModelScoped
class ChatStateManager @Inject constructor() {

    companion object {
        const val SERVER_TARGET_ID = "Servidor" // ID constante para el target del servidor
    }

    private val _activeTarget = MutableStateFlow<String?>(null)
    val activeTarget: StateFlow<String?> = _activeTarget.asStateFlow()

    private val _chatTargets = MutableStateFlow<List<String>>(emptyList())
    val chatTargets: StateFlow<List<String>> = _chatTargets.asStateFlow()

    private val _unreadTargets = MutableStateFlow<Set<String>>(emptySet())
    val unreadTargets: StateFlow<Set<String>> = _unreadTargets.asStateFlow()

    private val _usersInChannel = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val usersInChannel: StateFlow<Map<String, List<String>>> = _usersInChannel.asStateFlow()

    private val _showUserList = MutableStateFlow(false) // Valor inicial por defecto
    val showUserList: StateFlow<Boolean> = _showUserList.asStateFlow()

    val currentChannelUserListFlow: Flow<List<String>> = combine(
        _activeTarget,
        _usersInChannel
    ) { activeTarget, usersMap ->
        usersMap[activeTarget] ?: emptyList()
    }

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
            // Fallback a un target existente o al servidor si no hay targets
            _activeTarget.value = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
        }
    }

    fun openPrivateMessageTarget(nick: String, currentOwnNickname: String) {
        if (nick.isBlank() || nick.startsWith("#") || nick.equals(currentOwnNickname, ignoreCase = true)) {
            Log.w("ChatStateManager", "Invalid nick for PM: $nick")
            return // MainViewModel debe manejar el feedback al usuario
        }
        if (!_chatTargets.value.any { it.equals(nick, ignoreCase = true) }) {
            _chatTargets.value = ensureServerTargetIsFirstLocal((_chatTargets.value + nick).distinct())
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
            return null // MainViewModel maneja el mensaje al usuario
        }

        var newActiveTargetToSuggest: String? = null

        // Si es un PM (no empieza con #), se gestiona aquí directamente.
        // Para canales, MainViewModel inicia PART, y la actualización vendrá vía IrcMessageHandler -> updateStateFromHandlerResult.
        if (!targetName.startsWith("#")) {
            _chatTargets.value = ensureServerTargetIsFirstLocal(_chatTargets.value.filterNot { it.equals(targetName, ignoreCase = true) })
            _unreadTargets.value = _unreadTargets.value - targetName // Quitar de no leídos si estaba
            _usersInChannel.value = _usersInChannel.value - targetName // Quitar usuarios si era un PM (aunque no debería tenerlos)

            if (currentActiveTargetFromVM?.equals(targetName, ignoreCase = true) == true) {
                val nextTarget = _chatTargets.value.firstOrNull() ?: SERVER_TARGET_ID
                _activeTarget.value = nextTarget
                newActiveTargetToSuggest = nextTarget
                // Marcar el nuevo target activo como leído si es necesario
                if (_unreadTargets.value.contains(nextTarget)) {
                    _unreadTargets.value = _unreadTargets.value - nextTarget
                }
            }
            Log.d("ChatStateManager", "PM target '$targetName' closed. New active: $newActiveTargetToSuggest")
        } else {
            // Para canales, solo limpiamos el estado de no leído si estaba activo y se cierra.
            // La eliminación del target de la lista _chatTargets y _usersInChannel se hará en updateStateFromHandlerResult
            // cuando el servidor confirme el PART.
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
                    it.startsWith("@") -> 0 // Ops primero
                    it.startsWith("+") -> 1 // Voice después
                    else -> 2 // Usuarios normales
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
        // _showUserList.value = false; // Considerar si esto debe ser persistente o resetearse
        Log.d("ChatStateManager", "State reset for new connection.")
    }

    fun resetStateForDisconnection() {
        _chatTargets.value = ensureServerTargetIsFirstLocal(listOf(SERVER_TARGET_ID))
        _activeTarget.value = SERVER_TARGET_ID
        _unreadTargets.value = emptySet() // Limpiar no leídos de canales/PMs anteriores
        _usersInChannel.value = emptyMap() // Limpiar lista de usuarios de canales anteriores
        Log.d("ChatStateManager", "State reset for disconnection.")
    }

    fun updateStateFromHandlerResult(result: ChatUpdateResult, currentOwnNickProvider: () -> String) {
        val oldNick = currentOwnNickProvider()

        result.newCurrentNickname?.let {
            if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it)
                // MainViewModel se encarga de actualizar su `currentNickname` property
            }
        }
        result.newActiveTarget?.let { _activeTarget.value = it }
        result.newChatTargets?.let { _chatTargets.value = ensureServerTargetIsFirstLocal(it) }
        result.newUnreadTargets?.let { _unreadTargets.value = it }
        result.newUsersInChannel?.let { newMap ->
            // Asegurar el ordenamiento de usuarios al actualizar desde el handler
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
        // ownNickChangedTo en ChatUpdateResult podría ser usado para simplificar la lógica de renombrado si se prefiere
        result.ownNickChangedTo?.let {
             if (oldNick != it) {
                renameUserInAllChannelsInternal(oldNick, it)
             }
        }

        Log.d("ChatStateManager", "State updated from IrcMessageHandler result.")
    }

    private fun renameUserInAllChannelsInternal(oldNick: String, newNick: String) {
        val updatedUsersInChannel = _usersInChannel.value.toMutableMap()
        var ownNickAffected = false
        _usersInChannel.value.forEach { (channel, users) ->
            val newUsersList = users.map { user ->
                val baseNick = user.removePrefix("@").removePrefix("+")
                val prefix = user.takeWhile { it == '@' || it == '+' }
                if (baseNick.equals(oldNick, ignoreCase = true)) {
                    if (baseNick == oldNick) ownNickAffected = true // Asumiendo que oldNick es el nick propio sin prefijo
                    prefix + newNick
                } else {
                    user
                }
            }
            if (newUsersList != users) { // Solo actualizar si hubo cambios
                updatedUsersInChannel[channel] = newUsersList.sortedWith(compareBy<String> {
                    when {
                        it.startsWith("@") -> 0
                        it.startsWith("+") -> 1
                        else -> 2
                    }
                }.thenBy { it.lowercase() })
            }
        }
        if (updatedUsersInChannel.keys.isNotEmpty() || ownNickAffected) { // Solo actualizar el StateFlow si hubo cambios reales
            _usersInChannel.value = updatedUsersInChannel
            Log.d("ChatStateManager", "User '$oldNick' renamed to '$newNick' in channels.")
        }
    }

    private fun ensureServerTargetIsFirstLocal(targets: List<String>): List<String> {
        val distinctTargets = targets.distinctBy { it.lowercase() } // Evitar duplicados case-insensitive
        val serverTargetPresent = distinctTargets.any { it == SERVER_TARGET_ID }
        val otherTargets = distinctTargets.filterNot { it == SERVER_TARGET_ID }
        return if (serverTargetPresent) {
            listOf(SERVER_TARGET_ID) + otherTargets
        } else {
            // Si por alguna razón SERVER_TARGET_ID no estuviera (ej. lista inicial vacía), añadirlo.
            // Aunque la lógica actual de resetStateForConnection/Disconnection lo asegura.
            listOf(SERVER_TARGET_ID) + otherTargets
        }
    }
}
