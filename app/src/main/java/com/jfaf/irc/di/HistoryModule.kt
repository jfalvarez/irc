package com.jfaf.irc.di

import android.content.Context
import androidx.room.Room
import com.jfaf.irc.data.history.ChatHistoryStore
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.history.room.ChatHistoryDao
import com.jfaf.irc.data.history.room.RoomChatHistoryStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryBindsModule {
    @Binds
    @Singleton
    abstract fun bindChatHistoryStore(impl: RoomChatHistoryStore): ChatHistoryStore
}

@Module
@InstallIn(SingletonComponent::class)
object HistoryProvidesModule {

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase =
        Room.databaseBuilder(context, ChatDatabase::class.java, "chat_history.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideChatHistoryDao(db: ChatDatabase): ChatHistoryDao = db.chatHistoryDao()
}
