package com.example.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service interface for communicating with the YouTube Data API v3
 * (`https://www.googleapis.com/youtube/v3/`).
 *
 * OAuth2 Bearer tokens are automatically attached by [YouTubeOAuthInterceptor], or
 * can be supplied explicitly per request via the optional [authorization] header.
 */
interface YouTubeService {

    /**
     * Retrieves the authenticated user's channel details, including
     * `contentDetails.relatedPlaylists.uploads` for listing uploaded videos.
     */
    @GET("channels")
    suspend fun getMyChannels(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("mine") mine: Boolean = true,
        @Header("Authorization") authorization: String? = null
    ): YtChannelListResponse

    /**
     * Lists uploaded video items from a playlist (such as the user's `uploads` playlist).
     */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails,status",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 25,
        @Query("pageToken") pageToken: String? = null,
        @Header("Authorization") authorization: String? = null
    ): YtPlaylistItemsResponse

    /**
     * Lists videos owned by the authenticated user via the YouTube Data API v3 Search endpoint.
     */
    @GET("search")
    suspend fun searchMyVideos(
        @Query("part") part: String = "snippet",
        @Query("forMine") forMine: Boolean = true,
        @Query("type") type: String = "video",
        @Query("eventType") eventType: String? = null,
        @Query("maxResults") maxResults: Int = 25,
        @Query("pageToken") pageToken: String? = null,
        @Header("Authorization") authorization: String? = null
    ): YtSearchListResponse

    /**
     * Fetches full details (snippet, ISO duration, statistics, live streaming details,
     * embed player HTML, and privacy/embeddability status) for one or more video IDs.
     */
    @GET("videos")
    suspend fun getVideosByIds(
        @Query("part") part: String = "snippet,contentDetails,statistics,liveStreamingDetails,player,status",
        @Query("id") ids: String,
        @Header("Authorization") authorization: String? = null
    ): YtVideosListResponse

    /**
     * Direct `multipart/related` upload of a Short MP4 video to the user's YouTube channel via
     * YouTube Data API v3 (`youtube.upload` scope required).
     */
    @POST("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=multipart&part=snippet,status")
    suspend fun uploadVideo(
        @Body body: RequestBody,
        @Header("Authorization") authorization: String? = null
    ): YtVideoItem
}
