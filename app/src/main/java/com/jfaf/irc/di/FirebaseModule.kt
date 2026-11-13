package com.jfaf.irc.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.jfaf.irc.config.RemoteConfigManager
import com.jfaf.irc.data.firebase.FirebaseAuthSession
import com.jfaf.irc.data.firebase.FirestoreUserMetadataStore
import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.platform.UpdateChecker
import com.jfaf.irc.domain.user.UserMetadataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideAuthSession(impl: FirebaseAuthSession): AuthSession = impl

    @Provides
    @Singleton
    fun provideUserMetadataStore(impl: FirestoreUserMetadataStore): UserMetadataStore = impl

    @Provides
    @Singleton
    fun provideUpdateChecker(impl: RemoteConfigManager): UpdateChecker = impl
}
