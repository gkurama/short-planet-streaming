package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.ImageAspectOption
import com.example.data.model.ImageModelOption
import com.example.data.model.ImageSizeOption
import com.example.data.model.StudioClip
import com.example.data.model.VideoSource
import com.example.media.STUDIO_EMOJI_PACK
import com.example.media.VideoFilterPreset
import com.example.ui.components.SectionTitleRow
import com.example.ui.components.ShimmerLoadingBox
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioCard
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorSoft
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ImageGenUiState
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoverArtGeneratorTabContent(
    activeClip: StudioClip?,
    linkedSource: VideoSource?,
    initialHeadline: String,
    initialEmoji: String,
    initialFilter: VideoFilterPreset,
    imageGenState: ImageGenUiState,
    selectedSize: ImageSizeOption,
    selectedAspect: ImageAspectOption,
    selectedModel: ImageModelOption,
    onSelectSize: (ImageSizeOption) -> Unit,
    onSelectAspect: (ImageAspectOption) -> Unit,
    onSelectModel: (ImageModelOption) -> Unit,
    onGenerate: (
        prompt: String,
        headline: String,
        emoji: String,
        filter: VideoFilterPreset,
        photoUri: Uri?,
        preferLocalStudio: Boolean
    ) -> Unit,
    onApplyToClip: (Bitmap) -> Unit,
    onSaveCoverToGallery: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    var headline by remember(activeClip?.id) {
        mutableStateOf(initialHeadline.ifBlank { "MOMENTO ÉPICO" })
    }
    var selectedEmoji by remember(activeClip?.id) { mutableStateOf(initialEmoji) }
    var selectedFilter by remember(activeClip?.id) { mutableStateOf(initialFilter) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val defaultPrompt = remember(activeClip?.id) {
        if (activeClip != null) {
            "Capa cinematográfica para vídeo vertical sobre: ${activeClip.title}. Iluminação neon violeta, sem texto."
        } else {
            "Capa cinematográfica para YouTube Shorts e TikTok, estética estúdio cósmico escuro."
        }
    }
    var prompt by remember(activeClip?.id) { mutableStateOf(defaultPrompt) }

    val pickCoverPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedPhotoUri = uri
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitleRow(
                title = "Designer de Capas Pro",
                subtitle = "Cria capas 1K–4K com foto, miniatura do YouTube ou fundo IA.",
                badge = "1K / 2K / 4K"
            )
        }

        item {
            StudioCard(accentColor = ElectricViolet) {
                OutlinedTextField(
                    value = headline,
                    onValueChange = { headline = it.take(55) },
                    label = { Text("Título principal da capa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Emoji na capa", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    STUDIO_EMOJI_PACK.take(8).forEach { e ->
                        FilterChip(
                            selected = selectedEmoji == e,
                            onClick = { selectedEmoji = if (selectedEmoji == e) "" else e },
                            label = { Text(e, fontSize = 18.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Filtro da capa", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VideoFilterPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedFilter == preset,
                            onClick = { selectedFilter = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        pickCoverPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (pickedPhotoUri != null) "Foto selecionada! Tocar para trocar"
                        else if (linkedSource?.youtubeVideoId?.isNotBlank() == true) "Usar Foto da Galeria (ou Miniatura YouTube)"
                        else "Escolher Foto da Galeria"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Resolução e Proporção", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageSizeOption.entries.forEach { sizeOption ->
                        FilterChip(
                            selected = selectedSize == sizeOption,
                            onClick = { onSelectSize(sizeOption) },
                            label = { Text(sizeOption.label) },
                            modifier = Modifier.weight(1f).testTag("image_size_${sizeOption.label.lowercase()}")
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageAspectOption.entries.forEach { aspect ->
                        FilterChip(
                            selected = selectedAspect == aspect,
                            onClick = { onSelectAspect(aspect) },
                            label = { Text(aspect.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onGenerate(prompt, headline, selectedEmoji, selectedFilter, pickedPhotoUri, true) },
                    enabled = imageGenState !is ImageGenUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_local_cover_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPulse,
                        contentColor = SpaceNavy950
                    )
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criar Capa HD (${selectedSize.label} Estúdio)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt IA (opcional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cover_prompt_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageModelOption.entries.forEach { modelOpt ->
                        FilterChip(
                            selected = selectedModel == modelOpt,
                            onClick = { onSelectModel(modelOpt) },
                            label = { Text(modelOpt.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onGenerate(prompt, headline, selectedEmoji, selectedFilter, pickedPhotoUri, false) },
                    enabled = imageGenState !is ImageGenUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_cover_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar com Gemini IA (Auto Fallback)")
                }
            }
        }

        item {
            when (imageGenState) {
                is ImageGenUiState.Idle -> {
                    StudioCard {
                        Text(
                            text = "Personaliza título, emoji e filtro acima para criar uma capa HD e aplicar diretamente ao Short.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                is ImageGenUiState.Loading -> {
                    ShimmerLoadingBox(label = "A gerar capa ${selectedSize.label}...")
                }
                is ImageGenUiState.Error -> {
                    Surface(
                        color = CoralErrorSoft,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(imageGenState.message, color = CoralError, modifier = Modifier.padding(16.dp))
                    }
                }
                is ImageGenUiState.Success -> {
                    StudioCard(accentColor = ElectricViolet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Capa pronta", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                if (imageGenState.sourceNote.isNotBlank()) {
                                    Text(imageGenState.sourceNote, style = MonoCodeStyle, color = NeonCyan)
                                }
                            }
                            StatusPill(
                                label = "${imageGenState.sizeLabel} • ${imageGenState.aspectLabel}",
                                color = NeonCyan,
                                backgroundColor = NeonCyan.copy(alpha = 0.14f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            bitmap = imageGenState.bitmap.asImageBitmap(),
                            contentDescription = "Capa gerada",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, SpaceBorder, RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (activeClip != null) {
                            Button(
                                onClick = { onApplyToClip(imageGenState.bitmap) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text("Aplicar ao Short", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSaveCoverToGallery(imageGenState.bitmap, headline) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galeria")
                            }
                            OutlinedButton(
                                onClick = { shareGeneratedBitmap(context, imageGenState.bitmap) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Partilhar")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareGeneratedBitmap(context: Context, bitmap: Bitmap) {
    try {
        val dir = File(context.cacheDir, "shared_covers").apply { mkdirs() }
        val file = File(dir, "planet_cover_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partilhar Capa Short Planet Stream"))
    } catch (_: Exception) {
    }
}
