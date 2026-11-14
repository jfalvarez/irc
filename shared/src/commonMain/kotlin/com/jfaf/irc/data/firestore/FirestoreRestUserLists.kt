package com.jfaf.irc.data.firestore

import com.jfaf.irc.domain.platform.AuthSession
import com.jfaf.irc.domain.user.RemoteLists
import com.jfaf.irc.domain.user.RemoteUserLists
import com.jfaf.irc.domain.user.UserLists
import com.jfaf.irc.log.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Listas de la cuenta en Firestore por su API REST (escritorio, sin SDK), en el mismo documento
 * `users/{uid}` (`ignoredNicks`, `friends`) que usa Android. Por REST no hay escucha en tiempo real:
 * se consulta al iniciar sesión, cada [pollIntervalMillis] y cuando llega algo por [refreshRequests]
 * (p. ej. al volver a la ventana), como mucho una vez cada [minGapMillis]. Las reglas de Firestore se
 * aplican con el idToken de Firebase del usuario ([idToken]).
 * Docs: https://firebase.google.com/docs/firestore/use-rest-api
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRestUserLists(
    private val http: HttpClient,
    private val projectId: String,
    session: AuthSession,
    private val idToken: suspend () -> String?,
    private val now: () -> Long,
    private val refreshRequests: Flow<Unit> = emptyFlow(),
    private val pollIntervalMillis: Long = 5 * 60_000L,
    private val minGapMillis: Long = 30_000L
) : RemoteUserLists {

    // Cada consulta se emite aunque no cambie nada: tras un fallo, la misma respuesta sirve para volver a juntar.
    override val snapshots: Flow<RemoteLists?> = session.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else poll(uid) }

    private fun poll(uid: String): Flow<RemoteLists?> = flow {
        while (true) {
            emit(fetch(uid))
            val fetchedAt = now()
            withTimeoutOrNull(pollIntervalMillis) {
                refreshRequests.first { now() - fetchedAt >= minGapMillis }
            }
        }
    }

    /** Las listas de la cuenta; si el documento no existe, vacías. null si no hay token o falla. */
    private suspend fun fetch(uid: String): RemoteLists? {
        val token = idToken() ?: return null
        return try {
            val response = http.get("$BASE_URL/${documentName(uid)}") { bearerAuth(token) }
            when {
                response.status == HttpStatusCode.NotFound -> RemoteLists(uid, UserLists())
                response.status.isSuccess() -> RemoteLists(uid, parseLists(response.bodyAsText()))
                else -> {
                    Log.w(TAG, "Firestore respondió ${response.status.value} al leer las listas.")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer las listas de Firestore.", e)
            null
        }
    }

    override suspend fun apply(uid: String, add: UserLists, remove: UserLists): Boolean {
        val writes = listOfNotNull(
            write(uid, add, "appendMissingElements"), // = arrayUnion
            write(uid, remove, "removeAllFromArray") // = arrayRemove
        )
        if (writes.isEmpty()) return true
        val token = idToken() ?: return false
        return try {
            val body = buildJsonObject { putJsonArray("writes") { writes.forEach { add(it) } } }
            val response = http.post("$BASE_URL/projects/$projectId/databases/(default)/documents:commit") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                Log.w(TAG, "Firestore respondió ${response.status.value} al guardar las listas.")
            }
            response.status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron guardar las listas en Firestore.", e)
            false
        }
    }

    /**
     * Una escritura que aplica [transform] a los campos con nicks de [lists]. `update` con máscara vacía
     * no toca otros campos y crea el documento si no existe (como `set(…, merge)` en el SDK).
     */
    private fun write(uid: String, lists: UserLists, transform: String): JsonObject? {
        val fields = listOf(IGNORED_FIELD to lists.ignored, FRIENDS_FIELD to lists.friends).filter { it.second.isNotEmpty() }
        if (fields.isEmpty()) return null
        return buildJsonObject {
            putJsonObject("update") {
                put("name", documentName(uid))
                putJsonObject("fields") {}
            }
            putJsonObject("updateMask") { putJsonArray("fieldPaths") {} }
            putJsonArray("updateTransforms") {
                fields.forEach { (field, nicks) ->
                    add(buildJsonObject {
                        put("fieldPath", field)
                        putJsonObject(transform) {
                            putJsonArray("values") { nicks.sorted().forEach { add(buildJsonObject { put("stringValue", it) }) } }
                        }
                    })
                }
            }
        }
    }

    private fun documentName(uid: String) = "projects/$projectId/databases/(default)/documents/users/$uid"

    private companion object {
        const val TAG = "FirestoreRestUserLists"
        const val BASE_URL = "https://firestore.googleapis.com/v1"
        const val IGNORED_FIELD = "ignoredNicks"
        const val FRIENDS_FIELD = "friends"

        fun parseLists(text: String): UserLists {
            val fields = Json.parseToJsonElement(text).jsonObject["fields"]?.jsonObject
            fun strings(name: String): Set<String> =
                fields?.get(name)?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")?.jsonArray
                    ?.mapNotNull { it.jsonObject["stringValue"]?.jsonPrimitive?.content }
                    ?.toSet()
                    .orEmpty()
            return UserLists(strings(IGNORED_FIELD), strings(FRIENDS_FIELD))
        }
    }
}
