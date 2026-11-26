package com.example.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val TAG = "ShortVideoProcessor"

data class LocalVideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int
) {
    val durationLabel: String
        get() = formatMsToMmSs(durationMs)
}

data class ShortSegmentSpec(
    val index: Int,
    val title: String,
    val hookText: String,
    val startMs: Long,
    val endMs: Long,
    val startLabel: String,
    val endLabel: String,
    val durationSeconds: Long
)

data class TimedCaptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

fun formatMsToMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
}

fun clampTrimRangeSec(
    startSec: Int,
    endSec: Int,
    totalDurationSec: Int,
    minDurationSec: Int = 2
): Pair<Int, Int> {
    val safeTotal = totalDurationSec.coerceAtLeast(minDurationSec + 1)
    val safeMin = minDurationSec.coerceAtLeast(1).coerceAtMost(safeTotal - 1)
    val clampedStart = startSec.coerceIn(0, safeTotal - safeMin)
    val clampedEnd = endSec.coerceIn(clampedStart + safeMin, safeTotal)
    return clampedStart to clampedEnd
}

@androidx.annotation.OptIn(UnstableApi::class)
object ShortVideoProcessor {

    private fun shortsDir(context: Context): File =
        File(context.filesDir, "rendered_shorts").apply { mkdirs() }

    private fun imageSlidesDir(context: Context): File =
        File(context.cacheDir, "short_slides").apply { mkdirs() }

    fun getRenderedShortFile(context: Context, clipId: String): File? {
        if (clipId.isBlank()) return null
        val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(shortsDir(context), "short_$safeId.mp4")
        return file.takeIf { it.exists() && it.length() > 1024L }
    }

    fun listRenderedClipIds(context: Context): Set<String> {
        val dir = shortsDir(context)
        val files = dir.listFiles() ?: return emptySet()
        return files.mapNotNull { file ->
            if (file.isFile && file.name.startsWith("short_") && file.name.endsWith(".mp4") && file.length() > 1024L) {
                file.name.removePrefix("short_").removeSuffix(".mp4")
            } else null
        }.toSet()
    }

    suspend fun inspectVideo(context: Context, uri: Uri): LocalVideoInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 60_000L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            val name = queryDisplayName(context, uri) ?: "Video_Studio.mp4"
            LocalVideoInfo(
                uri = uri,
                displayName = name,
                durationMs = durationMs,
                width = width,
                height = height
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect local video $uri", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    fun computeAutoShortSegments(
        totalDurationMs: Long,
        preferredDurationPreset: String,
        baseTitle: String = "Short Automático"
    ): List<ShortSegmentSpec> {
        val safeTotalMs = totalDurationMs.coerceAtLeast(15_000L)
        val targetClipMs = when {
            preferredDurationPreset.contains("15") -> 25_000L
            preferredDurationPreset.contains("60") -> 58_000L
            else -> 42_000L
        }.coerceAtMost(safeTotalMs)

        if (safeTotalMs <= 25_000L) {
            val sec = (safeTotalMs / 1000L).coerceAtLeast(5L)
            return listOf(
                ShortSegmentSpec(
                    index = 1,
                    title = "$baseTitle • Short 1",
                    hookText = "O melhor momento do vídeo em 9:16!",
                    startMs = 0L,
                    endMs = safeTotalMs,
                    startLabel = formatMsToMmSs(0L),
                    endLabel = formatMsToMmSs(safeTotalMs),
                    durationSeconds = sec
                )
            )
        }

        val count = if (safeTotalMs >= targetClipMs * 2) 3 else 2
        val usableEndMs = (safeTotalMs - targetClipMs).coerceAtLeast(1_000L)
        val hooks = listOf(
            "Não vais acreditar no que aconteceu aqui!",
            "O ponto alto da jogada / transmissão!",
            "Olha este detalhe que quase ninguém reparou!"
        )
        val labels = listOf("Abertura Viral", "Clímax 9:16", "Momento Épico")

        return (0 until count).map { idx ->
            val startRatio = when (count) {
                2 -> if (idx == 0) 0.08 else 0.55
                else -> when (idx) {
                    0 -> 0.06
                    1 -> 0.42
                    else -> 0.78
                }
            }
            val startMs = ((usableEndMs * startRatio).toLong()).coerceIn(0L, usableEndMs)
            val endMs = (startMs + targetClipMs).coerceAtMost(safeTotalMs)
            val durationSec = ((endMs - startMs) / 1000L).coerceAtLeast(5L)
            ShortSegmentSpec(
                index = idx + 1,
                title = "${baseTitle.take(45)} • ${labels[idx % labels.size]}",
                hookText = hooks[idx % hooks.size],
                startMs = startMs,
                endMs = endMs,
                startLabel = formatMsToMmSs(startMs),
                endLabel = formatMsToMmSs(endMs),
                durationSeconds = durationSec
            )
        }
    }

