package com.example.data.model

import com.example.media.VideoFilterPreset

data class ShortTemplateItem(
    val id: String,
    val name: String,
    val headlineBanner: String,
    val category: String, // Gaming, Educativo, Entretenimento
    val hasCrownBadge: Boolean,
    val gradientColors: List<Long>,
    val headlineColorHex: Long,
    val filterPreset: VideoFilterPreset,
    val captionStyle: String,
    val framingMode: String,
    val emojiOverlay: String,
    val highlightKeywords: String
)

object ShortTemplateCatalog {
    val categories: List<String> = listOf("Todos", "Gaming", "Educativo", "Entretenimento")

    val templates: List<ShortTemplateItem> = listOf(
        ShortTemplateItem(
            id = "legenda_dinamica",
            name = "Legenda Dinâmica",
            headlineBanner = "LEGENDA DINÂMICA",
            category = "Gaming",
            hasCrownBadge = false,
            gradientColors = listOf(0xFF5B21B6, 0xFF9333EA, 0xFF1E103C),
            headlineColorHex = 0xFFFFFFFF,
            filterPreset = VideoFilterPreset.HIGH_CONTRAST,
            captionStyle = "Dinâmico",
            framingMode = "Seguir pessoa",
            emojiOverlay = "⚡",
            highlightKeywords = "incrível,viral,agora,jogada"
        ),
        ShortTemplateItem(
            id = "react_rapido",
            name = "React Rápido",
            headlineBanner = "REACT RÁPIDO",
            category = "Entretenimento",
            hasCrownBadge = true,
            gradientColors = listOf(0xFF1D4ED8, 0xFF7C3AED, 0xFFB45309),
            headlineColorHex = 0xFFFACC15,
            filterPreset = VideoFilterPreset.GTA_SUNSET,
            captionStyle = "Alto contraste",
            framingMode = "Seguir pessoa",
            emojiOverlay = "😱",
            highlightKeywords = "olha,reação,impossível,épico"
        ),
        ShortTemplateItem(
            id = "dica_de_ouro",
            name = "Dica de Ouro",
            headlineBanner = "DICA DE OURO",
            category = "Educativo",
            hasCrownBadge = true,
            gradientColors = listOf(0xFF92400E, 0xFFD97706, 0xFF291807),
            headlineColorHex = 0xFFFFFFFF,
            filterPreset = VideoFilterPreset.CINEMA_WARM,
            captionStyle = "Dinâmico",
            framingMode = "Seguir pessoa",
            emojiOverlay = "👑",
            highlightKeywords = "dica,segredo,passo,melhor"
        ),
        ShortTemplateItem(
            id = "top_5",
            name = "Top 5",
            headlineBanner = "TOP 5",
            category = "Entretenimento",
            hasCrownBadge = true,
            gradientColors = listOf(0xFF065F46, 0xFF0F172A, 0xFF5B21B6),
            headlineColorHex = 0xFFFACC15,
            filterPreset = VideoFilterPreset.NEON_CYBER,
            captionStyle = "Alto contraste",
            framingMode = "Centro fixo",
            emojiOverlay = "🔥",
            highlightKeywords = "top,ranking,primeiro,melhores"
        ),
        ShortTemplateItem(
            id = "clutch_epico",
            name = "Clutch Épico",
            headlineBanner = "CLUTCH ÉPICO",
            category = "Gaming",
            hasCrownBadge = false,
            gradientColors = listOf(0xFF312E81, 0xFF0891B2, 0xFF0F172A),
            headlineColorHex = 0xFF22D3EE,
            filterPreset = VideoFilterPreset.NEON_CYBER,
            captionStyle = "Alto contraste",
            framingMode = "Seguir pessoa",
            emojiOverlay = "🎯",
            highlightKeywords = "clutch,vitória,insano,headshot"
        ),
        ShortTemplateItem(
            id = "resumo_live",
            name = "Resumo da Live",
            headlineBanner = "RESUMO DA LIVE",
            category = "Educativo",
            hasCrownBadge = false,
            gradientColors = listOf(0xFF831843, 0xFF6D28D9, 0xFF130B29),
            headlineColorHex = 0xFFFFFFFF,
            filterPreset = VideoFilterPreset.CINEMA_WARM,
            captionStyle = "Dinâmico",
            framingMode = "Seguir pessoa",
            emojiOverlay = "🎬",
            highlightKeywords = "live,momento,resumo,canal"
        )
    )
}
