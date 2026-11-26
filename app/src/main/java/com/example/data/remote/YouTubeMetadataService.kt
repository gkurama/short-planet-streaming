package com.example.data.remote

import android.util.Log
import android.util.Xml
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import retrofit2.HttpException
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

private fun jsonEscape(value: String): String = buildString(value.length + 8) {
    append('"')
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

private const val TAG = "YouTubeMetadataService"

// --- Domain Models ---

data class ResolvedYouTubeMetadata(
    val normalizedUrl: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val detectedType: String,
    val durationLabel: String = "15:00",
    val description: String = ""
)

data class VideoProcessingUrls(
    val videoId: String,
    val watchUrl: String,
    val shortUrl: String,
    val embedUrl: String,
    val thumbnailUrl: String,
    val title: String,
    val channelName: String,
    val durationLabel: String,
    val isLive: Boolean,
    val isEmbeddable: Boolean,
    val privacyStatus: String
)

data class YouTubeChannelSummary(
    val channelId: String,
    val title: String,
    val handle: String,
    val thumbnailUrl: String,
    val uploadsPlaylistId: String,
    val subscriberCountLabel: String,
    val videoCountLabel: String
)

data class YouTubeLibraryVideo(
    val videoId: String,
    val url: String,
    val title: String,
    val description: String,
    val channelName: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val durationLabel: String,
    val sourceType: String,
    val viewCountLabel: String,
    val embedUrl: String = "https://www.youtube.com/embed/$videoId",
    val isEmbeddable: Boolean = true,
    val privacyStatus: String = "public"
)

data class YouTubeLibraryPage(
    val channel: YouTubeChannelSummary,
    val videos: List<YouTubeLibraryVideo>,
    val nextPageToken: String?
)

class YouTubeUnauthorizedException(message: String) : Exception(message)
class YouTubeApiDisabledException(message: String) : Exception(message)

class YouTubeMetadataService(
    private val dataApi: YouTubeService = YouTubeRetrofitClient.youTubeService,
    private val oAuth2Api: GoogleOAuth2Service = YouTubeRetrofitClient.oAuth2Service,
    private val oEmbedApi: YouTubeOEmbedApi = YouTubeRetrofitClient.oEmbedApi,
    private val okHttpClient: OkHttpClient = YouTubeRetrofitClient.okHttpClient
) {
    private val renderUploadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.MINUTES)
            .readTimeout(100, TimeUnit.MINUTES)
            .build()
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))) {
            return trimmed
        }
        val patterns = listOf(
            Regex("(?:youtube\\.com/watch\\?.*v=)([a-zA-Z0-9_\\-]{11})"),
            Regex("(?:youtu\\.be/)([a-zA-Z0-9_\\-]{11})"),
            Regex("(?:youtube\\.com/live/)([a-zA-Z0-9_\\-]{11})"),
            Regex("(?:youtube\\.com/shorts/)([a-zA-Z0-9_\\-]{11})"),
            Regex("(?:youtube\\.com/embed/)([a-zA-Z0-9_\\-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    suspend fun verifyOAuthToken(accessToken: String): Result<OAuth2TokenInfoResponse> =
        withContext(Dispatchers.IO) {
            try {
                val info = oAuth2Api.verifyAccessToken(accessToken)
                Result.success(info)
            } catch (e: Exception) {
                Log.w(TAG, "OAuth2 tokeninfo check failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun revokeOAuthToken(accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                oAuth2Api.revokeToken(accessToken)
                YouTubeRetrofitClient.setAccessToken(null)
                Result.success(Unit)
            } catch (e: Exception) {
                YouTubeRetrofitClient.setAccessToken(null)
                Log.w(TAG, "OAuth2 token revocation failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun fetchMyYouTubeLibrary(
        accessToken: String,
        pageToken: String? = null,
        existingChannel: YouTubeChannelSummary? = null
    ): Result<YouTubeLibraryPage> = withContext(Dispatchers.IO) {
        YouTubeRetrofitClient.setAccessToken(accessToken)
        val bearer = "Bearer $accessToken"
        try {
            val channelSummary = existingChannel ?: run {
                val channelResp = dataApi.getMyChannels(authorization = bearer)
                val ch = channelResp.items.firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Nenhum canal do YouTube encontrado nesta conta Google.")
                    )
                YouTubeChannelSummary(
                    channelId = ch.id,
                    title = ch.snippet?.title?.takeIf { it.isNotBlank() } ?: "Meu Canal YouTube",
                    handle = ch.snippet?.customUrl.orEmpty(),
                    thumbnailUrl = ch.snippet?.thumbnails?.bestUrl().orEmpty(),
                    uploadsPlaylistId = ch.contentDetails?.relatedPlaylists?.uploads.orEmpty(),
                    subscriberCountLabel = formatCompactCount(ch.statistics?.subscriberCount),
                    videoCountLabel = formatCompactCount(ch.statistics?.videoCount)
                )
            }

            // Use uploads playlist first; fall back to search(forMine=true) if uploads playlist ID is unavailable
            val playlistItems: List<YtPlaylistItem>
            val nextToken: String?
            val videoIds: List<String>

            if (channelSummary.uploadsPlaylistId.isNotBlank()) {
                val playlistResp = dataApi.getPlaylistItems(
                    authorization = bearer,
                    playlistId = channelSummary.uploadsPlaylistId,
                    pageToken = pageToken
                )
                playlistItems = playlistResp.items
                nextToken = playlistResp.nextPageToken
                videoIds = playlistItems.mapNotNull { item ->
                    item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId
                }.distinct()
            } else {
                val searchResp = dataApi.searchMyVideos(
                    authorization = bearer,
                    pageToken = pageToken
                )
                playlistItems = emptyList()
                nextToken = searchResp.nextPageToken
                videoIds = searchResp.items.mapNotNull { it.id?.videoId }.distinct()
            }

            val detailsById = if (videoIds.isNotEmpty()) {
                dataApi.getVideosByIds(
                    authorization = bearer,
                    ids = videoIds.joinToString(",")
                ).items.associateBy { it.id }
            } else {
                emptyMap()
            }

            val videos = if (playlistItems.isNotEmpty()) {
                playlistItems.mapNotNull { item ->
                    val vidId = item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId
                        ?: return@mapNotNull null
                    val detail = detailsById[vidId]
                    val snippet = detail?.snippet
                    val title = snippet?.title ?: item.snippet?.title ?: "Vídeo ($vidId)"
                    if (title == "Deleted video") return@mapNotNull null
                    val isLive = detail?.liveStreamingDetails != null ||
                        snippet?.liveBroadcastContent in listOf("live", "upcoming")
                    val durationLabel = if (snippet?.liveBroadcastContent == "live") {
                        "AO VIVO"
                    } else {
                        parseIsoDuration(detail?.contentDetails?.duration).ifBlank { "—" }
                    }
                    val thumb = snippet?.thumbnails?.bestUrl()
                        ?: item.snippet?.thumbnails?.bestUrl()
                        ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
                    val embedUrl = extractEmbedUrlFromHtml(detail?.player?.embedHtml, vidId)
                    val privacy = detail?.status?.privacyStatus
                        ?: item.status?.privacyStatus
                        ?: "public"
                    val embeddable = detail?.status?.embeddable ?: true

                    YouTubeLibraryVideo(
                        videoId = vidId,
                        url = "https://www.youtube.com/watch?v=$vidId",
                        title = title,
                        description = (snippet?.description ?: item.snippet?.description).orEmpty(),
                        channelName = snippet?.channelTitle ?: channelSummary.title,
                        thumbnailUrl = thumb,
                        publishedAt = (item.contentDetails?.videoPublishedAt ?: snippet?.publishedAt).orEmpty(),
                        durationLabel = durationLabel,
                        sourceType = if (isLive) "LIVE" else "VIDEO",
                        viewCountLabel = formatCompactCount(detail?.statistics?.viewCount),
                        embedUrl = embedUrl,
                        isEmbeddable = embeddable,
                        privacyStatus = privacy
                    )
                }
            } else {
                videoIds.mapNotNull { vidId ->
                    val detail = detailsById[vidId] ?: return@mapNotNull null
                    val snippet = detail.snippet
                    val title = snippet?.title ?: "Vídeo ($vidId)"
                    val isLive = detail.liveStreamingDetails != null ||
                        snippet?.liveBroadcastContent in listOf("live", "upcoming")
                    val durationLabel = if (snippet?.liveBroadcastContent == "live") {
                        "AO VIVO"
                    } else {
                        parseIsoDuration(detail.contentDetails?.duration).ifBlank { "—" }
                    }
                    YouTubeLibraryVideo(
                        videoId = vidId,
                        url = "https://www.youtube.com/watch?v=$vidId",
                        title = title,
                        description = snippet?.description.orEmpty(),
                        channelName = snippet?.channelTitle ?: channelSummary.title,
                        thumbnailUrl = snippet?.thumbnails?.bestUrl()
                            ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg",
                        publishedAt = snippet?.publishedAt.orEmpty(),
                        durationLabel = durationLabel,
                        sourceType = if (isLive) "LIVE" else "VIDEO",
                        viewCountLabel = formatCompactCount(detail.statistics?.viewCount),
                        embedUrl = extractEmbedUrlFromHtml(detail.player?.embedHtml, vidId),
                        isEmbeddable = detail.status?.embeddable ?: true,
                        privacyStatus = detail.status?.privacyStatus ?: "public"
                    )
                }
            }

            Result.success(
                YouTubeLibraryPage(
                    channel = channelSummary,
                    videos = videos,
                    nextPageToken = nextToken
                )
            )
        } catch (e: HttpException) {
            val code = e.code()
            val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            Log.e(TAG, "YouTube Data API HTTP $code: $body", e)
            if (code == 401) {
                return@withContext Result.failure(
                    YouTubeUnauthorizedException("Sessão YouTube expirada. A renovar acesso...")
                )
            }
            val reason = extractGoogleApiReason(body)
            if (reason == "accessNotConfigured") {
                return@withContext Result.failure(
                    YouTubeApiDisabledException(
                        "A YouTube Data API v3 ainda precisa de ativação no Google Cloud. Usa o carregamento direto pelo @handle ou ativa a API v3."
                    )
                )
            }
            val apiMsg = parseGoogleApiError(body)
            Result.failure(Exception(apiMsg ?: "Erro na API do YouTube (HTTP $code)."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YouTube library", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a user-provided local MP4 to the Render media API for temporary processing and upload.
     */
    suspend fun uploadShortToYouTube(
        accessToken: String,
        mp4File: File,
        title: String,
        description: String,
        privacyStatus: String = "unlisted",
        madeForKids: Boolean = false
    ): Result<YtVideoItem> = withContext(Dispatchers.IO) {
        if (!mp4File.exists() || mp4File.length() < 1024L) {
            return@withContext Result.failure(IllegalArgumentException("Ficheiro MP4 inválido ou vazio para envio."))
        }
        try {
            val baseUrl = BuildConfig.RENDER_API_BASE_URL.trim().trimEnd('/')
            if (!baseUrl.startsWith("https://")) {
                return@withContext Result.failure(IllegalStateException("O endereço HTTPS do backend Render ainda não foi configurado."))
            }
            val firebaseToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext Result.failure(IllegalStateException("Inicie sessão no aplicativo antes de publicar."))
            val resolvedPrivacy = privacyStatus.takeIf { it in listOf("public", "unlisted", "private") } ?: "unlisted"
            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "short.mp4", mp4File.asRequestBody("video/mp4".toMediaType()))
                .addFormDataPart("start_seconds", "0")
                .addFormDataPart("end_seconds", "180")
                .addFormDataPart("title", title.trim())
                .addFormDataPart("description", description.trim())
                .addFormDataPart("privacy_status", resolvedPrivacy)
                .addFormDataPart("made_for_kids", madeForKids.toString())
                .build()
            val request = Request.Builder()
                .url("$baseUrl/v1/shorts/publish")
                .header("Authorization", "Bearer $firebaseToken")
                .header("X-YouTube-Authorization", "Bearer $accessToken")
                .post(formBody)
                .build()
            renderUploadClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(responseBody).optString("detail") }.getOrNull()
                    Result.failure(Exception(detail?.takeIf { it.isNotBlank() } ?: "Falha no processamento ou envio do Short (HTTP ${response.code})."))
                } else {
                    val videoId = JSONObject(responseBody).optString("video_id")
                    if (videoId.isBlank()) {
                        Result.failure(Exception("O backend Render não devolveu o ID do vídeo publicado."))
                    } else {
                        Result.success(YtVideoItem(id = videoId))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Render/YouTube publishing failed: ${e.javaClass.simpleName}")
            Result.failure(Exception(e.localizedMessage ?: "Falha ao publicar Short via Render."))
        }
    }

    /**
     * Downloads the highest-resolution YouTube thumbnail available for [videoId] into [destFile].
     */
    suspend fun downloadYouTubeThumbnailFile(
        videoId: String,
        destFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("videoId vazio"))
        }
        val candidates = listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://img.youtube.com/vi/$videoId/sddefault.jpg",
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
        for (url in candidates) {
            try {
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    val bytes = if (resp.isSuccessful) resp.body?.bytes() else null
                    if (bytes != null && bytes.size > 2048) {
                        destFile.parentFile?.mkdirs()
                        destFile.writeBytes(bytes)
                        return@withContext Result.success(destFile)
                    }
                }
            } catch (_: Exception) {
            }
        }
        Result.failure(IllegalStateException("Não foi possível transferir a miniatura HD do vídeo."))
    }

    /**
     * Resolves canonical, share, embed, and thumbnail URLs of a YouTube video for downstream
     * editing and Short-cutting pipelines via YouTube Data API v3 (or oEmbed fallback).
     */
    suspend fun getVideoUrlsForProcessing(
        videoIdOrUrl: String,
        accessToken: String? = null
    ): Result<VideoProcessingUrls> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoIdOrUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("ID ou link do YouTube inválido para processamento.")
            )
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val shortUrl = "https://youtu.be/$videoId"
        val effectiveToken = accessToken?.takeIf { it.isNotBlank() }
            ?: YouTubeRetrofitClient.getAccessToken()

        if (!effectiveToken.isNullOrBlank()) {
            YouTubeRetrofitClient.setAccessToken(effectiveToken)
            try {
                val resp = dataApi.getVideosByIds(
                    ids = videoId,
                    authorization = "Bearer $effectiveToken"
                )
                val item = resp.items.firstOrNull()
                if (item != null) {
                    val snippet = item.snippet
                    val isLive = item.liveStreamingDetails != null ||
                        snippet?.liveBroadcastContent in listOf("live", "upcoming")
                    return@withContext Result.success(
                        VideoProcessingUrls(
                            videoId = videoId,
                            watchUrl = watchUrl,
                            shortUrl = shortUrl,
                            embedUrl = extractEmbedUrlFromHtml(item.player?.embedHtml, videoId),
                            thumbnailUrl = snippet?.thumbnails?.bestUrl()
                                ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                            title = snippet?.title?.takeIf { it.isNotBlank() } ?: "Vídeo ($videoId)",
                            channelName = snippet?.channelTitle?.takeIf { it.isNotBlank() } ?: "Canal YouTube",
                            durationLabel = parseIsoDuration(item.contentDetails?.duration).ifBlank { "15:00" },
                            isLive = isLive,
                            isEmbeddable = item.status?.embeddable ?: true,
                            privacyStatus = item.status?.privacyStatus ?: "public"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "API v3 video URL resolution failed, using oEmbed fallback: ${e.message}")
            }
        }

        try {
            val oEmbed = oEmbedApi.getOEmbed(watchUrl)
            Result.success(
                VideoProcessingUrls(
                    videoId = videoId,
                    watchUrl = watchUrl,
                    shortUrl = shortUrl,
                    embedUrl = "https://www.youtube.com/embed/$videoId",
                    thumbnailUrl = oEmbed.thumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                    title = oEmbed.title?.takeIf { it.isNotBlank() } ?: "Vídeo ($videoId)",
                    channelName = oEmbed.authorName?.takeIf { it.isNotBlank() } ?: "Canal YouTube",
                    durationLabel = "15:00",
                    isLive = videoIdOrUrl.contains("/live/", ignoreCase = true),
                    isEmbeddable = true,
                    privacyStatus = "public"
                )
            )
        } catch (e: Exception) {
            Result.success(
                VideoProcessingUrls(
                    videoId = videoId,
                    watchUrl = watchUrl,
                    shortUrl = shortUrl,
                    embedUrl = "https://www.youtube.com/embed/$videoId",
                    thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                    title = "Vídeo ($videoId)",
                    channelName = "Canal YouTube",
                    durationLabel = "15:00",
                    isLive = videoIdOrUrl.contains("/live/", ignoreCase = true),
                    isEmbeddable = true,
                    privacyStatus = "public"
                )
            )
        }
    }

    /**
     * Fetches a channel's public Videos + Lives without requiring YouTube Data API v3 activation.
     * Accepts:
     * - Channel ID (`UC...` or `youtube.com/channel/UC...`)
     * - Handle (`@PLANETOffGAMES` or `youtube.com/@...`)
     * - Any video URL or 11-char videoId belonging to that channel
     */
    suspend fun fetchPublicChannelLibrary(channelHintOrUrl: String): Result<YouTubeLibraryPage> =
        withContext(Dispatchers.IO) {
            val raw = channelHintOrUrl.trim()
            if (raw.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Indica o @handle, link do canal ou um vídeo do teu canal.")
                )
            }

            try {
                val (channelId, resolvedChannelName, resolvedHandle, resolvedAvatar) =
                    resolveChannelIdentity(raw)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Não foi possível localizar o canal YouTube para \"$raw\". Verifica o @handle ou link.")
                        )

                val allVideos = LinkedHashMap<String, YouTubeLibraryVideo>()
                var bestTitle = resolvedChannelName
                var bestAvatar = resolvedAvatar
                var subLabel = "—"

                // 1. Fetch public Videos tab (/channel/{id}/videos)
                val videosHtml = fetchYoutubeHtml("https://www.youtube.com/channel/$channelId/videos")
                if (!videosHtml.isNullOrBlank()) {
                    val info = parseChannelPageMetadata(videosHtml)
                    if (info.first.isNotBlank()) bestTitle = info.first
                    if (info.second.isNotBlank()) bestAvatar = info.second
                    if (info.third.isNotBlank()) subLabel = info.third

                    parseYtInitialDataVideos(
                        html = videosHtml,
                        channelName = bestTitle,
                        defaultType = "VIDEO"
                    ).forEach { vid -> allVideos[vid.videoId] = vid }
                }

                // 2. Fetch public Lives tab (/channel/{id}/streams)
                val streamsHtml = fetchYoutubeHtml("https://www.youtube.com/channel/$channelId/streams")
                if (!streamsHtml.isNullOrBlank()) {
                    parseYtInitialDataVideos(
                        html = streamsHtml,
                        channelName = bestTitle,
                        defaultType = "LIVE"
                    ).forEach { live ->
                        allVideos[live.videoId] = live.copy(sourceType = "LIVE")
                    }
                }

                // 3. Official Atom RSS Feed fallback (always works, returns latest ~15 uploads + metadata)
                val rssXml = fetchYoutubeHtml("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                if (!rssXml.isNullOrBlank()) {
                    val rssVideos = parseChannelRssFeed(rssXml, bestTitle)
                    rssVideos.forEach { rss ->
                        val existing = allVideos[rss.videoId]
                        if (existing == null) {
                            allVideos[rss.videoId] = rss
                        } else if (existing.description.isBlank() && rss.description.isNotBlank()) {
                            allVideos[rss.videoId] = existing.copy(description = rss.description)
                        }
                    }
                }

                val list = allVideos.values.toList()
                val summary = YouTubeChannelSummary(
                    channelId = channelId,
                    title = bestTitle.ifBlank { "Canal YouTube" },
                    handle = resolvedHandle,
                    thumbnailUrl = bestAvatar,
                    uploadsPlaylistId = "",
                    subscriberCountLabel = subLabel,
                    videoCountLabel = list.size.toString()
                )

                Result.success(
                    YouTubeLibraryPage(
                        channel = summary,
                        videos = list,
                        nextPageToken = null
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Public channel fetch failed for $raw", e)
                Result.failure(e)
            }
        }

    private data class ChannelIdentity(
        val channelId: String,
        val title: String,
        val handle: String,
        val avatarUrl: String
    )

    private fun resolveChannelIdentity(raw: String): ChannelIdentity? {
        // Case 1: Direct UC... ID or /channel/UC...
        Regex("(?:youtube\\.com/channel/)?(UC[a-zA-Z0-9_\\-]{22})").find(raw)?.let { m ->
            val cid = m.groupValues[1]
            return ChannelIdentity(cid, "Canal YouTube", "", "")
        }

        // Case 2: Video ID or watch/live/shorts URL
        val vidId = extractVideoId(raw)
        if (vidId != null) {
            val watchHtml = fetchYoutubeHtml("https://www.youtube.com/watch?v=$vidId")
            if (!watchHtml.isNullOrBlank()) {
                val cid = Regex("""itemprop="channelId"\s+content="(UC[a-zA-Z0-9_\-]{22})"""")
                    .find(watchHtml)?.groupValues?.getOrNull(1)
                    ?: Regex(""""channelId"\s*:\s*"(UC[a-zA-Z0-9_\-]{22})"""")
                        .find(watchHtml)?.groupValues?.getOrNull(1)
                if (!cid.isNullOrBlank()) {
                    val author = Regex(""""author"\s*:\s*"([^"]+)"""")
                        .find(watchHtml)?.groupValues?.getOrNull(1) ?: "Canal YouTube"
                    return ChannelIdentity(cid, unescapeJsonString(author), "", "")
                }
            }
        }

        // Case 3: Handle (@name) or custom channel URL
        val handleUrl = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("@") -> "https://www.youtube.com/$raw"
            else -> "https://www.youtube.com/@${raw.trimStart('@').replace(" ", "")}"
        }
        val channelHtml = fetchYoutubeHtml(handleUrl)
        if (!channelHtml.isNullOrBlank()) {
            val cid = Regex("""<meta\s+itemprop="identifier"\s+content="(UC[a-zA-Z0-9_\-]{22})"""")
                .find(channelHtml)?.groupValues?.getOrNull(1)
                ?: Regex(""""externalId"\s*:\s*"(UC[a-zA-Z0-9_\-]{22})"""")
                    .find(channelHtml)?.groupValues?.getOrNull(1)
                ?: Regex(""""channelId"\s*:\s*"(UC[a-zA-Z0-9_\-]{22})"""")
                    .find(channelHtml)?.groupValues?.getOrNull(1)
            if (!cid.isNullOrBlank()) {
                val meta = parseChannelPageMetadata(channelHtml)
                val handle = if (raw.startsWith("@")) raw else ""
                return ChannelIdentity(cid, meta.first, handle, meta.second)
            }
        }
        return null
    }

    private fun fetchYoutubeHtml(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept-Language", "pt-PT,pt;q=0.9,en;q=0.8")
                .header("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+999;")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP GET failed for $url: ${e.message}")
            null
        }
    }

    private fun parseChannelPageMetadata(html: String): Triple<String, String, String> {
        val title = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)
            ?: Regex(""""channelMetadataRenderer"\s*:\s*\{\s*"title"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.getOrNull(1)
            ?: ""
        val avatar = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1) ?: ""
        val subs = Regex(""""subscriberCountText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""([\d.,]+[KMBkmb]?)\s+(?:subscribers|subscritores|inscritos)""")
                .find(html)?.groupValues?.getOrNull(1)
            ?: "—"
        return Triple(unescapeHtml(unescapeJsonString(title)), avatar, subs)
    }

    private fun parseYtInitialDataVideos(
        html: String,
        channelName: String,
        defaultType: String
    ): List<YouTubeLibraryVideo> {
        val marker = "var ytInitialData = "
        var startIdx = html.indexOf(marker)
        if (startIdx == -1) {
            val altMarker = "ytInitialData = "
            startIdx = html.indexOf(altMarker)
            if (startIdx == -1) return emptyList()
            startIdx += altMarker.length
        } else {
            startIdx += marker.length
        }

        val jsonStart = html.indexOf('{', startIdx)
        if (jsonStart == -1) return emptyList()

        // Extract balanced JSON object
        var depth = 0
        var inString = false
        var escaped = false
        var endIdx = -1
        for (i in jsonStart until html.length) {
            val c = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        endIdx = i + 1
                        break
                    }
                }
            }
        }
        if (endIdx <= jsonStart) return emptyList()

        return try {
            val root = JSONObject(html.substring(jsonStart, endIdx))
            val collected = LinkedHashMap<String, YouTubeLibraryVideo>()
            walkJsonForVideoRenderers(root, channelName, defaultType, collected)
            collected.values.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing ytInitialData JSON: ${e.message}")
            emptyList()
        }
    }

    private fun walkJsonForVideoRenderers(
        node: Any?,
        channelName: String,
        defaultType: String,
        out: MutableMap<String, YouTubeLibraryVideo>
    ) {
        when (node) {
            is JSONObject -> {
                val vidRenderer = node.optJSONObject("videoRenderer")
                    ?: node.optJSONObject("gridVideoRenderer")
                    ?: node.optJSONObject("videoWithContextRenderer")
                if (vidRenderer != null) {
                    val vidId = vidRenderer.optString("videoId")
                    if (vidId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$")) && !out.containsKey(vidId)) {
                        val title = extractRunsOrSimpleText(vidRenderer.optJSONObject("title"))
                            ?: extractRunsOrSimpleText(vidRenderer.optJSONObject("headline"))
                            ?: "Vídeo ($vidId)"
                        val lengthText = extractRunsOrSimpleText(vidRenderer.optJSONObject("lengthText"))
                            ?: extractOverlayTime(vidRenderer)
                            ?: if (defaultType == "LIVE") "LIVE" else "—"
                        val viewText = extractRunsOrSimpleText(vidRenderer.optJSONObject("shortViewCountText"))
                            ?: extractRunsOrSimpleText(vidRenderer.optJSONObject("viewCountText"))
                            ?: "—"
                        val publishedText = extractRunsOrSimpleText(vidRenderer.optJSONObject("publishedTimeText")).orEmpty()
                        val descText = extractRunsOrSimpleText(vidRenderer.optJSONObject("descriptionSnippet")).orEmpty()
                        val isLiveNow = lengthText.equals("LIVE", ignoreCase = true) ||
                            lengthText.equals("AO VIVO", ignoreCase = true) ||
                            defaultType == "LIVE"

                        out[vidId] = YouTubeLibraryVideo(
                            videoId = vidId,
                            url = "https://www.youtube.com/watch?v=$vidId",
                            title = title,
                            description = descText,
                            channelName = channelName.ifBlank { "Canal YouTube" },
                            thumbnailUrl = "https://img.youtube.com/vi/$vidId/hqdefault.jpg",
                            publishedAt = publishedText,
                            durationLabel = if (isLiveNow && lengthText == "—") "LIVE" else lengthText,
                            sourceType = if (isLiveNow) "LIVE" else "VIDEO",
                            viewCountLabel = viewText.replace("visualizações", "").replace("views", "").trim().ifBlank { "—" }
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    walkJsonForVideoRenderers(node.opt(k), channelName, defaultType, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    walkJsonForVideoRenderers(node.opt(i), channelName, defaultType, out)
                }
            }
        }
    }

    private fun extractRunsOrSimpleText(obj: JSONObject?): String? {
        if (obj == null) return null
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun extractOverlayTime(renderer: JSONObject): String? {
        val overlays = renderer.optJSONArray("thumbnailOverlays") ?: return null
        for (i in 0 until overlays.length()) {
            val status = overlays.optJSONObject(i)
                ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
            val text = extractRunsOrSimpleText(status?.optJSONObject("text"))
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun parseChannelRssFeed(xml: String, fallbackChannel: String): List<YouTubeLibraryVideo> {
        val list = mutableListOf<YouTubeLibraryVideo>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inEntry = false
            var currentTag = ""
            var videoId = ""
            var title = ""
            var description = ""
            var published = ""
            var author = fallbackChannel
            var views = "—"

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.orEmpty()
                        if (currentTag == "entry") {
                            inEntry = true
                            videoId = ""
                            title = ""
                            description = ""
                            published = ""
                            author = fallbackChannel
                            views = "—"
                        } else if (inEntry && currentTag == "media:statistics") {
                            val rawViews = parser.getAttributeValue(null, "views")
                            views = formatCompactCount(rawViews)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text.orEmpty()
                        if (inEntry) {
                            when (currentTag) {
                                "yt:videoId" -> videoId += txt
                                "title" -> title += txt
                                "media:description" -> description += txt
                                "published" -> published += txt
                                "name" -> author += txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name.orEmpty()
                        if (endTag == "entry" && inEntry) {
                            val cleanId = videoId.trim()
                            if (cleanId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))) {
                                list.add(
                                    YouTubeLibraryVideo(
                                        videoId = cleanId,
                                        url = "https://www.youtube.com/watch?v=$cleanId",
                                        title = title.trim().ifBlank { "Vídeo ($cleanId)" },
                                        description = description.trim(),
                                        channelName = author.trim().ifBlank { fallbackChannel },
                                        thumbnailUrl = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                                        publishedAt = published.trim().take(10),
                                        durationLabel = "15:00",
                                        sourceType = "VIDEO",
                                        viewCountLabel = views
                                    )
                                )
                            }
                            inEntry = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS parse error: ${e.message}")
        }
        return list
    }

    suspend fun fetchMetadata(
        rawUrl: String,
        accessToken: String? = null
    ): Result<ResolvedYouTubeMetadata> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(rawUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Link do YouTube inválido. Usa um URL youtube.com/watch?v=... ou youtu.be/...")
            )
        val normalizedUrl = "https://www.youtube.com/watch?v=$videoId"
        val isLiveHint = rawUrl.contains("/live/", ignoreCase = true)

        if (!accessToken.isNullOrBlank()) {
            try {
                val resp = dataApi.getVideosByIds(
                    authorization = "Bearer $accessToken",
                    ids = videoId
                )
                val item = resp.items.firstOrNull()
                if (item != null) {
                    val isLive = item.liveStreamingDetails != null ||
                        item.snippet?.liveBroadcastContent in listOf("live", "upcoming") ||
                        isLiveHint
                    val dur = parseIsoDuration(item.contentDetails?.duration).ifBlank { "15:00" }
                    return@withContext Result.success(
                        ResolvedYouTubeMetadata(
                            normalizedUrl = normalizedUrl,
                            videoId = videoId,
                            title = item.snippet?.title?.takeIf { it.isNotBlank() } ?: "Vídeo YouTube ($videoId)",
                            channelName = item.snippet?.channelTitle?.takeIf { it.isNotBlank() } ?: "Canal YouTube",
                            detectedType = if (isLive) "LIVE" else "VIDEO",
                            durationLabel = dur,
                            description = item.snippet?.description.orEmpty()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube Data API single video lookup failed, falling back to oEmbed: ${e.message}")
            }
        }

        try {
            val oEmbed = oEmbedApi.getOEmbed(normalizedUrl)
            val resolvedTitle = oEmbed.title?.takeIf { it.isNotBlank() }
                ?: "Vídeo YouTube ($videoId)"
            val resolvedChannel = oEmbed.authorName?.takeIf { it.isNotBlank() }
                ?: "Canal YouTube"
            Result.success(
                ResolvedYouTubeMetadata(
                    normalizedUrl = normalizedUrl,
                    videoId = videoId,
                    title = resolvedTitle,
                    channelName = resolvedChannel,
                    detectedType = if (isLiveHint) "LIVE" else "VIDEO"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractGoogleApiReason(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(errorBody)
            json.optJSONObject("error")
                ?.optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("reason")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseGoogleApiError(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            val errObj = json.optJSONObject("error") ?: return null
            val message = errObj.optString("message")
            val reason = errObj.optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("reason")
                .orEmpty()
            when (reason) {
                "youtubeSignupRequired" -> "Esta conta Google ainda não tem um canal YouTube associado."
                "accessNotConfigured" -> "A YouTube Data API v3 ainda precisa de ativação no projeto Google Cloud."
                "insufficientPermissions" -> "Permissão insuficiente para ler a biblioteca do YouTube. Reconecta a conta."
                "quotaExceeded" -> "Quota diária da YouTube Data API excedida."
                else -> message.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeHtml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun unescapeJsonString(text: String): String = text
        .replace("\\u0026", "&")
        .replace("\\\"", "\"")

    companion object {
        fun parseIsoDuration(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            val match = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").find(iso.trim()) ?: return ""
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            val seconds = match.groupValues[3].toIntOrNull() ?: 0
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

        fun formatCompactCount(raw: String?): String {
            val value = raw?.toLongOrNull() ?: return "—"
            return when {
                value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
                value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
                else -> value.toString()
            }
        }

        fun parseTimestampToSeconds(timestamp: String): Int {
            val parts = timestamp.trim().split(":").mapNotNull { it.toIntOrNull() }
            return when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).coerceAtLeast(0)
                2 -> (parts[0] * 60 + parts[1]).coerceAtLeast(0)
                1 -> parts[0].coerceAtLeast(0)
                else -> 0
            }
        }
    }
}
