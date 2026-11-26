package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.TikTokAccountState
import com.example.media.formatMsToMmSs
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.EmeraldPulseSoft
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DualPublishStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneTimelineSelectorCard(
    totalDurationSeconds: Int,
    scrubSeconds: Int,
    startSeconds: Int,
    endSeconds: Int,
    aiBusy: Boolean,
    exportBusy: Boolean,
    onScrubChange: (Int) -> Unit,
    onPreviewAtScrub: (Int) -> Unit,
    onMarkStart: (Int) -> Unit,
    onMarkEnd: (Int) -> Unit,
    onQuickDurationSeconds: (Int) -> Unit,
    onAiOptimizeAndCutScene: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeMaxSec = totalDurationSeconds.coerceAtLeast(30)
    val clampedScrub = scrubSeconds.coerceIn(0, safeMaxSec)
    val clipLen = (endSeconds - startSeconds).coerceAtLeast(5)

    StudioCard(
        accentColor = ElectricViolet,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Escolher Cena Exata no Vídeo",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Arrasta a agulha até à cena que queres cortar e deixa a IA otimizar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            StatusPill(
                label = "${clipLen}s",
                color = NeonCyan,
                backgroundColor = NeonCyan.copy(alpha = 0.14f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agulha: ${formatMsToMmSs(clampedScrub * 1000L)}",
                style = MonoCodeStyle,
                color = NeonCyan,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Cena: ${formatMsToMmSs(startSeconds * 1000L)} → ${formatMsToMmSs(endSeconds * 1000L)}",
                style = MonoCodeStyle,
                color = EmeraldPulse
            )
        }

        Slider(
            value = clampedScrub.toFloat(),
            onValueChange = { onScrubChange(it.toInt()) },
            valueRange = 0f..safeMaxSec.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = SpaceNavy850
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scene_timeline_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onPreviewAtScrub(clampedScrub) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("scene_preview_here_button")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pré-ver", maxLines = 1)
            }
            OutlinedButton(
                onClick = { onMarkStart(clampedScrub) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("scene_mark_in_button")
            ) {
                Text("Início Aqui", maxLines = 1)
            }
            OutlinedButton(
                onClick = { onMarkEnd(clampedScrub) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("scene_mark_out_button")
            ) {
                Text("Fim Aqui", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(15, 30, 45, 58).forEach { sec ->
                FilterChip(
                    selected = clipLen == sec,
                    onClick = { onQuickDurationSeconds(sec) },
                    label = { Text("+${sec}s", style = MonoCodeStyle) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onAiOptimizeAndCutScene,
            enabled = !aiBusy && !exportBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("ai_cut_optimize_scene_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White
            )
        ) {
            if (aiBusy || exportBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("A otimizar e cortar esta cena...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("IA Otimizar & Cortar Esta Cena", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DualPublishStatusCard(
    status: DualPublishStatus,
    onDismiss: () -> Unit,
    onShareYouTubeFallback: () -> Unit,
    onShareTikTokFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(
        accentColor = if (status.youtubeSuccess && status.tikTokSuccess) EmeraldPulse else NeonCyan,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (status.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Publish,
                        contentDescription = null,
                        tint = EmeraldPulse,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Envio Simultâneo (YouTube + TikTok)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!status.isRunning) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Fechar estado", tint = TextSecondary)
                }
            }
        }

        if (status.isRunning) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NeonCyan,
                trackColor = SpaceNavy850
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        PlatformPublishRow(
            platform = "YouTube Shorts",
            message = status.youtubeStatus,
            isSuccess = status.youtubeSuccess,
            actionLabel = if (!status.isRunning && status.youtubeNeedsAppShare && !status.youtubeSuccess) "Enviar YT" else null,
            onAction = onShareYouTubeFallback
        )
        Spacer(modifier = Modifier.height(6.dp))
        PlatformPublishRow(
            platform = "TikTok",
            message = status.tikTokStatus,
            isSuccess = status.tikTokSuccess,
            actionLabel = if (!status.isRunning && status.tikTokNeedsAppShare && !status.tikTokSuccess) "Enviar TT" else null,
            onAction = onShareTikTokFallback
        )
    }
}

@Composable
private fun PlatformPublishRow(
    platform: String,
    message: String,
    isSuccess: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceNavy850)
            .border(
                1.dp,
                if (isSuccess) EmeraldPulse.copy(alpha = 0.5f) else SpaceBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Share,
            contentDescription = null,
            tint = if (isSuccess) EmeraldPulse else SolarAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(platform, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel, color = NeonCyan, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TikTokConnectionCard(
    tikTokAccount: TikTokAccountState,
    onStartTikTokOAuth: () -> Unit,
    onConnectTikTokHandle: (handle: String) -> Unit,
    onDisconnectTikTok: () -> Unit,
    onOpenTikTokApp: () -> Unit,
    isOAuthConfigured: Boolean = com.example.auth.TikTokAuthHelper.isConfigured(),
    modifier: Modifier = Modifier
) {
    var handleInput by remember(tikTokAccount.handle) { mutableStateOf(tikTokAccount.handle) }
    var showHandleFallback by remember { mutableStateOf(!isOAuthConfigured && !tikTokAccount.isOAuthConnected) }

    StudioCard(
        accentColor = if (tikTokAccount.isConnected) EmeraldPulse else ElectricViolet,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOAuthConfigured) {
                        "Conta TikTok (API OAuth2 & Envio Automático)"
                    } else {
                        "Conta TikTok (Envio Direto estilo Vozo AI)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (tikTokAccount.isConnected) {
                        val apiMode = if (tikTokAccount.isOAuthConnected || tikTokAccount.hasApiUploadToken) {
                            " • API Oficial v2 Ativa"
                        } else {
                            " • Envio Direto na App TikTok"
                        }
                        val name = tikTokAccount.displayName.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        "Conectada como ${tikTokAccount.handle}$name$apiMode"
                    } else if (isOAuthConfigured) {
                        "Inicia sessão no TikTok via API para publicar o mesmo corte automaticamente junto com o YouTube."
                    } else {
                        "Liga o teu @utilizador abaixo (sem senha nem chaves API): ao gerar o Short MP4, o app envia direto para o TikTok instalado no teu telemóvel."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                label = when {
                    tikTokAccount.isOAuthConnected || tikTokAccount.hasApiUploadToken -> "API OAuth2"
                    tikTokAccount.isConnected -> "Ligado @"
                    else -> "Por ligar"
                },
                color = if (tikTokAccount.isConnected) EmeraldPulse else TextSecondary,
                backgroundColor = if (tikTokAccount.isConnected) EmeraldPulseSoft else SpaceNavy850
            )
        }

        if (isOAuthConfigured) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartTikTokOAuth,
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("connect_tiktok_oauth_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = SpaceNavy950
                    )
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tikTokAccount.isOAuthConnected) "Renovar Ligação TikTok API" else "Entrar no TikTok (OAuth2 API)",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(onClick = onOpenTikTokApp) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (tikTokAccount.isConnected) "Abrir Perfil" else "Abrir TikTok")
                }

                if (tikTokAccount.isConnected) {
                    OutlinedButton(
                        onClick = onDisconnectTikTok,
                        modifier = Modifier.testTag("disconnect_tiktok_videos_button")
                    ) {
                        Text("Desconectar", color = CoralError)
                    }
                }
            }
        }

        if (!isOAuthConfigured || showHandleFallback) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = handleInput,
                    onValueChange = { handleInput = it },
                    label = { Text("@Utilizador TikTok") },
                    placeholder = { Text("@planetoffgames") },
                    leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tiktok_handle_input")
                )
                Button(
                    onClick = { onConnectTikTokHandle(handleInput) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("connect_tiktok_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = SpaceNavy950
                    )
                ) {
                    Text("Ligar @", fontWeight = FontWeight.Bold)
                }
            }

            if (!isOAuthConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onOpenTikTokApp) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (tikTokAccount.isConnected) "Abrir Perfil TikTok" else "Abrir App TikTok")
                    }
                    if (tikTokAccount.isConnected) {
                        OutlinedButton(
                            onClick = onDisconnectTikTok,
                            modifier = Modifier.testTag("disconnect_tiktok_videos_button")
                        ) {
                            Text("Desconectar", color = CoralError)
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = { showHandleFallback = true }) {
                Text("Ou ligar manualmente por @utilizador (app TikTok)", color = TextSecondary)
            }
        }
    }
}

