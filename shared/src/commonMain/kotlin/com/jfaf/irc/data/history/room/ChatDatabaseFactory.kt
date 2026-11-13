package com.jfaf.irc.data.history.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Nombre del archivo de la base de datos (Android lo resuelve con `getDatabasePath`). */
const val CHAT_DATABASE_NAME = "chat_history.db"

/**
 * Configuración común de [ChatDatabase]. Cada plataforma sólo aporta el builder con la ruta:
 * Android `Room.databaseBuilder(context, ruta)` y escritorio `Room.databaseBuilder(ruta)`.
 * Con [BundledSQLiteDriver] todas usan la misma versión de SQLite.
 */
fun buildChatDatabase(builder: RoomDatabase.Builder<ChatDatabase>): ChatDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
