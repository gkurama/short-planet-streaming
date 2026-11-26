package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class VideoSource(
    @DocumentId val id: String = "",
    val userId: String = "",
    val youtubeUrl: String = "",
    val youtubeVideoId: String = "",
    val title: String = "",
    val channelName: String = "",
    val sourceType: String = "VIDEO",
    val durationLabel: String = "",
    val status: String = "IMPORTED",
    val aiSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun thumbnailUrl(): String =
        if (youtubeVideoId.isNotBlank()) "https://img.youtube.com/vi/$youtubeVideoId/hqdefault.jpg"
        else ""

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "youtubeUrl" to youtubeUrl.trim().take(500),
        "youtubeVideoId" to youtubeVideoId.trim().take(32),
        "title" to title.trim().take(200),
        "channelName" to channelName.trim().take(120),
        "sourceType" to if (sourceType == "LIVE") "LIVE" else "VIDEO",
        "durationLabel" to durationLabel.trim().take(30),
        "status" to if (status in listOf("IMPORTED", "ANALYZED", "ARCHIVED")) status else "IMPORTED",
        "aiSummary" to aiSummary.trim().take(1500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StudioClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceId: String = "",
    val sourceTitle: String = "",
    val title: String = "",
    val hookText: String = "",
    val startTime: String = "00:00",
    val endTime: String = "00:45",
    val durationSeconds: Long = 45L,
    val viralScore: Long = 85L,
    val aspectRatio: String = "9:16",
    val captionStyle: String = "Dinâmico",
    val framingMode: String = "Seguir pessoa",
    val captionsText: String = "",
    val highlightKeywords: String = "",
    val targetPlatform: String = "BOTH",
    val publishStatus: String = "DRAFT",
    val publishNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "sourceId" to sourceId.take(128),
        "sourceTitle" to sourceTitle.trim().take(200),
        "title" to title.trim().take(140).ifBlank { "Novo Clip" },
        "hookText" to hookText.trim().take(260),
        "startTime" to startTime.trim().take(20),
        "endTime" to endTime.trim().take(20),
        "durationSeconds" to durationSeconds.coerceIn(5L, 300L),
        "viralScore" to viralScore.coerceIn(0L, 100L),
        "aspectRatio" to if (aspectRatio in ALLOWED_ASPECT_RATIOS) aspectRatio else "9:16",
        "captionStyle" to if (captionStyle in ALLOWED_CAPTION_STYLES) captionStyle else "Dinâmico",
        "framingMode" to if (framingMode in ALLOWED_FRAMING_MODES) framingMode else "Seguir pessoa",
        "captionsText" to captionsText.trim().take(2500),
        "highlightKeywords" to highlightKeywords.trim().take(240),
        "targetPlatform" to if (targetPlatform in ALLOWED_PLATFORMS) targetPlatform else "BOTH",
        "publishStatus" to if (publishStatus in ALLOWED_PUBLISH_STATUSES) publishStatus else "DRAFT",
        "publishNotes" to publishNotes.trim().take(600),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALLOWED_ASPECT_RATIOS = listOf("9:16", "1:1", "16:9")
        val ALLOWED_CAPTION_STYLES = listOf("Dinâmico", "Minimalista", "Alto contraste")
        val ALLOWED_FRAMING_MODES = listOf("Seguir pessoa", "Centro", "Manual")
        val ALLOWED_PLATFORMS = listOf("YOUTUBE_SHORTS", "TIKTOK", "BOTH")
        val ALLOWED_PUBLISH_STATUSES = listOf("DRAFT", "READY", "SCHEDULED", "PUBLISHED")
    }
}

@Keep
@IgnoreExtraProperties
data class StylePreferences(
    @DocumentId val id: String = "style",
    val userId: String = "",
    val editingPace: String = "Dinâmico",
    val captionPreset: String = "Destacadas",
    val framingPreset: String = "Seguir pessoa",
    val preferredDuration: String = "30–60 segundos",
    val brandTone: String = "Direto, energético e envolvente",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "editingPace" to if (editingPace in ALLOWED_PACES) editingPace else "Dinâmico",
        "captionPreset" to if (captionPreset in ALLOWED_CAPTION_PRESETS) captionPreset else "Destacadas",
        "framingPreset" to if (framingPreset in StudioClip.ALLOWED_FRAMING_MODES) framingPreset else "Seguir pessoa",
        "preferredDuration" to if (preferredDuration in ALLOWED_DURATIONS) preferredDuration else "30–60 segundos",
        "brandTone" to brandTone.trim().take(160),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALLOWED_PACES = listOf("Dinâmico", "Equilibrado", "Calmo")
        val ALLOWED_CAPTION_PRESETS = listOf("Destacadas", "Discretas", "Sem legendas")
        val ALLOWED_DURATIONS = listOf("15–30 segundos", "30–60 segundos", "60–90 segundos")
    }
}

