package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.BuildConfig
import com.example.auth.TikTokAuthHelper
import com.example.auth.YouTubeAuthHelper
import com.example.data.model.AiChatModelOption
import com.example.data.model.AiChatRole
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageModelOption
import com.example.data.model.ImageSizeOption
import com.example.data.model.ShortTemplateItem
import com.example.data.model.StudioClip
import com.example.data.model.StylePreferences
import com.example.data.model.TrendItem
import com.example.data.model.VideoSource
import com.example.data.remote.ResolvedYouTubeMetadata
import com.example.data.remote.TikTokUploadService
import com.example.data.remote.VideoProcessingUrls
import com.example.data.remote.YouTubeApiDisabledException
import com.example.data.remote.YouTubeChannelSummary
import com.example.data.remote.YouTubeLibraryVideo
import com.example.data.remote.YouTubeMetadataService
import com.example.data.remote.YouTubeRetrofitClient
import com.example.data.remote.YouTubeUnauthorizedException
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.StudioRepository
import com.example.media.ClipVisualStyle
import com.example.media.CoverStudioRenderer
import com.example.media.LocalVideoInfo
import com.example.media.ShortVideoProcessor
import com.example.media.StudioAudioGenerator
import com.example.media.StudioLocalPrefs
import com.example.media.TikTokAccountState
import com.example.media.TimedCaptionSegment
import com.example.media.VideoFilterPreset
import com.example.media.YouTubePersistentState
import com.example.media.formatMsToMmSs
import com.google.firebase.ai.Chat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(
        val bitmap: Bitmap,
        val sizeLabel: String,
        val aspectLabel: String,
        val sourceNote: String = ""
    ) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

sealed interface ShortExportUiState {
    data object Idle : ShortExportUiState
    data class Processing(val progressPercent: Int, val statusLabel: String) : ShortExportUiState
    data class Success(val clipId: String, val filePath: String) : ShortExportUiState
    data class Error(val message: String) : ShortExportUiState
}

data class YouTubeLibraryUiState(
    val isConnected: Boolean = false,
    val hasUploadScope: Boolean = false,
    val isCheckingAuth: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val apiNeedsActivation: Boolean = false,
    val channelHandleInput: String = "",
    val typeFilter: String = "ALL", // ALL, VIDEO, LIVE
    val channel: YouTubeChannelSummary? = null,
    val videos: List<YouTubeLibraryVideo> = emptyList(),
    val nextPageToken: String? = null,
    val searchQuery: String = "",
    val importingVideoId: String? = null,
    val error: String? = null
)

data class DualPublishStatus(
    val clipId: String,
    val clipTitle: String,
    val isRunning: Boolean = false,
    val youtubeStatus: String = "A aguardar...",
    val tikTokStatus: String = "A aguardar...",
    val youtubeSuccess: Boolean = false,
    val tikTokSuccess: Boolean = false,
    val youtubeNeedsAppShare: Boolean = false,
    val tikTokNeedsAppShare: Boolean = false,
    val youtubeVideoUrl: String = ""
)

data class ImportUiState(
    val urlInput: String = "",
    val isFetchingMeta: Boolean = false,
    val previewMeta: ResolvedYouTubeMetadata? = null,
    val manualTitle: String = "",
    val manualChannel: String = "",
    val manualDuration: String = "15:00",
    val manualDescription: String = "",
    val sourceType: String = "VIDEO",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class ChatUiState(
    val selectedRole: AiChatRole = AiChatRole.DIRECTOR,
    val selectedModel: AiChatModelOption = AiChatModelOption.GENERAL,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            id = "welcome",
            isFromUser = false,
            text = "Olá! Sou o teu Produtor IA no Planet Streaming. Cola uma ideia, escolhe um vídeo do teu YouTube ou pede ganchos, cortes 9:16 e comandos FFmpeg.",
            roleLabel = AiChatRole.DIRECTOR.displayName,
            modelLabel = AiChatModelOption.GENERAL.label
        )
    ),
    val isSending: Boolean = false,
    val error: String? = null
)