    /**
     * Splits a caption script into natural 3-5 word chunks distributed smoothly across [durationMs]
     * so subtitles animate phrase-by-phrase as the speaker talks rather than showing 1 frozen block.
     */
    fun buildTimedCaptionSegments(
        fullText: String,
        durationMs: Long
    ): List<TimedCaptionSegment> {
        val clean = fullText.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return emptyList()
        val safeTotalMs = durationMs.coerceAtLeast(3_000L)

        // Split on sentence boundaries first, then break long sentences into 4-5 word viral chunks
        val rawSentences = clean.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val phrases = mutableListOf<String>()
        for (sentence in rawSentences) {
            val words = sentence.split(" ").filter { it.isNotBlank() }
            if (words.size <= 5 && sentence.length <= 36) {
                phrases.add(sentence)
            } else {
                var i = 0
                while (i < words.size) {
                    val bucket = StringBuilder()
                    var count = 0
                    while (i < words.size && count < 5 && (bucket.length + words[i].length + 1 <= 36 || count == 0)) {
                        if (bucket.isNotEmpty()) bucket.append(' ')
                        bucket.append(words[i])
                        i++
                        count++
                    }
                    if (bucket.isNotEmpty()) phrases.add(bucket.toString())
                }
            }
        }
        if (phrases.isEmpty()) return emptyList()
        if (phrases.size == 1) {
            return listOf(TimedCaptionSegment(0L, safeTotalMs, phrases.first()))
        }

        val totalWeights = phrases.sumOf { it.length.coerceAtLeast(6) }.toDouble()
        var cursorMs = 0L
        return phrases.mapIndexed { idx, phrase ->
            val start = cursorMs
            val end = if (idx == phrases.lastIndex) {
                safeTotalMs
            } else {
                val share = ((phrase.length.coerceAtLeast(6) / totalWeights) * safeTotalMs).toLong()
                    .coerceAtLeast(1_100L)
                (start + share).coerceAtMost(safeTotalMs - (phrases.size - 1 - idx) * 700L)
            }
            cursorMs = end
            TimedCaptionSegment(startMs = start, endMs = end.coerceAtLeast(start + 400L), text = phrase)
        }
    }

