package com.jfaf.irc.domain.user

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendPresenceTrackerTest {
    private var clock = 1_000_000L
    private val tracker = FriendPresenceTracker { clock }

    @Test
    fun primeraVez_emiteEventoYQuedaOnline() = runBlocking {
        val event = async(start = CoroutineStart.UNDISPATCHED) { tracker.friendCameOnlineEvent.first() }
        tracker.friendSeen("ana")
        assertEquals("ana", withTimeout(1000) { event.await() })
        assertEquals(setOf("ana"), tracker.onlineFriendsFlow.first())
    }

    @Test
    fun segundaVez_noEmiteEvento() = runBlocking {
        tracker.friendSeen("ana")
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(200) { tracker.friendCameOnlineEvent.first() }
        }
        tracker.friendSeen("ana")
        assertNull(event.await())
    }

    @Test
    fun pasadaLaVentana_noFiguraOnline() = runBlocking {
        tracker.friendSeen("ana")
        clock += FriendPresenceTracker.ONLINE_WINDOW_MS
        tracker.friendSeen("luis") // fuerza recálculo (mismo comportamiento que hoy)
        assertEquals(setOf("luis"), tracker.onlineFriendsFlow.first())
    }

    @Test
    fun dentroDeLaVentana_sigueOnline() = runBlocking {
        tracker.friendSeen("ana")
        clock += FriendPresenceTracker.ONLINE_WINDOW_MS - 1
        tracker.friendSeen("luis")
        assertEquals(setOf("ana", "luis"), tracker.onlineFriendsFlow.first())
    }
}
