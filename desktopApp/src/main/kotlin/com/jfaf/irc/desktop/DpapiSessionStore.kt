package com.jfaf.irc.desktop

import com.jfaf.irc.data.auth.SessionStore
import com.jfaf.irc.data.auth.StoredSession
import com.jfaf.irc.log.Log
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Sesión guardada en [file]. En Windows va cifrada con DPAPI (sólo la puede descifrar el mismo usuario
 * de Windows en este equipo); en Linux y macOS, en claro con permisos sólo para el usuario.
 * Si no se puede leer (archivo dañado, otro usuario u otro equipo), se ignora: hay que volver a entrar.
 */
class DpapiSessionStore(
    private val file: File,
    private val encrypt: Boolean = Platform.isWindows()
) : SessionStore {

    override fun load(): StoredSession? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val plain = if (encrypt) Crypt32Util.cryptUnprotectData(bytes) else bytes
            StoredSession.fromJson(plain.decodeToString())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la sesión guardada: se ignora.", e)
            null
        }
    }

    override fun save(session: StoredSession) {
        val plain = session.toJson().encodeToByteArray()
        val data = if (encrypt) Crypt32Util.cryptProtectData(plain) else plain
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(data)
        if (!encrypt) restrictToOwner(tmp)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun clear() {
        file.delete()
    }

    private fun restrictToOwner(target: File) {
        try {
            Files.setPosixFilePermissions(target.toPath(), PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Sistema de archivos sin permisos POSIX
        }
    }

    private companion object {
        const val TAG = "DpapiSessionStore"
    }
}
