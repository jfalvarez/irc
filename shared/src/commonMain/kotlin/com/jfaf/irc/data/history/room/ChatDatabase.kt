package com.jfaf.irc.data.history.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [TabEntity::class, MessageEntity::class], version = 1, exportSchema = true)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}

/** Room genera la implementación (`actual`) en cada plataforma con KSP. */
@Suppress("KotlinNoActualForExpect")
expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
