package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.YouTubeLibraryVideo
import com.example.ui.components.PurpleGradientButton
import com.example.ui.components.YouTubeBadgeIcon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.YouTubeLibraryUiState

@Composable
fun SelectVideoScreen(
    ytLibraryState: YouTubeLibraryUiState,
    initialTabIndex: Int = 0, // 0 = Vídeos, 1 = Lives
    autopilotBusySourceId: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshYouTube: () -> Unit,
    onLoadPublicChannel: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadMoreYouTube: () -> Unit,
    onContinueWithSelected: (List<YouTubeLibraryVideo>) -> Unit,
    onPickLocalVideo: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    val selectedVideoIds = remember { mutableStateListOf<String>() }
    var channelInput by remember(ytLibraryState.channelHandleInput) {
        mutableStateOf(ytLibraryState.channelHandleInput)
    }

    val pickLocalVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickLocalVideo(uri)
        }
    }

    val filteredVideos = remember(ytLibraryState.videos, selectedTab, ytLibraryState.searchQuery) {
        val byType = if (selectedTab == 1) {
            ytLibraryState.videos.filter { it.sourceType == "LIVE" }
        } else {
            ytLibraryState.videos.filter { it.sourceType != "LIVE" }
        }
        val query = ytLibraryState.searchQuery.trim()
        if (query.isBlank()) byType
        else byType.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.channelName.contains(query, ignoreCase = true)
        }
    }

    val selectedObjects = remember(selectedVideoIds.toList(), ytLibraryState.videos) {
        ytLibraryState.videos.filter { it.videoId in selectedVideoIds }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Top Bar (← Selecionar Vídeo ⚙)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Selecionar Vídeo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Configurações",
                        tint = TextPrimary
                    )
                }
            }

            // 2-Tab Bar (Vídeos | Lives) matching Screen 4 mockup
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SpaceNavy950,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = ElectricViolet
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("select_video_tab_videos")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        YouTubeBadgeIcon(size = 18.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vídeos",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) TextPrimary else TextSecondary
                        )
                    }
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("select_video_tab_lives")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = LiveRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lives",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            // Quick Search + Local Video MP4 option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ytLibraryState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Filtrar por título...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshYouTube) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar canal", tint = ElectricVioletLight)
                }
                OutlinedButton(
                    onClick = {
                        pickLocalVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    border = BorderStroke(1.dp, SpaceBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricVioletLight)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("MP4", fontSize = 12.sp, color = TextPrimary)
                }
            }

            if (ytLibraryState.videos.isEmpty() && !ytLibraryState.isLoading) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Carrega o teu canal para selecionar vídeos",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            label = { Text("@Handle do Canal") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onLoadPublicChannel(channelInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Text("Buscar")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ytLibraryState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ElectricViolet)
                            }
                        }
                    }

                    items(filteredVideos, key = { it.videoId }) { video ->
                        val isSelected = video.videoId in selectedVideoIds
                        SelectVideoRowItem(
                            video = video,
                            isSelected = isSelected,
                            onToggle = {
                                if (isSelected) selectedVideoIds.remove(video.videoId)
                                else selectedVideoIds.add(video.videoId)
                            }
                        )
                    }

                    if (!ytLibraryState.nextPageToken.isNullOrBlank()) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMoreYouTube,
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, SpaceBorder)
                            ) {
                                Text("Carregar mais vídeos", color = ElectricVioletLight)
                            }
                        }
                    }
                }
            }

            // YouTube library entries open in YouTube. Editing begins from a local file below.
            Surface(
                color = SpaceNavy950,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val isBusy = autopilotBusySourceId != null
                    PurpleGradientButton(
                        text = if (isBusy) "A abrir no YouTube..." else "Abrir no YouTube (${selectedObjects.size})",
                        enabled = selectedObjects.isNotEmpty() && !isBusy,
                        onClick = { onContinueWithSelected(selectedObjects) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_video_continue_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectVideoRowItem(
    video: YouTubeLibraryVideo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    var useHqFallback by remember(video.videoId) { mutableStateOf(false) }
    val thumbUrl = if (useHqFallback) {
        video.thumbnailUrl.ifBlank { "https://img.youtube.com/vi/${video.videoId}/hqdefault.jpg" }
    } else {
        "https://img.youtube.com/vi/${video.videoId}/mqdefault.jpg"
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) ElectricViolet.copy(alpha = 0.14f) else SpaceNavy900,
        border = BorderStroke(1.dp, if (isSelected) ElectricViolet else SpaceBorder.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpaceNavy850)
            ) {
                AsyncImage(
                    model = thumbUrl,
                    onError = { if (!useHqFallback) useHqFallback = true },
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (video.sourceType == "LIVE") {
                    Surface(
                        color = LiveRed,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (video.durationLabel.isNotBlank() && video.durationLabel != "—") {
                    Surface(
                        color = SpaceNavy950.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = video.durationLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        video.durationLabel.takeIf { it.isNotBlank() && it != "—" },
                        video.viewCountLabel.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifBlank { video.channelName },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ElectricViolet else Color.Transparent)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else TextSecondary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selecionado",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