@Keep
@IgnoreExtraProperties
data class TrendItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val format: String = "",
    val hookIdea: String = "",
    val platform: String = "Ambos",
    val heatScore: Long = 88L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "topic" to topic.trim().take(140).ifBlank { "Tendência" },
        "format" to format.trim().take(100).ifBlank { "Short Dinâmico" },
        "hookIdea" to hookIdea.trim().take(300).ifBlank { "Gancho inicial" },
        "platform" to if (platform in ALLOWED_TREND_PLATFORMS) platform else "Ambos",
        "heatScore" to heatScore.coerceIn(0L, 100L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALLOWED_TREND_PLATFORMS = listOf("YouTube Shorts", "TikTok", "Ambos")
    }
}

// --- AI Feature UI / Domain Models ---

enum class AiChatRole(
    val displayName: String,
    val subtitle: String,
    val systemInstruction: String
) {
    DIRECTOR(
        displayName = "Diretor de Shorts",
        subtitle = "Ritmo, retenção e estrutura 9:16",
        systemInstruction = "És o Diretor Criativo do Planet Streaming, especialista em transformar vídeos longos e lives do YouTube em YouTube Shorts e TikToks de alta retenção. Responde sempre em português com sugestões práticas de corte, ritmo visual, enquadramento 9:16 e legendas dinâmicas."
    ),
    STRATEGIST(
        displayName = "Estrategista Viral",
        subtitle = "Ganchos nos 3s e potencial de alcance",
        systemInstruction = "És o Estrategista de Crescimento e Viralização do Planet Streaming. Ajuda criadores a identificar momentos de pico emocional, curiosidade e controvérsia construtiva para maximizar retenção, comentários e partilhas em Shorts e TikTok. Responde em português de forma clara e acionável."
    ),
    COPYWRITER(
        displayName = "Copywriter de Hooks",
        subtitle = "Títulos, legendas e CTAs magnéticos",
        systemInstruction = "És o Copywriter Especialista em Hooks e Legendas do Planet Streaming. Cria ganchos de abertura para os primeiros 3 segundos, palavras-chave para destacar em legendas cinéticas, descrições otimizadas e hashtags relevantes para YouTube Shorts e TikTok. Responde em português."
    ),
    TECHNICAL(
        displayName = "Editor Técnico",
        subtitle = "Comandos FFmpeg, corte e áudio",
        systemInstruction = "És o Engenheiro de Pós-Produção e FFmpeg do Planet Streaming. Ajudas criadores com cortes precisos, reenquadramento vertical 9:16 (crop 1080x1920), normalização de áudio LUFS, queima de legendas SRT/ASS e parâmetros de exportação para redes sociais. Responde em português."
    )
}

enum class AiChatModelOption(
    val modelId: String,
    val label: String,
    val badge: String
) {
    FAST("gemini-3.5-flash-lite", "Rápido (Flash-Lite)", "Ultra rápido"),
    GENERAL("gemini-3.8-flash", "Geral (Gemini 3.8)", "Equilibrado"),
    DEEP("gemini-4-argon", "Profundo (Gemini 4)", "Raciocínio")
}

enum class ImageSizeOption(
    val label: String,
    val resolutionHint: String
) {
    SIZE_1K("1K", "1024px • Rápido"),
    SIZE_2K("2K", "2048px • Alta def."),
    SIZE_4K("4K", "4096px • Ultra HD")
}

enum class ImageAspectOption(
    val label: String
) {
    PORTRAIT_9_16("9:16 Shorts"),
    LANDSCAPE_16_9("16:9 YouTube"),
    SQUARE_1_1("1:1 Quadrado")
}

enum class ImageModelOption(
    val modelId: String,
    val label: String,
    val hint: String
) {
    NANO_BANANA_2_1("gemini-nano-banana-2.1", "Nano Banana 2.1", "Padrão rápido"),
    FLASH_LITE_IMAGE("gemini-3.1-flash-lite-image", "Flash-Lite Image", "Ultra leve"),
    PRO_IMAGE("gemini-3-pro-image", "Gemini 3 Pro Image", "Máximo detalhe")
}

fun publishStatusLabelPt(status: String): String = when (status) {
    "DRAFT" -> "Rascunho"
    "READY" -> "Pronto"
    "SCHEDULED" -> "Agendado"
    "PUBLISHED" -> "Publicado"
    else -> status
}

data class ChatMessage(
    val id: String,
    val isFromUser: Boolean,
    val text: String,
    val roleLabel: String = "",
    val modelLabel: String = "",
    val isStreaming: Boolean = false
)

data class GeneratedClipProposal(
    val title: String,
    val hookText: String,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Long,
    val viralScore: Long,
    val captionsText: String,
    val highlightKeywords: String,
    val targetPlatform: String,
    val publishNotes: String
)

data class SourceAnalysisResult(
    val summary: String,
    val clips: List<GeneratedClipProposal>
)
