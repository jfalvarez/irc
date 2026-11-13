package com.jfaf.irc.di

import com.jfaf.irc.data.history.ChatHistoryStore
import com.jfaf.irc.data.history.room.RoomChatHistoryStore
import com.jfaf.irc.data.prefs.UserPreferencesRepository
import com.jfaf.irc.data.remote.IrcSession
import com.jfaf.irc.data.repositories.IrcRepository
import com.jfaf.irc.data.repositories.SilentWhoisCompletionSignal
import com.jfaf.irc.domain.IrcConnection
import com.jfaf.irc.domain.IrcSender
import com.jfaf.irc.domain.chat.IrcMessageHandler
import com.jfaf.irc.domain.notifications.BackgroundNotifications
import com.jfaf.irc.domain.platform.AppVisibility
import com.jfaf.irc.domain.user.FriendPresenceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/** Scope del proceso (antes `@ApplicationScope` de Hilt). */
val ApplicationScope = named("ApplicationScope")

/**
 * Lógica sin Android: sesión IRC, repositorios y coordinadores. Irá a `commonMain` (paso 2.2).
 * Las piezas de plataforma que necesita (textos, config, Firebase, notificador…) las aporta
 * [androidModule] (o el módulo de escritorio en 4.1).
 */
val commonModule = module {
    single(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { IrcSession() }
    single { IrcRepository(get(), get(), get(ApplicationScope)) } binds arrayOf(IrcConnection::class, IrcSender::class)
    single { SilentWhoisCompletionSignal() }
    single { AppVisibility() }
    single { FriendPresenceTracker() }
    single { UserPreferencesRepository(get()) }
    single<ChatHistoryStore> { RoomChatHistoryStore(get()) }

    factory { IrcMessageHandler(get(), get()) }
    factory { BackgroundNotifications(get(), get(), get(), get()) }
}
