package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object YouTubeApiConfig {
    const val YOUTUBE_DATA_API_BASE_URL = "https://www.googleapis.com/youtube/v3/"
    const val GOOGLE_OAUTH2_BASE_URL = "https://oauth2.googleapis.com/"
    const val YOUTUBE_OEMBED_BASE_URL = "https://www.youtube.com/"
    const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    const val YOUTUBE_UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload"
}

/**
 * OkHttp Interceptor that injects the OAuth2 Bearer token into YouTube Data API v3 calls
 * when an explicit Authorization header has not already been provided on the request.
 */
class YouTubeOAuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/json")

        val hasExplicitAuth = !original.header("Authorization").isNullOrBlank()
        val token = tokenProvider()?.trim()
        if (!hasExplicitAuth && !token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}

/**
 * Centralized Retrofit + OkHttp + Moshi client factory for YouTube Data API v3,
 * Google OAuth2 endpoints, and YouTube oEmbed.
 */
object YouTubeRetrofitClient {

    @Volatile
    private var inMemoryAccessToken: String? = null

    fun setAccessToken(token: String?) {
        inMemoryAccessToken = token?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getAccessToken(): String? = inMemoryAccessToken

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(YouTubeOAuthInterceptor { inMemoryAccessToken })
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val youTubeService: YouTubeService by lazy {
        createYouTubeService(okHttpClient)
    }

    val dataApiService: YouTubeDataApiService
        get() = youTubeService

    val oAuth2Service: GoogleOAuth2Service by lazy {
        createOAuth2Service(okHttpClient)
    }

    val oEmbedApi: YouTubeOEmbedApi by lazy {
        createOEmbedApi(okHttpClient)
    }

    fun createYouTubeService(
        client: OkHttpClient,
        baseUrl: String = YouTubeApiConfig.YOUTUBE_DATA_API_BASE_URL
    ): YouTubeService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeService::class.java)

    fun createDataApiService(
        client: OkHttpClient,
        baseUrl: String = YouTubeApiConfig.YOUTUBE_DATA_API_BASE_URL
    ): YouTubeDataApiService = createYouTubeService(client, baseUrl)

    fun createOAuth2Service(
        client: OkHttpClient,
        baseUrl: String = YouTubeApiConfig.GOOGLE_OAUTH2_BASE_URL
    ): GoogleOAuth2Service =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleOAuth2Service::class.java)

    fun createOEmbedApi(
        client: OkHttpClient,
        baseUrl: String = YouTubeApiConfig.YOUTUBE_OEMBED_BASE_URL
    ): YouTubeOEmbedApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeOEmbedApi::class.java)
}
