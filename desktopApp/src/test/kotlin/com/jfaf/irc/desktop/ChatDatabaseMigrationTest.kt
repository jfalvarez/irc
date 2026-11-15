package com.jfaf.irc.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.jfaf.irc.data.history.StoredMessage
import com.jfaf.irc.data.history.room.ChatDatabase
import com.jfaf.irc.data.history.room.RoomChatHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La base de datos pasa de la versión 1 a la 2 (hora y tipo de presencia en los mensajes) con una migración automática.
 * Se abre sin `fallbackToDestructiveMigration`: si la migración fallara, el historial no se borraría en silencio
 * (como hace la configuración real) sino que el test fallaría.
 */
class ChatDatabaseMigrationTest {

    /** Una base de datos tal como la dejaba la versión 1 (esquema de `schemas/…/1.json`), con un mensaje guardado. */
    private fun createVersion1Database(file: File) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `tabs` (`target` TEXT NOT NULL, `position` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`target`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `target` TEXT NOT NULL, `position` INTEGER NOT NULL, `fullText` TEXT NOT NULL, `type` TEXT NOT NULL, `sender` TEXT, `isOwnMessage` INTEGER NOT NULL, `mediaUrl` TEXT, `mediaType` TEXT NOT NULL)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_target` ON `messages` (`target`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0947e4f80646144893720df553ab972c')")
            connection.execSQL("INSERT INTO tabs (target, position, isActive) VALUES ('#chat', 0, 1)")
            connection.execSQL("INSERT INTO messages (target, position, fullText, type, sender, isOwnMessage, mediaUrl, mediaType) VALUES ('#chat', 0, '<ana> hola', 'CHANNEL_MSG_RECEIVED', 'ana', 0, NULL, 'NONE')")
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }

    @Test
    fun laMigracionDeLaVersion1ConservaElHistorialYAnadeLasColumnasNuevas() = runBlocking {
        val dir = Files.createTempDirectory("chatdb").toFile()
        val file = File(dir, "chat_history.db")
        try {
            createVersion1Database(file)
            val db = Room.databaseBuilder<ChatDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            try {
                val store = RoomChatHistoryStore(db.chatHistoryDao())
                val snapshot = store.load()
                assertEquals(listOf("#chat"), snapshot.tabs)
                val old = snapshot.messages.getValue("#chat").single()
                assertEquals("<ana> hola", old.fullText)
                assertNull(old.timestamp)
                assertNull(old.presence)

                // Y ya se pueden guardar la hora y la presencia
                store.replaceMessages(
                    "#chat",
                    listOf(StoredMessage("ana entra", "JOIN_PART_QUIT", "ana", false, null, "NONE", 1_700_000_000_000L, "JOIN"))
                )
                val saved = store.load().messages.getValue("#chat").single()
                assertEquals(1_700_000_000_000L, saved.timestamp)
                assertEquals("JOIN", saved.presence)
            } finally {
                db.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
