package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// --- Google OAuth2 DTOs & Service Interface ---

@JsonClass(generateAdapter = true)
data class OAuth2TokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "id_token") val idToken: String? = null
)

@JsonClass(generateAdapter = true)
data class OAuth2TokenInfoResponse(
    val azp: String? = null,
    val aud: String? = null,
    val sub: String? = null,
    val scope: String? = null,
    val exp: String? = null,
    @Json(name = "expires_in") val expiresIn: String? = null,
    val email: String? = null,
    @Json(name = "access_type") val accessType: String? = null
)

interface GoogleOAuth2Service {
    @FormUrlEncoded
    @POST("token")
    suspend fun exchangeAuthorizationCode(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String = "",
        @Field("grant_type") grantType: String = "authorization_code"
    ): OAuth2TokenResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshAccessToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): OAuth2TokenResponse

    @GET("tokeninfo")
    suspend fun verifyAccessToken(
        @Query("access_token") accessToken: String
    ): OAuth2TokenInfoResponse

    @FormUrlEncoded
    @POST("revoke")
    suspend fun revokeToken(
        @Field("token") token: String
    ): Response<Unit>
}

// --- YouTube Data API v3 DTOs ---

@JsonClass(generateAdapter = true)
data class YouTubeOEmbedResponse(
    val title: String? = null,
    @Json(name = "author_name") val authorName: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class YtThumbnails(
    val default: YtThumbnail? = null,
    val medium: YtThumbnail? = null,
    val high: YtThumbnail? = null,
    val standard: YtThumbnail? = null,
    val maxres: YtThumbnail? = null
) {
    fun bestUrl(): String? =
        maxres?.url ?: standard?.url ?: high?.url ?: medium?.url ?: default?.url
}

@JsonClass(generateAdapter = true)
data class YtThumbnail(val url: String? = null)

@JsonClass(generateAdapter = true)
data class YtChannelListResponse(
    val items: List<YtChannelItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YtChannelItem(
    val id: String = "",
    val snippet: YtChannelSnippet? = null,
    val contentDetails: YtChannelContentDetails? = null,
    val statistics: YtChannelStatistics? = null
)

@JsonClass(generateAdapter = true)
data class YtChannelSnippet(
    val title: String? = null,
    val customUrl: String? = null,
    val thumbnails: YtThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YtChannelContentDetails(
    val relatedPlaylists: YtRelatedPlaylists? = null
)

@JsonClass(generateAdapter = true)
data class YtRelatedPlaylists(
    val uploads: String? = null
)

@JsonClass(generateAdapter = true)
data class YtChannelStatistics(
    val subscriberCount: String? = null,
    val videoCount: String? = null
)

@JsonClass(generateAdapter = true)
data class YtPlaylistItemsResponse(
    val nextPageToken: String? = null,
    val items: List<YtPlaylistItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YtPlaylistItem(
    val snippet: YtPlaylistSnippet? = null,
    val contentDetails: YtPlaylistContentDetails? = null,
    val status: YtPlaylistItemStatus? = null
)

@JsonClass(generateAdapter = true)
data class YtPlaylistSnippet(
    val title: String? = null,
    val description: String? = null,
    val channelTitle: String? = null,
    val publishedAt: String? = null,
    val thumbnails: YtThumbnails? = null,
    val resourceId: YtResourceId? = null
)

@JsonClass(generateAdapter = true)
data class YtResourceId(
    val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YtPlaylistContentDetails(
    val videoId: String? = null,
    val videoPublishedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class YtPlaylistItemStatus(
    val privacyStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class YtSearchListResponse(
    val nextPageToken: String? = null,
    val items: List<YtSearchItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YtSearchItem(
    val id: YtSearchId? = null,
    val snippet: YtVideoSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YtSearchId(
    val kind: String? = null,
    val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YtVideosListResponse(
    val items: List<YtVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YtVideoItem(
    val id: String = "",
    val snippet: YtVideoSnippet? = null,
    val contentDetails: YtVideoContentDetails? = null,
    val statistics: YtVideoStatistics? = null,
    val liveStreamingDetails: YtLiveStreamingDetails? = null,
    val player: YtPlayerInfo? = null,
    val status: YtVideoStatus? = null
)

@JsonClass(generateAdapter = true)
data class YtVideoSnippet(
    val title: String? = null,
    val description: String? = null,
    val channelTitle: String? = null,
    val publishedAt: String? = null,
    val liveBroadcastContent: String? = null,
    val thumbnails: YtThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YtVideoContentDetails(
    val duration: String? = null
)

@JsonClass(generateAdapter = true)
data class YtVideoStatistics(
    val viewCount: String? = null,
    val likeCount: String? = null
)

@JsonClass(generateAdapter = true)
data class YtLiveStreamingDetails(
    val actualStartTime: String? = null,
    val scheduledStartTime: String? = null
)

@JsonClass(generateAdapter = true)
data class YtPlayerInfo(
    val embedHtml: String? = null
)

@JsonClass(generateAdapter = true)
data class YtVideoStatus(
    val uploadStatus: String? = null,
    val privacyStatus: String? = null,
    val embeddable: Boolean? = null
)

// --- YouTube Data API v3 & oEmbed Retrofit Service Interfaces ---

interface YouTubeOEmbedApi {
    @GET("oembed")
    suspend fun getOEmbed(
        @Query("url") videoUrl: String,
        @Query("format") format: String = "json"
    ): YouTubeOEmbedResponse
}

typealias YouTubeDataApiService = YouTubeService

fun extractEmbedUrlFromHtml(embedHtml: String?, videoId: String): String {
    val fallback = "https://www.youtube.com/embed/$videoId"
    if (embedHtml.isNullOrBlank()) return fallback
    val srcMatch = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(embedHtml)
        ?.groupValues?.getOrNull(1)?.trim()
        ?: return fallback
    return when {
        srcMatch.startsWith("//") -> "https:$srcMatch"
        srcMatch.startsWith("https://") -> srcMatch
        srcMatch.startsWith("http://") -> srcMatch.replaceFirst("http://", "https://")
        else -> fallback
    }
}
