package es.sebas1705.nanodemo.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.ScreenPhase
import es.sebas1705.nanodemo.core.designsystem.phase
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability

/**
 * Lo que el usuario acaba de mandar y todavía no está en `conversation` (el repositorio recién agrega el
 * turno cuando la respuesta termina bien): se muestra igual al toque, como cualquier chat. `stateAtSend`
 * distingue "el resultado de ESTE turno" del `Result` viejo que sigue en `state` hasta que arranca la
 * generación nueva (identidad, no igualdad: dos respuestas iguales seguirían siendo dos turnos).
 */
private data class InFlightTurn(
    val text: String,
    val imageUri: Uri?,
    val url: String?,
    val baseSize: Int,
    val stateAtSend: DemoState,
)

/** Chat: conversación multi-turno de un solo tier a la vez, con el selector de motor en la cabecera. */
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
    val config by viewModel.config.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val pendingImage by viewModel.pendingImage.collectAsState()
    val attachedUrl by viewModel.attachedUrl.collectAsState()
    val selectedMediaPipeModel by viewModel.selectedMediaPipeModel.collectAsState()
    val capabilities = viewModel.capabilitiesOf(activeTier)

    // Compartir desde otra app precarga el prompt (con una instrucción, no el texto pelado) en vez de
    // mandarlo solo: el usuario lo revisa/edita antes de enviar, nunca se envía sin que lo vea — ver el
    // intent-filter ACTION_SEND en AndroidManifest.xml y MainActivity.
    var promptText by remember { mutableStateOf(sharedText?.let { "Resume esto:\n\n$it" } ?: "") }
    var inFlight by remember { mutableStateOf<InFlightTurn?>(null) }
    var showTierSheet by remember { mutableStateOf(false) }
    var pinnedToBottom by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.setPendingImage(uri) },
    )

    val isGenerating = state is DemoState.Generating
    // Estado que corresponde a ESTE turno (no el Result/Error viejo previo a que arranque la generación).
    val turnState = inFlight?.takeIf { state !== it.stateAtSend }?.let { state }
    val streamingText = when (val s = turnState) {
        is DemoState.Generating -> s.partialText
        is DemoState.Result -> s.text
        else -> null
    }
    val errorMessage = (state as? DemoState.Error)?.takeIf { inFlight == null || state !== inFlight?.stateAtSend }?.message

    fun send(text: String, fromComposer: Boolean) {
        val prompt = text.trim()
        if (prompt.isEmpty() || isGenerating) return
        inFlight = InFlightTurn(prompt, pendingImage, attachedUrl, conversation.size, state)
        if (fromComposer) promptText = ""
        pinnedToBottom = true
        viewModel.generate(prompt)
    }

    LaunchedEffect(conversation.size) {
        inFlight?.let { if (conversation.size > it.baseSize) inFlight = null }
    }
    LaunchedEffect(state) {
        if (state !is DemoState.Generating && state !is DemoState.Result && state !is DemoState.Error) inFlight = null
        // Si falló, el texto vuelve al campo para reintentar/editar en vez de perderse.
        val turn = inFlight
        if (state is DemoState.Error && turn != null && state !== turn.stateAtSend && promptText.isEmpty()) {
            promptText = turn.text
        }
    }

    val itemCount = conversation.size + (if (inFlight != null) 2 else 0) + (if (errorMessage != null) 1 else 0)
    val streamedChars = streamingText?.length ?: 0

    // "Pegado al final": mientras el usuario no scrollee hacia arriba, cada chunk nuevo lo sigue. Si sube a
    // leer algo, deja de seguirlo; al volver al final, lo retoma.
    // `autoScrolling` evita que nuestro propio scrollToItem/scrollBy (que pasa un instante por "todavía
    // se puede bajar") se lea como si el usuario hubiera subido y suelte el seguimiento.
    val autoScrolling = remember { booleanArrayOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canForward) ->
            if (autoScrolling[0]) return@collect
            if (scrolling) pinnedToBottom = !canForward else if (!canForward) pinnedToBottom = true
        }
    }
    // Abrir/cerrar el teclado achica/agranda la lista: también hay que volver a pegarse al final.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(itemCount, streamedChars, imeVisible) {
        if (itemCount > 0 && pinnedToBottom) {
            autoScrolling[0] = true
            try {
                listState.scrollToItem(itemCount - 1)
                listState.scrollBy(100_000f)
            } finally {
                autoScrolling[0] = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatHeader(
            tier = activeTier,
            availability = availability[activeTier] ?: TierAvailability.UNKNOWN,
            modelName = selectedMediaPipeModel.displayName.takeIf { activeTier == InferenceTier.MEDIAPIPE_LOCAL },
            canStartOver = conversation.isNotEmpty() || inFlight != null,
            onOpenTierPicker = { showTierSheet = true },
            onStartOver = {
                inFlight = null
                viewModel.resetConversation()
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // Dentro de un Box para centrarlos: sus Column scrollean (pantallas chicas/landscape) y una
                // Column con scroll no puede centrar a sus hijos, no conoce su alto.
                state.phase != ScreenPhase.READY_GROUP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TierStatusPanel(state = state, tier = activeTier, onDownload = { viewModel.downloadModel() })
                }

                conversation.isEmpty() && inFlight == null && errorMessage == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyChat(tier = activeTier, onSuggestion = { send(it, fromComposer = false) })
                    }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val lastModelIndex = conversation.indexOfLast { it.role == Role.MODEL }
                    itemsIndexed(conversation, key = { index, _ -> index }) { index, message ->
                        if (message.role == Role.USER) {
                            UserBubble(message.text, message.imageUri, url = null, tier = activeTier)
                        } else {
                            AssistantMessage(
                                text = message.text,
                                tier = activeTier,
                                streaming = false,
                                // Solo compara lo que pidió ESTE turno con jsonMode activo — un turno viejo de
                                // antes de prenderlo no se marca como "inválido" retroactivamente.
                                extra = if (config.jsonMode && index == lastModelIndex && inFlight == null) {
                                    { JsonValidityBadge(message.text) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    inFlight?.let { turn ->
                        item(key = "inflight-user") { UserBubble(turn.text, turn.imageUri, turn.url, activeTier) }
                        if (errorMessage == null) {
                            item(key = "inflight-assistant") {
                                AssistantMessage(
                                    text = streamingText.orEmpty(),
                                    tier = activeTier,
                                    streaming = turnState !is DemoState.Result,
                                )
                            }
                        }
                    }
                    errorMessage?.let { message -> item(key = "error") { ErrorCard(message) } }
                }
            }
        }

        if (state.phase == ScreenPhase.READY_GROUP) {
            ChatComposer(
                value = promptText,
                onValueChange = { promptText = it },
                onSend = { send(promptText, fromComposer = true) },
                isGenerating = isGenerating || (inFlight != null && turnState == null),
                tier = activeTier,
                imageSupported = capabilities.images,
                pendingImageUri = pendingImage,
                attachedUrl = attachedUrl,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build(),
                    )
                },
                onClearImage = { viewModel.setPendingImage(null) },
                onAttachUrl = { viewModel.setAttachedUrl(it) },
                onClearUrl = { viewModel.setAttachedUrl(null) },
                // Antes AppNav le daba padding de la barra de navegación (y la consumía): imePadding solo
                // suma lo que el teclado agrega por encima de eso.
                modifier = Modifier.imePadding(),
            )
        }
    }

    if (showTierSheet) {
        TierPickerSheet(
            activeTier = activeTier,
            availability = availability,
            mediaPipeModels = viewModel.mediaPipeModels,
            selectedMediaPipeModel = selectedMediaPipeModel,
            onSelectTier = { tier -> viewModel.selectTier(tier) },
            onSelectMediaPipeModel = { viewModel.selectMediaPipeModel(it) },
            onDismiss = { showTierSheet = false },
        )
    }
}

