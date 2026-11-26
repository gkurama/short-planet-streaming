package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.TikTokAuthHelper
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.media.StudioLocalPrefs
import com.example.ui.components.CosmicHorizonBackdrop
import com.example.ui.components.PurpleGradientButton
import com.example.ui.components.ShortPlanetStreamLogoIcon
import com.example.ui.components.TikTokBadgeIcon
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorSoft
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.EmeraldPulseSoft
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTikTokInput by remember { mutableStateOf(false) }
    var tikTokHandleInput by remember {
        mutableStateOf(StudioLocalPrefs.loadTikTokAccount(context).handle)
    }
    var tikTokConnectedNote by remember {
        mutableStateOf(
            StudioLocalPrefs.loadTikTokAccount(context).takeIf { it.isConnected }?.handle
        )
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    fun launchGoogleLogin() {
        isLoading = true
        errorMessage = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isLoading = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CosmicHorizonBackdrop()

        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Brand Emblem + Headline (matching Screen 1: Splash / Login)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShortPlanetStreamLogoIcon(size = 128.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Short Planet\nStream",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Corte • Edite • Publique",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Bottom Login Actions (Entrar com YouTube + Conectar com TikTok)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = CoralErrorSoft,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = CoralError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (!tikTokConnectedNote.isNullOrBlank()) {
                    Surface(
                        color = EmeraldPulseSoft,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "TikTok ligado: $tikTokConnectedNote • Agora entra com o YouTube/Google para abrir o estúdio.",
                            color = EmeraldPulse,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                PurpleGradientButton(
                    text = if (isLoading) "A autenticar..." else "Entrar com YouTube",
                    onClick = ::launchGoogleLogin,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_sign_in_button"),
                    leadingContent = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(width = 30.dp, height = 22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        if (TikTokAuthHelper.isConfigured()) {
                            val state = TikTokAuthHelper.generateState()
                            val verifier = TikTokAuthHelper.generateCodeVerifier()
                            StudioLocalPrefs.savePendingTikTokOAuth(context.applicationContext, state, verifier)
                            TikTokAuthHelper.launchOAuthInBrowser(context, state, verifier)
                        } else {
                            showTikTokInput = !showTikTokInput
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.5.dp, ElectricVioletLight.copy(alpha = 0.72f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SpaceNavy900.copy(alpha = 0.72f),
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("splash_connect_tiktok_button")
                ) {
                    TikTokBadgeIcon(size = 24.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (tikTokConnectedNote != null) {
                            "TikTok: $tikTokConnectedNote"
                        } else {
                            "Conectar com TikTok"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                if (showTikTokInput) {
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
                        OutlinedButton(
                            onClick = {
                                val saved = StudioLocalPrefs.saveTikTokAccount(context.applicationContext, tikTokHandleInput)
                                if (saved.isConnected) {
                                    tikTokConnectedNote = saved.handle
                                    showTikTokInput = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ElectricViolet)
                        ) {
                            Text("Guardar", color = ElectricVioletLight)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Faça login para começar",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
