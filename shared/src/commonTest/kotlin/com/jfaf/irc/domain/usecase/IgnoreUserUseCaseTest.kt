package com.jfaf.irc.domain.usecase

import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.platform.AnalyticsTracker
import com.jfaf.irc.domain.user.UserMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgnoreUserUseCaseTest {

    private val chat = ChatStateManager()
    private val events = mutableListOf<String>()
    private val analytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) { events += name }
    }

    private class FakeStore : UserMetadataStore {
        val ignored = mutableListOf<String>()
        override val ignoredUsersFlow: Flow<Set<String>> = flowOf(emptySet())
        override val friendsFlow: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun addIgnoredUser(nick: String) { ignored += nick }
        override suspend fun removeIgnoredUser(nick: String) {}
        override suspend fun addFriend(nick: String) {}
        override suspend fun removeFriend(nick: String) {}
    }

    private fun serverTexts() = chat.allMessages.value[ChatStateManager.SERVER_TARGET_ID].orEmpty().map { it.fullText }

    @Test
    fun ignora_avisaYRegistraEvento() = runBlocking {
        val store = FakeStore()
        IgnoreUserUseCase(fakeStrings, store, chat, analytics)("pesado")
        assertEquals(listOf("pesado"), store.ignored)
        assertTrue(serverTexts().contains("USER_IGNORED_SYSTEM|pesado"))
        assertEquals(listOf("ignore_user"), events)
    }
}
