package com.jfaf.irc.data.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lo que se guarda para no tener que volver a entrar al arrancar. */
@Serializable
data class StoredSession(val uid: String, val email: String?, val refreshToken: String) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** null si el texto no es una sesión válida. */
        fun fromJson(text: String): StoredSession? = runCatching { json.decodeFromString<StoredSession>(text) }.getOrNull()
    }
}

/** Dónde se guarda la sesión. El refresh token da acceso a la cuenta: cada plataforma lo protege como pueda. */
interface SessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}
