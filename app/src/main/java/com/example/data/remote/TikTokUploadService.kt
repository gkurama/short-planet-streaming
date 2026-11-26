package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class TikTokPublishResult(
    val publishId: String,
    val statusNote: String,
    val isDirectPost: Boolean = false
)

/**
 * TikTok Open API v2 Content Posting client.
 * Supports:
 * 1. Direct Post (`video.publish`): `/v2/post/publish/creator_info/query/` + `/v2/post/publish/video/init/`
 * 2. Inbox Draft Upload fallback (`video.upload`): `/v2/post/publish/inbox/video/init/`
 */
class TikTokUploadService(
    private val okHttpClient: OkHttpClient = YouTubeRetrofitClient.okHttpClient,
    private val initEndpointUrl: String = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/",
    private val directPostInitUrl: String = "https://open.tiktokapis.com/v2/post/publish/video/init/",
    private val creatorInfoUrl: String = "https://open.tiktokapis.com/v2/post/publish/creator_info/query/"
) {
    /**
     * Publishes [mp4File] to TikTok in background. Tries Direct Post (`video.publish`) first
     * and seamlessly falls back to Inbox Draft (`video.upload`) if Direct Post isn't audited/authorized.
     */
    suspend fun uploadShortToTikTok(
        accessToken: String,
        mp4File: File,
        title: String,
        preferDirectPost: Boolean = true
    ): Result<TikTokPublishResult> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token TikTok em falta."))
        }
        if (!mp4File.exists() || mp4File.length() < 1024L) {
            return@withContext Result.failure(IllegalArgumentException("Ficheiro MP4 inválido para envio."))
        }

        if (preferDirectPost) {
            val directRes = attemptDirectPost(accessToken, mp4File, title)
            if (directRes.isSuccess) return@withContext directRes
        }

        uploadShortToTikTokInbox(accessToken = accessToken, mp4File = mp4File, title = title)
    }

    private fun attemptDirectPost(
        accessToken: String,
        mp4File: File,
        title: String
    ): Result<TikTokPublishResult> {
        return try {
            val privacyLevel = queryBestPrivacyLevel(accessToken)
            val initFirst = initDirectPost(accessToken, mp4File.length(), title, privacyLevel)
            val initResult = if (initFirst.isFailure && privacyLevel != "SELF_ONLY") {
                // Unaudited TikTok Developer apps require SELF_ONLY until App Review completes
                initDirectPost(accessToken, mp4File.length(), title, "SELF_ONLY")
            } else {
                initFirst
            }

            val (publishId, uploadUrl) = initResult.getOrElse { return Result.failure(it) }
            uploadBytesPut(uploadUrl, mp4File).getOrElse { return Result.failure(it) }

            Result.success(
                TikTokPublishResult(
                    publishId = publishId.ifBlank { title.take(24) },
                    statusNote = "Publicado diretamente no TikTok via API!",
                    isDirectPost = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryBestPrivacyLevel(accessToken: String): String {
        return try {
            val req = Request.Builder()
                .url(creatorInfoUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post("{}".toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                when {
                    !resp.isSuccessful -> "PUBLIC_TO_EVERYONE"
                    raw.contains("PUBLIC_TO_EVERYONE") -> "PUBLIC_TO_EVERYONE"
                    raw.contains("MUTUAL_FOLLOW_FRIENDS") -> "MUTUAL_FOLLOW_FRIENDS"
                    raw.contains("FOLLOWER_OF_CREATOR") -> "FOLLOWER_OF_CREATOR"
                    raw.contains("SELF_ONLY") -> "SELF_ONLY"
                    else -> "PUBLIC_TO_EVERYONE"
                }
            }
        } catch (_: Exception) {
            "PUBLIC_TO_EVERYONE"
        }
    }

    private fun initDirectPost(
        accessToken: String,
        fileSize: Long,
        title: String,
        privacyLevel: String
    ): Result<Pair<String, String>> {
        val safeTitle = escapeJson(title.take(150).ifBlank { "Planet Streaming Short #Shorts" })
        val payload = """
            {
              "post_info": {
                "title": "$safeTitle",
                "privacy_level": "$privacyLevel",
                "disable_duet": false,
                "disable_comment": false,
                "disable_stitch": false
              },
              "source_info": {
                "source": "FILE_UPLOAD",
                "video_size": $fileSize,
                "chunk_size": $fileSize,
                "total_chunk_count": 1
              }
            }
        """.trimIndent()

        val req = Request.Builder()
            .url(directPostInitUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val errCode = extractJsonStringField(raw, "code")
            if (!resp.isSuccessful || (errCode.isNotBlank() && errCode != "ok")) {
                return Result.failure(
                    IllegalStateException("TikTok Direct Post (${resp.code}/$errCode): ${raw.take(140)}")
                )
            }
            val publishId = extractJsonStringField(raw, "publish_id")
            val uploadUrl = extractJsonStringField(raw, "upload_url")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
            if (uploadUrl.isBlank()) {
                return Result.failure(IllegalStateException("TikTok API não devolveu upload_url."))
            }
            return Result.success(publishId to uploadUrl)
        }
    }

    suspend fun uploadShortToTikTokInbox(
        accessToken: String,
        mp4File: File,
        title: String
    ): Result<TikTokPublishResult> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token TikTok em falta."))
        }
        if (!mp4File.exists() || mp4File.length() < 1024L) {
            return@withContext Result.failure(IllegalArgumentException("Ficheiro MP4 inválido para envio."))
        }
        try {
            val fileSize = mp4File.length()
            val initPayload = """{"source_info":{"source":"FILE_UPLOAD","video_size":$fileSize,"chunk_size":$fileSize,"total_chunk_count":1}}"""

            val initReq = Request.Builder()
                .url(initEndpointUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(initPayload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            var publishId = ""
            var uploadUrl = ""
            okHttpClient.newCall(initReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Erro TikTok API (${resp.code}): ${raw.take(180)}")
                    )
                }
                publishId = extractJsonStringField(raw, "publish_id")
                uploadUrl = extractJsonStringField(raw, "upload_url")
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
            }

            if (uploadUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("TikTok API não devolveu URL de envio.")
                )
            }

            uploadBytesPut(uploadUrl, mp4File).getOrElse {
                return@withContext Result.failure(it)
            }

            Result.success(
                TikTokPublishResult(
                    publishId = publishId.ifBlank { title.take(24) },
                    statusNote = "Enviado para a caixa de rascunhos/notificações do TikTok!",
                    isDirectPost = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadBytesPut(uploadUrl: String, mp4File: File): Result<Unit> {
        val bytes = mp4File.readBytes()
        val putReq = Request.Builder()
            .url(uploadUrl)
            .header("Content-Type", "video/mp4")
            .header("Content-Range", "bytes 0-${bytes.size - 1}/${bytes.size}")
            .put(bytes.toRequestBody("video/mp4".toMediaType()))
            .build()

        okHttpClient.newCall(putReq).execute().use { putResp ->
            if (!putResp.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Falha no envio do ficheiro MP4 para TikTok (${putResp.code}).")
                )
            }
        }
        return Result.success(Unit)
    }

    private fun extractJsonStringField(json: String, field: String): String {
        val regex = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
}
