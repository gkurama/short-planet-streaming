package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageSizeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

private const val TAG = "CoverStudioRenderer"

object CoverStudioRenderer {

    fun coversDir(context: Context): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    fun getCoverFile(context: Context, clipId: String): File? {
        if (clipId.isBlank()) return null
        val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(coversDir(context), "cover_$safeId.png")
        return file.takeIf { it.exists() && it.length() > 1024L }
    }

    suspend fun downloadBitmapFromUrl(
        url: String,
        okHttpClient: OkHttpClient
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading bitmap from $url: ${e.message}")
            null
        }
    }

    suspend fun decodeUriBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed decoding image URI $uri", e)
            null
        }
    }

    fun renderCoverBitmap(
        headline: String,
        subtitle: String = "",
        emojiBadge: String = "",
        backgroundBitmap: Bitmap? = null,
        filterPreset: VideoFilterPreset = VideoFilterPreset.NEON_CYBER,
        aspectOption: ImageAspectOption = ImageAspectOption.PORTRAIT_9_16,
        sizeOption: ImageSizeOption = ImageSizeOption.SIZE_1K
    ): Bitmap {
        val baseScale = when (sizeOption) {
            ImageSizeOption.SIZE_4K -> 1.5f
            ImageSizeOption.SIZE_2K -> 1.25f
            ImageSizeOption.SIZE_1K -> 1.0f
        }
        val (w, h) = when (aspectOption) {
            ImageAspectOption.PORTRAIT_9_16 -> (1080 * baseScale).toInt() to (1920 * baseScale).toInt()
            ImageAspectOption.LANDSCAPE_16_9 -> (1920 * baseScale).toInt() to (1080 * baseScale).toInt()
            ImageAspectOption.SQUARE_1_1 -> (1080 * baseScale).toInt() to (1080 * baseScale).toInt()
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Background layer (picked photo / YouTube HD thumbnail / Studio gradient)
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled) {
            drawCenterCropBitmap(canvas, backgroundBitmap, w, h, filterPreset)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(
                        Color.parseColor("#080D1C"),
                        Color.parseColor("#1D1346"),
                        Color.parseColor("#0A2540")
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f, h * 0.35f, w * 0.65f,
                    intArrayOf(Color.argb(145, 0, 229, 255), Color.argb(75, 139, 92, 246), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(w * 0.5f, h * 0.35f, w * 0.65f, glowPaint)
        }

        // 2. Top & bottom vignette scrims for high readability
        val topScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.42f,
                Color.argb(210, 6, 10, 22),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.42f, topScrim)

        val bottomScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, h * 0.52f, 0f, h.toFloat(),
                Color.TRANSPARENT,
                Color.argb(235, 6, 10, 22),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.52f, w.toFloat(), h.toFloat(), bottomScrim)

        // 3. Outer studio neon border
        val margin = w * 0.028f
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.01f
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#00E5FF"),
                Color.parseColor("#8B5CF6"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            RectF(margin, margin, w - margin, h - margin),
            w * 0.045f, w * 0.045f,
            borderPaint
        )

        // 4. Emoji sticker burst (top-right or top-center)
        val cleanEmoji = emojiBadge.trim()
        if (cleanEmoji.isNotBlank()) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = w * 0.12f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cleanEmoji, w * 0.84f, h * 0.14f, emojiPaint)
        }

        // 5. Top pill badge ("PLANET SHORTS")
        val badgeText = if (subtitle.isNotBlank()) subtitle.take(28).uppercase() else "SHORTS • HD"
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#080D1C")
            textSize = w * 0.036f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeTextWidth = badgePaint.measureText(badgeText)
        val pillPadH = w * 0.04f
        val pillRect = RectF(
            margin + w * 0.03f,
            margin + h * 0.03f,
            margin + w * 0.03f + badgeTextWidth + pillPadH * 2,
            margin + h * 0.03f + w * 0.075f
        )
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
        }
        canvas.drawRoundRect(pillRect, 40f, 40f, pillBgPaint)
        val fontMetrics = badgePaint.fontMetrics
        val badgeY = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(badgeText, pillRect.left + pillPadH, badgeY, badgePaint)

        // 6. Big bold headline at bottom third
        val cleanTitle = headline.trim().ifBlank { "MOMENTO ÉPICO" }.uppercase().take(65)
        val textWidth = (w * 0.86f).toInt()
        val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#060A16")
            textSize = w * 0.085f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = w * 0.016f
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.085f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val strokeLayout = StaticLayout.Builder.obtain(cleanTitle, 0, cleanTitle.length, strokePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()
        val fillLayout = StaticLayout.Builder.obtain(cleanTitle, 0, cleanTitle.length, fillPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()

        val titleTop = h * 0.72f - (fillLayout.height * 0.35f)
        canvas.save()
        canvas.translate((w - textWidth) / 2f, titleTop)
        strokeLayout.draw(canvas)
        fillLayout.draw(canvas)
        canvas.restore()

        return out
    }

    private fun drawCenterCropBitmap(
        canvas: Canvas,
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        filterPreset: VideoFilterPreset
    ) {
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        val left = (targetW - scaledW) / 2f
        val top = (targetH - scaledH) / 2f
        val dst = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (filterPreset != VideoFilterPreset.ORIGINAL) {
                colorFilter = ColorMatrixColorFilter(filterPreset.toAndroidColorMatrix())
            }
        }
        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), dst, paint)
    }

    suspend fun saveCoverForClip(
        context: Context,
        clipId: String,
        bitmap: Bitmap
    ): File = withContext(Dispatchers.IO) {
        val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(coversDir(context), "cover_$safeId.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
        }
        file
    }

    suspend fun saveCoverToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim().replace(" ", "_").take(36).ifBlank { "PlanetCover" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PlanetStreaming")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Não foi possível guardar capa na galeria."))
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success(uri)
            } else {
                val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picDir, "PlanetStreaming").apply { mkdirs() }
                val dest = File(targetDir, fileName)
                FileOutputStream(dest).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Result.success(Uri.fromFile(dest))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving cover to gallery", e)
            Result.failure(e)
        }
    }
}
