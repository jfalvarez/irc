package com.jfaf.irc.di

import com.jfaf.irc.domain.text.StringProvider
import com.jfaf.irc.util.AndroidStringProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TextModule {
    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IrcSenderModule {
    @Binds
    abstract fun bindIrcSender(impl: com.jfaf.irc.data.repositories.IrcRepository): com.jfaf.irc.domain.IrcSender

    @Binds
    abstract fun bindIrcConnection(impl: com.jfaf.irc.data.repositories.IrcRepository): com.jfaf.irc.domain.IrcConnection
}
