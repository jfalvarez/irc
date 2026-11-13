package com.jfaf.irc.data.history.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TabEntity::class, MessageEntity::class], version = 1, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}
