package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletBright
import com.example.ui.theme.ElectricVioletDeep
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.EmeraldPulseSoft
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeRed

@Composable
fun ShortPlanetStreamLogoIcon(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_planet_play_emblem),
        contentDescription = "Logótipo Short Planet Stream",
        modifier = modifier.size(size)
    )
}

@Composable
fun CosmicHorizonBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF060310),
                    Color(0xFF0D0622),
                    Color(0xFF150934),
                    Color(0xFF070412)
                )
            )
        )
        // Subtle cosmic starfield
        val stars = listOf(
            0.14f to 0.12f, 0.82f to 0.10f, 0.25f to 0.28f, 0.72f to 0.22f,
            0.11f to 0.45f, 0.90f to 0.38f, 0.33f to 0.56f, 0.65f to 0.52f,
            0.18f to 0.68f, 0.85f to 0.66f, 0.50f to 0.15f, 0.43f to 0.41f
        )
        stars.forEachIndexed { idx, (fx, fy) ->
            drawCircle(
                color = Color.White.copy(alpha = if (idx % 2 == 0) 0.42f else 0.22f),
                radius = if (idx % 3 == 0) 2.2f else 1.4f,
                center = Offset(size.width * fx, size.height * fy)
            )
        }
        // Bottom glowing purple planet horizon arc (matching Splash mockup)
        val center = Offset(size.width * 0.5f, size.height * 1.18f)
        val radius = size.width * 0.95f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricVioletBright.copy(alpha = 0.52f),
                    ElectricViolet.copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.82f),
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.78f,
            center = Offset(size.width * 0.5f, size.height * 0.84f)
        )
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6D28D9),
                    Color(0xFF1E0B4B),
                    Color(0xFF070412)
                )
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFDDD6FE).copy(alpha = 0.75f),
                    Color(0xFFA855F7).copy(alpha = 0.25f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.72f),
                radius = size.width * 0.55f
            ),
            radius = size.width * 0.45f,
            center = Offset(size.width * 0.5f, size.height * 0.75f)
        )
    }
}

@Composable
fun ShortPlanetStreamBrandBar(
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            ShortPlanetStreamLogoIcon(size = 44.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append("Short Planet\n")
                    }
                    withStyle(
                        SpanStyle(
                            color = ElectricVioletLight,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append("Stream")
                    }
                },
                fontSize = 18.sp,
                lineHeight = 19.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsNone,
                    contentDescription = "Notificações",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configurações",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun PurpleGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = SpaceNavy850,
            contentColor = Color.White,
            disabledContentColor = TextSecondary
        ),
        modifier = modifier.height(54.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) {
                        Brush.horizontalGradient(listOf(ElectricViolet, ElectricVioletBright))
                    } else {
                        Brush.horizontalGradient(listOf(SpaceNavy850, SpaceNavy850))
                    },
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun YouTubeBadgeIcon(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = size * 1.35f, height = size)
            .clip(RoundedCornerShape(8.dp))
            .background(YouTubeRed),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "YouTube",
            tint = Color.White,
            modifier = Modifier.size(size * 0.72f)
        )
    }
}

@Composable
fun TikTokBadgeIcon(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.64f)) {
            val w = this.size.width
            val h = this.size.height
            fun drawNote(offsetX: Float, offsetY: Float, color: Color) {
                val path = Path().apply {
                    moveTo(w * 0.56f + offsetX, h * 0.14f + offsetY)
                    lineTo(w * 0.56f + offsetX, h * 0.68f + offsetY)
                    cubicTo(
                        w * 0.56f + offsetX, h * 0.88f + offsetY,
                        w * 0.22f + offsetX, h * 0.90f + offsetY,
                        w * 0.22f + offsetX, h * 0.68f + offsetY
                    )
                    cubicTo(
                        w * 0.22f + offsetX, h * 0.50f + offsetY,
                        w * 0.44f + offsetX, h * 0.48f + offsetY,
                        w * 0.48f + offsetX, h * 0.54f + offsetY
                    )
                }
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.16f)
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.56f + offsetX, h * 0.18f + offsetY),
                    end = Offset(w * 0.86f + offsetX, h * 0.34f + offsetY),
                    strokeWidth = w * 0.16f
                )
            }
            drawNote(-w * 0.04f, -h * 0.03f, Color(0xFF25F4EE))
            drawNote(w * 0.04f, h * 0.03f, Color(0xFFFE2C55))
            drawNote(0f, 0f, Color.White)
        }
    }
}

@Composable
fun PlanetBrandHeader(
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShortPlanetStreamLogoIcon(size = 40.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Short Planet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "STREAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricVioletLight
                )
            }
        }
        trailing()
    }
}

@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    accentColor: Color = SpaceBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceNavy900),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color = EmeraldPulse,
    backgroundColor: Color = EmeraldPulseSoft,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EyebrowTag(
    icon: ImageVector = Icons.Rounded.AutoAwesome,
    text: String,
    color: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun SectionTitleRow(
    title: String,
    subtitle: String,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = badge,
                style = MonoCodeStyle,
                color = ElectricVioletLight
            )
        }
    }
}

@Composable
fun ShimmerLoadingBox(
    label: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SpaceNavy850.copy(alpha = alpha))
            .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
