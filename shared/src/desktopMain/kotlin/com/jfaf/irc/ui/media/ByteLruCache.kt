package com.jfaf.irc.ui.media

/**
 * Caché en memoria de bytes por clave, con un tope de tamaño total: al pasarse, descarta lo menos usado.
 * Evita volver a descargar un GIF al pasar de la vista previa a pantalla completa o al desplazar la lista.
 * Segura entre hilos.
 */
internal class ByteLruCache(private val maxBytes: Long) {

    private val entries = LinkedHashMap<String, ByteArray>() // orden de inserción = del menos al más usado
    private var totalBytes = 0L

    @Synchronized
    fun get(key: String): ByteArray? {
        val value = entries.remove(key) ?: return null
        entries[key] = value // pasa a ser el más reciente
        return value
    }

    @Synchronized
    fun put(key: String, value: ByteArray) {
        entries.remove(key)?.let { totalBytes -= it.size }
        if (value.size > maxBytes) return // no cabe ni vacía: no se guarda
        entries[key] = value
        totalBytes += value.size
        val iterator = entries.entries.iterator()
        while (totalBytes > maxBytes && iterator.hasNext()) {
            val oldest = iterator.next()
            totalBytes -= oldest.value.size
            iterator.remove()
        }
    }

    @Synchronized
    fun sizeBytes(): Long = totalBytes
}
