package com.jfaf.irc.di

import com.jfaf.irc.ads.NativeAdManager
import com.jfaf.irc.domain.platform.AdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsModule {
    @Binds
    abstract fun bindAdProvider(impl: NativeAdManager): AdProvider
}
