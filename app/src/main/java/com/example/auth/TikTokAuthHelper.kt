package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import com.example.data.remote.YouTubeRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class TikTokTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val openId: String,
    val scope: String
)

data class TikTokUserProfile(
    val openId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String
)

/**
 * Shared callback bridge so `MainActivity.onCreate` / `onNewIntent` delivers
 * `planetstreaming://tiktok/callback?code=...&state=...` cleanly to Compose / ViewModel.
 */
object TikTokOAuthCallbackBus {
    private val _pendingCallbackUri = MutableStateFlow<Uri?>(null)
    val pendingCallbackUri: StateFlow<Uri?> = _pendingCallbackUri.asStateFlow()

    fun emitIfTikTokCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme.equals("planetstreaming", ignoreCase = true) &&
            data.host.equals("tiktok", ignoreCase = true)
        ) {
            _pendingCallbackUri.value = data
        }
    }

    fun consume() {
        _pendingCallbackUri.value = null
    }
}

/**
 * TikTok Login Kit v2 (OAuth2 + PKCE S256) client.
 * Credentials are read from [BuildConfig.TIKTOK_CLIENT_KEY] and [BuildConfig.TIKTOK_CLIENT_SECRET]
 * (managed in AI Studio Secrets / `.env`).
 */
class TikTokAuthHelper(
    private val okHttpClient: OkHttpClient = YouTubeRetrofitClient.okHttpClient,
    private val tokenEndpointUrl: String = "https://open.tiktokapis.com/v2/oauth/token/",
    private val userInfoEndpointUrl: String = "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,username,avatar_url"
) {
    companion object {
        const val REDIRECT_URI = "planetstreaming://tiktok/callback"
        const val AUTHORIZE_ENDPOINT = "https://www.tiktok.com/v2/auth/authorize/"
        const val DEFAULT_SCOPES = "user.info.basic,video.upload,video.publish"
        private const val PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun isConfigured(
            clientKey: String = BuildConfig.TIKTOK_CLIENT_KEY,
            clientSecret: String = BuildConfig.TIKTOK_CLIENT_SECRET
        ): Boolean =
            clientKey.isNotBlank() &&
                !clientKey.contains("DEFAULT_VALUE", ignoreCase = true) &&
                clientSecret.isNotBlank() &&
                !clientSecret.contains("DEFAULT_VALUE", ignoreCase = true)

        fun generateCodeVerifier(length: Int = 64): String {
            val rnd = SecureRandom()
            val safeLen = length.coerceIn(43, 128)
            return buildString(safeLen) {
                repeat(safeLen) {
                    append(PKCE_CHARS[rnd.nextInt(PKCE_CHARS.length)])
                }
            }
        }

        fun generateState(): String = generateCodeVerifier(32)

        fun computeCodeChallenge(codeVerifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            return encodeBase64UrlNoPadding(digest)
        }

        fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
            val out = StringBuilder((bytes.size * 4 + 2) / 3)
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
                val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
                out.append(BASE64_URL_ALPHABET[(b0 ushr 2) and 0x3F])
                val c1 = ((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 ushr 4) and 0x0F else 0)
                out.append(BASE64_URL_ALPHABET[c1])
                if (b1 >= 0) {
                    val c2 = ((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 ushr 6) and 0x03 else 0)
                    out.append(BASE64_URL_ALPHABET[c2])
                }
                if (b2 >= 0) {
                    out.append(BASE64_URL_ALPHABET[b2 and 0x3F])
                }
                i += 3
            }
            return out.toString()
        }

        fun buildAuthorizationUrl(
            clientKey: String,
            state: String,
            codeChallenge: String,
            redirectUri: String = REDIRECT_URI,
            scopes: String = DEFAULT_SCOPES
        ): String {
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            return buildString {
                append(AUTHORIZE_ENDPOINT)
                append("?client_key=").append(enc(clientKey))
                append("&scope=").append(enc(scopes))
                append("&response_type=code")
                append("&redirect_uri=").append(enc(redirectUri))
                append("&state=").append(enc(state))
                append("&code_challenge=").append(enc(codeChallenge))
                append("&code_challenge_method=S256")
            }
        }

        fun launchOAuthInBrowser(
            context: Context,
            state: String,
            codeVerifier: String
        ): Result<Unit> {
            val clientKey = BuildConfig.TIKTOK_CLIENT_KEY
            val clientSecret = BuildConfig.TIKTOK_CLIENT_SECRET
            if (!isConfigured(clientKey, clientSecret)) {
                return Result.failure(
                    IllegalStateException(
                        "Configura TIKTOK_CLIENT_KEY e TIKTOK_CLIENT_SECRET nos Secrets do AI Studio para ativar o login OAuth2 do TikTok."
                    )
                )
            }
            val challenge = computeCodeChallenge(codeVerifier)
            val url = buildAuthorizationUrl(
                clientKey = clientKey,
                state = state,
                codeChallenge = challenge
            )
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun exchangeCodeForTokens(
        clientKey: String,
        clientSecret: String,
        code: String,
        codeVerifier: String,
        redirectUri: String = REDIRECT_URI
    ): Result<TikTokTokenResponse> = withContext(Dispatchers.IO) {
        if (code.isBlank() || codeVerifier.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Código OAuth TikTok inválido."))
        }
        try {
            val form = FormBody.Builder()
                .add("client_key", clientKey)
                .add("client_secret", clientSecret)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
                .build()

            val req = Request.Builder()
                .url(tokenEndpointUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(form)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Erro TikTok OAuth (${resp.code}): ${raw.take(180)}")
                    )
                }
                val accessToken = extractJsonString(raw, "access_token")
                if (accessToken.isBlank()) {
                    val errDesc = extractJsonString(raw, "error_description")
                        .ifBlank { extractJsonString(raw, "error") }
                        .ifBlank { raw.take(140) }
                    return@withContext Result.failure(
                        IllegalStateException("TikTok OAuth recusado: $errDesc")
                    )
                }
                Result.success(
                    TikTokTokenResponse(
                        accessToken = accessToken,
                        refreshToken = extractJsonString(raw, "refresh_token"),
                        expiresInSec = extractJsonLong(raw, "expires_in", 86_400L),
                        openId = extractJsonString(raw, "open_id"),
                        scope = extractJsonString(raw, "scope")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(
        clientKey: String,
        clientSecret: String,
        refreshToken: String
    ): Result<TikTokTokenResponse> = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Refresh token TikTok ausente."))
        }
        try {
            val form = FormBody.Builder()
                .add("client_key", clientKey)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val req = Request.Builder()
                .url(tokenEndpointUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(form)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Falha ao renovar sessão TikTok (${resp.code}).")
                    )
                }
                val accessToken = extractJsonString(raw, "access_token")
                if (accessToken.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Sessão TikTok expirada: liga novamente a conta.")
                    )
                }
                Result.success(
                    TikTokTokenResponse(
                        accessToken = accessToken,
                        refreshToken = extractJsonString(raw, "refresh_token").ifBlank { refreshToken },
                        expiresInSec = extractJsonLong(raw, "expires_in", 86_400L),
                        openId = extractJsonString(raw, "open_id"),
                        scope = extractJsonString(raw, "scope")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(accessToken: String): Result<TikTokUserProfile> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token TikTok ausente."))
        }
        try {
            val req = Request.Builder()
                .url(userInfoEndpointUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Erro ao obter perfil TikTok (${resp.code}).")
                    )
                }
                val openId = extractJsonString(raw, "open_id")
                val username = extractJsonString(raw, "username")
                val displayName = extractJsonString(raw, "display_name")
                val avatarUrl = extractJsonString(raw, "avatar_url")
                    .replace("\\/", "/")
                Result.success(
                    TikTokUserProfile(
                        openId = openId,
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJsonString(json: String, field: String): String {
        val regex = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun extractJsonLong(json: String, field: String, defaultVal: Long): Long {
        val regex = Regex(""""$field"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: defaultVal
    }
}
