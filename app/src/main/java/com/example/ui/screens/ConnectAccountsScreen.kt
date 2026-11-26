package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.TikTokAuthHelper
import com.example.media.TikTokAccountState
import com.example.ui.components.PurpleGradientButton
import com.example.ui.components.TikTokBadgeIcon
import com.example.ui.components.YouTubeBadgeIcon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.YouTubeLibraryUiState

@Composable
fun ConnectAccountsScreen(
    ytLibraryState: YouTubeLibraryUiState,
    tikTokAccount: TikTokAccountState,
    onConnectYouTube: () -> Unit,
    onDisconnectYouTube: () -> Unit,
    onLoadPublicChannel: (String) -> Unit,
    onStartTikTokOAuth: () -> Unit,
    onConnectTikTokHandle: (String) -> Unit,
    onDisconnectTikTok: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTikTokHandleEditor by remember { mutableStateOf(false) }
    var tikTokHandleInput by remember(tikTokAccount.handle) { mutableStateOf(tikTokAccount.handle) }
    var showPublicChannelField by remember { mutableStateOf(false) }
    var publicChannelInput by remember(ytLibraryState.channelHandleInput) {
        mutableStateOf(ytLibraryState.channelHandleInput)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(SpaceNavy950, Color(0xFF120926), SpaceNavy950))
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar (< Conectar)
            Box(
                modifier = Modifier.fillMaxWidth(),
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
                    text = "Conectar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // YouTube Block (exact match to Screen 2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                YouTubeBadgeIcon(size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "YouTube",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Conecte sua conta do YouTube\npara acessar seus vídeos e lives.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            PurpleGradientButton(
                text = if (ytLibraryState.isConnected) {
                    "YouTube Conectado (${ytLibraryState.channel?.title ?: "Canal"})"
                } else {
                    "Entrar com Google"
                },
                onClick = onConnectYouTube,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_youtube_google_button"),
                leadingContent = {
                    if (ytLibraryState.isConnected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPulse,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    color = ElectricViolet,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { showPublicChannelField = !showPublicChannelField }) {
                    Text(
                        text = if (showPublicChannelField) "Ocultar @canal público" else "Ou carregar canal por @handle",
                        color = ElectricVioletLight
                    )
                }
                if (ytLibraryState.isConnected) {
                    TextButton(onClick = onDisconnectYouTube) {
                        Text("Desligar", color = TextSecondary)
                    }
                }
            }

            if (showPublicChannelField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = publicChannelInput,
                        onValueChange = { publicChannelInput = it },
                        label = { Text("@Handle ou URL do canal") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onLoadPublicChannel(publicChannelInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text("Carregar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider "ou"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = SpaceBorder)
                Text(
                    text = "ou",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = SpaceBorder)
            }

            Spacer(modifier = Modifier.height(26.dp))

            // TikTok Block (exact match to Screen 2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TikTokBadgeIcon(size = 38.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TikTok",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Conecte sua conta do TikTok\npara publicar seus shorts.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedButton(
                onClick = {
                    if (TikTokAuthHelper.isConfigured() && !tikTokAccount.isOAuthConnected) {
                        onStartTikTokOAuth()
                    } else {
                        showTikTokHandleEditor = !showTikTokHandleEditor
                    }
                },
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, if (tikTokAccount.isConnected) EmeraldPulse else ElectricVioletLight.copy(alpha = 0.72f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SpaceNavy900,
                    contentColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("connect_tiktok_account_button")
            ) {
                TikTokBadgeIcon(size = 24.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (tikTokAccount.isConnected) {
                        "TikTok Conectado (${tikTokAccount.handle})"
                    } else {
                        "Conectar com TikTok"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            if (showTikTokHandleEditor || (!tikTokAccount.isConnected && !TikTokAuthHelper.isConfigured())) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tikTokHandleInput,
                        onValueChange = { tikTokHandleInput = it },
                        label = { Text("@utilizador_tiktok") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onConnectTikTokHandle(tikTokHandleInput)
                            showTikTokHandleEditor = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text("Ligar @")
                    }
                }
            }

            if (tikTokAccount.isConnected) {
                TextButton(onClick = onDisconnectTikTok) {
                    Text("Desconectar TikTok (${tikTokAccount.handle})", color = TextSecondary)
                }
            }
        }
    }
}
