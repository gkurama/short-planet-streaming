package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiChatModelOption
import com.example.data.model.AiChatRole
import com.example.data.model.ChatMessage
import com.example.data.model.GeneratedClipProposal
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageModelOption
import com.example.data.model.ImageSizeOption
import com.example.data.model.SourceAnalysisResult
import com.example.data.model.StudioClip
import com.example.data.model.StylePreferences
import com.example.data.model.TrendItem
import com.example.data.model.VideoSource
import com.example.data.remote.YouTubeMetadataService
import com.example.media.ShortVideoProcessor
import com.example.media.TimedCaptionSegment
import com.example.media.VideoFilterPreset
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiStudioRepository"
private const val AI_TIMEOUT_MS = 45_000L

data class AiClipEditSuggestion(
    val title: String,
    val hookText: String,
    val coverTitle: String,
    val filterId: String,
    val emojiOverlay: String,
    val captionStyle: String,
    val captionsText: String,
    val highlightKeywords: String
)

class AiStudioRepository {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Brief Portuguese summary of the video/livestream"),
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Catchy short clip title in Portuguese"),
                        "hookText" to Schema.string(description = "First 3-second viral hook in Portuguese"),
                        "startTime" to Schema.string(description = "Start timestamp like 04:15"),
                        "endTime" to Schema.string(description = "End timestamp like 05:00"),
                        "durationSeconds" to Schema.integer(description = "Clip length in seconds between 15 and 90"),
                        "viralScore" to Schema.integer(description = "Estimated viral score from 70 to 99"),
                        "captionsText" to Schema.string(description = "Sample dynamic subtitle script in Portuguese"),
                        "highlightKeywords" to Schema.string(description = "3 comma-separated punchy keywords"),
                        "targetPlatform" to Schema.enumeration(
                            values = listOf("YOUTUBE_SHORTS", "TIKTOK", "BOTH"),
                            description = "Best target platform"
                        ),
                        "publishNotes" to Schema.string(description = "Short caption and 3 hashtags for publishing")
                    )
                ),
                description = "List of 3 recommended short vertical clip segments"
            )
        )
    )

    private val autopilotModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
        )
    }

    private val trendSchema = Schema.obj(
        properties = mapOf(
            "trends" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "topic" to Schema.string(description = "Trend topic in Portuguese"),
                        "format" to Schema.string(description = "Short video editing format in Portuguese"),
                        "hookIdea" to Schema.string(description = "Opening hook line in Portuguese"),
                        "platform" to Schema.enumeration(
                            values = listOf("YouTube Shorts", "TikTok", "Ambos"),
                            description = "Best short-form platform"
                        ),
                        "heatScore" to Schema.integer(description = "Momentum score from 75 to 99")
                    )
                ),
                description = "4 current short-form video trends"
            )
        )
    )

    private val trendsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = trendSchema
            }
        )
    }

    private val copyModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val aiEditSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Punchy Portuguese Short title"),
            "hookText" to Schema.string(description = "Strong 3-second opening hook in Portuguese"),
            "coverTitle" to Schema.string(description = "Short 2-4 word thumbnail/cover headline"),
            "filterId" to Schema.enumeration(
                values = VideoFilterPreset.entries.map { it.id },
                description = "Visual filter preset ID"
            ),
            "emojiOverlay" to Schema.string(description = "1 or 2 expressive emojis like 🔥😱"),
            "captionStyle" to Schema.enumeration(
                values = StudioClip.ALLOWED_CAPTION_STYLES,
                description = "Subtitle visual style"
            ),
            "captionsText" to Schema.string(description = "Dynamic subtitle script in Portuguese"),
            "highlightKeywords" to Schema.string(description = "3 comma-separated highlight words")
        )
    )

    private val aiEditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = aiEditSchema
            }
        )
    }

    private val speechTranscriptSchema = Schema.obj(
        properties = mapOf(
            "fullTranscript" to Schema.string(description = "Full spoken transcript in the clip's spoken language"),
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Segment start offset in milliseconds from 0"),
                        "endMs" to Schema.integer(description = "Segment end offset in milliseconds"),
                        "text" to Schema.string(description = "Short 3-6 word spoken subtitle phrase")
                    )
                ),
                description = "Time-synced spoken subtitle chunks"
            )
        )
    )

    private val speechTranscriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = speechTranscriptSchema
            }
        )
    }

    private fun friendlyAiError(e: Exception, fallback: String): Exception {
        val raw = e.localizedMessage.orEmpty()
        val msg = when {
            raw.contains("Something unexpected happened", ignoreCase = true) ||
                raw.contains("AppCheck", ignoreCase = true) ||
                raw.contains("403") ->
                "Serviço Gemini/App Check indisponível nesta build. Usa \"Cortar Shorts Auto (Sem IA)\" para criar e cortar os teus Shorts agora!"
            raw.isNotBlank() -> raw
            else -> fallback
        }
        return Exception(msg, e)
    }

    /**
     * Instant deterministic Short slicer (works 100% offline/without AI when desired).
     */
    fun buildFastAutoShortProposals(
        source: VideoSource,
        style: StylePreferences
    ): SourceAnalysisResult {
        val totalSeconds = YouTubeMetadataService.parseTimestampToSeconds(source.durationLabel)
            .takeIf { it >= 20 } ?: 900
        val segments = ShortVideoProcessor.computeAutoShortSegments(
            totalDurationMs = totalSeconds * 1000L,
            preferredDurationPreset = style.preferredDuration,
            baseTitle = source.title.ifBlank { "Short" }
        )
        val proposals = segments.map { seg ->
            GeneratedClipProposal(
                title = seg.title,
                hookText = seg.hookText,
                startTime = seg.startLabel,
                endTime = seg.endLabel,
                durationSeconds = seg.durationSeconds,
                viralScore = (86 + seg.index * 3).toLong().coerceAtMost(96L),
                captionsText = "${seg.hookText} Acompanha este momento completo de ${source.title}!",
                highlightKeywords = "incrível, momento, shorts",
                targetPlatform = "BOTH",
                publishNotes = "${seg.title} #Shorts #TikTok #PlanetStreaming"
            )
        }
        return SourceAnalysisResult(
            summary = "Corte automático 9:16 concluído (${proposals.size} Shorts preparados para edição e exportação).",
            clips = proposals
        )
    }

    // 1. Autopiloto IA: Stream structured JSON with gemini-3.8-flash
    suspend fun analyzeSourceForClips(
        source: VideoSource,
        style: StylePreferences,
        extraDescription: String = ""
    ): Result<SourceAnalysisResult> = try {
        val descSnippet = extraDescription.trim().take(600)
        val prompt = buildString {
            appendLine("Analisa este conteúdo do YouTube para o estúdio Planet Streaming e propõe exatamente 3 clips verticais curtos em português:")
            appendLine("- Título: ${source.title}")
            appendLine("- Canal: ${source.channelName}")
            appendLine("- Tipo: ${source.sourceType}")
            appendLine("- Duração informada: ${source.durationLabel.ifBlank { "Não especificada" }}")
            appendLine("- URL: ${source.youtubeUrl}")
            if (descSnippet.isNotBlank()) {
                appendLine("- Descrição do YouTube: $descSnippet")
            }
            appendLine()
            appendLine("Respeita rigorosamente o estilo do criador:")
            appendLine("- Ritmo de edição: ${style.editingPace}")
            appendLine("- Legendas: ${style.captionPreset}")
            appendLine("- Enquadramento: ${style.framingPreset}")
            appendLine("- Duração preferida: ${style.preferredDuration}")
            appendLine("- Tom da marca: ${style.brandTone}")
            appendLine("Mantém timestamps válidos dentro da duração do vídeo (ex: 01:15 a 02:00).")
        }

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            autopilotModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let { accumulated.append(it) }
            }
        }
        val rawJson = accumulated.toString().trim()
        if (rawJson.isBlank()) throw IllegalStateException("A IA devolveu uma resposta vazia.")
        val json = JSONObject(rawJson)
        val summary = json.optString("summary", "Análise concluída para ${source.title}.")
        val clipsArr = json.optJSONArray("clips") ?: throw IllegalStateException("Nenhum clip sugerido pela IA.")

        val proposals = buildList {
            for (i in 0 until clipsArr.length()) {
                val obj = clipsArr.getJSONObject(i)
                add(
                    GeneratedClipProposal(
                        title = obj.optString("title", "Clip ${i + 1}").take(140),
                        hookText = obj.optString("hookText", "").take(260),
                        startTime = obj.optString("startTime", "00:30").take(20),
                        endTime = obj.optString("endTime", "01:15").take(20),
                        durationSeconds = obj.optLong("durationSeconds", 45L).coerceIn(15L, 120L),
                        viralScore = obj.optLong("viralScore", 88L).coerceIn(0L, 100L),
                        captionsText = obj.optString("captionsText", "").take(2500),
                        highlightKeywords = obj.optString("highlightKeywords", "").take(240),
                        targetPlatform = obj.optString("targetPlatform", "BOTH"),
                        publishNotes = obj.optString("publishNotes", "").take(600)
                    )
                )
            }
        }
        Result.success(SourceAnalysisResult(summary = summary, clips = proposals))
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Autopilot analysis timed out", e)
        Result.failure(Exception("A análise IA expirou. Tenta o botão \"Cortar Shorts Auto (Sem IA)\" ao lado!", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Autopilot analysis failed", e)
        Result.failure(friendlyAiError(e, "Falha na análise IA."))
    }

    // 2. Trend Radar
    suspend fun generateTrendsForNiche(niche: String): Result<List<TrendItem>> = try {
        val prompt = """
            Gera exatamente 4 tendências atuais e formatos de vídeo curto para YouTube Shorts e TikTok em português sobre o tema/nicho:
            "$niche"
            Cada tendência deve ter um formato prático de edição e uma ideia de gancho (hook) para os primeiros 3 segundos.
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            trendsModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let { accumulated.append(it) }
            }
        }
        val rawJson = accumulated.toString().trim()
        if (rawJson.isBlank()) throw IllegalStateException("Resposta vazia do Radar de Tendências.")
        val json = JSONObject(rawJson)
        val arr = json.optJSONArray("trends") ?: throw IllegalStateException("Lista de tendências ausente.")
        val items = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    TrendItem(
                        topic = obj.optString("topic", niche).take(140),
                        format = obj.optString("format", "Corte dinâmico").take(100),
                        hookIdea = obj.optString("hookIdea", "").take(300),
                        platform = obj.optString("platform", "Ambos"),
                        heatScore = obj.optLong("heatScore", 88L).coerceIn(0L, 100L)
                    )
                )
            }
        }
        Result.success(items)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Trend generation timed out", e)
        Result.failure(Exception("O Radar de Tendências expirou após 45 segundos.", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Trend generation failed", e)
        Result.failure(friendlyAiError(e, "Erro ao consultar Radar de Tendências."))
    }

    // 3. Publishing Copy Assistant
    suspend fun generatePublishCopy(
        clipTitle: String,
        hookText: String,
        platform: String
    ): Result<String> = try {
        val prompt = """
            Cria uma descrição curta, envolvente e pronta para publicar no $platform em português, incluindo 4 hashtags relevantes no final (máximo 320 caracteres no total).
            Título do clip: $clipTitle
            Gancho inicial: $hookText
        """.trimIndent()
        val response = withTimeout(AI_TIMEOUT_MS) {
            copyModel.generateContent(prompt)
        }
        val text = response.text?.trim()
            ?: throw IllegalStateException("O modelo não devolveu texto.")
        Result.success(text.take(600))
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Publish copy timed out", e)
        Result.failure(Exception("Tempo limite atingido ao gerar descrição.", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Publish copy failed", e)
        Result.failure(friendlyAiError(e, "Erro ao gerar descrição IA."))
    }

    // 4. Multi-turn Gemini Chatbot ("Produtor IA")
    fun createChatSession(
        role: AiChatRole,
        modelOption: AiChatModelOption,
        studioContext: String,
        existingHistory: List<ChatMessage>
    ): Chat {
        val sysInstruction = content {
            text(
                "${role.systemInstruction}\n\nContexto atual do estúdio do utilizador:\n$studioContext"
            )
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            systemInstruction = sysInstruction
        )
        val historyContent = existingHistory
            .filter { !it.isStreaming && it.text.isNotBlank() }
            .map { msg ->
                content(role = if (msg.isFromUser) "user" else "model") {
                    text(msg.text)
                }
            }
        return model.startChat(history = historyContent)
    }

    fun sendChatMessageStream(chat: Chat, userMessage: String): Flow<String> = flow {
        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("O assistente devolveu uma resposta vazia.")
        }
    }

    // 5. Thumbnail & Cover Image Generation (1K, 2K, 4K)
    suspend fun generateStudioImage(
        promptText: String,
        sizeOption: ImageSizeOption,
        aspectOption: ImageAspectOption,
        modelOption: ImageModelOption = ImageModelOption.NANO_BANANA_2_1
    ): Result<Bitmap> = try {
        val sdkAspect = when (aspectOption) {
            ImageAspectOption.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
            ImageAspectOption.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
            ImageAspectOption.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
        }
        val sdkSize = when (sizeOption) {
            ImageSizeOption.SIZE_1K -> ImageSize.SIZE_1K
            ImageSizeOption.SIZE_2K -> ImageSize.SIZE_2K
            ImageSizeOption.SIZE_4K -> ImageSize.SIZE_4K
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(sdkAspect)
                .setImageSize(sdkSize)
                .build()
        }

        val imageModel = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )

        val response = withTimeout(60_000L) {
            imageModel.generateContent(promptText)
        }
        val bitmap = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image

        if (bitmap != null) {
            Result.success(bitmap)
        } else {
            Result.failure(IllegalStateException("Nenhuma imagem foi devolvida pelo modelo."))
        }
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Image generation timed out", e)
        Result.failure(Exception("A geração de imagem expirou após 60 segundos.", e))
    } catch (e: QuotaExceededException) {
        Log.e(TAG, "Image generation quota exceeded", e)
        Result.failure(Exception("Limite de quota atingido no projeto Firebase (a geração de imagens AI requer o plano Blaze).", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Image generation failed", e)
        Result.failure(friendlyAiError(e, "Falha ao gerar imagem."))
    }

    /**
     * Embedded AI Auto-Edit inside the Editor: optimizes hook, cover headline, color filter,
     * expressive emoji stickers, subtitle style, and highlight keywords in 1 click.
     * Falls back seamlessly to smart creator rules if cloud AI/AppCheck quota is unavailable.
     */
    suspend fun generateAiEditSuggestion(
        clip: StudioClip,
        sourceTitle: String
    ): Pair<AiClipEditSuggestion, Boolean> {
        try {
            val prompt = """
                És o editor automático do Planet Streaming. Otimiza esta cena exata selecionada pelo criador em português para máxima retenção no YouTube Shorts e TikTok:
                - Vídeo fonte: $sourceTitle
                - Cena escolhida: ${clip.startTime} até ${clip.endTime} (${clip.durationSeconds}s)
                - Título atual: ${clip.title}
                - Gancho atual: ${clip.hookText}
                - Legendas atuais: ${clip.captionsText}
                Escolhe o melhor filtro visual (neon, cinema, contrast, bw, sunset), 1-2 emojis chamativos, um título curto de capa (2-4 palavras em maiúsculas), e destaca 3 palavras-chave nas legendas.
            """.trimIndent()
            val response = withTimeout(22_000L) {
                aiEditModel.generateContent(prompt)
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isNotBlank()) {
                val obj = JSONObject(raw)
                return AiClipEditSuggestion(
                    title = obj.optString("title", clip.title).take(140).ifBlank { clip.title },
                    hookText = obj.optString("hookText", clip.hookText).take(260).ifBlank { clip.hookText },
                    coverTitle = obj.optString("coverTitle", "MOMENTO ÉPICO").take(42).ifBlank { "MOMENTO ÉPICO" },
                    filterId = VideoFilterPreset.fromId(obj.optString("filterId")).id,
                    emojiOverlay = obj.optString("emojiOverlay", "🔥😱").take(8).ifBlank { "🔥" },
                    captionStyle = obj.optString("captionStyle", "Dinâmico")
                        .takeIf { it in StudioClip.ALLOWED_CAPTION_STYLES } ?: "Dinâmico",
                    captionsText = obj.optString("captionsText", clip.captionsText).take(600)
                        .ifBlank { clip.hookText },
                    highlightKeywords = obj.optString("highlightKeywords", "épico, incrível, agora").take(120)
                ) to true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud AI Auto-Edit unavailable, applying studio preset: ${e.message}")
        }

        // Smart deterministic studio preset fallback
        val lower = "${clip.title} $sourceTitle".lowercase()
        val chosenFilter = when {
            lower.contains("gta") || lower.contains("game") || lower.contains("live") -> VideoFilterPreset.GTA_SUNSET
            lower.contains("terror") || lower.contains("mistério") -> VideoFilterPreset.BW_DRAMATIC
            lower.contains("dica") || lower.contains("tutorial") -> VideoFilterPreset.NEON_CYBER
            else -> VideoFilterPreset.HIGH_CONTRAST
        }
        val chosenEmoji = when {
            lower.contains("gta") || lower.contains("game") -> "🎮🔥"
            lower.contains("engraç") || lower.contains("meme") -> "😂💥"
            lower.contains("estreia") || lower.contains("novo") -> "🚀😱"
            else -> "🔥⚡"
        }
        val cleanCoverHeadline = clip.title
            .substringBefore("•")
            .trim()
            .split(" ")
            .take(4)
            .joinToString(" ")
            .ifBlank { "MOMENTO ÉPICO" }
            .uppercase()
        val hook = clip.hookText.ifBlank { "Olha o que aconteceu neste momento!" }
        val captions = if (clip.captionsText.isNotBlank()) {
            clip.captionsText
        } else {
            "$hook Assiste até ao final para veres a reação completa!"
        }
        return AiClipEditSuggestion(
            title = clip.title,
            hookText = hook,
            coverTitle = cleanCoverHeadline,
            filterId = chosenFilter.id,
            emojiOverlay = chosenEmoji,
            captionStyle = "Alto contraste",
            captionsText = captions,
            highlightKeywords = "aconteceu, final, completo"
        ) to false
    }

    /**
     * Transcribes spoken audio from the cut video clip into time-synced 3-6 word subtitle segments
     * using Firebase AI Logic (`gemini-3.8-flash` multimodal audio `inlineData`).
     */
    suspend fun transcribeAudioToTimedCaptions(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        clipDurationMs: Long
    ): Pair<String, List<TimedCaptionSegment>> {
        if (audioBytes.size < 512) return "" to emptyList()
        return try {
            val maxDur = clipDurationMs.coerceAtLeast(3_000L)
            val promptContent = content {
                inlineData(audioBytes, mimeType)
                text(
                    "Transcreve exatamente o que a pessoa fala neste áudio de ${maxDur / 1000L} segundos. " +
                        "Divide a fala em legendas curtas e dinâmicas (3 a 6 palavras por frase) sincronizadas " +
                        "com startMs e endMs entre 0 e $maxDur ms. Se não houver fala audível, devolve lista vazia."
                )
            }
            val response = withTimeout(30_000L) {
                speechTranscriptModel.generateContent(promptContent)
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isBlank()) return "" to emptyList()
            val root = JSONObject(raw)
            val transcript = root.optString("fullTranscript", "").trim()
            val arr = root.optJSONArray("segments")
            val segments = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val text = obj.optString("text", "").trim()
                        if (text.isBlank()) continue
                        val start = obj.optLong("startMs", 0L).coerceIn(0L, maxDur - 300L)
                        val end = obj.optLong("endMs", start + 2_200L).coerceIn(start + 400L, maxDur)
                        add(TimedCaptionSegment(startMs = start, endMs = end, text = text))
                    }
                }
            }
            val resolvedTranscript = transcript.ifBlank { segments.joinToString(" ") { it.text } }
            resolvedTranscript to segments
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Speech transcription unavailable, falling back to script chunking: ${e.message}")
            "" to emptyList()
        }
    }
}
