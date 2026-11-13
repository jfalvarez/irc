package com.jfaf.irc.ui.viewmodels

import com.jfaf.irc.log.Log
import com.jfaf.irc.data.history.ChatHistoryStore
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restaura al arrancar y guarda (con debounce) las pestañas y el historial de [ChatStateManager].
 * No se guarda la pestaña Servidor (el MOTD se acumularía en cada conexión).
 */
@OptIn(FlowPreview::class)
@ViewModelScoped
class ChatHistoryPersister @Inject constructor(
    private val store: ChatHistoryStore,
    private val chatStateManager: ChatStateManager
) {
    private companion object {
        const val TAG = "ChatHistoryPersister"
        const val SAVE_DEBOUNCE_MS = 1000L
    }

    private var lastSaved: Map<String, List<com.jfaf.irc.ui.model.UiChatMessage>> = emptyMap()

    fun start(scope: CoroutineScope) {
        scope.launch {
            // Primero se restaura: si se observara antes, el estado vacío inicial borraría lo guardado.
            try {
                val snapshot = store.load()
                val messages = snapshot.messages.mapValues { (_, list) -> list.map { it.toUi() } }
                chatStateManager.restore(snapshot.tabs, snapshot.activeTarget, messages)
                lastSaved = messages
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo restaurar el historial", e)
            }
            observeMessages(scope)
            observeTabs(scope)
        }
    }

    private fun observeMessages(scope: CoroutineScope) = scope.launch {
        chatStateManager.allMessages.debounce(SAVE_DEBOUNCE_MS).collect { current ->
            val persistable = current.mapValues { (_, list) -> list.filter { it.isPersistable() } }
            val changes = HistoryDiff.compute(lastSaved, persistable, ChatStateManager.SERVER_TARGET_ID)
            if (changes.isEmpty) return@collect
            try {
                changes.delete.forEach { store.deleteTarget(it) }
                changes.replace.forEach { (target, list) -> store.replaceMessages(target, list.map { it.toStored() }) }
                lastSaved = persistable
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo guardar el historial", e)
            }
        }
    }

    private fun observeTabs(scope: CoroutineScope) = scope.launch {
        combine(chatStateManager.chatTargets, chatStateManager.activeTarget) { tabs, active -> tabs to active }
            .distinctUntilChanged()
            .debounce(SAVE_DEBOUNCE_MS / 2)
            .collect { (tabs, active) ->
                try {
                    store.saveTabs(tabs.filter { it != ChatStateManager.SERVER_TARGET_ID }, active)
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudieron guardar las pestañas", e)
                }
            }
    }
}
