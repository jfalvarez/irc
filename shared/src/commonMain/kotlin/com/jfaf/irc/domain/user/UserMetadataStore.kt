package com.jfaf.irc.domain.user

import kotlinx.coroutines.flow.Flow

/**
 * Listas del usuario (nicks en minúsculas). Android: Firestore por cuenta;
 * escritorio: almacenamiento local (decisión 0.1).
 */
interface UserMetadataStore {
    /**
     * Si ahora mismo se pueden guardar cambios en las listas. Firestore exige sesión iniciada;
     * el almacén local siempre puede.
     */
    val canSave: Boolean
    val ignoredUsersFlow: Flow<Set<String>>
    val friendsFlow: Flow<Set<String>>
    suspend fun addIgnoredUser(nick: String)
    suspend fun removeIgnoredUser(nick: String)
    suspend fun addFriend(nick: String)
    suspend fun removeFriend(nick: String)
}
