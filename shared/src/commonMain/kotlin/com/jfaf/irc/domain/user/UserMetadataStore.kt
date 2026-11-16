package com.jfaf.irc.domain.user

import kotlinx.coroutines.flow.Flow

/**
 * Listas del usuario (nicks en minúsculas), guardadas en el dispositivo.
 */
interface UserMetadataStore {
    val ignoredUsersFlow: Flow<Set<String>>
    val friendsFlow: Flow<Set<String>>
    suspend fun addIgnoredUser(nick: String)
    suspend fun removeIgnoredUser(nick: String)
    suspend fun addFriend(nick: String)
    suspend fun removeFriend(nick: String)
}
