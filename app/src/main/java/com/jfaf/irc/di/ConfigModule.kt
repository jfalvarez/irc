package com.jfaf.irc.di

import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.util.BuildConfigAppConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigModule {
    @Binds
    abstract fun bindAppConfig(impl: BuildConfigAppConfig): AppConfig
}