class StudioViewModel(
    private val currentUserId: String,
    private val studioRepo: StudioRepository,
    private val aiRepo: AiStudioRepository,
    private val ytService: YouTubeMetadataService,
    private val tikTokUploadService: TikTokUploadService = TikTokUploadService(),
    private val tikTokAuthHelper: TikTokAuthHelper = TikTokAuthHelper()
) : ViewModel() {

    val sourcesState: StateFlow<UiState<List<VideoSource>>> =
        studioRepo.observeSources(currentUserId)
            .map<List<VideoSource>, UiState<List<VideoSource>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreFlowError(error, "sources")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val clipsState: StateFlow<UiState<List<StudioClip>>> =
        studioRepo.observeClips(currentUserId)
            .map<List<StudioClip>, UiState<List<StudioClip>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreFlowError(error, "clips")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val styleState: StateFlow<UiState<StylePreferences>> =
        studioRepo.observeStylePreferences(currentUserId)
            .map<StylePreferences, UiState<StylePreferences>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreFlowError(error, "style")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val trendsState: StateFlow<UiState<List<TrendItem>>> =
        studioRepo.observeTrends(currentUserId)
            .map<List<TrendItem>, UiState<List<TrendItem>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreFlowError(error, "trends")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private val _ytLibraryState = MutableStateFlow(YouTubeLibraryUiState())
    val ytLibraryState: StateFlow<YouTubeLibraryUiState> = _ytLibraryState.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _autopilotBusySourceId = MutableStateFlow<String?>(null)
    val autopilotBusySourceId: StateFlow<String?> = _autopilotBusySourceId.asStateFlow()

    private val _autopilotError = MutableStateFlow<String?>(null)
    val autopilotError: StateFlow<String?> = _autopilotError.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _chatState = MutableStateFlow(ChatUiState())
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _selectedImageSize = MutableStateFlow(ImageSizeOption.SIZE_1K)
    val selectedImageSize: StateFlow<ImageSizeOption> = _selectedImageSize.asStateFlow()

    private val _selectedImageAspect = MutableStateFlow(ImageAspectOption.PORTRAIT_9_16)
    val selectedImageAspect: StateFlow<ImageAspectOption> = _selectedImageAspect.asStateFlow()

    private val _selectedImageModel = MutableStateFlow(ImageModelOption.NANO_BANANA_2_1)
    val selectedImageModel: StateFlow<ImageModelOption> = _selectedImageModel.asStateFlow()

    private val _isGeneratingTrends = MutableStateFlow(false)
    val isGeneratingTrends: StateFlow<Boolean> = _isGeneratingTrends.asStateFlow()

    private val _trendSuggestions = MutableStateFlow<List<TrendItem>>(emptyList())
    val trendSuggestions: StateFlow<List<TrendItem>> = _trendSuggestions.asStateFlow()

    private val _trendsError = MutableStateFlow<String?>(null)
    val trendsError: StateFlow<String?> = _trendsError.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Local Video & MP4 Short Cutting State
    private val _localVideoInfo = MutableStateFlow<LocalVideoInfo?>(null)
    val localVideoInfo: StateFlow<LocalVideoInfo?> = _localVideoInfo.asStateFlow()

    private val _renderedClipIds = MutableStateFlow<Set<String>>(emptySet())
    val renderedClipIds: StateFlow<Set<String>> = _renderedClipIds.asStateFlow()

    private val _shortExportState = MutableStateFlow<ShortExportUiState>(ShortExportUiState.Idle)
    val shortExportState: StateFlow<ShortExportUiState> = _shortExportState.asStateFlow()

    private val _selectedVideoProcessingUrls = MutableStateFlow<VideoProcessingUrls?>(null)
    val selectedVideoProcessingUrls: StateFlow<VideoProcessingUrls?> = _selectedVideoProcessingUrls.asStateFlow()

    // Local Editor Visual Styles (filters, emojis, cover titles), TikTok Account, AI Auto-Edit & YouTube Upload
    private val _clipVisualStyles = MutableStateFlow<Map<String, ClipVisualStyle>>(emptyMap())
    val clipVisualStyles: StateFlow<Map<String, ClipVisualStyle>> = _clipVisualStyles.asStateFlow()

    private val _tikTokAccount = MutableStateFlow(TikTokAccountState())
    val tikTokAccount: StateFlow<TikTokAccountState> = _tikTokAccount.asStateFlow()

    private val _aiEditBusy = MutableStateFlow(false)
    val aiEditBusy: StateFlow<Boolean> = _aiEditBusy.asStateFlow()

    private val _youTubeUploadBusy = MutableStateFlow(false)
    val youTubeUploadBusy: StateFlow<Boolean> = _youTubeUploadBusy.asStateFlow()

    private val _autoPublishOnCut = MutableStateFlow(true)
    val autoPublishOnCut: StateFlow<Boolean> = _autoPublishOnCut.asStateFlow()

    private val _dualPublishStatus = MutableStateFlow<DualPublishStatus?>(null)
    val dualPublishStatus: StateFlow<DualPublishStatus?> = _dualPublishStatus.asStateFlow()

    private val _exportQuality = MutableStateFlow("1080p (Recomendado)")
    val exportQuality: StateFlow<String> = _exportQuality.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private var cancelCurrentExport: (() -> Unit)? = null

    // In-memory short-lived OAuth token (never written to disk/Firestore)
    private var ytAccessToken: String? = null
    private var activeChat: Chat? = null
    private var activeChatJob: Job? = null

    private fun unwrapFirestoreFlowError(error: Throwable, tag: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $tag", rootError)
        return UiState.Error(rootError.localizedMessage ?: "Falha ao carregar $tag.")
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearAutopilotError() {
        _autopilotError.value = null
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun hydrateRenderedShorts(context: Context) {
        val appCtx = context.applicationContext
        _renderedClipIds.value = ShortVideoProcessor.listRenderedClipIds(appCtx)
        _clipVisualStyles.value = StudioLocalPrefs.loadAllClipStyles(appCtx)
        _tikTokAccount.value = StudioLocalPrefs.loadTikTokAccount(appCtx)
        _autoPublishOnCut.value = StudioLocalPrefs.loadAutoPublishOnCut(appCtx)
        _exportQuality.value = StudioLocalPrefs.loadExportQuality(appCtx)
        _notificationsEnabled.value = StudioLocalPrefs.loadNotificationsEnabled(appCtx)
        val ytSaved = StudioLocalPrefs.loadYouTubePersistentState(appCtx)
        if (ytSaved.wasConnected && _ytLibraryState.value.channelHandleInput.isBlank() && ytSaved.channelHint.isNotBlank()) {
            _ytLibraryState.update { it.copy(channelHandleInput = ytSaved.channelHint) }
        }
    }

    fun setExportQuality(context: Context, quality: String) {
        _exportQuality.value = StudioLocalPrefs.saveExportQuality(context.applicationContext, quality)
        _snackbarMessage.value = "Qualidade de exportação definida para ${_exportQuality.value}."
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        _notificationsEnabled.value = StudioLocalPrefs.saveNotificationsEnabled(context.applicationContext, enabled)
    }

    fun setAutoPublishOnCut(context: Context, enabled: Boolean) {
        val saved = StudioLocalPrefs.saveAutoPublishOnCut(context.applicationContext, enabled)
        _autoPublishOnCut.value = saved
        _snackbarMessage.value = if (saved) {
            "Envio automático ativado: ao cortar um Short, envia para YouTube e TikTok!"
        } else {
            "Envio automático pausado (podes enviar manualmente quando quiseres)."
        }
    }

    fun clearDualPublishStatus() {
        _dualPublishStatus.value = null
    }

    // --- TikTok OAuth2 PKCE Connection & Visual Editor Tools ---

    fun startTikTokOAuth(context: Context) {
        val appCtx = context.applicationContext
        val state = TikTokAuthHelper.generateState()
        val verifier = TikTokAuthHelper.generateCodeVerifier()
        StudioLocalPrefs.savePendingTikTokOAuth(appCtx, state, verifier)
        TikTokAuthHelper.launchOAuthInBrowser(context, state, verifier).onFailure { e ->
            _snackbarMessage.value = e.localizedMessage
                ?: "Não foi possível abrir o login OAuth2 do TikTok."
        }
    }

    fun handleTikTokOAuthCallback(context: Context, uri: Uri) {
        val appCtx = context.applicationContext
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description").orEmpty()
            _snackbarMessage.value = "Autorização TikTok cancelada: ${desc.ifBlank { error }}"
            return
        }
        val code = uri.getQueryParameter("code").orEmpty()
        val callbackState = uri.getQueryParameter("state").orEmpty()
        val pending = StudioLocalPrefs.consumePendingTikTokOAuth(appCtx)
        if (code.isBlank() || pending == null) {
            _snackbarMessage.value = "Resposta OAuth2 do TikTok inválida ou expirada."
            return
        }
        if (callbackState.isNotBlank() && callbackState != pending.state) {
            _snackbarMessage.value = "Validação de segurança (state) do TikTok falhou. Tenta novamente."
            return
        }

        viewModelScope.launch {
            _snackbarMessage.value = "A concluir ligação à conta TikTok..."
            val tokenRes = tikTokAuthHelper.exchangeCodeForTokens(
                clientKey = BuildConfig.TIKTOK_CLIENT_KEY,
                clientSecret = BuildConfig.TIKTOK_CLIENT_SECRET,
                code = code,
                codeVerifier = pending.codeVerifier
            )
            tokenRes.fold(
                onSuccess = { tok ->
                    val profile = tikTokAuthHelper.fetchUserProfile(tok.accessToken).getOrNull()
                    val handleCandidate = profile?.username?.takeIf { it.isNotBlank() }
                        ?: profile?.displayName?.takeIf { it.isNotBlank() }
                        ?: "@tiktok_${tok.openId.takeLast(6)}"
                    val expiresAt = System.currentTimeMillis() + (tok.expiresInSec * 1000L)
                    val saved = StudioLocalPrefs.saveTikTokOAuthSession(
                        appCtx,
                        TikTokAccountState(
                            isConnected = true,
                            handle = handleCandidate,
                            apiAccessToken = tok.accessToken,
                            refreshToken = tok.refreshToken,
                            expiresAtEpochMs = expiresAt,
                            openId = tok.openId.ifBlank { profile?.openId.orEmpty() },
                            displayName = profile?.displayName.orEmpty(),
                            avatarUrl = profile?.avatarUrl.orEmpty(),
                            grantedScopes = tok.scope
                        )
                    )
                    _tikTokAccount.value = saved
                    _snackbarMessage.value = "Conta TikTok ${saved.handle} ligada via API OAuth2!"
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Falha ao autenticar com o TikTok."
                }
            )
        }
    }

    private suspend fun refreshTikTokTokenIfNeeded(context: Context): TikTokAccountState {
        val appCtx = context.applicationContext
        val current = _tikTokAccount.value
        if (!current.isConnected || current.refreshToken.isBlank()) return current
        val needsRefresh = current.apiAccessToken.isBlank() ||
            (current.expiresAtEpochMs > 0L && System.currentTimeMillis() >= current.expiresAtEpochMs - 300_000L)
        if (!needsRefresh || !TikTokAuthHelper.isConfigured()) return current

        val refreshed = tikTokAuthHelper.refreshAccessToken(
            clientKey = BuildConfig.TIKTOK_CLIENT_KEY,
            clientSecret = BuildConfig.TIKTOK_CLIENT_SECRET,
            refreshToken = current.refreshToken
        ).getOrNull() ?: return current

        val updated = StudioLocalPrefs.saveTikTokOAuthSession(
            appCtx,
            current.copy(
                apiAccessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + (refreshed.expiresInSec * 1000L),
                openId = refreshed.openId.ifBlank { current.openId },
                grantedScopes = refreshed.scope.ifBlank { current.grantedScopes }
            )
        )
        _tikTokAccount.value = updated
        return updated
    }

    fun connectTikTokAccount(context: Context, rawHandle: String, apiAccessToken: String = "") {
        val normalized = StudioLocalPrefs.normalizeTikTokHandle(rawHandle)
        if (normalized.isBlank()) {
            _snackbarMessage.value = "Indica o teu @utilizador do TikTok (ex: @planetoffgames)."
            return
        }
        val state = StudioLocalPrefs.saveTikTokAccount(context.applicationContext, normalized, apiAccessToken)
        _tikTokAccount.value = state
        _snackbarMessage.value = if (state.hasApiUploadToken) {
            "Conta TikTok ${state.handle} conectada com API direta em segundo plano!"
        } else {
            "Conta TikTok ${state.handle} conectada para partilha rápida!"
        }
    }

    fun disconnectTikTokAccount(context: Context) {
        _tikTokAccount.value = StudioLocalPrefs.clearTikTokAccount(context.applicationContext)
        _snackbarMessage.value = "Conta TikTok desconectada."
    }

    fun updateClipVisualStyle(
        context: Context,
        clipId: String,
        transform: (ClipVisualStyle) -> ClipVisualStyle
    ) {
        if (clipId.isBlank()) return
        val current = _clipVisualStyles.value[clipId] ?: ClipVisualStyle()
        val updated = transform(current)
        _clipVisualStyles.value = StudioLocalPrefs.saveClipStyle(context.applicationContext, clipId, updated)
    }

    fun runAiAutoEditForClip(context: Context, clip: StudioClip) {
        if (_aiEditBusy.value) return
        viewModelScope.launch {
            _aiEditBusy.value = true
            val (suggestion, fromCloud) = aiRepo.generateAiEditSuggestion(clip, clip.sourceTitle)
            val updatedClip = clip.copy(
                title = suggestion.title,
                hookText = suggestion.hookText,
                captionStyle = suggestion.captionStyle,
                captionsText = suggestion.captionsText,
                highlightKeywords = suggestion.highlightKeywords
            )
            studioRepo.updateClipEditor(currentUserId, updatedClip)
            updateClipVisualStyle(context, clip.id) { current ->
                current.copy(
                    filterId = suggestion.filterId,
                    emojiOverlay = suggestion.emojiOverlay,
                    coverTitle = suggestion.coverTitle,
                    burnOverlayOnExport = true
                )
            }
            _aiEditBusy.value = false
            _snackbarMessage.value = if (fromCloud) {
                "Edição IA aplicada: filtro, emojis, capa e legendas otimizados!"
            } else {
                "Auto-Edição Estúdio aplicada: filtro, emojis, capa e legendas otimizados!"
            }
        }
    }

    /**
     * Exact Scene Workflow ("Ir no vídeo, escolher a cena exata e assim a IA vai fazer"):
     * Saves the chosen timestamps [startLabel]..[endLabel], runs AI scene optimization (cover, filter,
     * emojis, hook & captions), renders the MP4 Short, and immediately auto-publishes to YouTube + TikTok
     * if [autoPublishOnCut] is active!
     */
    fun runAiCutAndOptimizeScene(
        context: Context,
        clip: StudioClip,
        startLabel: String,
        endLabel: String,
        aspectRatio: String = clip.aspectRatio,
        manualPanX: Float = 0f,
        autoPublishOverride: Boolean? = null
    ) {
        if (_aiEditBusy.value || _shortExportState.value is ShortExportUiState.Processing) return
        viewModelScope.launch {
            val startSec = YouTubeMetadataService.parseTimestampToSeconds(startLabel)
            var endSec = YouTubeMetadataService.parseTimestampToSeconds(endLabel)
            if (endSec <= startSec + 3) endSec = startSec + 30
            val durSec = (endSec - startSec).toLong().coerceIn(5L, 180L)

            val sceneClip = clip.copy(
                startTime = startLabel,
                endTime = endLabel,
                durationSeconds = durSec,
                aspectRatio = aspectRatio
            )

            _aiEditBusy.value = true
            val (suggestion, _) = aiRepo.generateAiEditSuggestion(sceneClip, sceneClip.sourceTitle)
            val finalClip = sceneClip.copy(
                title = suggestion.title,
                hookText = suggestion.hookText,
                captionStyle = suggestion.captionStyle,
                captionsText = suggestion.captionsText,
                highlightKeywords = suggestion.highlightKeywords,
                targetPlatform = "BOTH",
                publishStatus = "READY"
            )
            studioRepo.updateClipEditor(currentUserId, finalClip)
            updateClipVisualStyle(context, finalClip.id) { current ->
                current.copy(
                    filterId = suggestion.filterId,
                    emojiOverlay = suggestion.emojiOverlay,
                    coverTitle = suggestion.coverTitle,
                    burnOverlayOnExport = true,
                    panX = manualPanX
                )
            }
            _aiEditBusy.value = false

            renderClipToMp4Internal(
                context = context,
                clip = finalClip,
                aspectRatio = aspectRatio,
                startLabel = startLabel,
                endLabel = endLabel,
                manualPanX = manualPanX,
                overrideUri = null,
                triggerAutoPublish = autoPublishOverride ?: _autoPublishOnCut.value
            )
        }
    }

    // --- YouTube OAuth & Persistent Background Channel Library ---

    fun updateChannelHandleInput(value: String) {
        _ytLibraryState.update { it.copy(channelHandleInput = value) }
    }

    fun updateYouTubeTypeFilter(filter: String) {
        _ytLibraryState.update { it.copy(typeFilter = filter) }
    }

    fun checkSilentYouTubeAuth(context: Context) {
        hydrateRenderedShorts(context)
        val appCtx = context.applicationContext
        viewModelScope.launch {
            refreshTikTokTokenIfNeeded(appCtx)
        }
        if (ytAccessToken != null || _ytLibraryState.value.isCheckingAuth) return
        viewModelScope.launch {
            _ytLibraryState.update { it.copy(isCheckingAuth = true) }
            val token = YouTubeAuthHelper.getSilentAccessToken(appCtx, includeUploadScope = true)
            if (!token.isNullOrBlank()) {
                ytAccessToken = token
                YouTubeRetrofitClient.setAccessToken(token)
                val hasUpload = YouTubeAuthHelper.lastTokenIncludesUploadScope
                val saved = StudioLocalPrefs.loadYouTubePersistentState(appCtx)
                StudioLocalPrefs.saveYouTubePersistentState(
                    appCtx,
                    saved.copy(wasConnected = true, hasUploadScope = hasUpload)
                )
                _ytLibraryState.update {
                    it.copy(
                        isCheckingAuth = false,
                        isConnected = true,
                        hasUploadScope = hasUpload,
                        error = null
                    )
                }
                loadYouTubeLibraryInternal(token, appCtx)
            } else {
                _ytLibraryState.update { it.copy(isCheckingAuth = false) }
                val saved = StudioLocalPrefs.loadYouTubePersistentState(appCtx)
                if (saved.wasConnected && saved.channelHint.isNotBlank()) {
                    loadPublicChannelLibrary(saved.channelHint, appContext = appCtx)
                }
            }
        }
    }

    fun onYouTubeTokenGranted(token: String, context: Context? = null) {
        ytAccessToken = token
        YouTubeRetrofitClient.setAccessToken(token)
        val hasUpload = YouTubeAuthHelper.lastTokenIncludesUploadScope
        val appCtx = context?.applicationContext
        if (appCtx != null) {
            val prev = StudioLocalPrefs.loadYouTubePersistentState(appCtx)
            StudioLocalPrefs.saveYouTubePersistentState(
                appCtx,
                prev.copy(wasConnected = true, hasUploadScope = hasUpload)
            )
        }
        _ytLibraryState.update {
            it.copy(isConnected = true, hasUploadScope = hasUpload, error = null)
        }
        _snackbarMessage.value = if (hasUpload) {
            "YouTube ligado em segundo plano (Leitura + Envio Shorts)!"
        } else {
            "Conta YouTube ligada! A carregar vídeos e lives..."
        }
        loadYouTubeLibraryInternal(token, appCtx)
    }

    fun onYouTubeAuthError(message: String) {
        _ytLibraryState.update { it.copy(isLoading = false, error = message) }
    }

    fun disconnectYouTube(context: Context? = null) {
        val currentToken = ytAccessToken
        ytAccessToken = null
        YouTubeRetrofitClient.setAccessToken(null)
        context?.applicationContext?.let { StudioLocalPrefs.clearYouTubePersistentState(it) }
        viewModelScope.launch {
            YouTubeAuthHelper.revokeAccess(currentToken)
            _selectedVideoProcessingUrls.value = null
            _ytLibraryState.value = YouTubeLibraryUiState()
            _snackbarMessage.value = "Ligação OAuth2 ao YouTube terminada."
        }
    }

    fun resolveVideoUrlsForProcessing(videoIdOrUrl: String) {
        viewModelScope.launch {
            ytService.getVideoUrlsForProcessing(videoIdOrUrl, ytAccessToken).fold(
                onSuccess = { urls ->
                    _selectedVideoProcessingUrls.value = urls
                    _snackbarMessage.value = "URLs prontas para processamento: ${urls.watchUrl}"
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Não foi possível obter URLs do vídeo."
                }
            )
        }
    }

    fun clearSelectedProcessingUrls() {
        _selectedVideoProcessingUrls.value = null
    }

    fun refreshYouTubeLibrary(context: Context) {
        val currentHint = _ytLibraryState.value.channelHandleInput.trim()
        val importedSource = (sourcesState.value as? UiState.Success)?.data?.firstOrNull {
            it.youtubeVideoId.isNotBlank()
        }
        viewModelScope.launch {
            val freshToken = YouTubeAuthHelper.getSilentAccessToken(context.applicationContext)
                ?: ytAccessToken
            if (!freshToken.isNullOrBlank()) {
                ytAccessToken = freshToken
                loadYouTubeLibraryInternal(freshToken, context.applicationContext)
            } else if (currentHint.isNotBlank()) {
                loadPublicChannelLibrary(currentHint, appContext = context.applicationContext)
            } else if (importedSource != null) {
                loadPublicChannelLibrary(importedSource.youtubeVideoId, appContext = context.applicationContext)
            } else {
                _ytLibraryState.update {
                    it.copy(
                        isConnected = false,
                        error = "Conecta a tua conta Google ou indica o @handle do teu canal abaixo."
                    )
                }
            }
        }
    }

    fun loadPublicChannelLibrary(channelHintOrUrl: String? = null, appContext: Context? = null) {
        val importedSource = (sourcesState.value as? UiState.Success)?.data?.firstOrNull {
            it.youtubeVideoId.isNotBlank()
        }
        val query = channelHintOrUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: _ytLibraryState.value.channelHandleInput.trim().takeIf { it.isNotBlank() }
            ?: importedSource?.youtubeVideoId
            ?: ""

        if (query.isBlank()) {
            _ytLibraryState.update {
                it.copy(error = "Escreve o @handle do teu canal (ex: @PLANETOffGAMES) ou importa 1 vídeo primeiro.")
            }
            return
        }

        viewModelScope.launch {
            _ytLibraryState.update { it.copy(isLoading = true, error = null) }
            ytService.fetchPublicChannelLibrary(query).fold(
                onSuccess = { page ->
                    val resolvedHint = page.channel.handle.ifBlank {
                        query.takeIf { it.startsWith("@") } ?: page.channel.channelId
                    }
                    appContext?.let { ctx ->
                        val prev = StudioLocalPrefs.loadYouTubePersistentState(ctx)
                        StudioLocalPrefs.saveYouTubePersistentState(
                            ctx,
                            prev.copy(
                                wasConnected = true,
                                channelTitle = page.channel.title,
                                channelHint = resolvedHint.ifBlank { prev.channelHint }
                            )
                        )
                    }
                    _ytLibraryState.update {
                        it.copy(
                            isConnected = true,
                            isLoading = false,
                            channel = page.channel,
                            videos = page.videos,
                            nextPageToken = null,
                            error = if (page.videos.isEmpty()) {
                                "Canal encontrado (${page.channel.title}), mas não devolveu vídeos públicos."
                            } else null
                        )
                    }
                    if (page.videos.isNotEmpty()) {
                        _snackbarMessage.value = "Carregados ${page.videos.size} vídeos e lives de ${page.channel.title}!"
                    }
                },
                onFailure = { e ->
                    _ytLibraryState.update {
                        it.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Não foi possível carregar o canal."
                        )
                    }
                }
            )
        }
    }

    private fun loadYouTubeLibraryInternal(token: String, appContext: Context? = null) {
        viewModelScope.launch {
            _ytLibraryState.update { it.copy(isLoading = true, error = null, apiNeedsActivation = false) }
            ytService.fetchMyYouTubeLibrary(accessToken = token).fold(
                onSuccess = { page ->
                    appContext?.let { ctx ->
                        val prev = StudioLocalPrefs.loadYouTubePersistentState(ctx)
                        StudioLocalPrefs.saveYouTubePersistentState(
                            ctx,
                            prev.copy(
                                wasConnected = true,
                                channelTitle = page.channel.title,
                                channelHint = page.channel.handle.ifBlank { page.channel.channelId },
                                hasUploadScope = YouTubeAuthHelper.lastTokenIncludesUploadScope || prev.hasUploadScope
                            )
                        )
                    }
                    _ytLibraryState.update {
                        it.copy(
                            isConnected = true,
                            hasUploadScope = YouTubeAuthHelper.lastTokenIncludesUploadScope || it.hasUploadScope,
                            isLoading = false,
                            apiNeedsActivation = false,
                            channel = page.channel,
                            videos = page.videos,
                            nextPageToken = page.nextPageToken,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    if (e is YouTubeUnauthorizedException && appContext != null) {
                        val renewed = YouTubeAuthHelper.getSilentAccessToken(appContext, includeUploadScope = true)
                        if (!renewed.isNullOrBlank() && renewed != token) {
                            ytAccessToken = renewed
                            ytService.fetchMyYouTubeLibrary(accessToken = renewed).fold(
                                onSuccess = { page ->
                                    _ytLibraryState.update {
                                        it.copy(
                                            isConnected = true,
                                            hasUploadScope = YouTubeAuthHelper.lastTokenIncludesUploadScope || it.hasUploadScope,
                                            isLoading = false,
                                            apiNeedsActivation = false,
                                            channel = page.channel,
                                            videos = page.videos,
                                            nextPageToken = page.nextPageToken,
                                            error = null
                                        )
                                    }
                                    return@launch
                                },
                                onFailure = { inner ->
                                    handleLibraryFailure(inner, appContext)
                                    return@launch
                                }
                            )
                        }
                    }
                    handleLibraryFailure(e, appContext)
                }
            )
        }
    }

    private suspend fun handleLibraryFailure(e: Throwable, appContext: Context? = null) {
        val isApiDisabled = e is YouTubeApiDisabledException ||
            e.message?.contains("ativação", ignoreCase = true) == true

        if (isApiDisabled) {
            val hint = _ytLibraryState.value.channelHandleInput.trim().ifBlank {
                appContext?.let { StudioLocalPrefs.loadYouTubePersistentState(it).channelHint }.orEmpty()
            }.ifBlank {
                (sourcesState.value as? UiState.Success)?.data?.firstOrNull {
                    it.youtubeVideoId.isNotBlank()
                }?.youtubeVideoId.orEmpty()
            }
            if (hint.isNotBlank()) {
                val fallback = ytService.fetchPublicChannelLibrary(hint)
                if (fallback.isSuccess) {
                    val page = fallback.getOrThrow()
                    appContext?.let { ctx ->
                        val prev = StudioLocalPrefs.loadYouTubePersistentState(ctx)
                        StudioLocalPrefs.saveYouTubePersistentState(
                            ctx,
                            prev.copy(
                                wasConnected = true,
                                channelTitle = page.channel.title,
                                channelHint = page.channel.handle.ifBlank { page.channel.channelId }
                            )
                        )
                    }
                    _ytLibraryState.update {
                        it.copy(
                            isConnected = true,
                            isLoading = false,
                            apiNeedsActivation = true,
                            channel = page.channel,
                            videos = page.videos,
                            nextPageToken = null,
                            error = null
                        )
                    }
                    _snackbarMessage.value = "Canal ${page.channel.title} carregado (${page.videos.size} vídeos/lives)!"
                    return
                }
            }
        }

        _ytLibraryState.update {
            it.copy(
                isLoading = false,
                apiNeedsActivation = isApiDisabled,
                error = e.localizedMessage ?: "Falha ao comunicar com a API do YouTube."
            )
        }
    }

    fun loadMoreYouTubeVideos() {
        val state = _ytLibraryState.value
        val token = ytAccessToken ?: return
        val nextPage = state.nextPageToken ?: return
        val channel = state.channel ?: return
        if (state.isLoadingMore) return

        viewModelScope.launch {
            _ytLibraryState.update { it.copy(isLoadingMore = true) }
            ytService.fetchMyYouTubeLibrary(
                accessToken = token,
                pageToken = nextPage,
                existingChannel = channel
            ).fold(
                onSuccess = { page ->
                    _ytLibraryState.update { current ->
                        val existingIds = current.videos.map { it.videoId }.toSet()
                        val newOnes = page.videos.filterNot { it.videoId in existingIds }
                        current.copy(
                            isLoadingMore = false,
                            videos = current.videos + newOnes,
                            nextPageToken = page.nextPageToken
                        )
                    }
                },
                onFailure = { e ->
                    _ytLibraryState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = e.localizedMessage ?: "Erro ao carregar mais vídeos."
                        )
                    }
                }
            )
        }
    }

    fun updateYouTubeSearchQuery(query: String) {
        _ytLibraryState.update { it.copy(searchQuery = query) }
    }

    fun importFromYouTubeLibrary(
        video: YouTubeLibraryVideo,
        runFastAutoSlice: Boolean = false,
        context: Context? = null,
        onComplete: () -> Unit = {}
    ) {
        val existingSources = (sourcesState.value as? UiState.Success)?.data.orEmpty()
        val alreadySaved = existingSources.firstOrNull { it.youtubeVideoId == video.videoId }
        if (alreadySaved != null) {
            if (runFastAutoSlice) {
                runFastAutoShortsForSource(alreadySaved, context, onComplete)
            } else {
                _snackbarMessage.value = "\"${video.title}\" já está na biblioteca importada."
            }
            return
        }

        viewModelScope.launch {
            _ytLibraryState.update { it.copy(importingVideoId = video.videoId) }
            val resolvedUrls = ytService.getVideoUrlsForProcessing(video.videoId, ytAccessToken).getOrNull()
            if (resolvedUrls != null) {
                _selectedVideoProcessingUrls.value = resolvedUrls
            }
            val summaryText = video.description.trim().take(240).ifBlank {
                "Importado do canal ${video.channelName}."
            }
            val newSource = VideoSource(
                userId = currentUserId,
                youtubeUrl = resolvedUrls?.watchUrl ?: video.url,
                youtubeVideoId = video.videoId,
                title = (resolvedUrls?.title?.takeIf { it.isNotBlank() } ?: video.title).take(200),
                channelName = (resolvedUrls?.channelName?.takeIf { it.isNotBlank() } ?: video.channelName).take(120),
                sourceType = video.sourceType,
                durationLabel = (resolvedUrls?.durationLabel?.takeIf { it != "15:00" || video.durationLabel == "—" }
                    ?: video.durationLabel).take(30),
                status = "IMPORTED",
                aiSummary = summaryText
            )
            studioRepo.addSource(currentUserId, newSource).fold(
                onSuccess = { docId ->
                    _ytLibraryState.update { it.copy(importingVideoId = null) }
                    if (runFastAutoSlice) {
                        runFastAutoShortsForSource(newSource.copy(id = docId), context, onComplete)
                    } else {
                        _snackbarMessage.value = "\"${newSource.title}\" importado e URLs prontas para processamento!"
                    }
                },
                onFailure = { e ->
                    _ytLibraryState.update { it.copy(importingVideoId = null) }
                    _snackbarMessage.value = e.localizedMessage ?: "Erro ao importar vídeo."
                }
            )
        }
    }

    fun importSelectedLibraryVideosAndOpenEditor(
        videos: List<YouTubeLibraryVideo>,
        context: Context,
        onComplete: () -> Unit
    ) {
        val leadVideo = videos.firstOrNull() ?: return
        val existingSources = (sourcesState.value as? UiState.Success)?.data.orEmpty()
        val existingClips = (clipsState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            // Save any additional selected videos to Firestore sources in the background
            videos.drop(1).forEach { extra ->
                if (existingSources.none { it.youtubeVideoId == extra.videoId }) {
                    val src = VideoSource(
                        userId = currentUserId,
                        youtubeUrl = extra.url,
                        youtubeVideoId = extra.videoId,
                        title = extra.title.take(200),
                        channelName = extra.channelName.take(120),
                        sourceType = extra.sourceType,
                        durationLabel = extra.durationLabel.take(30),
                        status = "IMPORTED",
                        aiSummary = extra.description.take(240)
                    )
                    studioRepo.addSource(currentUserId, src)
                }
            }

            val existingLeadSource = existingSources.firstOrNull { it.youtubeVideoId == leadVideo.videoId }
            if (existingLeadSource != null) {
                val existingClip = existingClips.firstOrNull {
                    it.sourceId == existingLeadSource.id || it.sourceTitle.equals(existingLeadSource.title, ignoreCase = true)
                }
                if (existingClip != null) {
                    _selectedClipId.value = existingClip.id
                    onComplete()
                    return@launch
                }
            }

            importFromYouTubeLibrary(
                video = leadVideo,
                runFastAutoSlice = true,
                context = context,
                onComplete = onComplete
            )
        }
    }

    fun applyTemplateToActiveClip(
        context: Context,
        template: ShortTemplateItem,
        onComplete: () -> Unit = {}
    ) {
        val currentClips = (clipsState.value as? UiState.Success)?.data.orEmpty()
        val targetClip = currentClips.firstOrNull { it.id == _selectedClipId.value } ?: currentClips.firstOrNull()
        viewModelScope.launch {
            if (targetClip != null) {
                val updated = targetClip.copy(
                    captionStyle = template.captionStyle,
                    framingMode = template.framingMode,
                    highlightKeywords = template.highlightKeywords
                )
                studioRepo.updateClipEditor(currentUserId, updated)
                updateClipVisualStyle(context, targetClip.id) { current ->
                    current.copy(
                        filterId = template.filterPreset.id,
                        emojiOverlay = template.emojiOverlay,
                        coverTitle = template.headlineBanner,
                        burnOverlayOnExport = true
                    )
                }
                _selectedClipId.value = targetClip.id
                _snackbarMessage.value = "Template \"${template.name}\" aplicado no Editor!"
                onComplete()
            } else {
                val newClip = StudioClip(
                    userId = currentUserId,
                    sourceTitle = "Template • ${template.name}",
                    title = "${template.name} Short",
                    hookText = "Confira esse trecho incrível!",
                    startTime = "00:00",
                    endTime = "00:45",
                    durationSeconds = 45,
                    viralScore = 88,
                    aspectRatio = "9:16",
                    captionStyle = template.captionStyle,
                    framingMode = template.framingMode,
                    captionsText = "Confira esse trecho incrível com estilo ${template.name}!",
                    highlightKeywords = template.highlightKeywords,
                    targetPlatform = "BOTH",
                    publishStatus = "READY",
                    publishNotes = "Confira esse trecho incrível!\n#ShortPlanetStream"
                )
                studioRepo.addClip(currentUserId, newClip).onSuccess { newId ->
                    _selectedClipId.value = newId
                    updateClipVisualStyle(context, newId) { current ->
                        current.copy(
                            filterId = template.filterPreset.id,
                            emojiOverlay = template.emojiOverlay,
                            coverTitle = template.headlineBanner,
                            burnOverlayOnExport = true
                        )
                    }
                    _snackbarMessage.value = "Template \"${template.name}\" pronto para editar!"
                    onComplete()
                }
            }
        }
    }

    // --- YouTube URL Import ---

    fun updateImportUrl(url: String) {
        _importState.update { it.copy(urlInput = url, error = null) }
    }

    fun updateManualTitle(title: String) {
        _importState.update { it.copy(manualTitle = title, error = null) }
    }

    fun updateManualChannel(channel: String) {
        _importState.update { it.copy(manualChannel = channel) }
    }

    fun updateManualDuration(duration: String) {
        _importState.update { it.copy(manualDuration = duration) }
    }

    fun updateSourceType(type: String) {
        _importState.update { it.copy(sourceType = type) }
    }

    fun fetchYouTubeMetadata() {
        val url = _importState.value.urlInput.trim()
        if (url.isBlank()) {
            _importState.update { it.copy(error = "Cola um link do YouTube para continuar.") }
            return
        }
        viewModelScope.launch {
            _importState.update { it.copy(isFetchingMeta = true, error = null) }
            val result = ytService.fetchMetadata(url, ytAccessToken)
            result.fold(
                onSuccess = { meta ->
                    _importState.update {
                        it.copy(
                            isFetchingMeta = false,
                            previewMeta = meta,
                            manualTitle = meta.title,
                            manualChannel = meta.channelName,
                            manualDuration = meta.durationLabel,
                            manualDescription = meta.description,
                            sourceType = meta.detectedType
                        )
                    }
                },
                onFailure = { e ->
                    val videoId = ytService.extractVideoId(url)
                    if (videoId != null) {
                        _importState.update {
                            it.copy(
                                isFetchingMeta = false,
                                previewMeta = ResolvedYouTubeMetadata(
                                    normalizedUrl = "https://www.youtube.com/watch?v=$videoId",
                                    videoId = videoId,
                                    title = it.manualTitle,
                                    channelName = it.manualChannel,
                                    detectedType = it.sourceType
                                ),
                                error = "Não foi possível obter o título automático (${e.localizedMessage ?: "indisponível"}). Preenche o título manualmente abaixo."
                            )
                        }
                    } else {
                        _importState.update {
                            it.copy(
                                isFetchingMeta = false,
                                error = e.localizedMessage ?: "Link do YouTube inválido."
                            )
                        }
                    }
                }
            )
        }
    }

    fun saveImportedSource(onSaved: (String) -> Unit = {}) {
        val state = _importState.value
        val videoId = state.previewMeta?.videoId ?: ytService.extractVideoId(state.urlInput)
        val url = state.previewMeta?.normalizedUrl ?: state.urlInput.trim()
        val title = state.manualTitle.trim()

        if (videoId == null) {
            _importState.update { it.copy(error = "Cola um link válido do YouTube primeiro.") }
            return
        }
        if (title.isBlank()) {
            _importState.update { it.copy(error = "Indica um título para o vídeo ou transmissão.") }
            return
        }

        viewModelScope.launch {
            _importState.update { it.copy(isSaving = true, error = null) }
            val newSource = VideoSource(
                userId = currentUserId,
                youtubeUrl = url,
                youtubeVideoId = videoId,
                title = title,
                channelName = state.manualChannel.trim().ifBlank { "Canal YouTube" },
                sourceType = state.sourceType,
                durationLabel = state.manualDuration.trim().ifBlank { "15:00" },
                status = "IMPORTED",
                aiSummary = state.manualDescription.trim().take(240).ifBlank {
                    "Pronto para corte automático em Shorts 9:16."
                }
            )
            studioRepo.addSource(currentUserId, newSource).fold(
                onSuccess = { docId ->
                    _importState.value = ImportUiState()
                    _snackbarMessage.value = "Fonte guardada no teu estúdio!"
                    onSaved(docId)
                },
                onFailure = { e ->
                    _importState.update {
                        it.copy(
                            isSaving = false,
                            error = e.localizedMessage ?: "Erro ao guardar vídeo no Firestore."
                        )
                    }
                }
            )
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            studioRepo.deleteSource(currentUserId, sourceId).onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Erro ao remover vídeo."
            }
        }
    }

    // --- Fast Automatic Short Creation (Zero-AI Dependency) & Autopiloto IA ---

    fun runFastAutoShortsForSource(
        source: VideoSource,
        context: Context? = null,
        onComplete: () -> Unit = {}
    ) {
        if (_autopilotBusySourceId.value != null) return
        val style = (styleState.value as? UiState.Success)?.data ?: StylePreferences(userId = currentUserId)

        viewModelScope.launch {
            _autopilotBusySourceId.value = source.id
            _autopilotError.value = null
            val analysis = aiRepo.buildFastAutoShortProposals(source, style)
            persistProposalsAndNavigate(source, style, analysis, context, onComplete)
        }
    }

    fun runAutopilotForSource(
        source: VideoSource,
        context: Context? = null,
        onComplete: () -> Unit = {}
    ) {
        if (_autopilotBusySourceId.value != null) return
        val style = (styleState.value as? UiState.Success)?.data ?: StylePreferences(userId = currentUserId)
        val matchedLibVideo = _ytLibraryState.value.videos.firstOrNull { it.videoId == source.youtubeVideoId }

        viewModelScope.launch {
            _autopilotBusySourceId.value = source.id
            _autopilotError.value = null

            val aiResult = aiRepo.analyzeSourceForClips(
                source = source,
                style = style,
                extraDescription = matchedLibVideo?.description.orEmpty()
            )
            aiResult.fold(
                onSuccess = { analysis ->
                    persistProposalsAndNavigate(source, style, analysis, context, onComplete)
                },
                onFailure = { e ->
                    _autopilotBusySourceId.value = null
                    _autopilotError.value = e.localizedMessage
                        ?: "Falha na análise IA. Podes criar os Shorts com o Corte Automático (Sem IA)."
                }
            )
        }
    }

    /**
     * Vozo AI Long-to-Shorts 1-step pipeline:
     * Accepts a YouTube link + preferred presets (duration, aspect ratio, caption style, framing),
     * imports/reuses the source, runs AI highlight detection (with smart fast fallback), and opens Editor.
     */
    fun quickGenerateShortsFromUrl(
        context: Context,
        rawUrl: String,
        durationPreset: String = "30–60 segundos",
        aspectRatio: String = "9:16",
        captionStyle: String = "Dinâmico",
        framingMode: String = "Seguir pessoa",
        useAi: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        if (_autopilotBusySourceId.value != null) return
        val cleanUrl = rawUrl.trim()
        val videoId = ytService.extractVideoId(cleanUrl)
        if (videoId == null) {
            _snackbarMessage.value = "Cola um link válido do YouTube (ex: youtube.com/watch?v=...)."
            return
        }

        viewModelScope.launch {
            _autopilotBusySourceId.value = "quick_url_$videoId"
            _autopilotError.value = null

            val existing = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.youtubeVideoId == videoId }

            val source: VideoSource = if (existing != null) {
                existing
            } else {
                val meta = ytService.fetchMetadata(cleanUrl, ytAccessToken).getOrNull()
                val newSrc = VideoSource(
                    userId = currentUserId,
                    youtubeUrl = meta?.normalizedUrl ?: "https://www.youtube.com/watch?v=$videoId",
                    youtubeVideoId = videoId,
                    title = meta?.title?.takeIf { it.isNotBlank() } ?: "Vídeo YouTube ($videoId)",
                    channelName = meta?.channelName?.takeIf { it.isNotBlank() } ?: "Canal YouTube",
                    sourceType = meta?.detectedType ?: "VIDEO",
                    durationLabel = meta?.durationLabel?.takeIf { it.isNotBlank() } ?: "15:00",
                    status = "IMPORTED",
                    aiSummary = meta?.description?.take(240).orEmpty()
                )
                val newId = studioRepo.addSource(currentUserId, newSrc).getOrDefault("")
                newSrc.copy(id = newId)
            }

            val baseStyle = (styleState.value as? UiState.Success)?.data ?: StylePreferences(userId = currentUserId)
            val effectiveStyle = baseStyle.copy(
                preferredDuration = durationPreset,
                captionPreset = if (captionStyle == "Minimalista") "Discretas" else "Chamativas",
                framingPreset = framingMode
            )

            val analysis = if (useAi) {
                aiRepo.analyzeSourceForClips(source, effectiveStyle).getOrElse {
                    aiRepo.buildFastAutoShortProposals(source, effectiveStyle)
                }
            } else {
                aiRepo.buildFastAutoShortProposals(source, effectiveStyle)
            }

            val customAnalysis = analysis.copy(
                clips = analysis.clips.map { prop ->
                    prop.copy(targetPlatform = "BOTH")
                }
            )
            persistProposalsAndNavigate(
                source = source,
                style = effectiveStyle,
                analysis = customAnalysis,
                context = context,
                onComplete = onComplete
            )
            // Apply chosen aspectRatio & captionStyle onto the newly created lead clip if non-default
            val createdLeadId = _selectedClipId.value
            val createdLead = (clipsState.value as? UiState.Success)?.data?.firstOrNull { it.id == createdLeadId }
            if (createdLead != null && (aspectRatio != "9:16" || captionStyle != createdLead.captionStyle)) {
                studioRepo.updateClipEditor(
                    currentUserId,
                    createdLead.copy(aspectRatio = aspectRatio, captionStyle = captionStyle)
                )
            }
        }
    }

    /**
     * Vozo-style on-demand speech-to-captions extraction from the currently loaded local video clip.
     */
    fun transcribeClipSpeechWithAi(context: Context, clip: StudioClip) {
        val uri = _localVideoInfo.value?.uri
        if (uri == null) {
            _snackbarMessage.value = "Escolhe primeiro um vídeo do telemóvel em \"Corte\" para transcrever a fala real."
            return
        }
        if (_aiEditBusy.value) return
        viewModelScope.launch {
            _aiEditBusy.value = true
            val startSec = YouTubeMetadataService.parseTimestampToSeconds(clip.startTime)
            val endSec = YouTubeMetadataService.parseTimestampToSeconds(clip.endTime)
                .let { if (it > startSec + 2) it else startSec + 30 }
            val (transcript, _) = prepareDynamicSpeechCaptions(
                context = context,
                inputUri = uri,
                startMs = startSec * 1000L,
                endMs = endSec * 1000L,
                fallbackClip = clip
            )
            _aiEditBusy.value = false
            if (transcript.isNotBlank()) {
                studioRepo.updateClipEditor(currentUserId, clip.copy(captionsText = transcript))
                _snackbarMessage.value = "Fala transcrita com IA e aplicada às legendas do Short!"
            } else {
                _snackbarMessage.value = "Sem fala detetada neste trecho — legendas mantidas do roteiro."
            }
        }
    }

    private suspend fun persistProposalsAndNavigate(
        source: VideoSource,
        style: StylePreferences,
        analysis: com.example.data.model.SourceAnalysisResult,
        context: Context?,
        onComplete: () -> Unit
    ) {
        if (source.id.isNotBlank()) {
            studioRepo.updateSourceAnalysis(
                userId = currentUserId,
                sourceId = source.id,
                status = "ANALYZED",
                aiSummary = analysis.summary
            )
        }
        val captionStyle = when (style.captionPreset) {
            "Discretas", "Sem legendas" -> "Minimalista"
            else -> "Dinâmico"
        }
        var firstClip: StudioClip? = null
        analysis.clips.forEach { proposal ->
            val clip = StudioClip(
                userId = currentUserId,
                sourceId = source.id,
                sourceTitle = source.title,
                title = proposal.title,
                hookText = proposal.hookText,
                startTime = proposal.startTime,
                endTime = proposal.endTime,
                durationSeconds = proposal.durationSeconds,
                viralScore = proposal.viralScore,
                aspectRatio = "9:16",
                captionStyle = captionStyle,
                framingMode = style.framingPreset,
                captionsText = proposal.captionsText,
                highlightKeywords = proposal.highlightKeywords,
                targetPlatform = proposal.targetPlatform,
                publishStatus = "READY",
                publishNotes = proposal.publishNotes
            )
            studioRepo.addClip(currentUserId, clip).onSuccess { id ->
                if (firstClip == null) firstClip = clip.copy(id = id)
            }
        }
        val leadClip = firstClip
        if (leadClip != null) {
            _selectedClipId.value = leadClip.id
            if (context != null && source.youtubeVideoId.isNotBlank()) {
                val defaultStyle = ClipVisualStyle(
                    filterId = VideoFilterPreset.NEON_CYBER.id,
                    emojiOverlay = "🔥",
                    coverTitle = leadClip.title.substringBefore("•").trim().take(36),
                    burnOverlayOnExport = true
                )
                _clipVisualStyles.value = StudioLocalPrefs.saveClipStyle(
                    context.applicationContext,
                    leadClip.id,
                    defaultStyle
                )
                val res = ensureRealMp4ForClip(context.applicationContext, leadClip, source.youtubeVideoId)
                if (res.isSuccess && _autoPublishOnCut.value &&
                    (_ytLibraryState.value.isConnected || _tikTokAccount.value.isConnected)
                ) {
                    publishClipToBothPlatformsInternal(
                        context = context,
                        clip = leadClip,
                        existingFile = res.getOrNull()
                    )
                }
            }
        }
        _autopilotBusySourceId.value = null
        _snackbarMessage.value = "${analysis.clips.size} Shorts 9:16 criados e prontos!"
        onComplete()
    }

    // --- Real On-Device Local Video Picking, Media3 Cutting & 9:16 MP4 Export ---

    fun onLocalVideoPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val info = ShortVideoProcessor.inspectVideo(context, uri)
            if (info != null) {
                _localVideoInfo.value = info
                _snackbarMessage.value = "Vídeo carregado (${info.durationLabel})! Pronto para cortar Shorts 9:16."
            } else {
                _snackbarMessage.value = "Não foi possível ler os metadados deste vídeo."
            }
        }
    }

    /**
     * Loads a local video into the centered timeline editor without triggering premature export,
     * initializing or clamping start/end timestamps to the loaded video's real duration.
     */
    fun loadLocalVideoIntoEditor(
        context: Context,
        uri: Uri,
        existingActiveClip: StudioClip? = null
    ) {
        viewModelScope.launch {
            val info = ShortVideoProcessor.inspectVideo(context, uri)
            if (info == null) {
                _snackbarMessage.value = "Não foi possível ler os metadados deste vídeo."
                return@launch
            }
            _localVideoInfo.value = info
            val totalSec = (info.durationMs / 1000L).toInt().coerceAtLeast(3)
            val baseName = info.displayName.substringBeforeLast(".").ifBlank { "Clipe Vertical" }

            if (existingActiveClip == null || existingActiveClip.id.isBlank()) {
                val initEndSec = totalSec.coerceAtMost(30).coerceAtLeast(2)
                val newClip = StudioClip(
                    userId = currentUserId,
                    sourceTitle = baseName.take(120),
                    title = "${baseName.take(40)} • Short 9:16",
                    hookText = "Confira esse trecho incrível!",
                    startTime = formatMsToMmSs(0L),
                    endTime = formatMsToMmSs(initEndSec * 1000L),
                    durationSeconds = initEndSec.toLong(),
                    viralScore = 88L,
                    aspectRatio = "9:16",
                    captionStyle = "Dinâmico",
                    framingMode = "Centro",
                    captionsText = "Ajusta as legendas deste corte vertical!",
                    highlightKeywords = "incrível, agora, short",
                    targetPlatform = "BOTH",
                    publishStatus = "READY",
                    publishNotes = "$baseName #Shorts #ShortPlanetStream"
                )
                studioRepo.addClip(currentUserId, newClip).onSuccess { newId ->
                    _selectedClipId.value = newId
                }
            } else {
                val curStart = YouTubeMetadataService.parseTimestampToSeconds(existingActiveClip.startTime)
                val curEnd = YouTubeMetadataService.parseTimestampToSeconds(existingActiveClip.endTime)
                    .let { if (it > curStart) it else curStart + 30 }
                val (clampedStart, clampedEnd) = com.example.media.clampTrimRangeSec(
                    startSec = curStart,
                    endSec = curEnd.coerceAtMost(totalSec),
                    totalDurationSec = totalSec
                )
                val updated = existingActiveClip.copy(
                    startTime = formatMsToMmSs(clampedStart * 1000L),
                    endTime = formatMsToMmSs(clampedEnd * 1000L),
                    durationSeconds = (clampedEnd - clampedStart).toLong().coerceAtLeast(2L),
                    aspectRatio = "9:16",
                    sourceTitle = existingActiveClip.sourceTitle.ifBlank { baseName }
                )
                studioRepo.updateClipEditor(currentUserId, updated)
            }
            _snackbarMessage.value = "Vídeo carregado (${info.durationLabel})! Arrasta o ponto inicial e final na timeline."
        }
    }

    fun generateAutoMp4ShortsFromLocalVideo(
        context: Context,
        uri: Uri,
        onComplete: () -> Unit = {}
    ) {
        if (_shortExportState.value is ShortExportUiState.Processing) return
        val style = (styleState.value as? UiState.Success)?.data ?: StylePreferences(userId = currentUserId)

        viewModelScope.launch {
            _shortExportState.value = ShortExportUiState.Processing(2, "A analisar vídeo local...")
            val info = ShortVideoProcessor.inspectVideo(context, uri)
            if (info == null) {
                _shortExportState.value = ShortExportUiState.Error("Não foi possível abrir o vídeo selecionado.")
                return@launch
            }
            _localVideoInfo.value = info

            val segments = ShortVideoProcessor.computeAutoShortSegments(
                totalDurationMs = info.durationMs,
                preferredDurationPreset = style.preferredDuration,
                baseTitle = info.displayName.substringBeforeLast(".")
            )

            // Ensure a source card exists in Firestore too
            val sourceTitle = info.displayName.substringBeforeLast(".").take(180)
            val newSource = VideoSource(
                userId = currentUserId,
                youtubeUrl = uri.toString(),
                youtubeVideoId = "",
                title = sourceTitle,
                channelName = "Vídeo do Telemóvel",
                sourceType = "VIDEO",
                durationLabel = info.durationLabel,
                status = "ANALYZED",
                aiSummary = "Corte automático local em ${segments.size} Shorts 9:16."
            )
            val sourceId = studioRepo.addSource(currentUserId, newSource).getOrDefault("")

            var firstClip: StudioClip? = null
            var lastRenderedFile: File? = null

            for ((index, seg) in segments.withIndex()) {
                val clip = StudioClip(
                    userId = currentUserId,
                    sourceId = sourceId,
                    sourceTitle = sourceTitle,
                    title = seg.title,
                    hookText = seg.hookText,
                    startTime = seg.startLabel,
                    endTime = seg.endLabel,
                    durationSeconds = seg.durationSeconds,
                    viralScore = (88 + index * 3).toLong().coerceAtMost(97L),
                    aspectRatio = "9:16",
                    captionStyle = if (style.captionPreset == "Discretas") "Minimalista" else "Dinâmico",
                    framingMode = style.framingPreset,
                    captionsText = seg.hookText,
                    highlightKeywords = "momento, shorts, incrível",
                    targetPlatform = "BOTH",
                    publishStatus = "READY",
                    publishNotes = "${seg.title} #Shorts #TikTok"
                )
                val clipId = studioRepo.addClip(currentUserId, clip).getOrNull() ?: continue
                var savedClip = clip.copy(id = clipId)
                if (firstClip == null) firstClip = savedClip

                val (spokenTranscript, timedSegments) = prepareDynamicSpeechCaptions(
                    context = context,
                    inputUri = uri,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    fallbackClip = savedClip
                )
                if (spokenTranscript.isNotBlank()) {
                    savedClip = savedClip.copy(captionsText = spokenTranscript)
                    studioRepo.updateClipEditor(currentUserId, savedClip)
                    if (firstClip?.id == savedClip.id) firstClip = savedClip
                }

                val visualStyle = _clipVisualStyles.value[clipId] ?: ClipVisualStyle()
                val renderResult = runTransformerSuspend(
                    context = context,
                    inputUri = uri,
                    clipId = clipId,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    aspectRatio = "9:16",
                    manualPanX = 0f,
                    visualStyle = visualStyle,
                    captionsText = savedClip.captionsText.ifBlank { savedClip.hookText },
                    captionStyle = savedClip.captionStyle,
                    timedCaptions = timedSegments,
                    highlightKeywords = savedClip.highlightKeywords,
                    labelPrefix = "A cortar Short ${index + 1}/${segments.size}"
                )
                if (renderResult.isSuccess) {
                    lastRenderedFile = renderResult.getOrThrow()
                    _renderedClipIds.update { it + clipId }
                } else {
                    val err = renderResult.exceptionOrNull()?.localizedMessage ?: "Falha ao cortar Short ${index + 1}."
                    _shortExportState.value = ShortExportUiState.Error(err)
                    return@launch
                }
            }

            if (firstClip != null) {
                _selectedClipId.value = firstClip.id
            }
            val finalFile = lastRenderedFile
            if (firstClip != null && finalFile != null) {
                _shortExportState.value = ShortExportUiState.Success(firstClip.id, finalFile.absolutePath)
                _snackbarMessage.value = "${segments.size} Shorts MP4 9:16 cortados e prontos!"
                if (_autoPublishOnCut.value && (_ytLibraryState.value.isConnected || _tikTokAccount.value.isConnected)) {
                    val leadFile = ShortVideoProcessor.getRenderedShortFile(context, firstClip.id) ?: finalFile
                    publishClipToBothPlatformsInternal(context = context, clip = firstClip, existingFile = leadFile)
                }
                onComplete()
            } else {
                _shortExportState.value = ShortExportUiState.Error("Não foi possível guardar os Shorts gerados.")
            }
        }
    }

    /**
     * Converts selected gallery photos into a real playable & publishable 9:16 MP4 Short!
     */
    fun createShortMp4FromPhotos(
        context: Context,
        uris: List<Uri>,
        title: String = "Short de Fotos 9:16",
        targetClip: StudioClip? = null,
        onComplete: () -> Unit = {}
    ) {
        if (uris.isEmpty() || _shortExportState.value is ShortExportUiState.Processing) return
        viewModelScope.launch {
            _shortExportState.value = ShortExportUiState.Processing(2, "A preparar fotos para MP4 9:16...")
            val photoFiles = ShortVideoProcessor.copyPhotoUrisToCacheFiles(context, uris, "short_photo")
            if (photoFiles.isEmpty()) {
                _shortExportState.value = ShortExportUiState.Error("Não foi possível carregar as fotos selecionadas.")
                return@launch
            }

            val resolvedClip: StudioClip? = if (targetClip != null && targetClip.id.isNotBlank()) {
                targetClip
            } else {
                val clip = StudioClip(
                    userId = currentUserId,
                    sourceTitle = "Fotos da Galeria (${photoFiles.size})",
                    title = title.trim().ifBlank { "Short de Fotos" },
                    hookText = "Olha estes destaques em 9:16!",
                    startTime = "00:00",
                    endTime = "00:15",
                    durationSeconds = 15L,
                    viralScore = 90L,
                    aspectRatio = "9:16",
                    captionStyle = "Dinâmico",
                    framingMode = "Centro",
                    captionsText = "Confere os melhores momentos!",
                    highlightKeywords = "melhores, momentos, shorts",
                    targetPlatform = "BOTH",
                    publishStatus = "READY",
                    publishNotes = "$title #Shorts #TikTok #PlanetStreaming"
                )
                val newId = studioRepo.addClip(currentUserId, clip).getOrNull()
                newId?.let { clip.copy(id = it) }
            }

            if (resolvedClip == null) {
                _shortExportState.value = ShortExportUiState.Error("Não foi possível registar o Short no estúdio.")
                return@launch
            }

            val visualStyle = _clipVisualStyles.value[resolvedClip.id] ?: ClipVisualStyle(
                filterId = VideoFilterPreset.NEON_CYBER.id,
                emojiOverlay = "🔥",
                coverTitle = resolvedClip.title.take(32)
            )
            val durationMs = (photoFiles.size * 4_000L).coerceIn(10_000L, 30_000L)

            val res = runImagesTransformerSuspend(
                context = context,
                imageFiles = photoFiles,
                clipId = resolvedClip.id,
                totalDurationMs = durationMs,
                aspectRatio = resolvedClip.aspectRatio,
                visualStyle = visualStyle,
                captionsText = resolvedClip.captionsText.ifBlank { resolvedClip.hookText },
                captionStyle = resolvedClip.captionStyle,
                labelPrefix = "A criar Short MP4 de Fotos"
            )
            res.fold(
                onSuccess = { outFile ->
                    _selectedClipId.value = resolvedClip.id
                    _renderedClipIds.update { it + resolvedClip.id }
                    _shortExportState.value = ShortExportUiState.Success(resolvedClip.id, outFile.absolutePath)
                    _snackbarMessage.value = "Short MP4 9:16 criado a partir das fotos! Pronto para YouTube Shorts e TikTok."
                    if (_autoPublishOnCut.value && (_ytLibraryState.value.isConnected || _tikTokAccount.value.isConnected)) {
                        publishClipToBothPlatformsInternal(context = context, clip = resolvedClip, existingFile = outFile)
                    }
                    onComplete()
                },
                onFailure = { e ->
                    _shortExportState.value = ShortExportUiState.Error(
                        e.localizedMessage ?: "Falha ao converter fotos em MP4."
                    )
                }
            )
        }
    }

    fun renderClipToMp4(
        context: Context,
        clip: StudioClip,
        aspectRatio: String = clip.aspectRatio,
        startLabel: String = clip.startTime,
        endLabel: String = clip.endTime,
        manualPanX: Float = 0f,
        overrideUri: Uri? = null,
        triggerAutoPublish: Boolean = _autoPublishOnCut.value
    ) {
        if (_shortExportState.value is ShortExportUiState.Processing) return
        viewModelScope.launch {
            renderClipToMp4Internal(
                context = context,
                clip = clip,
                aspectRatio = aspectRatio,
                startLabel = startLabel,
                endLabel = endLabel,
                manualPanX = manualPanX,
                overrideUri = overrideUri,
                triggerAutoPublish = triggerAutoPublish
            )
        }
    }

    private suspend fun renderClipToMp4Internal(
        context: Context,
        clip: StudioClip,
        aspectRatio: String = clip.aspectRatio,
        startLabel: String = clip.startTime,
        endLabel: String = clip.endTime,
        manualPanX: Float = 0f,
        overrideUri: Uri? = null,
        triggerAutoPublish: Boolean = false
    ) {
        val uri = overrideUri ?: _localVideoInfo.value?.uri
        val visualStyle = _clipVisualStyles.value[clip.id] ?: ClipVisualStyle()

        if (uri != null) {
            val info = _localVideoInfo.value?.takeIf { it.uri == uri }
                ?: ShortVideoProcessor.inspectVideo(context, uri)?.also { _localVideoInfo.value = it }

            val startSec = YouTubeMetadataService.parseTimestampToSeconds(startLabel)
            var endSec = YouTubeMetadataService.parseTimestampToSeconds(endLabel)
            if (endSec <= startSec) endSec = startSec + 30

            val maxDurationMs = info?.durationMs ?: (endSec * 1000L)
            val startMs = (startSec * 1000L).coerceIn(0L, (maxDurationMs - 2000L).coerceAtLeast(0L))
            val endMs = (endSec * 1000L).coerceIn(startMs + 2000L, maxDurationMs.coerceAtLeast(startMs + 2000L))

            _shortExportState.value = ShortExportUiState.Processing(1, "A sincronizar legendas da fala...")
            val (spokenTranscript, timedSegments) = prepareDynamicSpeechCaptions(
                context = context,
                inputUri = uri,
                startMs = startMs,
                endMs = endMs,
                fallbackClip = clip
            )
            val updatedClip = if (spokenTranscript.isNotBlank() && spokenTranscript != clip.captionsText) {
                clip.copy(captionsText = spokenTranscript).also {
                    studioRepo.updateClipEditor(currentUserId, it)
                }
            } else clip

            val res = runTransformerSuspend(
                context = context,
                inputUri = uri,
                clipId = updatedClip.id,
                startMs = startMs,
                endMs = endMs,
                aspectRatio = aspectRatio,
                manualPanX = manualPanX,
                visualStyle = visualStyle,
                captionsText = updatedClip.captionsText.ifBlank { updatedClip.hookText },
                captionStyle = updatedClip.captionStyle,
                timedCaptions = timedSegments,
                highlightKeywords = updatedClip.highlightKeywords,
                labelPrefix = "A renderizar ${updatedClip.title.take(20)} ($aspectRatio)"
            )
            handleRenderOutcome(context, updatedClip, aspectRatio, res, triggerAutoPublish)
        } else {
            // Re-render fresh scene MP4 from saved cover / HD YouTube thumbnail + new filter/caption/emoji
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val res = ensureRealMp4ForClip(
                context = context,
                clip = clip.copy(aspectRatio = aspectRatio),
                youtubeVideoId = linkedSource?.youtubeVideoId.orEmpty(),
                forceRebuild = true
            )
            handleRenderOutcome(context, clip, aspectRatio, res, triggerAutoPublish)
        }
    }

    private suspend fun handleRenderOutcome(
        context: Context,
        clip: StudioClip,
        aspectRatio: String,
        res: Result<File>,
        triggerAutoPublish: Boolean
    ) {
        res.fold(
            onSuccess = { outFile ->
                _renderedClipIds.update { it + clip.id }
                _shortExportState.value = ShortExportUiState.Success(clip.id, outFile.absolutePath)
                studioRepo.updateClipPublishing(
                    userId = currentUserId,
                    clipId = clip.id,
                    targetPlatform = clip.targetPlatform,
                    publishStatus = if (clip.publishStatus == "DRAFT") "READY" else clip.publishStatus,
                    publishNotes = clip.publishNotes
                )
                if (triggerAutoPublish && (_ytLibraryState.value.isConnected || _tikTokAccount.value.isConnected)) {
                    _snackbarMessage.value = "Corte MP4 ($aspectRatio) pronto! A enviar para YouTube + TikTok..."
                    publishClipToBothPlatformsInternal(
                        context = context,
                        clip = clip,
                        existingFile = outFile
                    )
                } else {
                    _snackbarMessage.value = "Short MP4 ($aspectRatio) pronto com filtros e overlays! Podes publicar no YouTube ou TikTok."
                }
            },
            onFailure = { e ->
                _shortExportState.value = ShortExportUiState.Error(
                    e.localizedMessage ?: "Falha ao renderizar o Short MP4."
                )
            }
        )
    }

    /**
     * Ensures [clip] has a real local MP4 file on disk. If none was cut from a local video yet,
     * uses any saved cover image or downloads the linked YouTube HD thumbnail and renders a real
     * 9:16 MP4 Short with the clip's filter, emojis, headline & captions!
     */
    private suspend fun ensureRealMp4ForClip(
        context: Context,
        clip: StudioClip,
        youtubeVideoId: String,
        forceRebuild: Boolean = false
    ): Result<File> {
        if (!forceRebuild) {
            ShortVideoProcessor.getRenderedShortFile(context, clip.id)?.let { existing ->
                _renderedClipIds.update { it + clip.id }
                return Result.success(existing)
            }
        }
        val visualStyle = _clipVisualStyles.value[clip.id] ?: ClipVisualStyle()
        val coverFile = visualStyle.coverImagePath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 1024L }
            ?: CoverStudioRenderer.getCoverFile(context, clip.id)

        val imageFile: File = if (coverFile != null) {
            coverFile
        } else {
            val cacheImg = File(context.cacheDir, "yt_thumb_${clip.id}.jpg")
            val downloaded = if (youtubeVideoId.isNotBlank()) {
                ytService.downloadYouTubeThumbnailFile(youtubeVideoId, cacheImg).getOrNull()
            } else null

            if (downloaded != null) {
                downloaded
            } else {
                // Generate local neon Studio Cover as fallback image so MP4 always builds
                val fallbackBmp = CoverStudioRenderer.renderCoverBitmap(
                    headline = visualStyle.coverTitle.ifBlank { clip.title },
                    subtitle = clip.sourceTitle.ifBlank { "Planet Streaming" },
                    emojiBadge = visualStyle.emojiOverlay,
                    filterPreset = visualStyle.filterPreset
                )
                CoverStudioRenderer.saveCoverForClip(context, clip.id, fallbackBmp)
            }
        }

        val res = runImagesTransformerSuspend(
            context = context,
            imageFiles = listOf(imageFile),
            clipId = clip.id,
            totalDurationMs = 12_000L,
            aspectRatio = clip.aspectRatio,
            visualStyle = visualStyle,
            captionsText = clip.captionsText.ifBlank { clip.hookText },
            captionStyle = clip.captionStyle,
            labelPrefix = "A preparar MP4 ${clip.aspectRatio}"
        )
        res.onSuccess { _renderedClipIds.update { it + clip.id } }
        return res
    }

    private suspend fun prepareDynamicSpeechCaptions(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        fallbackClip: StudioClip
    ): Pair<String, List<TimedCaptionSegment>> {
        val clipDurMs = (endMs - startMs).coerceAtLeast(3_000L)
        val fallbackScript = buildString {
            val h = fallbackClip.hookText.trim()
            val c = fallbackClip.captionsText.trim()
            if (h.isNotBlank()) append(h)
            if (c.isNotBlank() && !c.equals(h, ignoreCase = true)) {
                if (isNotEmpty()) append(" ")
                append(c)
            }
        }
        val audioSlice = withContext(Dispatchers.IO) {
            StudioAudioGenerator.extractClippedAudioForTranscription(
                context = context,
                uri = inputUri,
                startMs = startMs,
                endMs = endMs
            )
        }
        if (audioSlice != null) {
            try {
                val bytes = withContext(Dispatchers.IO) { audioSlice.readBytes() }
                val (transcript, segments) = aiRepo.transcribeAudioToTimedCaptions(
                    audioBytes = bytes,
                    mimeType = "audio/mp4",
                    clipDurationMs = clipDurMs
                )
                if (segments.isNotEmpty()) {
                    return transcript to segments
                }
            } finally {
                try {
                    audioSlice.delete()
                } catch (_: Exception) {
                }
            }
        }
        return "" to ShortVideoProcessor.buildTimedCaptionSegments(fallbackScript, clipDurMs)
    }

    private suspend fun runTransformerSuspend(
        context: Context,
        inputUri: Uri,
        clipId: String,
        startMs: Long,
        endMs: Long,
        aspectRatio: String,
        manualPanX: Float,
        visualStyle: ClipVisualStyle,
        captionsText: String,
        captionStyle: String,
        timedCaptions: List<TimedCaptionSegment> = emptyList(),
        highlightKeywords: String = "",
        labelPrefix: String
    ): Result<File> = suspendCancellableCoroutine { cont ->
        _shortExportState.value = ShortExportUiState.Processing(1, "$labelPrefix • 1%")
        val cancelFn = ShortVideoProcessor.exportShortClip(
            context = context,
            inputUri = inputUri,
            clipId = clipId,
            startMs = startMs,
            endMs = endMs,
            aspectRatio = aspectRatio,
            manualPanX = manualPanX,
            visualStyle = visualStyle,
            captionsText = captionsText,
            captionStyle = captionStyle,
            timedCaptions = timedCaptions,
            highlightKeywords = highlightKeywords,
            onProgress = { pct ->
                _shortExportState.value = ShortExportUiState.Processing(pct, "$labelPrefix • $pct%")
            },
            onSuccess = { file ->
                if (cont.isActive) cont.resume(Result.success(file))
            },
            onError = { err ->
                if (cont.isActive) cont.resume(Result.failure(Exception(err)))
            }
        )
        cancelCurrentExport = cancelFn
        cont.invokeOnCancellation { cancelFn() }
    }

    private suspend fun runImagesTransformerSuspend(
        context: Context,
        imageFiles: List<File>,
        clipId: String,
        totalDurationMs: Long,
        aspectRatio: String,
        visualStyle: ClipVisualStyle,
        captionsText: String,
        captionStyle: String,
        timedCaptions: List<TimedCaptionSegment> = emptyList(),
        highlightKeywords: String = "",
        labelPrefix: String
    ): Result<File> = suspendCancellableCoroutine { cont ->
        _shortExportState.value = ShortExportUiState.Processing(2, "$labelPrefix • 2%")
        val cancelFn = ShortVideoProcessor.exportShortFromImages(
            context = context,
            imageFiles = imageFiles,
            clipId = clipId,
            totalDurationMs = totalDurationMs,
            aspectRatio = aspectRatio,
            visualStyle = visualStyle,
            captionsText = captionsText,
            captionStyle = captionStyle,
            timedCaptions = timedCaptions,
            highlightKeywords = highlightKeywords,
            onProgress = { pct ->
                _shortExportState.value = ShortExportUiState.Processing(pct, "$labelPrefix • $pct%")
            },
            onSuccess = { file ->
                if (cont.isActive) cont.resume(Result.success(file))
            },
            onError = { err ->
                if (cont.isActive) cont.resume(Result.failure(Exception(err)))
            }
        )
        cancelCurrentExport = cancelFn
        cont.invokeOnCancellation { cancelFn() }
    }

    fun cancelShortExport() {
        cancelCurrentExport?.invoke()
        cancelCurrentExport = null
        _shortExportState.value = ShortExportUiState.Idle
    }

    fun saveClipToGallery(context: Context, clip: StudioClip) {
        viewModelScope.launch {
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val file = ensureRealMp4ForClip(context, clip, linkedSource?.youtubeVideoId.orEmpty()).getOrNull()
            if (file == null) {
                _snackbarMessage.value = "Não foi possível preparar o ficheiro MP4 para guardar."
                return@launch
            }
            ShortVideoProcessor.saveShortToGallery(context, file, clip.title).fold(
                onSuccess = {
                    _snackbarMessage.value = "Short MP4 guardado em Movies/PlanetStreaming na tua Galeria!"
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Erro ao guardar na galeria."
                }
            )
        }
    }

    /**
     * Simultaneous Dual-Channel Publish ("aproveitar o mesmo corte e mandar ao mesmo tempo para as duas contas,
     * YouTube e TikTok ao mesmo tempo").
     * Ensures the 9:16 MP4 once, uploads to YouTube Data API v3 in background, and dispatches to TikTok
     * (via Content Posting API v2 if token set, or directly to the TikTok app with caption copied).
     */
    fun publishClipToBothPlatforms(
        context: Context,
        clip: StudioClip,
        activity: Activity? = null,
        onNeedsUploadConsent: ((IntentSenderRequest) -> Unit)? = null
    ) {
        if (_youTubeUploadBusy.value || _shortExportState.value is ShortExportUiState.Processing) return
        viewModelScope.launch {
            publishClipToBothPlatformsInternal(
                context = context,
                clip = clip,
                existingFile = null,
                activity = activity,
                onNeedsUploadConsent = onNeedsUploadConsent
            )
        }
    }

    private suspend fun publishClipToBothPlatformsInternal(
        context: Context,
        clip: StudioClip,
        existingFile: File? = null,
        activity: Activity? = null,
        onNeedsUploadConsent: ((IntentSenderRequest) -> Unit)? = null
    ) {
        _youTubeUploadBusy.value = true
        _dualPublishStatus.value = DualPublishStatus(
            clipId = clip.id,
            clipTitle = clip.title,
            isRunning = true,
            youtubeStatus = "A preparar Short MP4 9:16...",
            tikTokStatus = "A aguardar o mesmo MP4..."
        )

        val linkedSource = (sourcesState.value as? UiState.Success)?.data
            ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }

        val file = existingFile ?: ensureRealMp4ForClip(
            context = context,
            clip = clip,
            youtubeVideoId = linkedSource?.youtubeVideoId.orEmpty()
        ).getOrNull()

        if (file == null) {
            _youTubeUploadBusy.value = false
            _dualPublishStatus.value = DualPublishStatus(
                clipId = clip.id,
                clipTitle = clip.title,
                isRunning = false,
                youtubeStatus = "Falha ao gerar o MP4.",
                tikTokStatus = "Cancelado (MP4 indisponível)."
            )
            _snackbarMessage.value = "Não foi possível gerar o MP4 do Short para publicar."
            return
        }

        // 1. Background YouTube Data API v3 upload
        var ytSuccess = false
        var ytNeedsShare = false
        var ytVideoUrl = ""
        var ytStatusMsg = "A enviar para YouTube..."

        _dualPublishStatus.update {
            it?.copy(
                youtubeStatus = "A enviar MP4 para YouTube Shorts...",
                tikTokStatus = if (_tikTokAccount.value.hasApiUploadToken) {
                    "A enviar MP4 para TikTok API..."
                } else {
                    "MP4 pronto para ${_tikTokAccount.value.handle.ifBlank { "TikTok" }}"
                }
            )
        }

        val silentUploadToken = YouTubeAuthHelper.getSilentAccessToken(
            context.applicationContext,
            includeUploadScope = true
        ) ?: ytAccessToken?.takeIf { YouTubeAuthHelper.lastTokenIncludesUploadScope }

        if (!silentUploadToken.isNullOrBlank()) {
            val ytUploadRes = ytService.uploadShortToYouTube(
                accessToken = silentUploadToken,
                mp4File = file,
                title = clip.title,
                description = clip.publishNotes.ifBlank { clip.hookText }
            )
            if (ytUploadRes.isSuccess) {
                val uploaded = ytUploadRes.getOrThrow()
                ytSuccess = true
                ytVideoUrl = "https://youtube.com/shorts/${uploaded.id}"
                ytStatusMsg = "Publicado no YouTube Shorts! (ID: ${uploaded.id})"
            } else {
                val err = ytUploadRes.exceptionOrNull()
                if (err is YouTubeApiDisabledException) {
                    _ytLibraryState.update { it.copy(apiNeedsActivation = true) }
                }
                ytNeedsShare = true
                ytStatusMsg = err?.localizedMessage?.take(110)
                    ?: "Requer confirmação no YouTube app (ou ativar API v3)."
            }
        } else {
            ytNeedsShare = true
            ytStatusMsg = if (_ytLibraryState.value.isConnected) {
                "Sem permissão youtube.upload ativa — pronto p/ envio no YouTube."
            } else {
                "Pronto p/ envio direto via YouTube App."
            }
        }

        // 2. Same MP4 -> TikTok (Open API v2 Direct Post + Inbox fallback, else app share)
        var ttSuccess = false
        var ttNeedsShare = false
        var ttStatusMsg: String

        val ttAccount = refreshTikTokTokenIfNeeded(context)
        val ttHandle = ttAccount.handle
        val ttCaption = buildString {
            append(clip.publishNotes.ifBlank { clip.title })
            if (ttHandle.isNotBlank() && !contains(ttHandle)) {
                append("\n$ttHandle")
            }
        }

        if (ttAccount.hasApiUploadToken) {
            val ttRes = tikTokUploadService.uploadShortToTikTok(
                accessToken = ttAccount.apiAccessToken,
                mp4File = file,
                title = clip.title
            )
            if (ttRes.isSuccess) {
                ttSuccess = true
                ttStatusMsg = "${ttRes.getOrThrow().statusNote} ($ttHandle)"
            } else {
                ttNeedsShare = true
                ttStatusMsg = "TikTok API recusado (${ttRes.exceptionOrNull()?.localizedMessage?.take(60)}); usar envio App."
            }
        } else {
            ttNeedsShare = true
            ttStatusMsg = "Pronto para abrir no TikTok (${ttHandle.ifBlank { "Conectar API ou Partilhar MP4" }})."
        }

        // 3. Handle OAuth consent if YouTube failed purely for missing upload permission and caller provided launcher
        if (!ytSuccess && activity != null && onNeedsUploadConsent != null &&
            _ytLibraryState.value.isConnected && !YouTubeAuthHelper.lastTokenIncludesUploadScope &&
            !_ytLibraryState.value.apiNeedsActivation && silentUploadToken == null
        ) {
            var consentLaunched = false
            YouTubeAuthHelper.authorizeWithConsent(
                activity = activity,
                includeUploadScope = true,
                onAuthorized = {},
                onNeedsConsent = { req ->
                    consentLaunched = true
                    onNeedsUploadConsent(req)
                },
                onError = {}
            )
            if (consentLaunched) {
                _youTubeUploadBusy.value = false
                _dualPublishStatus.value = DualPublishStatus(
                    clipId = clip.id,
                    clipTitle = clip.title,
                    isRunning = false,
                    youtubeStatus = "Aguarda autorização youtube.upload para envio automático...",
                    tikTokStatus = ttStatusMsg,
                    youtubeSuccess = false,
                    tikTokSuccess = ttSuccess,
                    youtubeNeedsAppShare = true,
                    tikTokNeedsAppShare = ttNeedsShare
                )
                return
            }
        }

        // 4. Launch foreground app share seamlessly if API background upload couldn't complete without user app handoff
        if (ytSuccess && ttNeedsShare) {
            if (ShortVideoProcessor.shareShortToTikTok(context, file, clip.title, ttCaption)) {
                ttSuccess = true
                ttStatusMsg = "YouTube publicado! TikTok aberto com o mesmo MP4 e legenda."
            }
        } else if (ytNeedsShare && ttSuccess) {
            if (ShortVideoProcessor.shareShortToYouTube(context, file, clip.title, clip.publishNotes)) {
                ytSuccess = true
                ytStatusMsg = "TikTok enviado! YouTube aberto com o mesmo MP4."
            }
        } else if (ytNeedsShare && ttNeedsShare) {
            // Launch YouTube share first so user doesn't get conflicting chooser popups, and keep TikTok 1-tap button ready
            val openedYt = ShortVideoProcessor.shareShortToYouTube(context, file, clip.title, clip.publishNotes)
            if (openedYt) {
                ytStatusMsg = "YouTube aberto! Ao regressar, toca em \"Enviar TikTok\" acima para enviar o mesmo MP4."
            }
        }

        val finalStatus = if (ytSuccess || ttSuccess) "PUBLISHED" else "READY"
        studioRepo.updateClipPublishing(
            userId = currentUserId,
            clipId = clip.id,
            targetPlatform = "BOTH",
            publishStatus = finalStatus,
            publishNotes = clip.publishNotes
        )

        _youTubeUploadBusy.value = false
        _dualPublishStatus.value = DualPublishStatus(
            clipId = clip.id,
            clipTitle = clip.title,
            isRunning = false,
            youtubeStatus = ytStatusMsg,
            tikTokStatus = ttStatusMsg,
            youtubeSuccess = ytSuccess,
            tikTokSuccess = ttSuccess,
            youtubeNeedsAppShare = !ytSuccess,
            tikTokNeedsAppShare = !ttSuccess,
            youtubeVideoUrl = ytVideoUrl
        )

        _snackbarMessage.value = when {
            ytSuccess && ttSuccess -> "Short publicado nos 2 canais (YouTube + TikTok)!"
            ytSuccess -> "Short publicado no YouTube! Toca em Enviar TikTok para concluir o 2º canal."
            ttSuccess -> "Short enviado no TikTok! Conclui o envio no YouTube."
            else -> "MP4 pronto! Conclui a partilha no YouTube e no TikTok."
        }
    }

    fun launchYouTubeShareFallback(context: Context, clip: StudioClip) {
        viewModelScope.launch {
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val file = ensureRealMp4ForClip(context, clip, linkedSource?.youtubeVideoId.orEmpty()).getOrNull()
                ?: return@launch
            shareShortViaYouTubeApp(clip, file, context)
            _dualPublishStatus.update {
                it?.takeIf { st -> st.clipId == clip.id }?.copy(
                    youtubeSuccess = true,
                    youtubeNeedsAppShare = false,
                    youtubeStatus = "Enviado para YouTube Shorts App!"
                )
            }
        }
    }

    fun launchTikTokShareFallback(context: Context, clip: StudioClip) {
        publishClipToTikTok(context, clip)
    }

    fun publishWithSelectedPlatforms(
        context: Context,
        clip: StudioClip,
        sendToYouTube: Boolean,
        sendToTikTok: Boolean,
        description: String,
        activity: Activity? = null,
        onNeedsUploadConsent: ((IntentSenderRequest) -> Unit)? = null
    ) {
        if (!sendToYouTube && !sendToTikTok) {
            _snackbarMessage.value = "Seleciona pelo menos uma plataforma (YouTube ou TikTok)."
            return
        }
        val cleanDesc = description.trim()
        val updatedClip = clip.copy(
            publishNotes = cleanDesc,
            targetPlatform = when {
                sendToYouTube && sendToTikTok -> "BOTH"
                sendToYouTube -> "YOUTUBE_SHORTS"
                else -> "TIKTOK"
            }
        )
        viewModelScope.launch {
            studioRepo.updateClipEditor(currentUserId, updatedClip)
        }
        when {
            sendToYouTube && sendToTikTok -> publishClipToBothPlatforms(context, updatedClip, activity, onNeedsUploadConsent)
            sendToYouTube -> publishClipToYouTube(context, updatedClip, activity, onNeedsUploadConsent, forceYouTubeOnly = true)
            else -> publishClipToTikTok(context, updatedClip)
        }
    }

    fun publishClipToYouTube(
        context: Context,
        clip: StudioClip,
        activity: Activity? = null,
        onNeedsUploadConsent: ((IntentSenderRequest) -> Unit)? = null,
        forceYouTubeOnly: Boolean = false
    ) {
        if (_youTubeUploadBusy.value || _shortExportState.value is ShortExportUiState.Processing) return
        // "Da mesma forma que o YouTube, quando você manda para o YouTube, automaticamente manda para os dois":
        if (!forceYouTubeOnly && (_tikTokAccount.value.isConnected || _autoPublishOnCut.value)) {
            publishClipToBothPlatforms(context, clip, activity, onNeedsUploadConsent)
            return
        }
        viewModelScope.launch {
            _youTubeUploadBusy.value = true
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val fileRes = ensureRealMp4ForClip(context, clip, linkedSource?.youtubeVideoId.orEmpty())
            val file = fileRes.getOrNull()
            if (file == null) {
                _youTubeUploadBusy.value = false
                _snackbarMessage.value = fileRes.exceptionOrNull()?.localizedMessage
                    ?: "Não foi possível gerar o MP4 do Short para publicar."
                return@launch
            }

            // Try direct YouTube Data API v3 upload first if upload scope is silently authorized
            val uploadToken = YouTubeAuthHelper.getSilentAccessToken(context.applicationContext, includeUploadScope = true)
            if (!uploadToken.isNullOrBlank()) {
                _snackbarMessage.value = "A enviar Short MP4 diretamente para o teu canal YouTube..."
                val uploadRes = ytService.uploadShortToYouTube(
                    accessToken = uploadToken,
                    mp4File = file,
                    title = clip.title,
                    description = clip.publishNotes.ifBlank { clip.hookText }
                )
                if (uploadRes.isSuccess) {
                    val uploaded = uploadRes.getOrThrow()
                    studioRepo.updateClipPublishing(
                        userId = currentUserId,
                        clipId = clip.id,
                        targetPlatform = if (clip.targetPlatform == "TIKTOK") "BOTH" else "YOUTUBE_SHORTS",
                        publishStatus = "PUBLISHED",
                        publishNotes = clip.publishNotes
                    )
                    _youTubeUploadBusy.value = false
                    _snackbarMessage.value = "Short publicado no YouTube! ID: ${uploaded.id}"
                    return@launch
                } else {
                    val err = uploadRes.exceptionOrNull()
                    Log.w(TAG, "Direct API v3 YouTube upload fallback to app share: ${err?.message}")
                    if (err is YouTubeApiDisabledException) {
                        _ytLibraryState.update { it.copy(apiNeedsActivation = true) }
                    }
                }
            } else if (activity != null && onNeedsUploadConsent != null && _ytLibraryState.value.isConnected && !_ytLibraryState.value.apiNeedsActivation) {
                var handledByConsent = false
                YouTubeAuthHelper.authorizeWithConsent(
                    activity = activity,
                    includeUploadScope = true,
                    onAuthorized = { token ->
                        viewModelScope.launch {
                            val res = ytService.uploadShortToYouTube(
                                accessToken = token,
                                mp4File = file,
                                title = clip.title,
                                description = clip.publishNotes.ifBlank { clip.hookText }
                            )
                            _youTubeUploadBusy.value = false
                            if (res.isSuccess) {
                                studioRepo.updateClipPublishing(
                                    userId = currentUserId,
                                    clipId = clip.id,
                                    targetPlatform = if (clip.targetPlatform == "TIKTOK") "BOTH" else "YOUTUBE_SHORTS",
                                    publishStatus = "PUBLISHED",
                                    publishNotes = clip.publishNotes
                                )
                                _snackbarMessage.value = "Short publicado diretamente no YouTube! ID: ${res.getOrThrow().id}"
                            } else {
                                shareShortViaYouTubeApp(clip, file, context)
                            }
                        }
                    },
                    onNeedsConsent = { req ->
                        handledByConsent = true
                        _youTubeUploadBusy.value = false
                        onNeedsUploadConsent(req)
                    },
                    onError = {
                        _youTubeUploadBusy.value = false
                        shareShortViaYouTubeApp(clip, file, context)
                    }
                )
                if (handledByConsent) return@launch
                return@launch
            }

            _youTubeUploadBusy.value = false
            shareShortViaYouTubeApp(clip, file, context)
        }
    }

    private fun shareShortViaYouTubeApp(clip: StudioClip, file: File, context: Context) {
        val launched = ShortVideoProcessor.shareShortToYouTube(context, file, clip.title, clip.publishNotes)
        if (launched) {
            viewModelScope.launch {
                studioRepo.updateClipPublishing(
                    userId = currentUserId,
                    clipId = clip.id,
                    targetPlatform = if (clip.targetPlatform == "TIKTOK") "BOTH" else clip.targetPlatform,
                    publishStatus = "PUBLISHED",
                    publishNotes = clip.publishNotes
                )
            }
            _snackbarMessage.value = "A abrir YouTube Shorts com o vídeo MP4 e legenda copiada!"
        } else {
            _snackbarMessage.value = "Short MP4 pronto! Guarda na galeria ou instala a app YouTube para publicar."
        }
    }

    fun publishClipToTikTok(context: Context, clip: StudioClip) {
        if (_shortExportState.value is ShortExportUiState.Processing) return
        viewModelScope.launch {
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val fileRes = ensureRealMp4ForClip(context, clip, linkedSource?.youtubeVideoId.orEmpty())
            val file = fileRes.getOrNull()
            if (file == null) {
                _snackbarMessage.value = fileRes.exceptionOrNull()?.localizedMessage
                    ?: "Não foi possível gerar o MP4 do Short para enviar ao TikTok."
                return@launch
            }

            val ttAccount = refreshTikTokTokenIfNeeded(context)
            val ttHandle = ttAccount.handle
            if (ttAccount.hasApiUploadToken) {
                val apiRes = tikTokUploadService.uploadShortToTikTok(
                    accessToken = ttAccount.apiAccessToken,
                    mp4File = file,
                    title = clip.title
                )
                if (apiRes.isSuccess) {
                    val pub = apiRes.getOrThrow()
                    studioRepo.updateClipPublishing(
                        userId = currentUserId,
                        clipId = clip.id,
                        targetPlatform = if (clip.targetPlatform == "YOUTUBE_SHORTS") "BOTH" else "TIKTOK",
                        publishStatus = "PUBLISHED",
                        publishNotes = clip.publishNotes
                    )
                    _dualPublishStatus.update {
                        it?.takeIf { st -> st.clipId == clip.id }?.copy(
                            tikTokSuccess = true,
                            tikTokNeedsAppShare = false,
                            tikTokStatus = "${pub.statusNote} ($ttHandle)"
                        )
                    }
                    _snackbarMessage.value = "Short enviado para o TikTok $ttHandle (${pub.statusNote})!"
                    return@launch
                }
            }

            val ttCaption = buildString {
                append(clip.publishNotes.ifBlank { clip.title })
                if (ttHandle.isNotBlank() && !contains(ttHandle)) {
                    append("\n$ttHandle")
                }
            }
            val launched = ShortVideoProcessor.shareShortToTikTok(context, file, clip.title, ttCaption)
            if (launched) {
                studioRepo.updateClipPublishing(
                    userId = currentUserId,
                    clipId = clip.id,
                    targetPlatform = if (clip.targetPlatform == "YOUTUBE_SHORTS") "BOTH" else clip.targetPlatform,
                    publishStatus = "PUBLISHED",
                    publishNotes = clip.publishNotes
                )
                _dualPublishStatus.update {
                    it?.takeIf { st -> st.clipId == clip.id }?.copy(
                        tikTokSuccess = true,
                        tikTokNeedsAppShare = false,
                        tikTokStatus = "TikTok aberto com o Short MP4!"
                    )
                }
                val suffix = if (ttHandle.isNotBlank()) " ($ttHandle)" else ""
                _snackbarMessage.value = "A abrir TikTok$suffix com o Short MP4 e legenda copiada!"
            } else {
                _snackbarMessage.value = "Não foi possível abrir o TikTok para enviar o vídeo."
            }
        }
    }

    fun shareClipMp4Generic(context: Context, clip: StudioClip) {
        viewModelScope.launch {
            val linkedSource = (sourcesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == clip.sourceId || it.title.equals(clip.sourceTitle, ignoreCase = true) }
            val file = ensureRealMp4ForClip(context, clip, linkedSource?.youtubeVideoId.orEmpty()).getOrNull()
            if (file == null) {
                _snackbarMessage.value = "Não foi possível preparar o Short MP4."
                return@launch
            }
            ShortVideoProcessor.shareShortGeneric(context, file, clip.title, clip.publishNotes)
        }
    }

    // --- Clip Editor & Publishing ---

    fun createManualClip(
        title: String,
        hookText: String,
        sourceId: String = "",
        sourceTitle: String = "Projeto manual"
    ) {
        val style = (styleState.value as? UiState.Success)?.data ?: StylePreferences(userId = currentUserId)
        val fallbackSource = (sourcesState.value as? UiState.Success)?.data?.firstOrNull()
        val resolvedSourceId = sourceId.ifBlank { fallbackSource?.id.orEmpty() }
        val resolvedSourceTitle = if (sourceTitle == "Projeto manual" && fallbackSource != null) {
            fallbackSource.title
        } else {
            sourceTitle
        }

        viewModelScope.launch {
            val clip = StudioClip(
                userId = currentUserId,
                sourceId = resolvedSourceId,
                sourceTitle = resolvedSourceTitle,
                title = title.trim().ifBlank { "Novo Short 9:16" },
                hookText = hookText.trim().ifBlank { "Descobre este momento incrível!" },
                startTime = "00:00",
                endTime = "00:45",
                durationSeconds = 45L,
                viralScore = 84L,
                aspectRatio = "9:16",
                captionStyle = if (style.captionPreset == "Discretas") "Minimalista" else "Dinâmico",
                framingMode = style.framingPreset,
                captionsText = hookText.trim().ifBlank { "Adiciona ou edita as legendas automáticas aqui." },
                highlightKeywords = "incrível, segredo, agora",
                targetPlatform = "BOTH",
                publishStatus = "DRAFT",
                publishNotes = "#shorts #planetstreaming"
            )
            studioRepo.addClip(currentUserId, clip).fold(
                onSuccess = { newId ->
                    _selectedClipId.value = newId
                    _snackbarMessage.value = "Clip criado no editor!"
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Erro ao criar clip."
                }
            )
        }
    }

    fun saveClipEdits(clip: StudioClip) {
        viewModelScope.launch {
            studioRepo.updateClipEditor(currentUserId, clip).fold(
                onSuccess = { _snackbarMessage.value = "Alterações do clip guardadas!" },
                onFailure = { e -> _snackbarMessage.value = e.localizedMessage ?: "Erro ao guardar clip." }
            )
        }
    }

    fun updateClipPublishing(
        clipId: String,
        targetPlatform: String,
        publishStatus: String,
        publishNotes: String
    ) {
        viewModelScope.launch {
            studioRepo.updateClipPublishing(
                userId = currentUserId,
                clipId = clipId,
                targetPlatform = targetPlatform,
                publishStatus = publishStatus,
                publishNotes = publishNotes
            ).fold(
                onSuccess = { _snackbarMessage.value = "Estado de publicação atualizado!" },
                onFailure = { e -> _snackbarMessage.value = e.localizedMessage ?: "Erro ao atualizar publicação." }
            )
        }
    }

    fun generateAiPublishCopy(clip: StudioClip) {
        viewModelScope.launch {
            val platformLabel = when (clip.targetPlatform) {
                "YOUTUBE_SHORTS" -> "YouTube Shorts"
                "TIKTOK" -> "TikTok"
                else -> "YouTube Shorts e TikTok"
            }
            aiRepo.generatePublishCopy(clip.title, clip.hookText, platformLabel).fold(
                onSuccess = { copy ->
                    studioRepo.updateClipPublishing(
                        userId = currentUserId,
                        clipId = clip.id,
                        targetPlatform = clip.targetPlatform,
                        publishStatus = clip.publishStatus,
                        publishNotes = copy
                    ).onSuccess {
                        _snackbarMessage.value = "Descrição viral gerada pela IA!"
                    }
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Erro ao gerar descrição."
                }
            )
        }
    }

    fun deleteClip(clipId: String) {
        viewModelScope.launch {
            studioRepo.deleteClip(currentUserId, clipId).onSuccess {
                if (_selectedClipId.value == clipId) _selectedClipId.value = null
                _snackbarMessage.value = "Clip eliminado."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Erro ao eliminar clip."
            }
        }
    }

    // --- Style Preferences ---

    fun saveStylePreferences(prefs: StylePreferences) {
        viewModelScope.launch {
            studioRepo.saveStylePreferences(currentUserId, prefs).fold(
                onSuccess = { _snackbarMessage.value = "O teu estilo foi guardado na nuvem!" },
                onFailure = { e -> _snackbarMessage.value = e.localizedMessage ?: "Erro ao guardar estilo." }
            )
        }
    }

    // --- Trend Radar ---

    fun generateTrends(niche: String) {
        val cleanNiche = niche.trim().ifBlank { "criação de conteúdo e streaming" }
        viewModelScope.launch {
            _isGeneratingTrends.value = true
            _trendsError.value = null
            aiRepo.generateTrendsForNiche(cleanNiche).fold(
                onSuccess = { items ->
                    _trendSuggestions.value = items
                    _isGeneratingTrends.value = false
                },
                onFailure = { e ->
                    _isGeneratingTrends.value = false
                    _trendsError.value = e.localizedMessage ?: "Erro ao consultar o Radar de Tendências."
                }
            )
        }
    }

    fun saveTrend(trend: TrendItem) {
        viewModelScope.launch {
            studioRepo.addTrend(currentUserId, trend.copy(userId = currentUserId)).fold(
                onSuccess = { _snackbarMessage.value = "Tendência guardada no teu radar!" },
                onFailure = { e -> _snackbarMessage.value = e.localizedMessage ?: "Erro ao guardar tendência." }
            )
        }
    }

    fun deleteTrend(trendId: String) {
        viewModelScope.launch {
            studioRepo.deleteTrend(currentUserId, trendId)
        }
    }

    // --- Multi-Turn Gemini Chatbot ("Produtor IA") ---

    fun selectChatRole(role: AiChatRole) {
        activeChat = null
        _chatState.update { it.copy(selectedRole = role, error = null) }
    }

    fun selectChatModel(model: AiChatModelOption) {
        activeChat = null
        _chatState.update { it.copy(selectedModel = model, error = null) }
    }

    fun resetChat() {
        activeChatJob?.cancel()
        activeChat = null
        val current = _chatState.value
        _chatState.value = ChatUiState(
            selectedRole = current.selectedRole,
            selectedModel = current.selectedModel
        )
    }

    private fun buildStudioContextSummary(): String {
        val sources = (sourcesState.value as? UiState.Success)?.data.orEmpty()
        val clips = (clipsState.value as? UiState.Success)?.data.orEmpty()
        val style = (styleState.value as? UiState.Success)?.data
        val ytChannel = _ytLibraryState.value.channel
        return buildString {
            if (ytChannel != null) {
                appendLine("Canal YouTube ligado: ${ytChannel.title} (${ytChannel.videoCountLabel} vídeos)")
            }
            appendLine("Vídeos/lives importados (${sources.size}):")
            sources.take(5).forEach { appendLine("- ${it.title} [${it.sourceType}] (${it.durationLabel})") }
            appendLine("Clips no estúdio (${clips.size}):")
            clips.take(5).forEach { appendLine("- ${it.title} (${it.aspectRatio}, ${it.startTime}-${it.endTime}, score ${it.viralScore})") }
            if (style != null) {
                appendLine("Estilo do criador: ritmo=${style.editingPace}, legendas=${style.captionPreset}, enquadramento=${style.framingPreset}, duração=${style.preferredDuration}, tom=${style.brandTone}")
            }
        }
    }

    fun sendChatMessage(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _chatState.value.isSending) return

        val stateSnapshot = _chatState.value
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isFromUser = true,
            text = cleanPrompt
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            isFromUser = false,
            text = "",
            roleLabel = stateSnapshot.selectedRole.displayName,
            modelLabel = stateSnapshot.selectedModel.label,
            isStreaming = true
        )

        val historyBeforeSend = stateSnapshot.messages
        _chatState.update {
            it.copy(
                messages = it.messages + userMsg + assistantPlaceholder,
                isSending = true,
                error = null
            )
        }

        activeChatJob = viewModelScope.launch {
            try {
                val chat = activeChat ?: aiRepo.createChatSession(
                    role = stateSnapshot.selectedRole,
                    modelOption = stateSnapshot.selectedModel,
                    studioContext = buildStudioContextSummary(),
                    existingHistory = historyBeforeSend
                ).also { activeChat = it }

                aiRepo.sendChatMessageStream(chat, cleanPrompt).collect { partial ->
                    _chatState.update { current ->
                        current.copy(
                            messages = current.messages.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(text = partial) else msg
                            }
                        )
                    }
                }
                _chatState.update { current ->
                    current.copy(
                        isSending = false,
                        messages = current.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat streaming failed", e)
                val errorText = e.localizedMessage ?: "Falha na resposta do Gemini."
                _chatState.update { current ->
                    current.copy(
                        isSending = false,
                        error = errorText,
                        messages = current.messages.filterNot { it.id == assistantMsgId && it.text.isBlank() }
                            .map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                            }
                    )
                }
            }
        }
    }

    // --- High-Quality AI Cover / Thumbnail Generation (1K, 2K, 4K) ---

    fun selectImageSize(size: ImageSizeOption) {
        _selectedImageSize.value = size
    }

    fun selectImageAspect(aspect: ImageAspectOption) {
        _selectedImageAspect.value = aspect
    }

    fun selectImageModel(model: ImageModelOption) {
        _selectedImageModel.value = model
    }

    fun generateCoverImage(
        context: Context,
        prompt: String,
        headline: String = "",
        emojiBadge: String = "",
        filterPreset: VideoFilterPreset = VideoFilterPreset.NEON_CYBER,
        backgroundPhotoUri: Uri? = null,
        youtubeVideoId: String? = null,
        activeClipId: String? = null,
        preferLocalStudio: Boolean = false
    ) {
        val cleanPrompt = prompt.trim()
        val size = _selectedImageSize.value
        val aspect = _selectedImageAspect.value
        val model = _selectedImageModel.value

        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading

            // Decode custom photo or YouTube HD thumbnail for local studio cover / fallback
            val bgBitmap: Bitmap? = when {
                backgroundPhotoUri != null -> CoverStudioRenderer.decodeUriBitmap(context, backgroundPhotoUri)
                !youtubeVideoId.isNullOrBlank() -> CoverStudioRenderer.downloadBitmapFromUrl(
                    "https://img.youtube.com/vi/$youtubeVideoId/hqdefault.jpg",
                    YouTubeRetrofitClient.okHttpClient
                )
                else -> null
            }

            if (preferLocalStudio || bgBitmap != null || cleanPrompt.isBlank()) {
                val bmp = CoverStudioRenderer.renderCoverBitmap(
                    headline = headline.ifBlank { cleanPrompt.take(44) },
                    subtitle = "PLANET STREAMING",
                    emojiBadge = emojiBadge,
                    backgroundBitmap = bgBitmap,
                    filterPreset = filterPreset,
                    aspectOption = aspect,
                    sizeOption = size
                )
                attachCoverBitmapToClip(context, activeClipId, bmp)
                _imageGenState.value = ImageGenUiState.Success(
                    bitmap = bmp,
                    sizeLabel = size.label,
                    aspectLabel = aspect.label,
                    sourceNote = "Designer Estúdio HD (Sem custo de quota)"
                )
                return@launch
            }

            aiRepo.generateStudioImage(cleanPrompt, size, aspect, model).fold(
                onSuccess = { aiBitmap ->
                    val finalBmp = if (headline.isNotBlank() || emojiBadge.isNotBlank()) {
                        CoverStudioRenderer.renderCoverBitmap(
                            headline = headline,
                            subtitle = "PLANET AI",
                            emojiBadge = emojiBadge,
                            backgroundBitmap = aiBitmap,
                            filterPreset = filterPreset,
                            aspectOption = aspect,
                            sizeOption = size
                        )
                    } else aiBitmap
                    attachCoverBitmapToClip(context, activeClipId, finalBmp)
                    _imageGenState.value = ImageGenUiState.Success(
                        bitmap = finalBmp,
                        sizeLabel = size.label,
                        aspectLabel = aspect.label,
                        sourceNote = "Gerada por ${model.label}"
                    )
                },
                onFailure = {
                    // Seamless fallback on Blaze plan / App Check quota limit so cover creation ALWAYS works!
                    val fallbackBmp = CoverStudioRenderer.renderCoverBitmap(
                        headline = headline.ifBlank { cleanPrompt.take(44) },
                        subtitle = "ESTÚDIO HD",
                        emojiBadge = emojiBadge.ifBlank { "🔥" },
                        backgroundBitmap = bgBitmap,
                        filterPreset = filterPreset,
                        aspectOption = aspect,
                        sizeOption = size
                    )
                    attachCoverBitmapToClip(context, activeClipId, fallbackBmp)
                    _imageGenState.value = ImageGenUiState.Success(
                        bitmap = fallbackBmp,
                        sizeLabel = size.label,
                        aspectLabel = aspect.label,
                        sourceNote = "Capa Estúdio HD criada localmente (plano gratuito)"
                    )
                    _snackbarMessage.value = "Quota Gemini Image indisponível; capa HD gerada localmente no Estúdio!"
                }
            )
        }
    }

    private suspend fun attachCoverBitmapToClip(
        context: Context,
        clipId: String?,
        bitmap: Bitmap
    ) {
        if (clipId.isNullOrBlank()) return
        val file = CoverStudioRenderer.saveCoverForClip(context.applicationContext, clipId, bitmap)
        updateClipVisualStyle(context, clipId) { current ->
            current.copy(coverImagePath = file.absolutePath)
        }
    }

    fun applyCoverToActiveClip(context: Context, clipId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            attachCoverBitmapToClip(context, clipId, bitmap)
            _snackbarMessage.value = "Capa aplicada ao Short selecionado!"
        }
    }

    fun saveCoverToGallery(context: Context, bitmap: Bitmap, title: String) {
        viewModelScope.launch {
            CoverStudioRenderer.saveCoverToGallery(context, bitmap, title).fold(
                onSuccess = {
                    _snackbarMessage.value = "Capa guardada em Pictures/PlanetStreaming na tua Galeria!"
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Falha ao guardar capa na galeria."
                }
            )
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(
            currentUserId: String,
            repository: StudioRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StudioViewModel(
                    currentUserId = currentUserId,
                    studioRepo = repository,
                    aiRepo = AiStudioRepository(),
                    ytService = YouTubeMetadataService()
                )
            }
        }
    }
}
