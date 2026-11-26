package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.StudioClip
import com.example.data.remote.YouTubeLibraryVideo
import com.example.ui.components.ShortPlanetStreamBrandBar
import com.example.ui.components.StudioCard
import com.example.ui.components.YouTubeBadgeIcon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletBright
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.YouTubeLibraryUiState

@Composable
fun HomeScreen(
    ytLibraryState: YouTubeLibraryUiState,
    clipsState: UiState<List<StudioClip>>,
    autopilotBusySourceId: String?,
    onOpenVideosList: () -> Unit,
    onOpenLivesList: () -> Unit,
    onOpenVideoInEditor: (YouTubeLibraryVideo) -> Unit,
    onOpenClipInEditor: (String) -> Unit,
    onOpenConnectAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNotificationsBanner by remember { mutableStateOf(false) }

    val channelVideos = remember(ytLibraryState.videos) {
        ytLibraryState.videos.filter { it.sourceType != "LIVE" }
    }
    val channelLives = remember(ytLibraryState.videos) {
        ytLibraryState.videos.filter { it.sourceType == "LIVE" }
    }
    val recentClips = (clipsState as? UiState.Success)?.data.orEmpty()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Brand Bar (Short Planet Stream + Notifications + Settings)
            item {
                ShortPlanetStreamBrandBar(
                    onNotificationsClick = { showNotificationsBanner = !showNotificationsBanner },
                    onSettingsClick = onOpenSettings
                )
            }

            if (showNotificationsBanner) {
                item {
                    StudioCard(accentColor = ElectricViolet) {
                        Text("Atividades do Estúdio", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusSummary = buildString {
                            if (ytLibraryState.channel != null) {
                                append("Canal: ${ytLibraryState.channel.title} (${ytLibraryState.videos.size} vídeos carregados). ")
                            } else {
                                append("Conecta a tua conta YouTube para sincronizar vídeos e lives. ")
                            }
                            if (recentClips.isNotEmpty()) {
                                append("${recentClips.size} Shorts prontos no Editor.")
                            }
                        }
                        Text(statusSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            // Dual Action Pill Shortcuts (Vídeos do Canal | Lives) - Screen 9 layout
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onOpenVideosList,
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(50.dp)
                            .testTag("home_channel_videos_pill")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(ElectricViolet, ElectricVioletBright)),
                                    RoundedCornerShape(26.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(width = 24.dp, height = 18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = ElectricViolet,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Vídeos do Canal",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onOpenLivesList,
                        shape = RoundedCornerShape(26.dp),
                        color = SpaceNavy900,
                        border = BorderStroke(1.dp, SpaceBorder),
                        modifier = Modifier
                            .weight(0.85f)
                            .height(50.dp)
                            .testTag("home_lives_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Podcasts,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lives",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Connect Prompt when YouTube library isn't loaded yet
            if (!ytLibraryState.isConnected && ytLibraryState.videos.isEmpty()) {
                item {
                    StudioCard(accentColor = ElectricViolet) {
                        Text(
                            text = "Conecte sua conta do YouTube",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Carregue seus vídeos e transmissões ao vivo para cortar Shorts e enviar para YouTube e TikTok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onOpenConnectAccounts,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Conectar Contas", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenVideosList,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, SpaceBorder)
                            ) {
                                Text("Selecionar Vídeo", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            if (ytLibraryState.isLoading || autopilotBusySourceId != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SpaceNavy900)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ElectricVioletLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (autopilotBusySourceId != null) {
                                "A preparar vídeo no Editor..."
                            } else {
                                "A sincronizar vídeos e lives do YouTube..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Section 1: Vídeos do Canal (2-column grid matching Screen 9)
            item {
                HomeSectionHeader(
                    leadingIcon = { YouTubeBadgeIcon(size = 18.dp) },
                    title = "Vídeos do Canal",
                    onSeeAll = onOpenVideosList
                )
            }

            if (channelVideos.isEmpty()) {
                item {
                    EmptyChannelSectionPlaceholder(
                        message = if (ytLibraryState.isConnected) {
                            "Nenhum vídeo regular carregado ainda."
                        } else {
                            "Conecta o teu YouTube ou toca em Selecionar Vídeo para começar."
                        },
                        actionLabel = if (ytLibraryState.isConnected) "Atualizar" else "Selecionar Vídeo",
                        onClick = if (ytLibraryState.isConnected) onRefreshYouTube else onOpenVideosList
                    )
                }
            } else {
                val displayVideos = channelVideos.take(4).chunked(2)
                items(displayVideos.size) { rowIdx ->
                    val pair = displayVideos[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { video ->
                            HomeVideoGridCard(
                                video = video,
                                isLive = false,
                                onClick = { onOpenVideoInEditor(video) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Section 2: Lives do YouTube (2-column grid matching Screen 9)
            item {
                HomeSectionHeader(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = LiveRed,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Lives do YouTube",
                    onSeeAll = onOpenLivesList
                )
            }

            if (channelLives.isEmpty()) {
                item {
                    EmptyChannelSectionPlaceholder(
                        message = "Sem transmissões ao vivo listadas no momento.",
                        actionLabel = "Ver todas / Selecionar",
                        onClick = onOpenLivesList
                    )
                }
            } else {
                val displayLives = channelLives.take(4).chunked(2)
                items(displayLives.size) { rowIdx ->
                    val pair = displayLives[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { live ->
                            HomeVideoGridCard(
                                video = live,
                                isLive = true,
                                onClick = { onOpenVideoInEditor(live) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Recent Shorts in Studio (if any already created)
            if (recentClips.isNotEmpty()) {
                item {
                    HomeSectionHeader(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = ElectricVioletLight,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        title = "Shorts no Editor",
                        onSeeAll = { onOpenClipInEditor(recentClips.first().id) }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentClips.take(3).forEach { clip ->
                            Surface(
                                onClick = { onOpenClipInEditor(clip.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = SpaceNavy900,
                                border = BorderStroke(1.dp, SpaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = clip.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${clip.startTime} → ${clip.endTime} • ${clip.aspectRatio}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Abrir no Editor",
                                        tint = ElectricVioletLight
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    leadingIcon: @Composable () -> Unit,
    title: String,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            leadingIcon()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ver todos",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricVioletLight,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ElectricVioletLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HomeVideoGridCard(
    video: YouTubeLibraryVideo,
    isLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var useHqFallback by remember(video.videoId) { mutableStateOf(false) }
    val thumbUrl = when {
        useHqFallback -> video.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${video.videoId}/hqdefault.jpg" }
        video.videoId.matches(Regex("^[a-zA-Z0-9_\\-]{11}$")) -> "https://img.youtube.com/vi/${video.videoId}/mqdefault.jpg"
        else -> video.thumbnailUrl
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceNavy900)
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(SpaceNavy850)
        ) {
            AsyncImage(
                model = thumbUrl,
                onError = { if (!useHqFallback) useHqFallback = true },
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isLive) {
                Surface(
                    color = LiveRed,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "AO VIVO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            } else if (video.durationLabel.isNotBlank() && video.durationLabel != "—") {
                Surface(
                    color = SpaceNavy950.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = video.durationLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = if (isLive) Icons.Rounded.Podcasts else Icons.Rounded.Visibility,
                contentDescription = null,
                tint = if (isLive) LiveRed else TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isLive) {
                    "${video.viewCountLabel.ifBlank { "Live" }} assistindo"
                } else {
                    video.viewCountLabel.ifBlank { video.durationLabel }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyChannelSectionPlaceholder(
    message: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SpaceNavy900,
        border = BorderStroke(1.dp, SpaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                border = BorderStroke(1.dp, ElectricVioletLight.copy(alpha = 0.6f))
            ) {
                Text(actionLabel, color = ElectricVioletLight, fontSize = 12.sp)
            }
        }
    }
}
