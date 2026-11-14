package com.jfaf.irc.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Firebase Auth por su API REST (Identity Toolkit y Secure Token), con la «Clave de API web» del proyecto.
 * Docs: https://firebase.google.com/docs/reference/rest/auth
 */
class FirebaseRestAuth(
    private val http: HttpClient,
    private val apiKey: String,
    private val now: () -> Long
) : FirebaseAuthApi {

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): FirebaseTokens {
        val request = SignInWithIdpRequest(
            postBody = "id_token=${googleIdToken.encodeURLParameter()}&providerId=google.com",
            requestUri = "http://localhost"
        )
        val response: SignInWithIdpResponse = postJson(SIGN_IN_URL, json.encodeToString(request), refreshing = false)
        return FirebaseTokens(
            uid = response.localId,
            email = response.email,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = expiresAt(response.expiresIn)
        )
    }

    override suspend fun refresh(refreshToken: String, email: String?): FirebaseTokens {
        val request = RefreshRequest(refreshToken = refreshToken)
        val response: RefreshResponse = postJson(REFRESH_URL, json.encodeToString(request), refreshing = true)
        return FirebaseTokens(
            uid = response.userId,
            email = email,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = expiresAt(response.expiresIn)
        )
    }

    private fun expiresAt(expiresIn: String) = now() + (expiresIn.toLongOrNull() ?: 3600L) * 1000

    private suspend inline fun <reified T> postJson(url: String, body: String, refreshing: Boolean): T {
        val response = try {
            http.post("$url?key=${apiKey.encodeURLParameter()}") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FirebaseAuthException("Sin conexión con Firebase Auth", invalidCredentials = false, cause = e)
        }
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            val message = runCatching { json.decodeFromString<ErrorResponse>(text).error.message }.getOrNull()
                ?: response.status.toString()
            // Al renovar, un 400 significa que la sesión ya no vale; al entrar, que el token de Google no sirve.
            throw FirebaseAuthException(
                "Firebase Auth respondió ${response.status.value}: $message",
                invalidCredentials = response.status == HttpStatusCode.BadRequest && refreshing
            )
        }
        return json.decodeFromString(text)
    }

    @Serializable
    private data class SignInWithIdpRequest(
        val postBody: String,
        val requestUri: String,
        val returnSecureToken: Boolean = true,
        val returnIdpCredential: Boolean = false
    )

    @Serializable
    private data class SignInWithIdpResponse(
        val localId: String,
        val email: String? = null,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String = "3600"
    )

    @Serializable
    private data class RefreshRequest(
        @SerialName("grant_type") val grantType: String = "refresh_token",
        @SerialName("refresh_token") val refreshToken: String
    )

    @Serializable
    private data class RefreshResponse(
        @SerialName("user_id") val userId: String,
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: String = "3600"
    )

    @Serializable
    private data class ErrorResponse(val error: ErrorBody)

    @Serializable
    private data class ErrorBody(val message: String = "")

    private companion object {
        const val SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp"
        const val REFRESH_URL = "https://securetoken.googleapis.com/v1/token"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
