package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.StudioClip
import com.example.data.model.VideoSource
import com.example.media.ClipVisualStyle
import com.example.media.TikTokAccountState
import com.example.media.VideoFilterPreset
import com.example.ui.components.DualPublishStatusCard
import com.example.ui.components.PurpleGradientButton
import com.example.ui.components.ShortPlanetStreamLogoIcon
import com.example.ui.components.TikTokBadgeIcon
import com.example.ui.components.YouTubeBadgeIcon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DualPublishStatus
import com.example.ui.viewmodel.ShortExportUiState
import com.example.ui.viewmodel.UiState
import java.io.File

@Composable
fun PublishScreen(
    clipsState: UiState<List<StudioClip>>,
    sourcesState: UiState<List<VideoSource>>,
    selectedClipId: String?,
    clipVisualStyles: Map<String, ClipVisualStyle>,
    tikTokAccount: TikTokAccountState,
    isYouTubeConnected: Boolean,
    shortExportState: ShortExportUiState,
    youTubeUploadBusy: Boolean,
    dualPublishStatus: DualPublishStatus?,
    onPublishNow: (clip: StudioClip, sendYouTube: Boolean, sendTikTok: Boolean, title: String, description: String, privacyStatus: String, madeForKids: Boolean) -> Unit,
    onSaveToGallery: (StudioClip) -> Unit,
    onShareYouTubeFallback: (StudioClip) -> Unit,
    onShareTikTokFallback: (StudioClip) -> Unit,
    onDismissDualPublish: () -> Unit,
    onOpenConnectAccounts: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = (clipsState as? UiState.Success)?.data.orEmpty()
    val sources = (sourcesState as? UiState.Success)?.data.orEmpty()
    val activeClip = clips.firstOrNull { it.id == selectedClipId } ?: clips.firstOrNull()
    val linkedSource = remember(activeClip?.id, sources) {
        if (activeClip == null) null
        else sources.firstOrNull { it.id == activeClip.sourceId }
            ?: sources.firstOrNull { it.title.equals(activeClip.sourceTitle, ignoreCase = true) }
    }

    var sendToYouTube by remember(activeClip?.id) { mutableStateOf(true) }
    var sendToTikTok by remember(activeClip?.id) { mutableStateOf(true) }
    var titleText by remember(activeClip?.id, activeClip?.title) { mutableStateOf(activeClip?.title.orEmpty()) }
    var privacyStatus by remember(activeClip?.id) { mutableStateOf("unlisted") }
    var madeForKids by remember(activeClip?.id) { mutableStateOf<Boolean?>(null) }
    var confirmedCommunityGuidelines by remember(activeClip?.id) { mutableStateOf(false) }
    var confirmedRights by remember(activeClip?.id) { mutableStateOf(false) }
    var descriptionText by remember(activeClip?.id, activeClip?.publishNotes) {
        val defaultDesc = activeClip?.publishNotes?.takeIf { it.isNotBlank() }
            ?: "${activeClip?.hookText?.takeIf { it.isNotBlank() } ?: "Confira esse trecho incrível!"}\n\n#ShortPlanetStream"
        mutableStateOf(defaultDesc)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            // Top Bar (< Publicar seu Short)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = "Publicar seu Short",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (activeClip == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nenhum Short selecionado para publicar.",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("Voltar ao Editor", color = ElectricVioletLight)
                        }
                    }
                }
            } else {
                val visualStyle = clipVisualStyles[activeClip.id] ?: ClipVisualStyle()
                val customCover = remember(visualStyle.coverImagePath) {
                    visualStyle.coverImagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
                }
                val videoId = linkedSource?.youtubeVideoId.orEmpty()
                val hasValidYt = videoId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$"))
                val colorFilter = remember(visualStyle.filterId) {
                    if (visualStyle.filterPreset == VideoFilterPreset.ORIGINAL) null
                    else ColorFilter.colorMatrix(visualStyle.filterPreset.toComposeColorMatrix())
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Centered 9:16 Vertical Preview Card matching Screen 7
                    item {
                        Box(
                            modifier = Modifier
                                .width(156.dp)
                                .aspectRatio(9f / 14.5f)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, ElectricVioletLight.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                                .background(SpaceNavy900)
                        ) {
                            if (customCover != null) {
                                AsyncImage(
                                    model = customCover,
                                    contentDescription = activeClip.title,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (hasValidYt) {
                                var useHq by remember(videoId) { mutableStateOf(false) }
                                AsyncImage(
                                    model = if (useHq) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                    onError = { if (!useHq) useHq = true },
                                    contentDescription = activeClip.title,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.img_studio_hero_1795221130779),
                                    contentDescription = activeClip.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                SpaceNavy950.copy(alpha = 0.92f)
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ShortPlanetStreamLogoIcon(size = 24.dp)
                                Text(
                                    text = visualStyle.coverTitle.ifBlank { "Short Planet Stream" },
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Dual Publish Progress / Status Feedback if running or completed
                    if (dualPublishStatus != null && dualPublishStatus.clipId == activeClip.id) {
                        item {
                            DualPublishStatusCard(
                                status = dualPublishStatus,
                                onDismiss = onDismissDualPublish,
                                onShareYouTubeFallback = { onShareYouTubeFallback(activeClip) },
                                onShareTikTokFallback = { onShareTikTokFallback(activeClip) }
                            )
                        }
                    }

                    if (shortExportState is ShortExportUiState.Processing) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, ElectricViolet),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = shortExportState.statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricVioletLight
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (shortExportState.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                        color = ElectricViolet,
                                        trackColor = SpaceNavy850
                                    )
                                }
                            }
                        }
                    }

                    // "Enviar para:" Platform Checkboxes Card matching Screen 7
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SpaceNavy900,
                            border = BorderStroke(1.dp, SpaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Enviar para:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    TextButton(onClick = onOpenConnectAccounts) {
                                        Text(
                                            text = if (isYouTubeConnected && tikTokAccount.isConnected) "Contas OK" else "Conectar Contas",
                                            color = ElectricVioletLight,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                HorizontalDivider(color = SpaceBorder.copy(alpha = 0.6f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { sendToYouTube = !sendToYouTube }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    YouTubeBadgeIcon(size = 24.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("YouTube", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (isYouTubeConnected) "Canal conectado" else "Envio direto via YouTube App",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isYouTubeConnected) EmeraldPulse else TextSecondary
                                        )
                                    }
                                    Checkbox(
                                        checked = sendToYouTube,
                                        onCheckedChange = { sendToYouTube = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ElectricViolet,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }

                                HorizontalDivider(color = SpaceBorder.copy(alpha = 0.45f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { sendToTikTok = !sendToTikTok }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TikTokBadgeIcon(size = 28.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("TikTok", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (tikTokAccount.isConnected) tikTokAccount.handle else "Partilha rápida no app TikTok",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (tikTokAccount.isConnected) EmeraldPulse else TextSecondary
                                        )
                                    }
                                    Checkbox(
                                        checked = sendToTikTok,
                                        onCheckedChange = { sendToTikTok = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ElectricViolet,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (sendToYouTube) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, SpaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Detalhes do YouTube", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = titleText,
                                        onValueChange = { titleText = it },
                                        label = { Text("Título (até 100 caracteres)") },
                                        supportingText = { Text("${titleText.length}/100") },
                                        isError = titleText.isBlank() || titleText.length > 100,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Visibilidade", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    listOf("unlisted" to "Não listado", "private" to "Privado", "public" to "Público").forEach { (value, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = privacyStatus == value, onClick = { privacyStatus = value })
                                            Text(label, color = TextPrimary)
                                        }
                                    }
                                    Text("Este vídeo é feito para crianças?", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    listOf(null to "Escolhe uma opção", true to "Sim", false to "Não").forEach { (value, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = madeForKids == value, onClick = { madeForKids = value })
                                            Text(label, color = TextPrimary)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = confirmedRights, onCheckedChange = { confirmedRights = it })
                                        Text("Tenho direito de editar e publicar este arquivo.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = confirmedCommunityGuidelines, onCheckedChange = { confirmedCommunityGuidelines = it })
                                        Text("Confirmo que o vídeo segue as Diretrizes da Comunidade do YouTube.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    // "Descrição (opcional)" Box matching Screen 7
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Descrição (opcional)",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = descriptionText,
                                onValueChange = { descriptionText = it },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("publish_description_input")
                            )
                        }
                    }

                    item {
                        TextButton(
                            onClick = { onSaveToGallery(activeClip) }
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ou guardar MP4 na Galeria", color = TextSecondary)
                        }
                    }
                }

                // Bottom Pinned "Publicar Agora" CTA
                Surface(color = SpaceNavy950, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        val busy = youTubeUploadBusy || shortExportState is ShortExportUiState.Processing
                        PurpleGradientButton(
                            text = if (busy) "A publicar..." else "Publicar Agora",
                            enabled = !busy && (sendToYouTube || sendToTikTok) &&
                                (!sendToYouTube || (titleText.isNotBlank() && titleText.length <= 100 && madeForKids != null && confirmedRights && confirmedCommunityGuidelines)),
                            onClick = {
                                onPublishNow(activeClip, sendToYouTube, sendToTikTok, titleText, descriptionText, privacyStatus, madeForKids ?: false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("publish_now_button"),
                            leadingContent = {
                                if (busy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.RocketLaunch,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
