package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageSizeOption
import com.example.data.model.ShortTemplateCatalog
import com.example.data.remote.YouTubeMetadataService
import com.example.media.ClipVisualStyle
import com.example.media.CoverStudioRenderer
import com.example.media.ShortVideoProcessor
import com.example.media.StudioAudioGenerator
import com.example.media.StudioLocalPrefs
import com.example.media.TikTokAccountState
import com.example.media.VideoFilterPreset
import com.example.media.YouTubePersistentState
import com.example.media.clampTrimRangeSec
import com.example.media.formatMsToMmSs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Short Planet Stream", appName)
    }

    @Test
    fun `extract youtube video id from watch, live, shorts, and youtu_be urls`() {
        val service = YouTubeMetadataService()
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(service.extractVideoId("https://example.com/not-youtube"))
    }

    @Test
    fun `parse iso 8601 duration and clip timestamps accurately`() {
        assertEquals("15:22", YouTubeMetadataService.parseIsoDuration("PT15M22S"))
        assertEquals("1:02:05", YouTubeMetadataService.parseIsoDuration("PT1H2M5S"))
        assertEquals("0:45", YouTubeMetadataService.parseIsoDuration("PT45S"))

        assertEquals(135, YouTubeMetadataService.parseTimestampToSeconds("02:15"))
        assertEquals(3735, YouTubeMetadataService.parseTimestampToSeconds("1:02:15"))
        assertEquals(0, YouTubeMetadataService.parseTimestampToSeconds("invalid"))
    }

    @Test
    fun `format milliseconds to mm_ss and h_mm_ss`() {
        assertEquals("00:00", formatMsToMmSs(0L))
        assertEquals("00:45", formatMsToMmSs(45_000L))
        assertEquals("02:15", formatMsToMmSs(135_000L))
        assertEquals("1:02:05", formatMsToMmSs(3_725_000L))
    }

    @Test
    fun `compute auto short segments for 15 minute video and short under 25s video`() {
        val fifteenMinMs = 15 * 60 * 1000L
        val segments = ShortVideoProcessor.computeAutoShortSegments(
            totalDurationMs = fifteenMinMs,
            preferredDurationPreset = "30–60 segundos",
            baseTitle = "Gameplay Épica"
        )
        assertEquals(3, segments.size)
        segments.forEachIndexed { idx, seg ->
            assertEquals(idx + 1, seg.index)
            assertTrue(seg.endMs > seg.startMs)
            assertTrue(seg.endMs <= fifteenMinMs)
            assertEquals(58L, seg.durationSeconds)
            assertEquals(formatMsToMmSs(seg.startMs), seg.startLabel)
            assertEquals(formatMsToMmSs(seg.endMs), seg.endLabel)
        }

        val fastSegments = ShortVideoProcessor.computeAutoShortSegments(
            totalDurationMs = fifteenMinMs,
            preferredDurationPreset = "15–30 segundos",
            baseTitle = "Highlight Rápido"
        )
        assertEquals(3, fastSegments.size)
        assertTrue(fastSegments.all { it.durationSeconds == 25L })

        val shortVideoMs = 20_000L
        val singleSegment = ShortVideoProcessor.computeAutoShortSegments(
            totalDurationMs = shortVideoMs,
            preferredDurationPreset = "30–60 segundos",
            baseTitle = "Clipe Curto"
        )
        assertEquals(1, singleSegment.size)
        assertEquals(0L, singleSegment.first().startMs)
        assertEquals(20_000L, singleSegment.first().endMs)
        assertEquals("00:00", singleSegment.first().startLabel)
        assertEquals("00:20", singleSegment.first().endLabel)
    }

    @Test
    fun `studio local prefs persist clip visual style and tiktok handle cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("@planetoffgames", StudioLocalPrefs.normalizeTikTokHandle("planetoffgames"))
        assertEquals("@planetoffgames", StudioLocalPrefs.normalizeTikTokHandle("https://www.tiktok.com/@planetoffgames?lang=pt"))
        assertEquals("", StudioLocalPrefs.normalizeTikTokHandle("   "))

        val savedTikTok = StudioLocalPrefs.saveTikTokAccount(context, "planetoffgames")
        assertTrue(savedTikTok.isConnected)
        assertEquals("@planetoffgames", savedTikTok.handle)
        assertEquals("@planetoffgames", StudioLocalPrefs.loadTikTokAccount(context).handle)

        val clearedTikTok = StudioLocalPrefs.clearTikTokAccount(context)
        assertFalse(clearedTikTok.isConnected)
        assertEquals("", StudioLocalPrefs.loadTikTokAccount(context).handle)

        val style = ClipVisualStyle(
            filterId = VideoFilterPreset.NEON_CYBER.id,
            emojiOverlay = "🔥 🎮",
            coverTitle = "MOMENTO GTA VI",
            coverImagePath = "/tmp/cover_clip1.png",
            burnOverlayOnExport = true,
            zoomFactor = 1.25f,
            panX = -0.35f,
            aiTrimTighten = true,
            audioBoost = false
        )
        val map = StudioLocalPrefs.saveClipStyle(context, "clip_test_1", style)
        val loaded = map["clip_test_1"]
        assertEquals(VideoFilterPreset.NEON_CYBER.id, loaded?.filterId)
        assertEquals("🔥 🎮", loaded?.emojiOverlay)
        assertEquals("MOMENTO GTA VI", loaded?.coverTitle)
        assertEquals("/tmp/cover_clip1.png", loaded?.coverImagePath)
        assertEquals(1.25f, loaded?.zoomFactor ?: 0f, 0.01f)
        assertEquals(-0.35f, loaded?.panX ?: 0f, 0.01f)
        assertTrue(loaded?.aiTrimTighten == true)
        assertFalse(loaded?.audioBoost == true)
    }

    @Test
    fun `cover studio renderer produces valid bitmaps across aspect ratios and filters`() {
        val portrait = CoverStudioRenderer.renderCoverBitmap(
            headline = "GTA VI ESTREIA ÉPICA",
            subtitle = "PLANET OFF GAMES",
            emojiBadge = "🔥",
            filterPreset = VideoFilterPreset.GTA_SUNSET,
            aspectOption = ImageAspectOption.PORTRAIT_9_16,
            sizeOption = ImageSizeOption.SIZE_1K
        )
        assertTrue(portrait.height > portrait.width)
        assertEquals(1080, portrait.width)
        assertEquals(1920, portrait.height)

        val landscape = CoverStudioRenderer.renderCoverBitmap(
            headline = "LIVE HISTÓRICA",
            emojiBadge = "🚀",
            filterPreset = VideoFilterPreset.NEON_CYBER,
            aspectOption = ImageAspectOption.LANDSCAPE_16_9,
            sizeOption = ImageSizeOption.SIZE_1K
        )
        assertTrue(landscape.width > landscape.height)

        val square = CoverStudioRenderer.renderCoverBitmap(
            headline = "CORTE VIRAL",
            emojiBadge = "⚡",
            filterPreset = VideoFilterPreset.BW_DRAMATIC,
            aspectOption = ImageAspectOption.SQUARE_1_1,
            sizeOption = ImageSizeOption.SIZE_1K
        )
        assertEquals(square.width, square.height)
    }

    @Test
    fun `studio local prefs persist youtube session, tiktok oauth session, pending pkce, and auto-publish toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(StudioLocalPrefs.loadAutoPublishOnCut(context))
        StudioLocalPrefs.saveAutoPublishOnCut(context, false)
        assertFalse(StudioLocalPrefs.loadAutoPublishOnCut(context))
        StudioLocalPrefs.saveAutoPublishOnCut(context, true)
        assertTrue(StudioLocalPrefs.loadAutoPublishOnCut(context))

        StudioLocalPrefs.savePendingTikTokOAuth(context, "st_123", "ver_456")
        val pending = StudioLocalPrefs.consumePendingTikTokOAuth(context)
        assertNotNull(pending)
        assertEquals("st_123", pending?.state)
        assertEquals("ver_456", pending?.codeVerifier)
        assertNull(StudioLocalPrefs.consumePendingTikTokOAuth(context))

        val savedOAuth = StudioLocalPrefs.saveTikTokOAuthSession(
            context,
            TikTokAccountState(
                isConnected = true,
                handle = "planetoffgames",
                apiAccessToken = "act.oauth_123",
                refreshToken = "rft.oauth_456",
                expiresAtEpochMs = 1_900_000_000_000L,
                openId = "open_99",
                displayName = "PLANET Off GAMES",
                avatarUrl = "https://example.com/tt.jpg",
                grantedScopes = "user.info.basic,video.upload,video.publish"
            )
        )
        assertTrue(savedOAuth.isConnected)
        assertTrue(savedOAuth.isOAuthConnected)
        assertEquals("@planetoffgames", savedOAuth.handle)

        val loadedTt = StudioLocalPrefs.loadTikTokAccount(context)
        assertEquals("act.oauth_123", loadedTt.apiAccessToken)
        assertEquals("rft.oauth_456", loadedTt.refreshToken)
        assertEquals("open_99", loadedTt.openId)
        assertEquals("PLANET Off GAMES", loadedTt.displayName)
        assertTrue(loadedTt.isOAuthConnected)

        val clearedTt = StudioLocalPrefs.clearTikTokAccount(context)
        assertFalse(clearedTt.hasApiUploadToken)
        assertFalse(clearedTt.isOAuthConnected)

        val ytState = YouTubePersistentState(
            wasConnected = true,
            channelTitle = "PLANET Off GAMES",
            channelHint = "@PLANETOffGAMES",
            hasUploadScope = true
        )
        StudioLocalPrefs.saveYouTubePersistentState(context, ytState)
        val loadedYt = StudioLocalPrefs.loadYouTubePersistentState(context)
        assertTrue(loadedYt.wasConnected)
        assertEquals("PLANET Off GAMES", loadedYt.channelTitle)
        assertEquals("@PLANETOffGAMES", loadedYt.channelHint)
        assertTrue(loadedYt.hasUploadScope)

        StudioLocalPrefs.clearYouTubePersistentState(context)
        val clearedYt = StudioLocalPrefs.loadYouTubePersistentState(context)
        assertFalse(clearedYt.wasConnected)
        assertFalse(clearedYt.hasUploadScope)
    }

    @Test
    fun `studio audio generator creates valid 44100hz stereo wav header and exact duration payload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavFile = StudioAudioGenerator.generateStudioTrackWav(
            context = context,
            fileName = "unit_test_bgm.wav",
            durationMs = 4_000L
        )
        try {
            assertTrue(wavFile.exists())
            // 44100 frames/s * 4s * 2 channels * 2 bytes = 705600 bytes PCM + 44 header
            val expectedPcmBytes = 44_100 * 4 * 2 * 2
            assertEquals(44L + expectedPcmBytes, wavFile.length())

            val bytes = wavFile.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        } finally {
            wavFile.delete()
        }
    }

    @Test
    fun `buildTimedCaptionSegments splits multi-sentence script into sequential non-overlapping windows`() {
        val script = "Olha essa jogada absurda no GTA VI! O carro voou pela ponte inteira e explodiu tudo na hora exata."
        val segments = ShortVideoProcessor.buildTimedCaptionSegments(script, durationMs = 12_000L)
        assertTrue(segments.size >= 3)
        assertEquals(0L, segments.first().startMs)
        assertEquals(12_000L, segments.last().endMs)
        for (i in segments.indices) {
            assertTrue(segments[i].text.isNotBlank())
            assertTrue(segments[i].endMs > segments[i].startMs)
            if (i > 0) {
                assertTrue(segments[i].startMs >= segments[i - 1].endMs)
            }
        }
    }

    @Test
    fun `studio local prefs persist export quality, notifications, and template catalog has categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("1080p (Recomendado)", StudioLocalPrefs.loadExportQuality(context))
        StudioLocalPrefs.saveExportQuality(context, "720p (Rápido)")
        assertEquals("720p (Rápido)", StudioLocalPrefs.loadExportQuality(context))

        assertTrue(StudioLocalPrefs.loadNotificationsEnabled(context))
        StudioLocalPrefs.saveNotificationsEnabled(context, false)
        assertFalse(StudioLocalPrefs.loadNotificationsEnabled(context))

        assertTrue(ShortTemplateCatalog.templates.size >= 6)
        assertTrue(ShortTemplateCatalog.categories.containsAll(listOf("Todos", "Gaming", "Educativo", "Entretenimento")))
    }

    @Test
    fun `clampTrimRangeSec clamps bounds and enforces minimum clip duration`() {
        assertEquals(10 to 35, clampTrimRangeSec(startSec = 10, endSec = 35, totalDurationSec = 90, minDurationSec = 2))
        assertEquals(0 to 45, clampTrimRangeSec(startSec = -5, endSec = 120, totalDurationSec = 45, minDurationSec = 2))
        assertEquals(20 to 25, clampTrimRangeSec(startSec = 20, endSec = 21, totalDurationSec = 60, minDurationSec = 5))
        assertEquals(28 to 30, clampTrimRangeSec(startSec = 40, endSec = 50, totalDurationSec = 30, minDurationSec = 2))
    }
}
