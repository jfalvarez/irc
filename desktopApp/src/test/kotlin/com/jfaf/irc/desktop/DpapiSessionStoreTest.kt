package com.jfaf.irc.desktop

import com.jfaf.irc.data.auth.StoredSession
import com.sun.jna.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DpapiSessionStoreTest {

    private val dir: File = Files.createTempDirectory("irc-session-test").toFile()
    private val file = File(dir, SESSION_FILE)
    private val session = StoredSession("u1", "ana@example.com", "refresh-muy-secreto")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun guardaYRecuperaLaSesion() {
        val store = DpapiSessionStore(file)
        assertNull(store.load())
        store.save(session)
        assertEquals(session, DpapiSessionStore(file).load())
        store.clear()
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun enWindows_elArchivoNoContieneElTokenEnClaro() {
        if (!Platform.isWindows()) return
        DpapiSessionStore(file).save(session)
        assertFalse(file.readBytes().decodeToString().contains("refresh-muy-secreto"))
        assertFalse(file.readBytes().decodeToString().contains("ana@example.com"))
    }

    @Test
    fun archivoIlegible_seIgnora() {
        file.writeText("esto no es una sesión")
        assertNull(DpapiSessionStore(file).load())
        assertNull(DpapiSessionStore(file, encrypt = false).load())
    }

    @Test
    fun sinCifrar_tambienFunciona() {
        val store = DpapiSessionStore(file, encrypt = false)
        store.save(session)
        assertEquals(session, store.load())
    }
}
