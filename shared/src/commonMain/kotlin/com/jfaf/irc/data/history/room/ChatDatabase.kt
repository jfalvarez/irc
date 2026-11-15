package com.jfaf.irc.data.history.room

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [TabEntity::class, MessageEntity::class],
    version = 2, // 2: `timestamp` y `presence` en los mensajes (separadores de fecha y agrupar entradas y salidas)
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}

/** Room genera la implementación (`actual`) en cada plataforma con KSP. */
@Suppress("KotlinNoActualForExpect")
expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