    /**
     * Renders a transparent overlay bitmap containing cover headline badge, emoji stickers,
     * and safe-zone stylized subtitles (with highlighted keywords) to be burned into exported MP4 frames.
     */
    fun renderOverlayBitmap(
        aspectRatio: String,
        coverTitle: String,
        emojiOverlay: String,
        captionsText: String,
        captionStyle: String,
        highlightKeywords: String = ""
    ): Bitmap? {
        val cleanTitle = coverTitle.trim()
        val cleanEmoji = emojiOverlay.trim()
        val cleanCaptions = captionsText.trim().take(78)
        if (cleanTitle.isBlank() && cleanEmoji.isBlank() && cleanCaptions.isBlank()) {
            return null
        }

        val (w, h) = when (aspectRatio) {
            "1:1" -> 720 to 720
            "16:9" -> 1280 to 720
            else -> 720 to 1280
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Top cover headline pill
        if (cleanTitle.isNotBlank()) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (w * 0.045f).coerceIn(24f, 44f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textW = (w * 0.80f).toInt()
            val layout = StaticLayout.Builder.obtain(
                cleanTitle.uppercase().take(48),
                0,
                cleanTitle.take(48).length,
                titlePaint,
                textW
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .build()

            val boxPadH = 26f
            val boxPadV = 16f
            val topY = h * 0.06f
            val rect = RectF(
                (w - textW) / 2f - boxPadH,
                topY,
                (w + textW) / 2f + boxPadH,
                topY + layout.height + boxPadV * 2
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(215, 12, 18, 36)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.parseColor("#00E5FF")
            }
            canvas.drawRoundRect(rect, 22f, 22f, bgPaint)
            canvas.drawRoundRect(rect, 22f, 22f, borderPaint)
            canvas.save()
            canvas.translate((w - textW) / 2f, topY + boxPadV)
            layout.draw(canvas)
            canvas.restore()
        }

        // 2. Emoji sticker overlay
        if (cleanEmoji.isNotBlank()) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (w * 0.12f).coerceIn(64f, 110f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cleanEmoji, w * 0.84f, h * 0.24f, emojiPaint)
        }

        // 3. Dynamic caption bar positioned inside YouTube Shorts & TikTok safe zone (~65-70% height)
        //    so bottom channel/title/buttons NEVER clip or cover the subtitles!
        if (cleanCaptions.isNotBlank()) {
            val accentColor = when (captionStyle) {
                "Alto contraste" -> Color.parseColor("#FFB020")
                "Minimalista" -> Color.WHITE
                else -> Color.parseColor("#00E5FF")
            }
            val baseColor = if (captionStyle == "Dinâmico" || captionStyle == "Alto contraste") {
                Color.WHITE
            } else {
                accentColor
            }
            val keywords = highlightKeywords.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            val spannable = SpannableString(cleanCaptions)
            if (keywords.isNotEmpty() && captionStyle != "Minimalista") {
                var offset = 0
                for (token in cleanCaptions.split(" ")) {
                    val cleanToken = token.lowercase().trim(',', '.', '!', '?', ':', ';')
                    if (keywords.any { cleanToken.contains(it) }) {
                        val endOffset = (offset + token.length).coerceAtMost(cleanCaptions.length)
                        spannable.setSpan(
                            ForegroundColorSpan(accentColor),
                            offset,
                            endOffset,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    offset += token.length + 1
                }
            }

            val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = baseColor
                textSize = (w * 0.052f).coerceIn(28f, 48f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(6f, 0f, 3f, Color.BLACK)
            }
            val capW = (w * 0.82f).toInt()
            val layout = StaticLayout.Builder.obtain(
                spannable,
                0,
                spannable.length,
                capPaint,
                capW
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .build()

            val padH = 22f
            val padV = 16f
            // Keep caption box well above the bottom 28% Shorts/TikTok overlay chrome
            val maxBottomY = if (aspectRatio == "9:16") h * 0.70f else h * 0.76f
            val topY = (maxBottomY - layout.height).coerceAtLeast(h * 0.42f)
            val rect = RectF(
                (w - capW) / 2f - padH,
                topY - padV,
                (w + capW) / 2f + padH,
                topY + layout.height + padV
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (captionStyle == "Alto contraste") {
                    Color.argb(238, 0, 0, 0)
                } else {
                    Color.argb(205, 8, 13, 28)
                }
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(140, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            }
            canvas.drawRoundRect(rect, 20f, 20f, bgPaint)
            canvas.drawRoundRect(rect, 20f, 20f, borderPaint)
            canvas.save()
            canvas.translate((w - capW) / 2f, topY)
            layout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }

    /**
     * Frame-accurate Media3 [BitmapOverlay] that swaps subtitle phrases cleanly at each
     * [TimedCaptionSegment] boundary without unnecessary GL texture re-uploads per frame.
     */
    private class TimedCaptionsBitmapOverlay(
        private val aspectRatio: String,
        private val coverTitle: String,
        private val emojiOverlay: String,
        private val timedSegments: List<TimedCaptionSegment>,
        private val fallbackCaptionText: String,
        private val captionStyle: String,
        private val highlightKeywords: String
    ) : BitmapOverlay() {
        private var currentSegIndex = Int.MIN_VALUE
        private var activeBitmap: Bitmap? = null

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val timeMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
            val targetIdx = if (timedSegments.isEmpty()) {
                -1
            } else {
                val matched = timedSegments.indexOfFirst { timeMs >= it.startMs && timeMs < it.endMs }
                when {
                    matched >= 0 -> matched
                    timeMs >= timedSegments.last().endMs -> timedSegments.lastIndex
                    else -> 0
                }
            }

            val existing = activeBitmap
            if (targetIdx == currentSegIndex && existing != null && !existing.isRecycled) {
                return existing
            }

            val textForFrame = if (targetIdx >= 0) timedSegments[targetIdx].text else fallbackCaptionText
            val rendered = renderOverlayBitmap(
                aspectRatio = aspectRatio,
                coverTitle = coverTitle,
                emojiOverlay = emojiOverlay,
                captionsText = textForFrame,
                captionStyle = captionStyle,
                highlightKeywords = highlightKeywords
            ) ?: Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

            currentSegIndex = targetIdx
            activeBitmap = rendered
            return rendered
        }

        override fun release() {
            super.release()
            activeBitmap = null
        }
    }

    private fun buildVideoEffectsList(
        aspectRatio: String,
        manualPanX: Float,
        visualStyle: ClipVisualStyle,
        captionsText: String,
        captionStyle: String,
        timedCaptions: List<TimedCaptionSegment> = emptyList(),
        highlightKeywords: String = "",
        clipDurationMs: Long = 15_000L
    ): List<Effect> = buildList {
        val effectivePan = if (manualPanX != 0f) manualPanX else visualStyle.panX
        if (effectivePan != 0f && aspectRatio != "16:9") {
            val shift = (effectivePan.coerceIn(-1f, 1f) * 0.25f)
            val left = (-1f + shift).coerceIn(-1f, 0f)
            val right = (1f + shift).coerceIn(0f, 1f)
            if (right - left >= 0.4f) {
                add(Crop(left, right, -1f, 1f))
            }
        }
        if (visualStyle.zoomFactor > 1.02f) {
            val z = visualStyle.zoomFactor.coerceIn(1f, 1.8f)
            add(ScaleAndRotateTransformation.Builder().setScale(z, z).build())
        }
        addAll(visualStyle.filterPreset.toMedia3Effects())

        val targetAspect = when (aspectRatio) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            else -> 9f / 16f
        }
        add(
            Presentation.createForAspectRatio(
                targetAspect,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        if (visualStyle.burnOverlayOnExport) {
            val segments = timedCaptions.ifEmpty {
                buildTimedCaptionSegments(captionsText, clipDurationMs)
            }
            val hasAnyOverlay = visualStyle.coverTitle.isNotBlank() ||
                visualStyle.emojiOverlay.isNotBlank() ||
                segments.isNotEmpty() ||
                captionsText.isNotBlank()
            if (hasAnyOverlay) {
                add(
                    OverlayEffect(
                        listOf(
                            TimedCaptionsBitmapOverlay(
                                aspectRatio = aspectRatio,
                                coverTitle = visualStyle.coverTitle,
                                emojiOverlay = visualStyle.emojiOverlay,
                                timedSegments = segments,
                                fallbackCaptionText = captionsText,
                                captionStyle = captionStyle,
                                highlightKeywords = highlightKeywords
                            )
                        )
                    )
                )
            }
        }
    }

    fun exportShortClip(
        context: Context,
        inputUri: Uri,
        clipId: String,
        startMs: Long,
        endMs: Long,
        aspectRatio: String,
        manualPanX: Float = 0f,
        visualStyle: ClipVisualStyle = ClipVisualStyle(),
        captionsText: String = "",
        captionStyle: String = "Dinâmico",
        timedCaptions: List<TimedCaptionSegment> = emptyList(),
        highlightKeywords: String = "",
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val mainHandler = Handler(Looper.getMainLooper())
        var activeTransformer: Transformer? = null
        val cancelled = AtomicBoolean(false)

        thread(start = true, isDaemon = true, name = "ShortAudioProbe") {
            val rawStartMs = startMs.coerceAtLeast(0L)
            val rawEndMs = if (endMs > rawStartMs + 1000L) endMs else rawStartMs + 30_000L
            // Vozo-style AI Trim: tighten lead-in/lead-out dead air on clips >= 10s
            val trimShift = if (visualStyle.aiTrimTighten && (rawEndMs - rawStartMs) >= 10_000L) 450L else 0L
            val safeStartMs = rawStartMs + trimShift
            val safeEndMs = (rawEndMs - trimShift).coerceAtLeast(safeStartMs + 3_000L)
            val clipDurationMs = (safeEndMs - safeStartMs).coerceAtLeast(2_000L)
            val hasSourceAudio = StudioAudioGenerator.hasAudioTrack(context, inputUri)
            val fallbackWav = if (!hasSourceAudio) {
                StudioAudioGenerator.generateStudioTrackWav(
                    context = context,
                    fileName = "silent_fix_${clipId}_${System.currentTimeMillis()}.wav",
                    durationMs = clipDurationMs
                )
            } else null

            if (cancelled.get()) return@thread

            mainHandler.post {
                if (cancelled.get()) return@post
                try {
                    val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    val tempFile = File(shortsDir(context), "tmp_short_$safeId.mp4")
                    val finalFile = File(shortsDir(context), "short_$safeId.mp4")
                    if (tempFile.exists()) tempFile.delete()

                    val mediaItem = MediaItem.Builder()
                        .setUri(inputUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(safeStartMs)
                                .setEndPositionMs(safeEndMs)
                                .build()
                        )
                        .build()

                    val videoEffects = buildVideoEffectsList(
                        aspectRatio = aspectRatio,
                        manualPanX = manualPanX,
                        visualStyle = visualStyle,
                        captionsText = captionsText,
                        captionStyle = captionStyle,
                        timedCaptions = timedCaptions,
                        highlightKeywords = highlightKeywords,
                        clipDurationMs = clipDurationMs
                    )

                    val editedVideoItem = EditedMediaItem.Builder(mediaItem)
                        .setEffects(Effects(emptyList(), videoEffects))
                        .build()

                    activeTransformer = startTransformer(
                        context = context,
                        mainHandler = mainHandler,
                        tempFile = tempFile,
                        finalFile = finalFile,
                        isCancelled = { cancelled.get() },
                        onProgress = onProgress,
                        onSuccess = onSuccess,
                        onError = onError
                    ).also { transformer ->
                        onProgress(2)
                        if (fallbackWav != null) {
                            val videoSeq = EditedMediaItemSequence.withVideoFrom(listOf(editedVideoItem))
                            val audioItem = EditedMediaItem.Builder(
                                MediaItem.fromUri(Uri.fromFile(fallbackWav))
                            ).build()
                            val audioSeq = EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
                            val composition = Composition.Builder(videoSeq, audioSeq).build()
                            transformer.start(composition, tempFile.absolutePath)
                        } else {
                            transformer.start(editedVideoItem, tempFile.absolutePath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Media3 Transformer", e)
                    onError(e.localizedMessage ?: "Não foi possível iniciar o corte do vídeo.")
                }
            }
        }

        return {
            cancelled.set(true)
            mainHandler.post {
                try {
                    activeTransformer?.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Converts one or more photos / YouTube thumbnails / custom cover images into a real
     * playable & uploadable MP4 Short using Media3 Transformer (with filters, emojis, cover title,
     * dynamic safe-zone subtitles, and a guaranteed stereo AAC audio track).
     */
    fun exportShortFromImages(
        context: Context,
        imageFiles: List<File>,
        clipId: String,
        totalDurationMs: Long = 12_000L,
        aspectRatio: String = "9:16",
        visualStyle: ClipVisualStyle = ClipVisualStyle(),
        captionsText: String = "",
        captionStyle: String = "Dinâmico",
        timedCaptions: List<TimedCaptionSegment> = emptyList(),
        highlightKeywords: String = "",
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val validFiles = imageFiles.filter { it.exists() && it.length() > 100L }
        if (validFiles.isEmpty()) {
            onError("Nenhuma imagem disponível para gerar o Short MP4.")
            return {}
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var activeTransformer: Transformer? = null
        val cancelled = AtomicBoolean(false)

        thread(start = true, isDaemon = true, name = "ShortImageAudioPrep") {
            val perSlideMs = (totalDurationMs.coerceIn(5_000L, 45_000L) / validFiles.size)
                .coerceAtLeast(3_000L)
            val actualDurationMs = perSlideMs * validFiles.size
            val audioWav = StudioAudioGenerator.generateStudioTrackWav(
                context = context,
                fileName = "bgm_${clipId}_${System.currentTimeMillis()}.wav",
                durationMs = actualDurationMs
            )
            if (cancelled.get()) return@thread

            mainHandler.post {
                if (cancelled.get()) return@post
                try {
                    val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    val tempFile = File(shortsDir(context), "tmp_img_short_$safeId.mp4")
                    val finalFile = File(shortsDir(context), "short_$safeId.mp4")
                    if (tempFile.exists()) tempFile.delete()

                    val videoEffects = buildVideoEffectsList(
                        aspectRatio = aspectRatio,
                        manualPanX = 0f,
                        visualStyle = visualStyle,
                        captionsText = captionsText,
                        captionStyle = captionStyle,
                        timedCaptions = timedCaptions,
                        highlightKeywords = highlightKeywords,
                        clipDurationMs = actualDurationMs
                    )

                    val editedItems = validFiles.mapIndexed { idx, file ->
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.fromFile(file))
                            .setImageDurationMs(perSlideMs)
                            .build()
                        val zoomScale = if (idx % 2 == 0) 1.06f else 1.12f
                        val itemEffects = listOf(
                            ScaleAndRotateTransformation.Builder().setScale(zoomScale, zoomScale).build()
                        ) + videoEffects
                        EditedMediaItem.Builder(mediaItem)
                            .setDurationUs(perSlideMs * 1000L)
                            .setFrameRate(30)
                            .setEffects(Effects(emptyList(), itemEffects))
                            .build()
                    }

                    val videoSequence = EditedMediaItemSequence.withVideoFrom(editedItems)
                    val audioItem = EditedMediaItem.Builder(
                        MediaItem.fromUri(Uri.fromFile(audioWav))
                    ).build()
                    val audioSequence = EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
                    val composition = Composition.Builder(videoSequence, audioSequence).build()

                    activeTransformer = startTransformer(
                        context = context,
                        mainHandler = mainHandler,
                        tempFile = tempFile,
                        finalFile = finalFile,
                        isCancelled = { cancelled.get() },
                        onProgress = onProgress,
                        onSuccess = onSuccess,
                        onError = onError
                    ).also { transformer ->
                        onProgress(3)
                        transformer.start(composition, tempFile.absolutePath)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Media3 image-to-MP4 Transformer", e)
                    onError(e.localizedMessage ?: "Falha ao converter imagem(ns) em vídeo MP4.")
                }
            }
        }

        return {
            cancelled.set(true)
            mainHandler.post {
                try {
                    activeTransformer?.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun copyPhotoUrisToCacheFiles(
        context: Context,
        uris: List<Uri>,
        prefix: String
    ): List<File> = withContext(Dispatchers.IO) {
        val dir = imageSlidesDir(context)
        uris.mapIndexedNotNull { idx, uri ->
            try {
                val bmp = CoverStudioRenderer.decodeUriBitmap(context, uri, maxDimension = 1920)
                    ?: return@mapIndexedNotNull null
                val dest = File(dir, "${prefix}_${idx}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(dest).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                dest
            } catch (e: Exception) {
                Log.w(TAG, "Failed copying photo $uri: ${e.message}")
                null
            }
        }
    }

    private fun startTransformer(
        context: Context,
        mainHandler: Handler,
        tempFile: File,
        finalFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ): Transformer {
        val progressHolder = ProgressHolder()
        var transformerRef: Transformer? = null
        val progressRunnable = object : Runnable {
            override fun run() {
                val transformer = transformerRef ?: return
                if (isCancelled()) return
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }

        val transformer = Transformer.Builder(context.applicationContext)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    mainHandler.removeCallbacks(progressRunnable)
                    if (isCancelled()) return
                    if (tempFile.exists() && tempFile.length() > 1024L) {
                        if (finalFile.exists()) finalFile.delete()
                        if (tempFile.renameTo(finalFile)) {
                            onProgress(100)
                            onSuccess(finalFile)
                        } else {
                            tempFile.copyTo(finalFile, overwrite = true)
                            tempFile.delete()
                            onProgress(100)
                            onSuccess(finalFile)
                        }
                    } else {
                        onError("O ficheiro MP4 gerado ficou vazio. Tenta outro intervalo ou foto.")
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    mainHandler.removeCallbacks(progressRunnable)
                    if (isCancelled()) return
                    Log.e(TAG, "Media3 Transformer export failed", exportException)
                    if (tempFile.exists()) tempFile.delete()
                    onError(
                        exportException.localizedMessage
                            ?: "Falha ao renderizar o Short MP4 no dispositivo."
                    )
                }
            })
            .build()

        transformerRef = transformer
        mainHandler.postDelayed(progressRunnable, 250L)
        return transformer
    }

    suspend fun saveShortToGallery(
        context: Context,
        mp4File: File,
        clipTitle: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!mp4File.exists()) {
            return@withContext Result.failure(IllegalStateException("Short MP4 ainda não foi gerado."))
        }
        try {
            val safeName = clipTitle
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(" ", "_")
                .take(40)
                .ifBlank { "PlanetShort" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/PlanetStreaming"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Não foi possível criar registo na galeria."))
                resolver.openOutputStream(uri)?.use { out ->
                    mp4File.inputStream().use { input -> input.copyTo(out) }
                } ?: return@withContext Result.failure(IllegalStateException("Falha ao escrever ficheiro na galeria."))
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success(uri)
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, "PlanetStreaming").apply { mkdirs() }
                val dest = File(targetDir, fileName)
                FileOutputStream(dest).use { out ->
                    mp4File.inputStream().use { input -> input.copyTo(out) }
                }
                Result.success(Uri.fromFile(dest))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Short to gallery", e)
            Result.failure(e)
        }
    }

    fun shareShortToYouTube(
        context: Context,
        mp4File: File,
        title: String,
        captionNotes: String
    ): Boolean {
        copyCaptionToClipboard(context, "$title #Shorts\n\n$captionNotes")
        return shareVideoFile(
            context = context,
            mp4File = mp4File,
            title = "$title #Shorts",
            text = captionNotes,
            preferredPackages = listOf("com.google.android.youtube"),
            chooserTitle = "Publicar no YouTube Shorts"
        )
    }

    fun shareShortToTikTok(
        context: Context,
        mp4File: File,
        title: String,
        captionNotes: String
    ): Boolean {
        copyCaptionToClipboard(context, "$title\n\n$captionNotes")
        return shareVideoFile(
            context = context,
            mp4File = mp4File,
            title = title,
            text = captionNotes,
            preferredPackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            chooserTitle = "Enviar Short para TikTok"
        )
    }

    fun shareShortGeneric(
        context: Context,
        mp4File: File,
        title: String,
        captionNotes: String
    ): Boolean {
        copyCaptionToClipboard(context, "$title\n\n$captionNotes")
        return shareVideoFile(
            context = context,
            mp4File = mp4File,
            title = title,
            text = captionNotes,
            preferredPackages = emptyList(),
            chooserTitle = "Partilhar Short MP4 9:16"
        )
    }

    fun openTikTokProfileOrApp(context: Context, handle: String): Boolean {
        val clean = StudioLocalPrefs.normalizeTikTokHandle(handle)
        val url = if (clean.isNotBlank()) "https://www.tiktok.com/$clean" else "https://www.tiktok.com/"
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyCaptionToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Planet Streaming Short", text.trim()))
        } catch (_: Exception) {
        }
    }

    private fun shareVideoFile(
        context: Context,
        mp4File: File,
        title: String,
        text: String,
        preferredPackages: List<String>,
        chooserTitle: String
    ): Boolean {
        if (!mp4File.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                mp4File
            )
            val needsNewTask = context !is android.app.Activity
            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n\n$text".trim())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            for (pkg in preferredPackages) {
                val targeted = Intent(baseIntent).apply { setPackage(pkg) }
                if (targeted.resolveActivity(context.packageManager) != null) {
                    context.startActivity(targeted)
                    return true
                }
            }

            val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
                if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share MP4 Short", e)
            false
        }
    }
}
