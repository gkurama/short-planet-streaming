package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageModelOption
import com.example.data.model.ImageSizeOption
import com.example.data.model.StudioClip
import com.example.data.model.VideoSource
import com.example.data.remote.YouTubeMetadataService
import com.example.media.ClipVisualStyle
import com.example.media.CoverStudioRenderer
import com.example.media.LocalVideoInfo
import com.example.media.STUDIO_EMOJI_PACK
import com.example.media.ShortVideoProcessor
import com.example.media.VideoFilterPreset
import com.example.media.clampTrimRangeSec
import com.example.media.formatMsToMmSs
import com.example.ui.components.PurpleGradientButton
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorSoft
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.ShortExportUiState
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class EditorGridTool(val label: String, val icon: ImageVector) {
    CORTAR("Cortar", Icons.Rounded.ContentCut),
    LEGENDAS("Legendas", Icons.Rounded.ClosedCaption),
    TEXTO("Texto", Icons.Rounded.Title),
    FILTROS("Filtros", Icons.Rounded.FilterVintage),
    MUSICA("Música", Icons.Rounded.MusicNote),
    FIGURINHAS("Figurinhas", Icons.Rounded.SentimentSatisfiedAlt),
    EFEITOS("Efeitos", Icons.Rounded.AutoAwesome),
    AJUSTES("Ajustes", Icons.Rounded.Tune)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    clipsState: UiState<List<StudioClip>>,
    sourcesState: UiState<List<VideoSource>>,
    selectedClipId: String?,
    renderedClipIds: Set<String>,
    clipVisualStyles: Map<String, ClipVisualStyle>,
    aiEditBusy: Boolean,
    localVideoInfo: LocalVideoInfo?,
    shortExportState: ShortExportUiState,
    imageGenState: ImageGenUiState,
    selectedImageSize: ImageSizeOption,
    selectedImageAspect: ImageAspectOption,
    selectedImageModel: ImageModelOption,
    onSelectClip: (String) -> Unit,
    onCreateManualClip: (title: String, hook: String) -> Unit,
    onSaveClipEdits: (StudioClip) -> Unit,
    onUpdateVisualStyle: (String, (ClipVisualStyle) -> ClipVisualStyle) -> Unit,
    onRunAiAutoEdit: (StudioClip) -> Unit,
    onTranscribeClipSpeech: (StudioClip) -> Unit,
    onAiOptimizeAndCutScene: (StudioClip, String, String, String, Float) -> Unit,
    onLoadLocalVideoForEditor: (Uri, StudioClip?) -> Unit,
    onCreateMp4FromPhotosForClip: (List<Uri>, StudioClip?) -> Unit,
    onRenderClipToMp4: (StudioClip, String, String, String, Float) -> Unit,
    onCancelShortExport: () -> Unit,
    onSelectImageSize: (ImageSizeOption) -> Unit,
    onSelectImageAspect: (ImageAspectOption) -> Unit,
    onSelectImageModel: (ImageModelOption) -> Unit,
    onGenerateCoverImage: (
        prompt: String,
        headline: String,
        emoji: String,
        filter: VideoFilterPreset,
        photoUri: Uri?,
        youtubeVideoId: String?,
        activeClipId: String?,
        preferLocalStudio: Boolean
    ) -> Unit,
    onApplyCoverToClip: (String, Bitmap) -> Unit,
    onSaveCoverToGallery: (Bitmap, String) -> Unit,
    onNextToPublish: (StudioClip) -> Unit,
    onOpenSelectVideo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clips = (clipsState as? UiState.Success)?.data.orEmpty()
    val sources = (sourcesState as? UiState.Success)?.data.orEmpty()
    val activeClip = clips.firstOrNull { it.id == selectedClipId } ?: clips.firstOrNull()
    val linkedSource = remember(activeClip?.id, activeClip?.sourceId, activeClip?.sourceTitle, sources) {
        if (activeClip == null) null
        else sources.firstOrNull { it.id.isNotBlank() && it.id == activeClip.sourceId }
            ?: sources.firstOrNull {
                activeClip.sourceTitle.isNotBlank() && it.title.equals(activeClip.sourceTitle, ignoreCase = true)
            }
            ?: if (sources.size == 1 && activeClip.sourceId.isNotBlank()) sources.first() else null
    }

    var showCoverDesigner by remember { mutableStateOf(false) }

    val pickLocalVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onLoadLocalVideoForEditor(uri, activeClip)
        }
    }

    val pickPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onCreateMp4FromPhotosForClip(uris, activeClip)
        }
    }

    if (showCoverDesigner) {
        val activeVisualStyle = activeClip?.let { clipVisualStyles[it.id] } ?: ClipVisualStyle()
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { showCoverDesigner = false },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Voltar ao Editor", tint = TextPrimary)
                    }
                    Text("Designer de Capas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                CoverArtGeneratorTabContent(
                    activeClip = activeClip,
                    linkedSource = linkedSource,
                    initialHeadline = activeVisualStyle.coverTitle.ifBlank { activeClip?.title?.substringBefore("•")?.trim().orEmpty() },
                    initialEmoji = activeVisualStyle.emojiOverlay.ifBlank { "🔥" },
                    initialFilter = activeVisualStyle.filterPreset,
                    imageGenState = imageGenState,
                    selectedSize = selectedImageSize,
                    selectedAspect = selectedImageAspect,
                    selectedModel = selectedImageModel,
                    onSelectSize = onSelectImageSize,
                    onSelectAspect = onSelectImageAspect,
                    onSelectModel = onSelectImageModel,
                    onGenerate = { prompt, headline, emoji, filter, photoUri, preferLocal ->
                        onGenerateCoverImage(
                            prompt,
                            headline,
                            emoji,
                            filter,
                            photoUri,
                            linkedSource?.youtubeVideoId,
                            activeClip?.id,
                            preferLocal
                        )
                    },
                    onApplyToClip = { bmp ->
                        val id = activeClip?.id
                        if (!id.isNullOrBlank()) {
                            onApplyCoverToClip(id, bmp)
                            showCoverDesigner = false
                        }
                    },
                    onSaveCoverToGallery = onSaveCoverToGallery
                )
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
        ) {
            // Top Bar (< Editor 9:16 + Carregar Vídeo + IA)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Voltar",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Editor Vertical 9:16",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            pickLocalVideoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.testTag("editor_topbar_load_video")
                    ) {
                        Icon(Icons.Rounded.VideoFile, contentDescription = "Carregar Vídeo", tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Carregar", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (activeClip != null) {
                        TextButton(
                            onClick = { onRunAiAutoEdit(activeClip) },
                            enabled = !aiEditBusy
                        ) {
                            if (aiEditBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = ElectricVioletLight, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = "IA Auto-Editar", tint = ElectricVioletLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("IA", color = ElectricVioletLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (activeClip == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = SpaceNavy900,
                        border = BorderStroke(1.dp, SpaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.2f))
                                    .border(1.dp, ElectricVioletLight.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCut,
                                    contentDescription = null,
                                    tint = ElectricVioletLight,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Editor de Clipe Vertical",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Carregue um vídeo do dispositivo para marcar o ponto inicial e final na timeline e exportar em 9:16 via Media3 Transformer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            PurpleGradientButton(
                                text = "Carregar Vídeo na Timeline",
                                onClick = {
                                    pickLocalVideoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editor_empty_load_video_button")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenSelectVideo,
                                border = BorderStroke(1.dp, SpaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Selecionar Vídeo do Canal", color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pickPhotosLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                border = BorderStroke(1.dp, SpaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricVioletLight)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Criar Short com Fotos (MP4)", color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = { onCreateManualClip("Novo Short 9:16", "Confira esse trecho incrível!") }) {
                                Text("Ou criar clipe em branco", color = ElectricVioletLight)
                            }
                        }
                    }
                }
            } else {
                val visualStyle = clipVisualStyles[activeClip.id] ?: ClipVisualStyle()
                var title by remember(activeClip.id, activeClip.title) { mutableStateOf(activeClip.title) }
                var hookText by remember(activeClip.id, activeClip.hookText) { mutableStateOf(activeClip.hookText) }
                var startTime by remember(activeClip.id, activeClip.startTime) { mutableStateOf(activeClip.startTime) }
                var endTime by remember(activeClip.id, activeClip.endTime) { mutableStateOf(activeClip.endTime) }
                var aspectRatio by remember(activeClip.id, activeClip.aspectRatio) { mutableStateOf(activeClip.aspectRatio.ifBlank { "9:16" }) }
                var captionStyle by remember(activeClip.id, activeClip.captionStyle) { mutableStateOf(activeClip.captionStyle) }
                var framingMode by remember(activeClip.id, activeClip.framingMode) { mutableStateOf(activeClip.framingMode) }
                var captionsText by remember(activeClip.id, activeClip.captionsText) { mutableStateOf(activeClip.captionsText) }
                var highlightKeywords by remember(activeClip.id, activeClip.highlightKeywords) { mutableStateOf(activeClip.highlightKeywords) }

                var manualPanX by remember(activeClip.id) { mutableFloatStateOf(visualStyle.panX) }
                var manualZoom by remember(activeClip.id) { mutableFloatStateOf(visualStyle.zoomFactor) }
                var isPlayingVideo by remember(activeClip.id, localVideoInfo?.uri) { mutableStateOf(false) }
                var youtubeEmbedBlocked by remember(activeClip.id) { mutableStateOf(false) }
                var showSafeZoneGuide by remember { mutableStateOf(false) }
                var previewVertical by remember(activeClip.id) { mutableStateOf(activeClip.aspectRatio != "16:9") }
                var previewSegIdx by remember(activeClip.id) { mutableIntStateOf(0) }
                var activeTool by remember { mutableStateOf<EditorGridTool?>(EditorGridTool.CORTAR) }

                val renderedFile = remember(activeClip.id, renderedClipIds, shortExportState) {
                    ShortVideoProcessor.getRenderedShortFile(context, activeClip.id)
                }
                val hasRenderedMp4 = renderedFile != null
                var showExportedPreview by remember(activeClip.id) { mutableStateOf(false) }
                LaunchedEffect(shortExportState) {
                    if (shortExportState is ShortExportUiState.Success && shortExportState.clipId == activeClip.id) {
                        showExportedPreview = true
                        isPlayingVideo = true
                    }
                }

                val pickCoverPhotoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        savePickedCoverShortcut(
                            context = context,
                            clipId = activeClip.id,
                            headline = visualStyle.coverTitle.ifBlank { title },
                            emoji = visualStyle.emojiOverlay,
                            filter = visualStyle.filterPreset,
                            photoUri = uri,
                            onSavedPath = { path ->
                                onUpdateVisualStyle(activeClip.id) { it.copy(coverImagePath = path) }
                            }
                        )
                    }
                }

                val videoId = linkedSource?.youtubeVideoId.orEmpty()
                val hasValidYouTube = videoId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))
                val localDurationSec = ((localVideoInfo?.durationMs ?: 0L) / 1000L).toInt()
                val ytDurationSec = linkedSource?.durationLabel?.let {
                    YouTubeMetadataService.parseTimestampToSeconds(it)
                } ?: 0
                val totalDurationSec = remember(localDurationSec, ytDurationSec) {
                    if (localDurationSec > 0) localDurationSec.coerceAtLeast(3)
                    else maxOf(ytDurationSec, 120)
                }

                LaunchedEffect(localVideoInfo?.uri, localDurationSec) {
                    if (localDurationSec > 0) {
                        val parsedStart = YouTubeMetadataService.parseTimestampToSeconds(startTime)
                        val parsedEnd = YouTubeMetadataService.parseTimestampToSeconds(endTime)
                            .let { if (it > parsedStart) it else (parsedStart + minOf(30, localDurationSec)) }
                        val (cs, ce) = clampTrimRangeSec(parsedStart, parsedEnd, localDurationSec, minDurationSec = 2)
                        startTime = formatMsToMmSs(cs * 1000L)
                        endTime = formatMsToMmSs(ce * 1000L)
                    }
                }

                val rawStartSec = YouTubeMetadataService.parseTimestampToSeconds(startTime)
                val rawEndSec = YouTubeMetadataService.parseTimestampToSeconds(endTime)
                    .let { if (it > rawStartSec) it else rawStartSec + 30 }
                val (startSeconds, endSeconds) = clampTrimRangeSec(
                    startSec = rawStartSec,
                    endSec = rawEndSec,
                    totalDurationSec = totalDurationSec,
                    minDurationSec = 2
                )

                var currentPlayheadMs by remember(activeClip.id, localVideoInfo?.uri) {
                    mutableLongStateOf(startSeconds * 1000L)
                }
                var scrubTargetMs by remember(activeClip.id, localVideoInfo?.uri) {
                    mutableLongStateOf(startSeconds * 1000L)
                }
                var scrubNonce by remember { mutableIntStateOf(0) }

                val liveColorFilter = remember(visualStyle.filterId) {
                    if (visualStyle.filterPreset == VideoFilterPreset.ORIGINAL) null
                    else ColorFilter.colorMatrix(visualStyle.filterPreset.toComposeColorMatrix())
                }
                val customCoverFile = remember(visualStyle.coverImagePath) {
                    visualStyle.coverImagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
                }
                val targetBias = when (framingMode) {
                    "Manual" -> manualPanX
                    "Seguir pessoa" -> 0.14f
                    else -> 0f
                }
                val targetZoom = when (framingMode) {
                    "Manual" -> manualZoom
                    "Seguir pessoa" -> 1.10f
                    else -> 1f
                }
                val animatedBias by animateFloatAsState(targetValue = targetBias, label = "bias")
                val animatedZoom by animateFloatAsState(targetValue = targetZoom, label = "zoom")

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Clip switcher when multiple clips exist
                    if (clips.size > 1) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                clips.forEach { c ->
                                    FilterChip(
                                        selected = c.id == activeClip.id,
                                        onClick = { onSelectClip(c.id) },
                                        label = {
                                            Text(
                                                text = c.title.take(24),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 1. CENTERED VERTICAL VIDEO PREVIEW PLAYER (Live ExoPlayer scrubbing + 9:16 framing)
                    item(key = activeClip.id) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (localVideoInfo != null && hasRenderedMp4) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !showExportedPreview,
                                        onClick = { showExportedPreview = false },
                                        label = { Text("Timeline (Origem)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = showExportedPreview,
                                        onClick = { showExportedPreview = true },
                                        label = { Text("Clipe Vertical MP4", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                val boxModifier = when {
                                    previewVertical || aspectRatio == "9:16" -> Modifier
                                        .widthIn(max = 264.dp)
                                        .fillMaxWidth(0.68f)
                                        .aspectRatio(9f / 16f)
                                    aspectRatio == "1:1" -> Modifier
                                        .widthIn(max = 310.dp)
                                        .fillMaxWidth(0.78f)
                                        .aspectRatio(1f)
                                    else -> Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9.5f)
                                }

                                Box(
                                    modifier = boxModifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SpaceNavy950)
                                        .border(1.5.dp, ElectricViolet.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                ) {
                                    val showLocalSourcePlayer = localVideoInfo != null && !showExportedPreview
                                    if (showExportedPreview && hasRenderedMp4 && renderedFile != null) {
                                        LocalShortExoPlayer(
                                            mp4File = renderedFile,
                                            seekSeconds = 0,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (showLocalSourcePlayer && localVideoInfo != null) {
                                        LoadedSourceExoPlayer(
                                            videoUri = localVideoInfo.uri,
                                            startMs = startSeconds * 1000L,
                                            endMs = endSeconds * 1000L,
                                            scrubPositionMs = scrubTargetMs,
                                            scrubNonce = scrubNonce,
                                            isPlaying = isPlayingVideo,
                                            zoomScale = animatedZoom,
                                            panBias = animatedBias,
                                            onPositionChanged = { posMs -> currentPlayheadMs = posMs },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (isPlayingVideo && hasRenderedMp4 && renderedFile != null) {
                                        LocalShortExoPlayer(
                                            mp4File = renderedFile,
                                            seekSeconds = startSeconds,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (isPlayingVideo && hasValidYouTube && !youtubeEmbedBlocked) {
                                        YouTubeClipWebViewPlayer(
                                            videoId = videoId,
                                            startSeconds = startSeconds,
                                            endSeconds = endSeconds,
                                            onEmbedBlocked = {
                                                youtubeEmbedBlocked = true
                                                isPlayingVideo = false
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (customCoverFile != null) {
                                        AsyncImage(
                                            model = customCoverFile,
                                            contentDescription = activeClip.title,
                                            contentScale = ContentScale.Crop,
                                            colorFilter = liveColorFilter,
                                            alignment = BiasAlignment(animatedBias, 0f),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = animatedZoom
                                                    scaleY = animatedZoom
                                                }
                                        )
                                    } else if (hasValidYouTube) {
                                        var useHqFallback by remember(activeClip.id, videoId) { mutableStateOf(false) }
                                        val thumbUrl = if (useHqFallback) {
                                            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                        } else {
                                            "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                                        }
                                        val zoomMult = if (useHqFallback) 1.38f else 1f
                                        AsyncImage(
                                            model = thumbUrl,
                                            onError = { if (!useHqFallback) useHqFallback = true },
                                            contentDescription = activeClip.title,
                                            contentScale = ContentScale.Crop,
                                            colorFilter = liveColorFilter,
                                            alignment = BiasAlignment(animatedBias, 0f),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = animatedZoom * zoomMult
                                                    scaleY = animatedZoom * zoomMult
                                                }
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_studio_hero_1795221130779),
                                            contentDescription = activeClip.title,
                                            contentScale = ContentScale.Crop,
                                            colorFilter = liveColorFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Top preview aspect toggle (9:16 / 16:9)
                                    Surface(
                                        onClick = { previewVertical = !previewVertical },
                                        color = SpaceNavy950.copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = if (previewVertical) "9:16" else aspectRatio,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (!isPlayingVideo && visualStyle.coverTitle.isNotBlank()) {
                                        Surface(
                                            color = SpaceNavy950.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, ElectricVioletLight.copy(alpha = 0.75f)),
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 8.dp, start = 46.dp, end = 46.dp)
                                        ) {
                                            Text(
                                                text = visualStyle.coverTitle.uppercase(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (!isPlayingVideo && visualStyle.emojiOverlay.isNotBlank()) {
                                        Text(
                                            text = visualStyle.emojiOverlay,
                                            fontSize = 26.sp,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        )
                                    }

                                    if (showSafeZoneGuide && !isPlayingVideo) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = 36.dp, bottom = 42.dp, start = 12.dp, end = 36.dp)
                                                .border(1.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                        )
                                    }

                                    // Play / Pause control
                                    if (!showExportedPreview) {
                                        if (isPlayingVideo) {
                                            Surface(
                                                onClick = { isPlayingVideo = false },
                                                color = SpaceNavy950.copy(alpha = 0.82f),
                                                shape = RoundedCornerShape(50),
                                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.8f)),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        if (showLocalSourcePlayer) Icons.Rounded.Pause else Icons.Rounded.Stop,
                                                        contentDescription = "Pausar",
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        if (showLocalSourcePlayer) "Pausar" else "Parar",
                                                        color = NeonCyan,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .align(Alignment.Center)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.58f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                                    .clickable {
                                                        if (showLocalSourcePlayer || hasRenderedMp4 || (hasValidYouTube && !youtubeEmbedBlocked)) {
                                                            isPlayingVideo = true
                                                        } else {
                                                            pickLocalVideoLauncher.launch(
                                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                            )
                                                        }
                                                    }
                                                    .testTag("editor_play_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Reproduzir trecho",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Live Karaoke Caption Pill in preview
                                    val fullScript = captionsText.ifBlank { hookText }
                                    val timedSegments = remember(fullScript, startSeconds, endSeconds) {
                                        val durMs = ((endSeconds - startSeconds).coerceAtLeast(10)) * 1000L
                                        ShortVideoProcessor.buildTimedCaptionSegments(fullScript, durMs)
                                    }
                                    var activeWordIdx by remember(activeClip.id, fullScript) { mutableIntStateOf(0) }
                                    LaunchedEffect(fullScript) { previewSegIdx = 0 }
                                    LaunchedEffect(timedSegments.size, isPlayingVideo) {
                                        if (timedSegments.size > 1 && !isPlayingVideo) {
                                            while (true) {
                                                kotlinx.coroutines.delay(2200L)
                                                previewSegIdx = (previewSegIdx + 1) % timedSegments.size
                                                activeWordIdx = 0
                                            }
                                        }
                                    }
                                    val captionSample = if (timedSegments.isNotEmpty()) {
                                        timedSegments[previewSegIdx % timedSegments.size].text
                                    } else fullScript.take(72)
                                    val sampleWords = remember(captionSample) {
                                        captionSample.split(" ").filter { it.isNotBlank() }
                                    }
                                    LaunchedEffect(captionSample, isPlayingVideo) {
                                        activeWordIdx = 0
                                        if (sampleWords.size > 1 && !isPlayingVideo) {
                                            while (true) {
                                                kotlinx.coroutines.delay(420L)
                                                activeWordIdx = (activeWordIdx + 1) % sampleWords.size
                                            }
                                        }
                                    }
                                    if (captionSample.isNotBlank() && !showExportedPreview) {
                                        val keywords = highlightKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                                        val styled = buildAnnotatedString {
                                            sampleWords.forEachIndexed { idx, word ->
                                                if (idx > 0) append(" ")
                                                val clean = word.lowercase().trim(',', '.', '!', '?', ':')
                                                val isKey = keywords.any { clean.contains(it) }
                                                val isCurrent = idx == (activeWordIdx % sampleWords.size.coerceAtLeast(1))
                                                when {
                                                    isCurrent && captionStyle != "Minimalista" -> {
                                                        withStyle(SpanStyle(color = SolarAmber, fontWeight = FontWeight.ExtraBold)) {
                                                            append(word.uppercase())
                                                        }
                                                    }
                                                    isKey && captionStyle != "Minimalista" -> {
                                                        withStyle(SpanStyle(color = NeonCyan, fontWeight = FontWeight.ExtraBold)) {
                                                            append(word.uppercase())
                                                        }
                                                    }
                                                    else -> withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                                        append(word)
                                                    }
                                                }
                                            }
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.78f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(start = 10.dp, end = 10.dp, bottom = 36.dp)
                                        ) {
                                            Text(
                                                text = styled,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Timecode pill bottom-end
                                    if (!showExportedPreview) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.78f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "${formatMsToMmSs(currentPlayheadMs)} / $endTime",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. LOADED VIDEO STATUS & DRAGGABLE TIMELINE + MEDIA3 TRANSFORMER CTA
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, SpaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = localVideoInfo?.displayName ?: "Nenhum MP4 local carregado",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (localVideoInfo != null) TextPrimary else TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (localVideoInfo != null) {
                                                "Duração total: ${formatMsToMmSs(localVideoInfo.durationMs)} • Arraste os marcadores roxos"
                                            } else {
                                                "Carregue um vídeo do aparelho para cortar com Media3 Transformer"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            pickLocalVideoLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("timeline_load_video_button")
                                    ) {
                                        Text(
                                            text = if (localVideoInfo != null) "Trocar" else "Carregar Vídeo",
                                            color = NeonCyan,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            DraggableCutTimelineCard(
                                youtubeVideoId = videoId,
                                hasValidYouTube = hasValidYouTube,
                                localVideoUri = localVideoInfo?.uri,
                                totalDurationSec = totalDurationSec,
                                startSeconds = startSeconds,
                                endSeconds = endSeconds,
                                currentPositionSec = (currentPlayheadMs / 1000L).toInt().coerceIn(0, totalDurationSec),
                                onRangeChanged = { newStartSec, newEndSec, scrubbedSec ->
                                    val (cs, ce) = clampTrimRangeSec(newStartSec, newEndSec, totalDurationSec, minDurationSec = 2)
                                    startTime = formatMsToMmSs(cs * 1000L)
                                    endTime = formatMsToMmSs(ce * 1000L)
                                    isPlayingVideo = false
                                    scrubTargetMs = scrubbedSec * 1000L
                                    currentPlayheadMs = scrubTargetMs
                                    scrubNonce++
                                },
                                onSetStartAtPlayhead = {
                                    val playSec = (currentPlayheadMs / 1000L).toInt()
                                    val (cs, ce) = clampTrimRangeSec(playSec, endSeconds, totalDurationSec, minDurationSec = 2)
                                    startTime = formatMsToMmSs(cs * 1000L)
                                    endTime = formatMsToMmSs(ce * 1000L)
                                },
                                onSetEndAtPlayhead = {
                                    val playSec = (currentPlayheadMs / 1000L).toInt()
                                    val (cs, ce) = clampTrimRangeSec(startSeconds, playSec, totalDurationSec, minDurationSec = 2)
                                    startTime = formatMsToMmSs(cs * 1000L)
                                    endTime = formatMsToMmSs(ce * 1000L)
                                }
                            )

                            // Centered Media3 Transformer Export Card directly under Timeline
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (shortExportState is ShortExportUiState.Processing) {
                                        Text(
                                            text = shortExportState.statusLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeonCyan,
                                            textAlign = TextAlign.Center
                                        )
                                        LinearProgressIndicator(
                                            progress = { (shortExportState.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = EmeraldPulse,
                                            trackColor = SpaceNavy850
                                        )
                                        OutlinedButton(onClick = onCancelShortExport, modifier = Modifier.fillMaxWidth()) {
                                            Text("Cancelar Media3 Transformer", color = CoralError)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (localVideoInfo == null && !hasValidYouTube) {
                                                    pickLocalVideoLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                    )
                                                } else {
                                                    isPlayingVideo = false
                                                    val updated = activeClip.copy(
                                                        title = title,
                                                        hookText = hookText,
                                                        startTime = startTime,
                                                        endTime = endTime,
                                                        durationSeconds = (endSeconds - startSeconds).toLong().coerceAtLeast(2L),
                                                        aspectRatio = aspectRatio,
                                                        captionStyle = captionStyle,
                                                        framingMode = framingMode,
                                                        captionsText = captionsText,
                                                        highlightKeywords = highlightKeywords
                                                    )
                                                    onSaveClipEdits(updated)
                                                    onRenderClipToMp4(updated, aspectRatio, startTime, endTime, manualPanX)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = SpaceNavy950),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("create_vertical_clip_button")
                                        ) {
                                            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (hasRenderedMp4) {
                                                    "Reexportar Clipe Vertical ($aspectRatio)"
                                                } else {
                                                    "Criar Clipe Vertical ($aspectRatio)"
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. 2x4 TOOL GRID (Cortar, Legendas, Texto, Filtros, Música, Figurinhas, Efeitos, Ajustes)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val topRow = listOf(
                                EditorGridTool.CORTAR,
                                EditorGridTool.LEGENDAS,
                                EditorGridTool.TEXTO,
                                EditorGridTool.FILTROS
                            )
                            val bottomRow = listOf(
                                EditorGridTool.MUSICA,
                                EditorGridTool.FIGURINHAS,
                                EditorGridTool.EFEITOS,
                                EditorGridTool.AJUSTES
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                topRow.forEach { tool ->
                                    EditorGridToolTile(
                                        tool = tool,
                                        selected = activeTool == tool,
                                        onClick = { activeTool = if (activeTool == tool) null else tool },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                bottomRow.forEach { tool ->
                                    EditorGridToolTile(
                                        tool = tool,
                                        selected = activeTool == tool,
                                        onClick = { activeTool = if (activeTool == tool) null else tool },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // 4. EXPANDED CONTROL PANEL FOR SELECTED GRID TOOL
                    if (activeTool != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    when (activeTool) {
                                        EditorGridTool.CORTAR -> {
                                            Text("Cortar e Exportar MP4", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StudioClip.ALLOWED_ASPECT_RATIOS.forEach { ratio ->
                                                    FilterChip(
                                                        selected = aspectRatio == ratio,
                                                        onClick = { aspectRatio = ratio },
                                                        label = { Text(ratio) }
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = startTime,
                                                    onValueChange = { startTime = it },
                                                    label = { Text("Início") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = endTime,
                                                    onValueChange = { endTime = it },
                                                    label = { Text("Fim") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("AI Trim (Cortar Pausas)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                    Text("Remove silêncios no início e fim do corte.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                }
                                                Switch(
                                                    checked = visualStyle.aiTrimTighten,
                                                    onCheckedChange = { c ->
                                                        onUpdateVisualStyle(activeClip.id) { it.copy(aiTrimTighten = c) }
                                                    }
                                                )
                                            }

                                            if (shortExportState is ShortExportUiState.Processing) {
                                                Text(shortExportState.statusLabel, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                                                LinearProgressIndicator(
                                                    progress = { (shortExportState.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = EmeraldPulse,
                                                    trackColor = SpaceNavy850
                                                )
                                                OutlinedButton(onClick = onCancelShortExport, modifier = Modifier.fillMaxWidth()) {
                                                    Text("Cancelar corte", color = CoralError)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        onRenderClipToMp4(activeClip, aspectRatio, startTime, endTime, manualPanX)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = SpaceNavy950),
                                                    modifier = Modifier.fillMaxWidth().testTag("render_short_mp4_button")
                                                ) {
                                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (hasRenderedMp4) "Recortar Short MP4 ($aspectRatio)" else "Cortar Short MP4 ($aspectRatio)",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            pickLocalVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Rounded.VideoFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Vídeo MP4", fontSize = 12.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            pickPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Fotos→MP4", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }

                                        EditorGridTool.LEGENDAS -> {
                                            Text("Legendas Dinâmicas & Transcrição IA", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            OutlinedButton(
                                                onClick = { onTranscribeClipSpeech(activeClip) },
                                                enabled = !aiEditBusy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (aiEditBusy) "A transcrever..." else "Transcrever Fala do Vídeo (IA)", color = NeonCyan)
                                            }
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StudioClip.ALLOWED_CAPTION_STYLES.forEach { st ->
                                                    FilterChip(selected = captionStyle == st, onClick = { captionStyle = st }, label = { Text(st) })
                                                }
                                            }
                                            OutlinedTextField(
                                                value = captionsText,
                                                onValueChange = { captionsText = it },
                                                label = { Text("Texto das legendas") },
                                                minLines = 2,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = highlightKeywords,
                                                onValueChange = { highlightKeywords = it },
                                                label = { Text("Palavras em destaque (vírgula)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        EditorGridTool.TEXTO -> {
                                            Text("Texto de Destaque e Capa", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            OutlinedTextField(
                                                value = visualStyle.coverTitle,
                                                onValueChange = { txt ->
                                                    onUpdateVisualStyle(activeClip.id) { it.copy(coverTitle = txt.take(48)) }
                                                },
                                                label = { Text("Texto no topo do vídeo") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = title,
                                                onValueChange = { title = it },
                                                label = { Text("Título do Short") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = hookText,
                                                onValueChange = { hookText = it },
                                                label = { Text("Frase de gancho (3 primeiros segundos)") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        pickCoverPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Foto Capa")
                                                }
                                                Button(
                                                    onClick = { showCoverDesigner = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Capas IA HD")
                                                }
                                            }
                                        }

                                        EditorGridTool.FILTROS -> {
                                            Text("Filtros de Cor", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                VideoFilterPreset.entries.forEach { preset ->
                                                    FilterChip(
                                                        selected = visualStyle.filterPreset == preset,
                                                        onClick = {
                                                            onUpdateVisualStyle(activeClip.id) { it.copy(filterId = preset.id) }
                                                        },
                                                        label = { Text(preset.label) }
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Gravar filtro e overlays no MP4", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                Switch(
                                                    checked = visualStyle.burnOverlayOnExport,
                                                    onCheckedChange = { c ->
                                                        onUpdateVisualStyle(activeClip.id) { it.copy(burnOverlayOnExport = c) }
                                                    }
                                                )
                                            }
                                        }

                                        EditorGridTool.MUSICA -> {
                                            Text("Áudio e Trilha de Estúdio", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Clareza de Voz & Áudio Estéreo", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                    Text("Garante áudio limpo (44.1kHz estéreo) ou trilha de estúdio no MP4.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                }
                                                Switch(
                                                    checked = visualStyle.audioBoost,
                                                    onCheckedChange = { c ->
                                                        onUpdateVisualStyle(activeClip.id) { it.copy(audioBoost = c) }
                                                    }
                                                )
                                            }
                                        }

                                        EditorGridTool.FIGURINHAS -> {
                                            Text("Figurinhas e Emojis", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                STUDIO_EMOJI_PACK.forEach { emoji ->
                                                    FilterChip(
                                                        selected = visualStyle.emojiOverlay.contains(emoji),
                                                        onClick = {
                                                            val next = if (visualStyle.emojiOverlay == emoji) "" else emoji
                                                            onUpdateVisualStyle(activeClip.id) { it.copy(emojiOverlay = next) }
                                                        },
                                                        label = { Text(emoji, fontSize = 18.sp) }
                                                    )
                                                }
                                                FilterChip(
                                                    selected = visualStyle.emojiOverlay.isBlank(),
                                                    onClick = { onUpdateVisualStyle(activeClip.id) { it.copy(emojiOverlay = "") } },
                                                    label = { Text("Nenhum") }
                                                )
                                            }
                                        }

                                        EditorGridTool.EFEITOS -> {
                                            Text("Efeitos Automáticos com IA", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "Deixa a IA otimizar título, filtro, legenda e cortar o trecho exato pronto para Shorts.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                            Button(
                                                onClick = { onAiOptimizeAndCutScene(activeClip, startTime, endTime, aspectRatio, manualPanX) },
                                                enabled = !aiEditBusy,
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (aiEditBusy) "A processar..." else "Aplicar Efeitos & Cortar Cena com IA", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        EditorGridTool.AJUSTES -> {
                                            Text("Ajustes de Enquadramento", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StudioClip.ALLOWED_FRAMING_MODES.forEach { mode ->
                                                    FilterChip(
                                                        selected = framingMode == mode,
                                                        onClick = { framingMode = mode },
                                                        label = { Text(mode) }
                                                    )
                                                }
                                            }
                                            if (framingMode == "Manual") {
                                                Text("Pan: ${(manualPanX * 100).roundToInt()}% • Zoom: ${"%.2f".format(manualZoom)}x", style = MonoCodeStyle, color = NeonCyan)
                                                Slider(
                                                    value = manualPanX,
                                                    onValueChange = {
                                                        manualPanX = it
                                                        onUpdateVisualStyle(activeClip.id) { s -> s.copy(panX = it) }
                                                    },
                                                    valueRange = -1f..1f
                                                )
                                                Slider(
                                                    value = manualZoom,
                                                    onValueChange = {
                                                        manualZoom = it
                                                        onUpdateVisualStyle(activeClip.id) { s -> s.copy(zoomFactor = it) }
                                                    },
                                                    valueRange = 1f..1.8f
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Mostrar Guia 9:16 Safe Zone", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                                Switch(checked = showSafeZoneGuide, onCheckedChange = { showSafeZoneGuide = it })
                                            }
                                        }

                                        null -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. BOTTOM PINNED "Próximo: Publicar" CTA matching Screen 5
                Surface(color = SpaceNavy950, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        PurpleGradientButton(
                            text = "Próximo: Publicar",
                            onClick = {
                                val updated = activeClip.copy(
                                    title = title,
                                    hookText = hookText,
                                    startTime = startTime,
                                    endTime = endTime,
                                    durationSeconds = (endSeconds - startSeconds).toLong().coerceAtLeast(5L),
                                    aspectRatio = aspectRatio,
                                    captionStyle = captionStyle,
                                    framingMode = framingMode,
                                    captionsText = captionsText,
                                    highlightKeywords = highlightKeywords
                                )
                                onSaveClipEdits(updated)
                                onNextToPublish(updated)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_next_publish_button")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableCutTimelineCard(
    youtubeVideoId: String,
    hasValidYouTube: Boolean,
    localVideoUri: Uri?,
    totalDurationSec: Int,
    startSeconds: Int,
    endSeconds: Int,
    currentPositionSec: Int,
    onRangeChanged: (startSec: Int, endSec: Int, scrubbedSec: Int) -> Unit,
    onSetStartAtPlayhead: () -> Unit,
    onSetEndAtPlayhead: () -> Unit
) {
    val context = LocalContext.current
    var localFrames by remember(localVideoUri) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(localVideoUri) {
        if (localVideoUri != null) {
            localFrames = withContext(Dispatchers.IO) {
                extractFilmstripBitmaps(context, localVideoUri, count = 8)
            }
        } else {
            localFrames = emptyList()
        }
    }

    val safeTotal = totalDurationSec.coerceAtLeast(3)
    val latestStart by rememberUpdatedState(startSeconds)
    val latestEnd by rememberUpdatedState(endSeconds)
    val latestOnRangeChanged by rememberUpdatedState(onRangeChanged)

    val startFrac = (startSeconds.toFloat() / safeTotal.toFloat()).coerceIn(0f, 0.96f)
    val endFrac = (endSeconds.toFloat() / safeTotal.toFloat()).coerceIn(startFrac + 0.02f, 1f)
    val playheadFrac = (currentPositionSec.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating Instruction Banner ("← Arraste para selecionar o trecho desejado →")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SpaceNavy900,
                border = BorderStroke(1.dp, SpaceBorder)
            ) {
                Text(
                    text = "Arraste os marcadores para definir Início e Fim",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            val density = LocalDensity.current
            val handleWidth = 24.dp
            val handleWidthPx = with(density) { handleWidth.toPx() }
            val trackWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(100f)
            val usableSpanPx = (trackWidthPx - handleWidthPx * 2f).coerceAtLeast(24f)

            val startX = (startFrac * usableSpanPx).roundToInt()
            val endX = ((endFrac * usableSpanPx) + handleWidthPx).roundToInt()
                .coerceAtLeast(startX + handleWidthPx.roundToInt() + 6)
            val playheadX = (handleWidthPx + playheadFrac * usableSpanPx).roundToInt()
                .coerceIn(0, (trackWidthPx - 4f).roundToInt())

            // Filmstrip thumbnail frames strip
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpaceNavy900)
            ) {
                if (localFrames.isNotEmpty()) {
                    localFrames.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else if (hasValidYouTube) {
                    val ytFrames = listOf("1.jpg", "2.jpg", "3.jpg", "hqdefault.jpg", "1.jpg", "2.jpg", "3.jpg", "hqdefault.jpg")
                    ytFrames.forEach { suffix ->
                        AsyncImage(
                            model = "https://img.youtube.com/vi/$youtubeVideoId/$suffix",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    repeat(8) {
                        Image(
                            painter = painterResource(id = R.drawable.img_studio_hero_1795221130779),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Left dimmed mask
            Box(
                modifier = Modifier
                    .width(with(density) { startX.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(SpaceNavy950.copy(alpha = 0.72f))
            )

            // Right dimmed mask
            val rightStartPx = endX + handleWidthPx
            if (rightStartPx < trackWidthPx) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(rightStartPx.roundToInt(), 0) }
                        .width(with(density) { (trackWidthPx - rightStartPx).toDp() })
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(SpaceNavy950.copy(alpha = 0.72f))
                )
            }

            // Active White Selection Border Window
            val selectedBoxWidthPx = (endX - startX).coerceAtLeast(24)
            Box(
                modifier = Modifier
                    .offset { IntOffset(startX + (handleWidthPx / 2f).roundToInt(), 0) }
                    .width(with(density) { selectedBoxWidthPx.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(6.dp))
            )

            // Playhead needle
            Box(
                modifier = Modifier
                    .offset { IntOffset(playheadX, 0) }
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(NeonCyan)
            )

            // Left Draggable Purple Diamond Handle (Início)
            Box(
                modifier = Modifier
                    .offset { IntOffset(startX, 0) }
                    .width(handleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(listOf(ElectricVioletLight, ElectricViolet))
                    )
                    .pointerInput(safeTotal) {
                        var dragStartSec = latestStart.toFloat()
                        detectHorizontalDragGestures(
                            onDragStart = { dragStartSec = latestStart.toFloat() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val deltaSec = (dragAmount / usableSpanPx) * safeTotal
                                val maxStart = (latestEnd - 2).coerceAtLeast(0).toFloat()
                                dragStartSec = (dragStartSec + deltaSec).coerceIn(0f, maxStart)
                                val nextStart = dragStartSec.roundToInt()
                                latestOnRangeChanged(nextStart, latestEnd, nextStart)
                            }
                        )
                    }
                    .testTag("trim_handle_start"),
                contentAlignment = Alignment.Center
            ) {
                Text("◆", color = Color.White, fontSize = 14.sp)
            }

            // Right Draggable Purple Diamond Handle (Fim)
            Box(
                modifier = Modifier
                    .offset { IntOffset(endX, 0) }
                    .width(handleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(listOf(ElectricVioletLight, ElectricViolet))
                    )
                    .pointerInput(safeTotal) {
                        var dragEndSec = latestEnd.toFloat()
                        detectHorizontalDragGestures(
                            onDragStart = { dragEndSec = latestEnd.toFloat() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val deltaSec = (dragAmount / usableSpanPx) * safeTotal
                                val minEnd = (latestStart + 2).coerceAtMost(safeTotal).toFloat()
                                dragEndSec = (dragEndSec + deltaSec).coerceIn(minEnd, safeTotal.toFloat())
                                val nextEnd = dragEndSec.roundToInt()
                                latestOnRangeChanged(latestStart, nextEnd, nextEnd)
                            }
                        )
                    }
                    .testTag("trim_handle_end"),
                contentAlignment = Alignment.Center
            ) {
                Text("◆", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Início: ${formatMsToMmSs(startSeconds * 1000L)}",
                style = MonoCodeStyle,
                color = ElectricVioletLight
            )
            Text(
                text = "Duração: ${(endSeconds - startSeconds).coerceAtLeast(1)}s",
                style = MonoCodeStyle,
                color = NeonCyan
            )
            Text(
                text = "Fim: ${formatMsToMmSs(endSeconds * 1000L)}",
                style = MonoCodeStyle,
                color = ElectricVioletLight
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = onSetStartAtPlayhead,
                border = BorderStroke(1.dp, SpaceBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("mark_start_playhead_button")
            ) {
                Text("Início aqui", color = TextPrimary, fontSize = 11.5.sp)
            }
            OutlinedButton(
                onClick = onSetEndAtPlayhead,
                border = BorderStroke(1.dp, SpaceBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("mark_end_playhead_button")
            ) {
                Text("Fim aqui", color = TextPrimary, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun EditorGridToolTile(
    tool: EditorGridTool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) ElectricViolet.copy(alpha = 0.24f) else SpaceNavy900,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) ElectricVioletLight else SpaceBorder
        ),
        modifier = modifier
            .height(74.dp)
            .testTag("editor_grid_${tool.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (selected) ElectricVioletLight else TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tool.label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ElectricVioletLight else TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun extractFilmstripBitmaps(context: Context, uri: Uri, count: Int): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (durMs <= 0L) return emptyList()
        (0 until count).mapNotNull { i ->
            val timeUs = ((durMs * (i + 0.5)) / count * 1000.0).toLong()
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                Bitmap.createScaledBitmap(raw, 160, 90, true)
            }
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

private fun savePickedCoverShortcut(
    context: Context,
    clipId: String,
    headline: String,
    emoji: String,
    filter: VideoFilterPreset,
    photoUri: Uri,
    onSavedPath: (String) -> Unit
) {
    Thread {
        try {
            val stream = context.contentResolver.openInputStream(photoUri)
            val src = android.graphics.BitmapFactory.decodeStream(stream)
            stream?.close()
            val bmp = CoverStudioRenderer.renderCoverBitmap(
                headline = headline,
                subtitle = "SHORT PLANET STREAM",
                emojiBadge = emoji,
                backgroundBitmap = src,
                filterPreset = filter
            )
            val dir = CoverStudioRenderer.coversDir(context)
            val safeId = clipId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(dir, "cover_$safeId.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onSavedPath(file.absolutePath)
            }
        } catch (_: Exception) {
        }
    }.start()
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LoadedSourceExoPlayer(
    videoUri: Uri,
    startMs: Long,
    endMs: Long,
    scrubPositionMs: Long,
    scrubNonce: Int,
    isPlaying: Boolean,
    zoomScale: Float,
    panBias: Float,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            if (startMs > 0L) seekTo(startMs)
        }
    }

    LaunchedEffect(player, isPlaying) {
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(player, scrubNonce) {
        if (scrubNonce > 0) {
            player.seekTo(scrubPositionMs.coerceAtLeast(0L))
        }
    }

    LaunchedEffect(player, isPlaying, startMs, endMs) {
        while (true) {
            val pos = player.currentPosition
            latestOnPositionChanged(pos)
            if (isPlaying && endMs > startMs + 500L && pos >= endMs) {
                player.seekTo(startMs)
            }
            kotlinx.coroutines.delay(180L)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.graphicsLayer {
            scaleX = zoomScale.coerceIn(1f, 2f)
            scaleY = zoomScale.coerceIn(1f, 2f)
            translationX = -panBias * 140f
        },
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        }
    )
}

@Composable
private fun LocalShortExoPlayer(
    mp4File: File,
    seekSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(mp4File.absolutePath, mp4File.lastModified()) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(mp4File)))
            repeatMode = Player.REPEAT_MODE_ONE
            if (seekSeconds > 0) seekTo(seekSeconds * 1000L)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        }
    )
}

private class YouTubeErrorJsBridge(
    private val onErrorCode: (Int) -> Unit
) {
    @JavascriptInterface
    fun onYouTubeError(code: Int) = onErrorCode(code)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeClipWebViewPlayer(
    videoId: String,
    startSeconds: Int,
    endSeconds: Int,
    onEmbedBlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeVideoId = remember(videoId) {
        if (videoId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))) videoId else ""
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(safeVideoId) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    if (safeVideoId.isEmpty()) return

    val appOrigin = "https://${context.packageName}"
    val endQuery = if (endSeconds > startSeconds) ",\n          end: $endSeconds" else ""
    val html = """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #070412; overflow: hidden; }
              #player { width: 100%; height: 100%; }
            </style>
          </head>
          <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              function onYouTubeIframeAPIReady() {
                new YT.Player('player', {
                  videoId: '$safeVideoId',
                  playerVars: {
                    autoplay: 1,
                    playsinline: 1,
                    rel: 0,
                    modestbranding: 1,
                    start: $startSeconds,
                    origin: '$appOrigin'$endQuery
                  },
                  events: {
                    onError: function(event) {
                      if (window.PlanetBridge && window.PlanetBridge.onYouTubeError) {
                        window.PlanetBridge.onYouTubeError(event.data || 150);
                      }
                    }
                  }
                });
              }
            </script>
          </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor("#070412"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                addJavascriptInterface(
                    YouTubeErrorJsBridge { code ->
                        if (code == 100 || code == 101 || code == 150 || code == 152) {
                            post { onEmbedBlocked() }
                        }
                    },
                    "PlanetBridge"
                )
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return true
                        val host = uri.host.orEmpty()
                        val isTrustedYoutube = uri.scheme == "https" &&
                            (host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".googlevideo.com"))
                        return !isTrustedYoutube
                    }
                }
                tag = safeVideoId
                loadDataWithBaseURL("$appOrigin/", html, "text/html", "utf-8", null)
                webViewRef = this
            }
        },
        update = { view ->
            val currentTag = view.tag as? String
            if (currentTag != safeVideoId) {
                view.tag = safeVideoId
                view.loadDataWithBaseURL("$appOrigin/", html, "text/html", "utf-8", null)
            }
        }
    )
}
