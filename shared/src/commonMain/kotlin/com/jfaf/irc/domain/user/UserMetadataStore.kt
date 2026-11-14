package com.jfaf.irc.domain.user

import kotlinx.coroutines.flow.Flow

/**
 * Listas del usuario (nicks en minúsculas). Siempre se guardan en el dispositivo; con sesión,
 * además se sincronizan con la cuenta (`SyncedUserMetadataStore`).
 */
interface UserMetadataStore {
    val ignoredUsersFlow: Flow<Set<String>>
    val friendsFlow: Flow<Set<String>>
    suspend fun addIgnoredUser(nick: String)
    suspend fun removeIgnoredUser(nick: String)
    suspend fun addFriend(nick: String)
    suspend fun removeFriend(nick: String)

    /**
     * Antes de borrar la cuenta [uid]: deja de sincronizar con ella, desvincula las listas del
     * dispositivo (se quedan en él) y borra su copia en la cuenta. false si no se pudo borrar esa
     * copia. Sin sincronización no hay nada que borrar.
     */
    suspend fun deleteAccountData(uid: String): Boolean = true
}
