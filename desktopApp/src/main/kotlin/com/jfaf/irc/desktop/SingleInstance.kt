package com.jfaf.irc.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Garantiza una sola instancia de la app por carpeta de datos: DataStore y Room son de un solo proceso.
 * El bloqueo se mantiene mientras viva el proceso (el sistema lo libera al terminar, aunque sea por un fallo).
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {

    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        /** Devuelve el bloqueo, o `null` si otra instancia ya lo tiene. */
        fun tryAcquire(dataDir: File): SingleInstance? {
            dataDir.mkdirs()
            val channel = RandomAccessFile(File(dataDir, "app.lock"), "rw").channel
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // el bloqueo ya lo tiene esta misma JVM
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(channel, lock)
        }
    }
}
