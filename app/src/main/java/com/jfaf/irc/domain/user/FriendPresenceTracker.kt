package com.jfaf.irc.domain.user

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Amigos vistos recientemente (por WHOIS silencioso) y aviso cuando uno aparece. */
@Singleton
class FriendPresenceTracker(private val now: () -> Long) {

    @Inject constructor() : this({ System.currentTimeMillis() })

    private val lastSeen = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _friendCameOnlineEvent = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val friendCameOnlineEvent: SharedFlow<String> = _friendCameOnlineEvent.asSharedFlow()

    val onlineFriendsFlow: Flow<Set<String>> = lastSeen.map { timestamps ->
        val t = now()
        timestamps.filterValues { (t - it) < ONLINE_WINDOW_MS }.keys
    }.distinctUntilChanged()

    fun friendSeen(nick: String) {
        val previouslyOnline = lastSeen.value.containsKey(nick)
        lastSeen.value = lastSeen.value + (nick to now())
        if (!previouslyOnline) _friendCameOnlineEvent.tryEmit(nick)
    }

    companion object {
        const val ONLINE_WINDOW_MS = 70_000L
    }
}