@Composable
private fun ChatHeader(
    tier: InferenceTier,
    availability: TierAvailability,
    modelName: String?,
    canStartOver: Boolean,
    onOpenTierPicker: () -> Unit,
    onStartOver: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TierPill(tier = tier, availability = availability, modelName = modelName, onClick = onOpenTierPicker)
        IconButton(onClick = onStartOver, enabled = canStartOver) {
            Icon(
                Icons.Filled.EditNote,
                contentDescription = "Nueva conversación",
                tint = if (canStartOver) DemoColors.Ink else DemoColors.Muted.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Con "Modo JSON" activo, muestra si la última respuesta del modelo terminó siendo JSON válido —
 * el punto del control es justamente comparar qué tiers lo garantizan (Cloud/Firebase, vía
 * `responseMimeType`) y cuáles lo emulan por prompt (el resto, ver `TierCapabilities.structuredOutput`).
 * Parseo simple con `org.json` (ya en el SDK de Android, sin dependencias nuevas): alcanza para
 * distinguir "es JSON" de "no lo es", no valida un schema.
 */
@Composable
private fun JsonValidityBadge(responseText: String) {
    val isValidJson = remember(responseText) {
        runCatching { org.json.JSONTokener(responseText.trim()).nextValue() }.isSuccess
    }
    Text(
        if (isValidJson) "✓ JSON válido" else "✗ no es JSON válido",
        style = readoutStyle,
        color = if (isValidJson) DemoColors.TierA else DemoColors.Danger,
    )
}
