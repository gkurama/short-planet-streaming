package com.example

import com.example.auth.TikTokAuthHelper
import com.example.data.remote.TikTokUploadService
import com.example.data.remote.YouTubeMetadataService
import com.example.data.remote.YouTubeOAuthInterceptor
import com.example.data.remote.YouTubeRetrofitClient
import com.example.data.remote.YouTubeService
import com.example.data.remote.extractEmbedUrlFromHtml
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRetrofitApiTest {

    @Test
    fun `extractEmbedUrlFromHtml handles protocol-relative, https, and fallback`() {
        assertEquals(
            "https://www.youtube.com/embed/abc12345678",
            extractEmbedUrlFromHtml(
                """<iframe width="480" height="270" src="//www.youtube.com/embed/abc12345678"></iframe>""",
                "abc12345678"
            )
        )
        assertEquals(
            "https://www.youtube.com/embed/xyz98765432",
            extractEmbedUrlFromHtml(null, "xyz98765432")
        )
    }

    @Test
    fun `oauth interceptor attaches bearer token and skips when null`() {
        var currentToken: String? = "oauth2-secret-token"
        val capturedAuthHeaders = mutableListOf<String?>()

        val client = OkHttpClient.Builder()
            .addInterceptor(YouTubeOAuthInterceptor { currentToken })
            .addInterceptor { chain ->
                val req = chain.request()
                capturedAuthHeaders.add(req.header("Authorization"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"items":[]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        runTest {
            val api: YouTubeService = YouTubeRetrofitClient.createYouTubeService(client)
            api.getMyChannels()
            currentToken = null
            api.getMyChannels()
        }

        assertEquals("Bearer oauth2-secret-token", capturedAuthHeaders[0])
        assertNull(capturedAuthHeaders[1])
    }

    @Test
    fun `fetchMyYouTubeLibrary and getVideoUrlsForProcessing work via Retrofit services`() = runTest {
        val capturedPaths = mutableListOf<String>()
        val capturedAuth = mutableListOf<String?>()

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor(YouTubeOAuthInterceptor { YouTubeRetrofitClient.getAccessToken() })
            .addInterceptor { chain ->
                val req = chain.request()
                capturedPaths.add(req.url.encodedPath)
                capturedAuth.add(req.header("Authorization"))
                val json = when {
                    req.url.encodedPath.endsWith("/channels") -> """
                        {
                          "items": [{
                            "id": "UC_CHANNEL_01",
                            "snippet": {
                              "title": "PLANET Off GAMES",
                              "customUrl": "@PLANETOffGAMES",
                              "thumbnails": { "high": { "url": "https://yt3.ggpht.com/avatar.jpg" } }
                            },
                            "contentDetails": {
                              "relatedPlaylists": { "uploads": "UU_UPLOADS_01" }
                            },
                            "statistics": {
                              "subscriberCount": "12500",
                              "videoCount": "48"
                            }
                          }]
                        }
                    """.trimIndent()
                    req.url.encodedPath.endsWith("/playlistItems") -> """
                        {
                          "nextPageToken": "NEXT_25",
                          "items": [{
                            "snippet": {
                              "title": "Live Épica GTA",
                              "description": "Melhores momentos da live",
                              "channelTitle": "PLANET Off GAMES",
                              "publishedAt": "2026-11-20T18:00:00Z",
                              "resourceId": { "videoId": "dQw4w9WgXcQ" }
                            },
                            "contentDetails": {
                              "videoId": "dQw4w9WgXcQ",
                              "videoPublishedAt": "2026-11-20T18:00:00Z"
                            },
                            "status": { "privacyStatus": "public" }
                          }]
                        }
                    """.trimIndent()
                    req.url.encodedPath.endsWith("/videos") -> """
                        {
                          "items": [{
                            "id": "dQw4w9WgXcQ",
                            "snippet": {
                              "title": "Live Épica GTA",
                              "description": "Melhores momentos da live",
                              "channelTitle": "PLANET Off GAMES",
                              "liveBroadcastContent": "none",
                              "thumbnails": { "maxres": { "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg" } }
                            },
                            "contentDetails": { "duration": "PT15M22S" },
                            "statistics": { "viewCount": "5400" },
                            "liveStreamingDetails": { "actualStartTime": "2026-11-20T16:00:00Z" },
                            "player": { "embedHtml": "<iframe src=\"//www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>" },
                            "status": { "privacyStatus": "public", "embeddable": true, "uploadStatus": "processed" }
                          }]
                        }
                    """.trimIndent()
                    req.url.encodedPath.endsWith("/tokeninfo") -> """
                        {
                          "scope": "https://www.googleapis.com/auth/youtube.readonly",
                          "expires_in": "3599",
                          "email": "creator@example.com"
                        }
                    """.trimIndent()
                    req.url.encodedPath.endsWith("/search") -> """
                        {
                          "items": [{
                            "id": { "kind": "youtube#video", "videoId": "dQw4w9WgXcQ" },
                            "snippet": { "title": "Live Épica GTA" }
                          }]
                        }
                    """.trimIndent()
                    else -> "{}"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val ytService: YouTubeService = YouTubeRetrofitClient.createYouTubeService(mockHttpClient)
        val searchResult = ytService.searchMyVideos(authorization = "Bearer ya29.test-token")
        assertEquals("dQw4w9WgXcQ", searchResult.items.first().id?.videoId)

        val service = YouTubeMetadataService(
            dataApi = ytService,
            oAuth2Api = YouTubeRetrofitClient.createOAuth2Service(mockHttpClient),
            oEmbedApi = YouTubeRetrofitClient.createOEmbedApi(mockHttpClient),
            okHttpClient = mockHttpClient
        )

        val libResult = service.fetchMyYouTubeLibrary(accessToken = "ya29.test-token")
        assertTrue(libResult.isSuccess)
        val page = libResult.getOrThrow()
        assertEquals("PLANET Off GAMES", page.channel.title)
        assertEquals("UU_UPLOADS_01", page.channel.uploadsPlaylistId)
        assertEquals("NEXT_25", page.nextPageToken)
        assertEquals(1, page.videos.size)

        val vid = page.videos.first()
        assertEquals("dQw4w9WgXcQ", vid.videoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", vid.url)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", vid.embedUrl)
        assertEquals("LIVE", vid.sourceType)
        assertEquals("15:22", vid.durationLabel)
        assertTrue(vid.isEmbeddable)

        val urlsResult = service.getVideoUrlsForProcessing("dQw4w9WgXcQ", accessToken = "ya29.test-token")
        assertTrue(urlsResult.isSuccess)
        val urls = urlsResult.getOrThrow()
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", urls.watchUrl)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", urls.shortUrl)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", urls.embedUrl)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", urls.thumbnailUrl)
        assertFalse(urls.privacyStatus.isBlank())

        val tokenInfo = service.verifyOAuthToken("ya29.test-token")
        assertTrue(tokenInfo.isSuccess)
        assertEquals("creator@example.com", tokenInfo.getOrThrow().email)

        assertTrue(capturedAuth.all { it == "Bearer ya29.test-token" || it == null })
        assertTrue(capturedPaths.any { it.endsWith("/channels") })
        assertTrue(capturedPaths.any { it.endsWith("/playlistItems") })
        assertTrue(capturedPaths.any { it.endsWith("/videos") })
        assertTrue(capturedPaths.any { it.endsWith("/tokeninfo") })

        YouTubeRetrofitClient.setAccessToken(null)
    }

    @Test
    fun `uploadShortToYouTube sends multipart snippet and mp4 payload via Retrofit`() = runTest {
        var uploadRequestUrl = ""
        var uploadAuthHeader: String? = null
        var contentTypeHeader: String? = null

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                uploadRequestUrl = req.url.toString()
                uploadAuthHeader = req.header("Authorization")
                contentTypeHeader = req.body?.contentType()?.toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"id":"UPLOADED_SHORT_99","snippet":{"title":"Short Épico #Shorts"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

        val ytService = YouTubeRetrofitClient.createYouTubeService(mockHttpClient)
        val service = YouTubeMetadataService(
            dataApi = ytService,
            oAuth2Api = YouTubeRetrofitClient.createOAuth2Service(mockHttpClient),
            oEmbedApi = YouTubeRetrofitClient.createOEmbedApi(mockHttpClient),
            okHttpClient = mockHttpClient
        )

        val tempMp4 = java.io.File.createTempFile("test_short_", ".mp4")
        try {
            val payload = ByteArray(2048).apply {
                this[3] = 0x18
                this[4] = 0x66
                this[5] = 0x74
                this[6] = 0x79
                this[7] = 0x70
            }
            tempMp4.writeBytes(payload)
            val result = service.uploadShortToYouTube(
                accessToken = "ya29.upload-token",
                mp4File = tempMp4,
                title = "Short Épico",
                description = "Corte automático #Shorts"
            )
            assertTrue(result.isSuccess)
            assertEquals("UPLOADED_SHORT_99", result.getOrThrow().id)
            assertTrue(uploadRequestUrl.contains("uploadType=multipart"))
            assertEquals("Bearer ya29.upload-token", uploadAuthHeader)
            assertTrue(contentTypeHeader?.contains("multipart/related") == true)
        } finally {
            tempMp4.delete()
        }
    }

    @Test
    fun `uploadShortToTikTokInbox initializes upload and sends mp4 bytes via PUT`() = runTest {
        val capturedRequests = mutableListOf<Triple<String, String, String?>>()

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedRequests.add(
                    Triple(req.method, req.url.toString(), req.header("Authorization"))
                )
                val bodyJson = if (req.method == "POST") {
                    """{"data":{"publish_id":"tt_pub_42","upload_url":"https://upload.tiktokapis.com/upload/video42?token=xyz"},"error":{"code":"ok"}}"""
                } else {
                    "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val tikTokService = TikTokUploadService(
            okHttpClient = mockHttpClient,
            initEndpointUrl = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/"
        )

        val tempMp4 = java.io.File.createTempFile("tiktok_short_", ".mp4")
        try {
            tempMp4.writeBytes(ByteArray(2048) { 0x2A })
            val res = tikTokService.uploadShortToTikTokInbox(
                accessToken = "act.tiktok-test-token",
                mp4File = tempMp4,
                title = "Cena GTA VI"
            )
            assertTrue(res.isSuccess)
            assertEquals("tt_pub_42", res.getOrThrow().publishId)
            assertEquals(2, capturedRequests.size)
            assertEquals("POST", capturedRequests[0].first)
            assertEquals("Bearer act.tiktok-test-token", capturedRequests[0].third)
            assertEquals("PUT", capturedRequests[1].first)
            assertEquals("https://upload.tiktokapis.com/upload/video42?token=xyz", capturedRequests[1].second)
        } finally {
            tempMp4.delete()
        }
    }

    @Test
    fun `TikTokAuthHelper computes RFC7636 PKCE S256 challenge and exchanges OAuth tokens and profile`() = runTest {
        // RFC 7636 Appendix B test vector for S256
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, TikTokAuthHelper.computeCodeChallenge(verifier))

        val authUrl = TikTokAuthHelper.buildAuthorizationUrl(
            clientKey = "aw_client_123",
            state = "state_xyz",
            codeChallenge = expectedChallenge
        )
        assertTrue(authUrl.startsWith(TikTokAuthHelper.AUTHORIZE_ENDPOINT))
        assertTrue(authUrl.contains("client_key=aw_client_123"))
        assertTrue(authUrl.contains("code_challenge=$expectedChallenge"))
        assertTrue(authUrl.contains("code_challenge_method=S256"))
        assertTrue(authUrl.contains("planetstreaming%3A%2F%2Ftiktok%2Fcallback"))
        assertTrue(authUrl.contains("video.publish"))

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val json = when {
                    req.url.encodedPath.endsWith("/oauth/token/") -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        val bodyText = buf.readUtf8()
                        if (bodyText.contains("grant_type=refresh_token")) {
                            """{"access_token":"act.refreshed_99","refresh_token":"rft.new_88","expires_in":86400,"open_id":"open_tt_01","scope":"user.info.basic,video.upload,video.publish"}"""
                        } else {
                            """{"access_token":"act.initial_77","refresh_token":"rft.init_66","expires_in":86400,"open_id":"open_tt_01","scope":"user.info.basic,video.upload,video.publish"}"""
                        }
                    }
                    req.url.encodedPath.endsWith("/user/info/") -> """
                        {
                          "data": {
                            "user": {
                              "open_id": "open_tt_01",
                              "username": "planetoffgames",
                              "display_name": "PLANET Off GAMES",
                              "avatar_url": "https:\/\/p16.tiktokcdn.com\/avatar.jpg"
                            }
                          },
                          "error": {"code": "ok"}
                        }
                    """.trimIndent()
                    else -> "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val helper = TikTokAuthHelper(okHttpClient = mockHttpClient)
        val tokenRes = helper.exchangeCodeForTokens(
            clientKey = "aw_client_123",
            clientSecret = "sec_456",
            code = "auth_code_abc",
            codeVerifier = verifier
        )
        assertTrue(tokenRes.isSuccess)
        assertEquals("act.initial_77", tokenRes.getOrThrow().accessToken)
        assertEquals("open_tt_01", tokenRes.getOrThrow().openId)

        val refreshRes = helper.refreshAccessToken(
            clientKey = "aw_client_123",
            clientSecret = "sec_456",
            refreshToken = "rft.init_66"
        )
        assertTrue(refreshRes.isSuccess)
        assertEquals("act.refreshed_99", refreshRes.getOrThrow().accessToken)
        assertEquals("rft.new_88", refreshRes.getOrThrow().refreshToken)

        val profileRes = helper.fetchUserProfile("act.refreshed_99")
        assertTrue(profileRes.isSuccess)
        val profile = profileRes.getOrThrow()
        assertEquals("planetoffgames", profile.username)
        assertEquals("PLANET Off GAMES", profile.displayName)
        assertEquals("https://p16.tiktokcdn.com/avatar.jpg", profile.avatarUrl)
    }

    @Test
    fun `uploadShortToTikTok uses Direct Post and falls back cleanly to Inbox Draft on unaudited scope failure`() = runTest {
        val directPaths = mutableListOf<String>()
        val directClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                directPaths.add("${req.method} ${req.url.encodedPath}")
                val json = when {
                    req.url.encodedPath.endsWith("/creator_info/query/") ->
                        """{"data":{"privacy_level_options":["PUBLIC_TO_EVERYONE","SELF_ONLY"]},"error":{"code":"ok"}}"""
                    req.url.encodedPath.endsWith("/post/publish/video/init/") ->
                        """{"data":{"publish_id":"dp_pub_1","upload_url":"https://upload.tiktokapis.com/dp/video1"},"error":{"code":"ok"}}"""
                    else -> "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val fallbackPaths = mutableListOf<String>()
        val fallbackClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                fallbackPaths.add("${req.method} ${req.url.encodedPath}")
                val (code, json) = when {
                    req.url.encodedPath.endsWith("/creator_info/query/") ->
                        403 to """{"error":{"code":"scope_not_authorized"}}"""
                    req.url.encodedPath.endsWith("/post/publish/video/init/") ->
                        403 to """{"error":{"code":"scope_not_authorized","message":"video.publish missing"}}"""
                    req.url.encodedPath.endsWith("/post/publish/inbox/video/init/") ->
                        200 to """{"data":{"publish_id":"inbox_pub_2","upload_url":"https://upload.tiktokapis.com/inbox/video2"},"error":{"code":"ok"}}"""
                    else -> 200 to "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Forbidden")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val tempMp4 = java.io.File.createTempFile("tiktok_unified_", ".mp4")
        try {
            tempMp4.writeBytes(ByteArray(2048) { 0x11 })

            val directRes = TikTokUploadService(okHttpClient = directClient)
                .uploadShortToTikTok(accessToken = "act.direct", mp4File = tempMp4, title = "Short Direto")
            assertTrue(directRes.isSuccess)
            assertTrue(directRes.getOrThrow().isDirectPost)
            assertEquals("dp_pub_1", directRes.getOrThrow().publishId)
            assertTrue(directPaths.any { it.contains("/post/publish/video/init/") })
            assertTrue(directPaths.any { it.startsWith("PUT ") })

            val fallbackRes = TikTokUploadService(okHttpClient = fallbackClient)
                .uploadShortToTikTok(accessToken = "act.inbox", mp4File = tempMp4, title = "Short Rascunho")
            assertTrue(fallbackRes.isSuccess)
            assertFalse(fallbackRes.getOrThrow().isDirectPost)
            assertEquals("inbox_pub_2", fallbackRes.getOrThrow().publishId)
            assertTrue(fallbackPaths.any { it.contains("/post/publish/inbox/video/init/") })
            assertTrue(fallbackPaths.any { it.startsWith("PUT ") })
        } finally {
            tempMp4.delete()
        }
    }
}

