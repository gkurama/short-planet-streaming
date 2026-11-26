package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AiChatModelOption
import com.example.data.model.AiChatRole
import com.example.data.model.ChatMessage
import com.example.ui.components.SectionTitleRow
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorSoft
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletDeep
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SpaceBorder
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ChatUiState

@Composable
fun ChatbotScreen(
    chatState: ChatUiState,
    onSelectRole: (AiChatRole) -> Unit,
    onSelectModel: (AiChatModelOption) -> Unit,
    onSendMessage: (String) -> Unit,
    onResetChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatState.messages.size, chatState.messages.lastOrNull()?.text?.length) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            // Header + Role & Model Switchers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceNavy900)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitleRow(
                        title = "Produtor IA",
                        subtitle = chatState.selectedRole.subtitle,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onResetChat,
                        modifier = Modifier.testTag("reset_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Nova conversa",
                            tint = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Papel do assistente (System Instruction)", style = MonoCodeStyle, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiChatRole.entries.forEach { role ->
                        FilterChip(
                            selected = chatState.selectedRole == role,
                            onClick = { onSelectRole(role) },
                            label = { Text(role.displayName) },
                            modifier = Modifier.testTag("chat_role_${role.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Modelo Gemini", style = MonoCodeStyle, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiChatModelOption.entries.forEach { modelOpt ->
                        FilterChip(
                            selected = chatState.selectedModel == modelOpt,
                            onClick = { onSelectModel(modelOpt) },
                            label = { Text("${modelOpt.label} • ${modelOpt.badge}") },
                            modifier = Modifier.testTag("chat_model_${modelOpt.name.lowercase()}")
                        )
                    }
                }
            }

            // Scrollable Conversation Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("chat_message_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                if (!chatState.error.isNullOrBlank()) {
                    item {
                        Surface(
                            color = CoralErrorSoft,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = chatState.error,
                                color = CoralError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Quick Prompt Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPrompts = listOf(
                    "Cria 3 ganchos virais de 3s para o meu último vídeo",
                    "Sugere ritmo e cortes 9:16 para aumentar retenção",
                    "Gera um comando FFmpeg para crop 1080x1920",
                    "Escreve descrição e hashtags para YouTube Shorts"
                )
                quickPrompts.forEach { prompt ->
                    SuggestionChip(
                        onClick = { if (!chatState.isSending) onSendMessage(prompt) },
                        label = { Text(prompt) }
                    )
                }
            }

            // Composer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceNavy900)
                    .border(1.dp, SpaceBorder)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Pergunta ao ${chatState.selectedRole.displayName}...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = {
                        if (input.isNotBlank() && !chatState.isSending) {
                            val textToSend = input
                            input = ""
                            onSendMessage(textToSend)
                        }
                    },
                    enabled = input.isNotBlank() && !chatState.isSending,
                    shape = RoundedCornerShape(14.dp),
                    color = ElectricViolet,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (chatState.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Enviar mensagem",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromUser) ElectricVioletDeep else SpaceNavy850
    val borderColor = if (message.isFromUser) ElectricViolet else SpaceBorder

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!message.isFromUser && message.roleLabel.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${message.roleLabel} • ${message.modelLabel}",
                    style = MonoCodeStyle,
                    color = NeonCyan
                )
            }
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isFromUser) 18.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 18.dp
            ),
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (message.text.isBlank() && message.isStreaming) "A pensar..." else message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (message.isStreaming && message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpaceNavy950.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("A transmitir...", style = MonoCodeStyle, color = NeonCyan)
                    }
                }
            }
        }
    }
}
