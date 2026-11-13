package com.jfaf.irc.di

import com.jfaf.irc.domain.chat.ChatHistoryPersister
import com.jfaf.irc.domain.chat.ChatStateManager
import com.jfaf.irc.domain.chat.command.CommandProcessor
import com.jfaf.irc.domain.usecase.AttemptNickServIdentificationUseCase
import com.jfaf.irc.domain.usecase.CloseTargetUseCase
import com.jfaf.irc.domain.usecase.ConnectUseCase
import com.jfaf.irc.domain.usecase.DisconnectUseCase
import com.jfaf.irc.domain.usecase.HandleIncomingMessageUseCase
import com.jfaf.irc.domain.usecase.IgnoreUserUseCase
import com.jfaf.irc.domain.usecase.JoinChannelUseCase
import com.jfaf.irc.domain.usecase.OpenPrivateMessageUseCase
import com.jfaf.irc.domain.usecase.PartChannelUseCase
import com.jfaf.irc.domain.usecase.PerformWhoisUseCase
import com.jfaf.irc.domain.usecase.SendMessageOrCommandUseCase
import com.jfaf.irc.ui.viewmodels.MainViewModel
import com.jfaf.irc.ui.viewmodels.SettingsViewModel
import com.jfaf.irc.ui.viewmodels.SignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { newMainViewModel() }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { SignInViewModel(get(), get()) }
}

/**
 * Grafo de [MainViewModel]. Sustituye a `@ViewModelScoped`: un único [ChatStateManager] por
 * ViewModel, compartido por el persister, el procesador de comandos y los use cases.
 */
private fun Scope.newMainViewModel(): MainViewModel {
    val chat = ChatStateManager()
    val partChannel = PartChannelUseCase(get(), get(), chat)
    return MainViewModel(
        ircConnection = get(),
        userPreferencesRepository = get(),
        userMetadataStore = get(),
        friendPresence = get(),
        chatStateManager = chat,
        chatHistoryPersister = ChatHistoryPersister(get(), chat),
        connectUseCase = ConnectUseCase(get(), get(), get(), chat),
        attemptNickServIdentificationUseCase = AttemptNickServIdentificationUseCase(get(), get(), get(), chat),
        disconnectUseCase = DisconnectUseCase(get(), get(), chat),
        ignoreUserUseCase = IgnoreUserUseCase(get(), get(), chat, get(), get()),
        performWhoisUseCase = PerformWhoisUseCase(get(), get(), chat, get()),
        joinChannelUseCase = JoinChannelUseCase(get(), get(), chat),
        openPrivateMessageUseCase = OpenPrivateMessageUseCase(get(), chat, get()),
        partChannelUseCase = partChannel,
        closeTargetUseCase = CloseTargetUseCase(get(), chat, partChannel),
        sendMessageOrCommandUseCase = SendMessageOrCommandUseCase(
            get(), get(), chat, CommandProcessor(get(), chat, get())
        ),
        handleIncomingMessageUseCase = HandleIncomingMessageUseCase(get(), chat, get(), get(), get(), get(), get()),
        silentWhoisCompletionSignal = get(),
        strings = get(),
        adProvider = get()
    )
}
