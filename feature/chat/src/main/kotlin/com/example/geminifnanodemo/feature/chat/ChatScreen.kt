package com.example.geminifnanodemo.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geminifnanodemo.core.designsystem.ImageAttachRow
import com.example.geminifnanodemo.core.designsystem.MessageBubble
import com.example.geminifnanodemo.core.designsystem.ScreenPhase
import com.example.geminifnanodemo.core.designsystem.TierChannelRow
import com.example.geminifnanodemo.core.designsystem.channelLetter
import com.example.geminifnanodemo.core.designsystem.downloadableMessage
import com.example.geminifnanodemo.core.designsystem.label
import com.example.geminifnanodemo.core.designsystem.phase
import com.example.geminifnanodemo.core.designsystem.signalColor
import com.example.geminifnanodemo.core.designsystem.unavailableMessage
import com.example.geminifnanodemo.core.ui.DemoColors
import com.example.geminifnanodemo.domain.models.ChatMessage
import com.example.geminifnanodemo.domain.models.DemoState
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.MediaPipeModelOption
import com.example.geminifnanodemo.domain.models.TierAvailability
import com.example.geminifnanodemo.domain.models.TierCapabilities
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** Chat: selector de canal + conversación multi-turno de un solo tier a la vez. */
@Composable
fun ChatScreen(
    conversationId: Long? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId) {
        conversationId?.let(viewModel::openConversation)
    }

    val state by viewModel.state.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val pendingImage by viewModel.pendingImage.collectAsState()
    val selectedMediaPipeModel by viewModel.selectedMediaPipeModel.collectAsState()
    var promptText by remember { mutableStateOf("Explícame en una frase qué es un agente de IA.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "CHAT",
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InferenceTier.entries.forEach { tier ->
                TierChannelRow(
                    tier = tier,
                    isActive = tier == activeTier,
                    availability = availability[tier] ?: TierAvailability.UNKNOWN,
                    onClick = { viewModel.selectTier(tier) },
                )
            }
        }

        if (activeTier == InferenceTier.MEDIAPIPE_LOCAL) {
            MediaPipeModelRow(
                models = viewModel.mediaPipeModels,
                selected = selectedMediaPipeModel,
                onSelect = { viewModel.selectMediaPipeModel(it) },
            )
        }

        ActivePanel(
            state = state,
            activeTier = activeTier,
            capabilities = viewModel.capabilitiesOf(activeTier),
            conversation = conversation,
            pendingImageUri = pendingImage,
            promptText = promptText,
            onPromptChange = { promptText = it },
            onGenerate = { viewModel.generate(promptText) },
            onDownload = { viewModel.downloadModel() },
            onPickImage = { viewModel.setPendingImage(it) },
            onResetConversation = { viewModel.resetConversation() },
        )
    }
}

/** Elegir qué modelo del catálogo de MediaPipe usar — cada uno se descarga/mantiene por separado. */
@Composable
private fun MediaPipeModelRow(
    models: List<MediaPipeModelOption>,
    selected: MediaPipeModelOption,
    onSelect: (MediaPipeModelOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("MODELO", style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models) { model ->
                FilterChip(
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    label = { Text("${model.displayName} (${model.sizeHint})") },
                )
            }
        }
    }
}

@Composable
private fun ActivePanel(
    state: DemoState,
    activeTier: InferenceTier,
    capabilities: TierCapabilities,
    conversation: List<ChatMessage>,
    pendingImageUri: Uri?,
    promptText: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDownload: () -> Unit,
    onPickImage: (Uri?) -> Unit,
    onResetConversation: () -> Unit,
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> onPickImage(uri) },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, activeTier.signalColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        AnimatedContent(targetState = state.phase, label = "activePanel") { phase ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (phase) {
                    ScreenPhase.CHECKING -> {
                        Text("Comprobando disponibilidad de ${activeTier.label()}…", color = DemoColors.Ink)
                        CircularProgressIndicator(color = activeTier.signalColor)
                    }

                    ScreenPhase.UNAVAILABLE -> {
                        Text(activeTier.unavailableMessage(), color = DemoColors.Ink)
                    }

                    ScreenPhase.DOWNLOADABLE -> {
                        Text(activeTier.downloadableMessage(), color = DemoColors.Ink)
                        Button(onClick = onDownload) {
                            Text("Descargar modelo")
                        }
                    }

                    ScreenPhase.DOWNLOADING -> {
                        val percent = (state as? DemoState.Downloading)?.progressPercent
                        if (percent != null) {
                            Text("Descargando ${activeTier.label()}… $percent%", color = DemoColors.Ink)
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                color = activeTier.signalColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Preparando ${activeTier.label()}… (puede tardar)", color = DemoColors.Ink)
                            CircularProgressIndicator(color = activeTier.signalColor)
                        }
                    }

                    ScreenPhase.READY_GROUP -> {
                        if (conversation.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                conversation.forEach { message -> MessageBubble(message, activeTier.signalColor) }
                            }
                            TextButton(onClick = onResetConversation) { Text("Nueva conversación") }
                        }

                        if (capabilities.images) {
                            ImageAttachRow(
                                pendingImageUri = pendingImageUri,
                                onLaunchPicker = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest.Builder()
                                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            .build()
                                    )
                                },
                                onClear = { onPickImage(null) },
                            )
                        }

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = onPromptChange,
                            label = { Text("Prompt") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val isGenerating = state is DemoState.Generating
                        Button(
                            onClick = onGenerate,
                            enabled = !isGenerating
                        ) {
                            Text(if (isGenerating) "Generando…" else "Generar con ${activeTier.label()}")
                        }

                        when (state) {
                            is DemoState.Generating -> {
                                Text("Generando:", style = MaterialTheme.typography.titleSmall, color = DemoColors.Muted)
                                Text(state.partialText, color = DemoColors.Ink)
                            }
                            is DemoState.Error -> {
                                Text("Error: ${state.message}", color = DemoColors.Danger)
                            }
                            // Result ya quedó reflejado como el último turno del transcript de arriba.
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
