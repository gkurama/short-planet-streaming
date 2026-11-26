package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.example.data.remote.YouTubeApiConfig
import com.example.data.remote.YouTubeRetrofitClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object YouTubeAuthHelper {
    private const val TAG = "YouTubeAuth"
    const val YOUTUBE_READONLY_SCOPE = YouTubeApiConfig.YOUTUBE_READONLY_SCOPE
    const val YOUTUBE_UPLOAD_SCOPE = YouTubeApiConfig.YOUTUBE_UPLOAD_SCOPE

    @Volatile
    var lastTokenIncludesUploadScope: Boolean = false
        private set

    private fun buildRequest(includeUploadScope: Boolean = true): AuthorizationRequest {
        val scopes = if (includeUploadScope) {
            listOf(Scope(YOUTUBE_READONLY_SCOPE), Scope(YOUTUBE_UPLOAD_SCOPE))
        } else {
            listOf(Scope(YOUTUBE_READONLY_SCOPE))
        }
        return AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()
    }

    /**
     * Silently refreshes an authorized OAuth2 access token in the background.
     * Tries full read+upload scopes first, falling back to readonly if upload consent isn't granted yet.
     */
    suspend fun getSilentAccessToken(
        context: Context,
        includeUploadScope: Boolean = true
    ): String? {
        if (includeUploadScope) {
            try {
                val fullResult = Identity.getAuthorizationClient(context)
                    .authorize(buildRequest(includeUploadScope = true))
                    .await()
                if (!fullResult.hasResolution()) {
                    val token = fullResult.accessToken?.takeIf { it.isNotBlank() }
                    if (!token.isNullOrBlank()) {
                        lastTokenIncludesUploadScope = true
                        YouTubeRetrofitClient.setAccessToken(token)
                        return token
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent read+upload token unavailable, trying readonly: ${e.message}")
            }
        }

        return try {
            val result = Identity.getAuthorizationClient(context)
                .authorize(buildRequest(includeUploadScope = false))
                .await()
            if (!result.hasResolution()) {
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                if (!token.isNullOrBlank()) {
                    lastTokenIncludesUploadScope = false
                    YouTubeRetrofitClient.setAccessToken(token)
                }
                token
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent YouTube authorization not yet granted: ${e.message}")
            null
        }
    }

    /**
     * Authorizes YouTube OAuth2 with Google Identity Services.
     * Requests both read + upload scopes so Shorts can be published directly in background,
     * and automatically falls back to readonly if the upload scope causes an API consent error.
     */
    fun authorizeWithConsent(
        activity: Activity,
        includeUploadScope: Boolean = true,
        onAuthorized: (String) -> Unit,
        onNeedsConsent: (IntentSenderRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(buildRequest(includeUploadScope))
            .addOnSuccessListener { result ->
                val token = result.accessToken
                val pendingIntent = result.pendingIntent
                when {
                    result.hasResolution() && pendingIntent != null -> {
                        onNeedsConsent(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                    !token.isNullOrBlank() -> {
                        lastTokenIncludesUploadScope = includeUploadScope
                        YouTubeRetrofitClient.setAccessToken(token)
                        onAuthorized(token)
                    }
                    else -> {
                        onError("Não foi possível obter o token de acesso OAuth2 do YouTube.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "YouTube authorization failed (upload=$includeUploadScope): ${e.message}")
                if (includeUploadScope) {
                    // Fallback to readonly scope if Google Cloud consent screen hasn't enabled youtube.upload yet
                    authorizeWithConsent(
                        activity = activity,
                        includeUploadScope = false,
                        onAuthorized = onAuthorized,
                        onNeedsConsent = onNeedsConsent,
                        onError = onError
                    )
                } else {
                    val msg = if (e is ApiException) {
                        "Erro na autorização Google/YouTube (código ${e.statusCode}). Podes também usar o @handle do teu canal abaixo."
                    } else {
                        e.localizedMessage ?: "Falha ao autorizar acesso ao YouTube."
                    }
                    onError(msg)
                }
            }
    }

    fun parseAuthorizationResult(context: Context, data: Intent?): Result<String> {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                val grantedScopes: List<String> = result.grantedScopes
                lastTokenIncludesUploadScope = grantedScopes.any {
                    it.contains("youtube.upload", ignoreCase = true) ||
                        it.endsWith("/auth/youtube", ignoreCase = true)
                }
                YouTubeRetrofitClient.setAccessToken(token)
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Permissão do YouTube recusada ou token vazio."))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "YouTube consent result error: ${e.statusCode}", e)
            Result.failure(Exception("Autorização YouTube não concluída (código ${e.statusCode}).", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YouTube consent result", e)
            Result.failure(e)
        }
    }

    suspend fun revokeAccess(token: String?) {
        withContext(Dispatchers.IO) {
            try {
                if (!token.isNullOrBlank()) {
                    YouTubeRetrofitClient.oAuth2Service.revokeToken(token)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to revoke OAuth2 token: ${e.message}")
            } finally {
                lastTokenIncludesUploadScope = false
                YouTubeRetrofitClient.setAccessToken(null)
            }
        }
    }
}
