package com.example.media

import android.content.Context
import android.graphics.ColorMatrix as AndroidColorMatrix
import androidx.compose.ui.graphics.ColorMatrix
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import org.json.JSONObject

enum class VideoFilterPreset(
    val id: String,
    val label: String,
    val badgeColorHex: Long
) {
    ORIGINAL("original", "Original", 0xFF94A3B8),
    NEON_CYBER("neon", "Neon Cyber", 0xFF00E5FF),
    CINEMA_WARM("cinema", "Cinema", 0xFFFFB020),
    HIGH_CONTRAST("contrast", "Vibrante", 0xFF8B5CF6),
    BW_DRAMATIC("bw", "P&B Pro", 0xFFE2E8F0),
    GTA_SUNSET("sunset", "Sunset", 0xFFFF5E7E);

    fun toComposeColorMatrix(): ColorMatrix = ColorMatrix(toColorMatrixArray())

    fun toAndroidColorMatrix(): AndroidColorMatrix = AndroidColorMatrix(toColorMatrixArray())

    private fun toColorMatrixArray(): FloatArray = when (this) {
        ORIGINAL -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        NEON_CYBER -> floatArrayOf(
            1.04f, 0f, 0f, 0f, -6f,
            0f, 1.12f, 0f, 0f, 8f,
            0f, 0f, 1.28f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
        CINEMA_WARM -> floatArrayOf(
            1.22f, 0.05f, 0f, 0f, 10f,
            0f, 1.08f, 0f, 0f, 4f,
            0f, 0f, 0.86f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
        HIGH_CONTRAST -> floatArrayOf(
            1.28f, 0f, 0f, 0f, -22f,
            0f, 1.28f, 0f, 0f, -22f,
            0f, 0f, 1.28f, 0f, -22f,
            0f, 0f, 0f, 1f, 0f
        )
        BW_DRAMATIC -> floatArrayOf(
            0.35f, 0.65f, 0.14f, 0f, -12f,
            0.35f, 0.65f, 0.14f, 0f, -12f,
            0.35f, 0.65f, 0.14f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
        GTA_SUNSET -> floatArrayOf(
            1.26f, 0.06f, 0f, 0f, 14f,
            0f, 0.96f, 0.04f, 0f, -4f,
            0.05f, 0f, 1.14f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun toMedia3Effects(): List<Effect> = when (this) {
        ORIGINAL -> emptyList()
        NEON_CYBER -> listOf(
            RgbAdjustment.Builder()
                .setRedScale(0.95f)
                .setGreenScale(1.08f)
                .setBlueScale(1.24f)
                .build(),
            HslAdjustment.Builder().adjustSaturation(22f).build()
        )
        CINEMA_WARM -> listOf(
            RgbAdjustment.Builder()
                .setRedScale(1.18f)
                .setGreenScale(1.04f)
                .setBlueScale(0.86f)
                .build(),
            Contrast(0.18f)
        )
        HIGH_CONTRAST -> listOf(
            Contrast(0.32f),
            HslAdjustment.Builder().adjustSaturation(28f).build()
        )
        BW_DRAMATIC -> listOf(
            RgbFilter.createGrayscaleFilter(),
            Contrast(0.28f)
        )
        GTA_SUNSET -> listOf(
            RgbAdjustment.Builder()
                .setRedScale(1.22f)
                .setGreenScale(0.94f)
                .setBlueScale(1.12f)
                .build(),
            HslAdjustment.Builder().adjustSaturation(25f).build()
        )
    }

    companion object {
        fun fromId(id: String?): VideoFilterPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ORIGINAL
    }
}

data class ClipVisualStyle(
    val filterId: String = VideoFilterPreset.ORIGINAL.id,
    val emojiOverlay: String = "",
    val coverTitle: String = "",
    val coverImagePath: String = "",
    val burnOverlayOnExport: Boolean = true,
    val zoomFactor: Float = 1f,
    val panX: Float = 0f,
    val aiTrimTighten: Boolean = false,
    val audioBoost: Boolean = true
) {
    val filterPreset: VideoFilterPreset
        get() = VideoFilterPreset.fromId(filterId)
}

data class TikTokAccountState(
    val isConnected: Boolean = false,
    val handle: String = "",
    val apiAccessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val openId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val grantedScopes: String = ""
) {
    val hasApiUploadToken: Boolean
        get() = apiAccessToken.isNotBlank()

    val isOAuthConnected: Boolean
        get() = apiAccessToken.isNotBlank() && (openId.isNotBlank() || refreshToken.isNotBlank())
}

data class PendingTikTokOAuthState(
    val state: String,
    val codeVerifier: String
)

data class YouTubePersistentState(
    val wasConnected: Boolean = false,
    val channelTitle: String = "",
    val channelHint: String = "",
    val hasUploadScope: Boolean = false
)

val STUDIO_EMOJI_PACK: List<String> = listOf(
    "🔥", "😱", "🚀", "⚡", "🎯", "💥", "😂", "👑", "💯", "🎮", "🎬", "👀"
)

object StudioLocalPrefs {
    private const val PREFS_NAME = "planet_studio_local_prefs"
    private const val KEY_CLIP_STYLES = "clip_visual_styles_json"
    private const val KEY_TIKTOK_CONNECTED = "tiktok_connected"
    private const val KEY_TIKTOK_HANDLE = "tiktok_handle"
    private const val KEY_TIKTOK_API_TOKEN = "tiktok_api_token"
    private const val KEY_TIKTOK_REFRESH_TOKEN = "tiktok_refresh_token"
    private const val KEY_TIKTOK_EXPIRES_AT = "tiktok_expires_at"
    private const val KEY_TIKTOK_OPEN_ID = "tiktok_open_id"
    private const val KEY_TIKTOK_DISPLAY_NAME = "tiktok_display_name"
    private const val KEY_TIKTOK_AVATAR_URL = "tiktok_avatar_url"
    private const val KEY_TIKTOK_SCOPES = "tiktok_scopes"
    private const val KEY_TIKTOK_PENDING_STATE = "tiktok_pending_oauth_state"
    private const val KEY_TIKTOK_PENDING_VERIFIER = "tiktok_pending_oauth_verifier"
    private const val KEY_AUTO_PUBLISH_ON_CUT = "auto_publish_on_cut"
    private const val KEY_YT_WAS_CONNECTED = "yt_was_connected"
    private const val KEY_YT_CHANNEL_TITLE = "yt_channel_title"
    private const val KEY_YT_CHANNEL_HINT = "yt_channel_hint"
    private const val KEY_YT_HAS_UPLOAD = "yt_has_upload"
    private const val KEY_EXPORT_QUALITY = "export_quality_label"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    fun loadAllClipStyles(context: Context): Map<String, ClipVisualStyle> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CLIP_STYLES, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = LinkedHashMap<String, ClipVisualStyle>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val clipId = keys.next()
                val obj = root.optJSONObject(clipId) ?: continue
                result[clipId] = ClipVisualStyle(
                    filterId = obj.optString("filterId", VideoFilterPreset.ORIGINAL.id),
                    emojiOverlay = obj.optString("emojiOverlay", ""),
                    coverTitle = obj.optString("coverTitle", ""),
                    coverImagePath = obj.optString("coverImagePath", ""),
                    burnOverlayOnExport = obj.optBoolean("burnOverlayOnExport", true),
                    zoomFactor = obj.optDouble("zoomFactor", 1.0).toFloat().coerceIn(1f, 2f),
                    panX = obj.optDouble("panX", 0.0).toFloat().coerceIn(-1f, 1f),
                    aiTrimTighten = obj.optBoolean("aiTrimTighten", false),
                    audioBoost = obj.optBoolean("audioBoost", true)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveClipStyle(context: Context, clipId: String, style: ClipVisualStyle): Map<String, ClipVisualStyle> {
        val current = loadAllClipStyles(context).toMutableMap()
        current[clipId] = style
        val root = JSONObject()
        current.forEach { (id, item) ->
            val obj = JSONObject().apply {
                put("filterId", item.filterId)
                put("emojiOverlay", item.emojiOverlay)
                put("coverTitle", item.coverTitle)
                put("coverImagePath", item.coverImagePath)
                put("burnOverlayOnExport", item.burnOverlayOnExport)
                put("zoomFactor", item.zoomFactor.toDouble())
                put("panX", item.panX.toDouble())
                put("aiTrimTighten", item.aiTrimTighten)
                put("audioBoost", item.audioBoost)
            }
            root.put(id, obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIP_STYLES, root.toString())
            .apply()
        return current
    }

    fun savePendingTikTokOAuth(context: Context, state: String, codeVerifier: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIKTOK_PENDING_STATE, state)
            .putString(KEY_TIKTOK_PENDING_VERIFIER, codeVerifier)
            .apply()
    }

    fun consumePendingTikTokOAuth(context: Context): PendingTikTokOAuthState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = prefs.getString(KEY_TIKTOK_PENDING_STATE, "").orEmpty()
        val verifier = prefs.getString(KEY_TIKTOK_PENDING_VERIFIER, "").orEmpty()
        prefs.edit()
            .remove(KEY_TIKTOK_PENDING_STATE)
            .remove(KEY_TIKTOK_PENDING_VERIFIER)
            .apply()
        return if (state.isNotBlank() && verifier.isNotBlank()) {
            PendingTikTokOAuthState(state = state, codeVerifier = verifier)
        } else null
    }

    fun loadTikTokAccount(context: Context): TikTokAccountState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val connected = prefs.getBoolean(KEY_TIKTOK_CONNECTED, false)
        val handle = prefs.getString(KEY_TIKTOK_HANDLE, "").orEmpty()
        val token = prefs.getString(KEY_TIKTOK_API_TOKEN, "").orEmpty()
        val refreshToken = prefs.getString(KEY_TIKTOK_REFRESH_TOKEN, "").orEmpty()
        val expiresAt = prefs.getLong(KEY_TIKTOK_EXPIRES_AT, 0L)
        val openId = prefs.getString(KEY_TIKTOK_OPEN_ID, "").orEmpty()
        val displayName = prefs.getString(KEY_TIKTOK_DISPLAY_NAME, "").orEmpty()
        val avatarUrl = prefs.getString(KEY_TIKTOK_AVATAR_URL, "").orEmpty()
        val scopes = prefs.getString(KEY_TIKTOK_SCOPES, "").orEmpty()
        val active = connected && (handle.isNotBlank() || token.isNotBlank() || openId.isNotBlank())
        return TikTokAccountState(
            isConnected = active,
            handle = handle,
            apiAccessToken = token,
            refreshToken = refreshToken,
            expiresAtEpochMs = expiresAt,
            openId = openId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            grantedScopes = scopes
        )
    }

    fun saveTikTokOAuthSession(
        context: Context,
        accountState: TikTokAccountState
    ): TikTokAccountState {
        val normalizedHandle = normalizeTikTokHandle(
            accountState.handle.ifBlank { accountState.displayName.replace(" ", "") }
        )
        val finalState = accountState.copy(
            isConnected = true,
            handle = normalizedHandle.ifBlank { "@tiktok_creator" }
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TIKTOK_CONNECTED, finalState.isConnected)
            .putString(KEY_TIKTOK_HANDLE, finalState.handle)
            .putString(KEY_TIKTOK_API_TOKEN, finalState.apiAccessToken)
            .putString(KEY_TIKTOK_REFRESH_TOKEN, finalState.refreshToken)
            .putLong(KEY_TIKTOK_EXPIRES_AT, finalState.expiresAtEpochMs)
            .putString(KEY_TIKTOK_OPEN_ID, finalState.openId)
            .putString(KEY_TIKTOK_DISPLAY_NAME, finalState.displayName)
            .putString(KEY_TIKTOK_AVATAR_URL, finalState.avatarUrl)
            .putString(KEY_TIKTOK_SCOPES, finalState.grantedScopes)
            .apply()
        return finalState
    }

    fun saveTikTokAccount(
        context: Context,
        handle: String,
        apiAccessToken: String = ""
    ): TikTokAccountState {
        val clean = normalizeTikTokHandle(handle)
        val connected = clean.isNotBlank()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingToken = if (apiAccessToken.isNotBlank()) apiAccessToken.trim() else {
            prefs.getString(KEY_TIKTOK_API_TOKEN, "").orEmpty()
        }
        val prev = loadTikTokAccount(context)
        prefs.edit()
            .putBoolean(KEY_TIKTOK_CONNECTED, connected)
            .putString(KEY_TIKTOK_HANDLE, clean)
            .putString(KEY_TIKTOK_API_TOKEN, if (connected) existingToken else "")
            .apply()
        return prev.copy(
            isConnected = connected,
            handle = clean,
            apiAccessToken = if (connected) existingToken else ""
        )
    }

    fun clearTikTokAccount(context: Context): TikTokAccountState {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TIKTOK_CONNECTED, false)
            .putString(KEY_TIKTOK_HANDLE, "")
            .putString(KEY_TIKTOK_API_TOKEN, "")
            .putString(KEY_TIKTOK_REFRESH_TOKEN, "")
            .putLong(KEY_TIKTOK_EXPIRES_AT, 0L)
            .putString(KEY_TIKTOK_OPEN_ID, "")
            .putString(KEY_TIKTOK_DISPLAY_NAME, "")
            .putString(KEY_TIKTOK_AVATAR_URL, "")
            .putString(KEY_TIKTOK_SCOPES, "")
            .remove(KEY_TIKTOK_PENDING_STATE)
            .remove(KEY_TIKTOK_PENDING_VERIFIER)
            .apply()
        return TikTokAccountState()
    }

    fun loadAutoPublishOnCut(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PUBLISH_ON_CUT, true)

    fun saveAutoPublishOnCut(context: Context, enabled: Boolean): Boolean {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_PUBLISH_ON_CUT, enabled)
            .apply()
        return enabled
    }

    fun loadExportQuality(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXPORT_QUALITY, null)
            ?.takeIf { it.isNotBlank() }
            ?: "1080p (Recomendado)"

    fun saveExportQuality(context: Context, quality: String): String {
        val clean = quality.trim().ifBlank { "1080p (Recomendado)" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXPORT_QUALITY, clean)
            .apply()
        return clean
    }

    fun loadNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun saveNotificationsEnabled(context: Context, enabled: Boolean): Boolean {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
        return enabled
    }

    fun loadYouTubePersistentState(context: Context): YouTubePersistentState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return YouTubePersistentState(
            wasConnected = prefs.getBoolean(KEY_YT_WAS_CONNECTED, false),
            channelTitle = prefs.getString(KEY_YT_CHANNEL_TITLE, "").orEmpty(),
            channelHint = prefs.getString(KEY_YT_CHANNEL_HINT, "").orEmpty(),
            hasUploadScope = prefs.getBoolean(KEY_YT_HAS_UPLOAD, false)
        )
    }

    fun saveYouTubePersistentState(context: Context, state: YouTubePersistentState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_YT_WAS_CONNECTED, state.wasConnected)
            .putString(KEY_YT_CHANNEL_TITLE, state.channelTitle)
            .putString(KEY_YT_CHANNEL_HINT, state.channelHint)
            .putBoolean(KEY_YT_HAS_UPLOAD, state.hasUploadScope)
            .apply()
    }

    fun clearYouTubePersistentState(context: Context) {
        saveYouTubePersistentState(context, YouTubePersistentState())
    }

    fun normalizeTikTokHandle(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("https://www.tiktok.com/")
            .removePrefix("https://tiktok.com/")
            .substringBefore("?")
            .substringBefore("/")
            .trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }
}
