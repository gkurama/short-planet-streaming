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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudioClip
import com.example.data.model.VideoSource
import com.example.media.TikTokAccountState
import com.example.ui.components.StudioCard
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.YouTubeLibraryUiState

@Composable
fun StatsScreen(
    ytLibraryState: YouTubeLibraryUiState,
    sourcesState: UiState<List<VideoSource>>,
    clipsState: UiState<List<StudioClip>>,
    renderedClipIds: Set<String>,
    onOpenClipInEditor: (String) -> Unit,
    onOpenAiProducerChat: () -> Unit,
    onOpenConnectAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sources = (sourcesState as? UiState.Success)?.data.orEmpty()
    val clips = (clipsState as? UiState.Success)?.data.orEmpty()
    val avgViral = if (clips.isNotEmpty()) clips.map { it.viralScore }.average().toInt() else 0

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Estatísticas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Channel Overview Card
            item {
                StudioCard(accentColor = ElectricViolet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ytLibraryState.channel?.title ?: "Canal não conectado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (ytLibraryState.channel != null) {
                                    "${ytLibraryState.channel.subscriberCountLabel} inscritos • ${ytLibraryState.channel.videoCountLabel} vídeos"
                                } else {
                                    "Liga a tua conta do YouTube para ver métricas reais do canal."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (ytLibraryState.channel == null) {
                            Button(
                                onClick = onOpenConnectAccounts,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text("Conectar")
                            }
                        }
                    }
                }
            }

            // 2x2 Metrics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricTile(
                        icon = Icons.Rounded.PlayCircle,
                        label = "Vídeos / Lives",
                        value = "${ytLibraryState.videos.size.coerceAtLeast(sources.size)}",
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricTile(
                        icon = Icons.Rounded.ContentCut,
                        label = "Shorts Criados",
                        value = "${clips.size}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricTile(
                        icon = Icons.Rounded.HighQuality,
                        label = "MP4 Exportados",
                        value = "${renderedClipIds.size}",
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricTile(
                        icon = Icons.Rounded.TrendingUp,
                        label = "Score Viral Médio",
                        value = if (avgViral > 0) "$avgViral%" else "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Produtor IA Chat Card
            item {
                StudioCard(accentColor = ElectricVioletLight) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElectricViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = ElectricVioletLight)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Produtor IA (Gemini)", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Pede roteiros, ganchos e ideias virais para os teus próximos Shorts.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAiProducerChat,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abrir Produtor IA", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Clips ranking
            if (clips.isNotEmpty()) {
                item {
                    Text(
                        text = "Desempenho estimado dos Shorts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                items(clips.take(6).size) { idx ->
                    val clip = clips[idx]
                    Surface(
                        onClick = { onOpenClipInEditor(clip.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = SpaceNavy900,
                        border = BorderStroke(1.dp, SpaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(clip.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${clip.startTime} → ${clip.endTime} • ${clip.publishStatus}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text(
                                text = "${clip.viralScore}/100",
                                color = EmeraldPulse,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SpaceNavy900,
        border = BorderStroke(1.dp, SpaceBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = ElectricVioletLight, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun SettingsScreen(
    userEmail: String?,
    userName: String?,
    ytLibraryState: YouTubeLibraryUiState,
    tikTokAccount: TikTokAccountState,
    exportQuality: String,
    notificationsEnabled: Boolean,
    showBackButton: Boolean,
    onOpenConnectAccounts: () -> Unit,
    onSelectExportQuality: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQualityOptions by remember { mutableStateOf(false) }

    val connectedSummary = buildString {
        append(if (ytLibraryState.isConnected) "YouTube (${ytLibraryState.channel?.title ?: "Ligado"})" else "YouTube")
        append(" • ")
        append(if (tikTokAccount.isConnected) "TikTok (${tikTokAccount.handle})" else "TikTok")
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header (< Configurações) matching Screen 8 mockup
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showBackButton) {
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
                    }
                    Text(
                        text = "Configurações",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Profile card when accessed via Perfil tab (not cluttering Settings card)
            if (!showBackButton) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SpaceNavy900,
                        border = BorderStroke(1.dp, SpaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (userName?.firstOrNull() ?: userEmail?.firstOrNull() ?: 'S').uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName ?: "Criador Short Planet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userEmail ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Grouped Settings Card (exact rows & icons matching Screen 8)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SpaceNavy900,
                    border = BorderStroke(1.dp, SpaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Rounded.AccountBox,
                            title = "Contas Conectadas",
                            subtitle = connectedSummary,
                            onClick = onOpenConnectAccounts
                        )
                        HorizontalDivider(color = SpaceBorder.copy(alpha = 0.55f))

                        SettingsActionRow(
                            icon = Icons.Rounded.HighQuality,
                            title = "Qualidade de Exportação",
                            subtitle = exportQuality,
                            onClick = { showQualityOptions = !showQualityOptions }
                        )
                        if (showQualityOptions) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpaceNavy850)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("1080p (Recomendado)", "720p (Rápido)").forEach { opt ->
                                    val active = exportQuality == opt
                                    OutlinedButton(
                                        onClick = {
                                            onSelectExportQuality(opt)
                                            showQualityOptions = false
                                        },
                                        border = BorderStroke(1.dp, if (active) ElectricViolet else SpaceBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = opt,
                                            color = if (active) ElectricVioletLight else TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = SpaceBorder.copy(alpha = 0.55f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Notificações",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = onToggleNotifications,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ElectricViolet
                                )
                            )
                        }
                        HorizontalDivider(color = SpaceBorder.copy(alpha = 0.55f))

                        SettingsInfoRow(
                            icon = Icons.Rounded.Contrast,
                            title = "Tema",
                            value = "Modo Escuro"
                        )
                        HorizontalDivider(color = SpaceBorder.copy(alpha = 0.55f))

                        SettingsInfoRow(
                            icon = Icons.Rounded.Info,
                            title = "Sobre o App",
                            value = "v1.0.0"
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    border = BorderStroke(1.dp, CoralError.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = CoralError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Terminar sessão", color = CoralError, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
