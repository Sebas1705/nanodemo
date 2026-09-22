package es.sebas1705.nanodemo.feature.chat

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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.ImageAttachRow
import es.sebas1705.nanodemo.core.designsystem.MessageBubble
import es.sebas1705.nanodemo.core.designsystem.ScreenPhase
import es.sebas1705.nanodemo.core.designsystem.TierChannelRow
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.downloadableMessage
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.phase
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.unavailableMessage
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** Chat: selector de canal + conversación multi-turno de un solo tier a la vez. */
@Composable
fun ChatScreen(
    conversationId: Long? = null,
    sharedText: String? = null,
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
    val attachedUrl by viewModel.attachedUrl.collectAsState()
    val selectedMediaPipeModel by viewModel.selectedMediaPipeModel.collectAsState()
    var promptText by remember {
        // Compartir desde otra app precarga el prompt (con una instrucción, no el texto pelado) en
        // vez de mandarlo solo: el usuario lo revisa/edita antes de generar, nunca se envía sin que
        // lo vea — ver el intent-filter ACTION_SEND en AndroidManifest.xml y MainActivity.
        mutableStateOf(sharedText?.let { "Resume esto:\n\n$it" } ?: "Explícame en una frase qué es un agente de IA.")
    }

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
            attachedUrl = attachedUrl,
            promptText = promptText,
            onPromptChange = { promptText = it },
            onGenerate = { viewModel.generate(promptText) },
            onDownload = { viewModel.downloadModel() },
            onPickImage = { viewModel.setPendingImage(it) },
            onAttachUrl = { viewModel.setAttachedUrl(it) },
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
    attachedUrl: String?,
    promptText: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDownload: () -> Unit,
    onPickImage: (Uri?) -> Unit,
    onAttachUrl: (String?) -> Unit,
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

                        // A diferencia de imágenes, no depende de TierCapabilities: el contenido de la
                        // página se manda como texto de más, así que funciona en los 5 tiers por igual.
                        UrlAttachRow(attachedUrl = attachedUrl, onAttach = onAttachUrl, onClear = { onAttachUrl(null) })

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

/**
 * "Pegar un link y preguntar": la app descarga la página y la manda de contexto al generar (ver
 * `InferenceRepository.generate()`) — funciona igual en los 5 tiers, no como las imágenes (solo
 * Nano/Cloud/LiteRT-LM/Firebase AI tienen encoder de visión).
 */
@Composable
private fun UrlAttachRow(attachedUrl: String?, onAttach: (String) -> Unit, onClear: () -> Unit) {
    var urlInput by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("LINK (OPCIONAL)", style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
        if (attachedUrl != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    attachedUrl,
                    style = readoutStyle,
                    color = DemoColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Quitar") }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { if (urlInput.isNotBlank()) { onAttach(urlInput.trim()); urlInput = "" } },
                    enabled = urlInput.isNotBlank(),
                ) { Text("Adjuntar") }
            }
        }
    }
}
