package es.sebas1705.nanodemo.feature.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.PromptBar
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.StatusIndicator
import es.sebas1705.nanodemo.core.designsystem.TypingDots
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.TierChannelRow
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.downloadableMessage
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.unavailableMessage
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.LiteRtLmAccelerator
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierAvailability

private const val MAX_IMAGE_SIDE_PX = 1024

/** Decodifica la imagen achicada (lado mayor ≤ 1024px): una foto de cámara completa son decenas de MB en RAM. */
@Composable
internal fun rememberImageBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE_PX) sample *= 2
                context.contentResolver.openInputStream(it)?.use { s ->
                    BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Selector compacto del motor activo (reemplaza a las 5 filas fijas de arriba): abre `TierPickerSheet`. */
@Composable
internal fun TierPill(
    tier: InferenceTier,
    availability: TierAvailability,
    modelName: String?,
    onClick: () -> Unit,
) {
    val color = tier.signalColor
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DemoColors.Panel)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TierAvatar(tier, 28.dp)
        Column {
            Text(
                tier.label(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DemoColors.Ink,
            )
            modelName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted) }
        }
        StatusIndicator(availability, color)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = DemoColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierPickerSheet(
    activeTier: InferenceTier,
    availability: Map<InferenceTier, TierAvailability>,
    mediaPipeModels: List<MediaPipeModelOption>,
    selectedMediaPipeModel: MediaPipeModelOption,
    onSelectTier: (InferenceTier) -> Unit,
    onSelectMediaPipeModel: (MediaPipeModelOption) -> Unit,
    liteRtLmAccelerator: LiteRtLmAccelerator,
    onSelectLiteRtLmAccelerator: (LiteRtLmAccelerator) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DemoColors.Panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Elegí con qué motor hablar", style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
            InferenceTier.entries.forEach { tier ->
                TierChannelRow(
                    tier = tier,
                    isActive = tier == activeTier,
                    availability = availability[tier] ?: TierAvailability.UNKNOWN,
                    onClick = {
                        onSelectTier(tier)
                        // MediaPipe y LiteRT-LM dejan la hoja abierta: justo abajo aparece su selector (modelo / CPU-GPU).
                        if (tier != InferenceTier.MEDIAPIPE_LOCAL && tier != InferenceTier.LITERT_LM_LOCAL) onDismiss()
                    },
                )
            }
            if (activeTier == InferenceTier.MEDIAPIPE_LOCAL) {
                MediaPipeModelRow(mediaPipeModels, selectedMediaPipeModel, onSelectMediaPipeModel)
            }
            if (activeTier == InferenceTier.LITERT_LM_LOCAL) {
                LiteRtLmAcceleratorRow(liteRtLmAccelerator, onSelectLiteRtLmAccelerator)
            }
        }
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
        SectionLabel("Modelo")
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

// ---------------------------------------------------------------------------------------------
// Mensajes
// ---------------------------------------------------------------------------------------------

/** Turno del usuario: burbuja a la derecha, teñida con el color del canal (imagen y/o link arriba del texto). */
@Composable
internal fun UserBubble(text: String, imageUri: Uri?, url: String?, tier: InferenceTier) {
    val bitmap = rememberImageBitmap(imageUri)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(tier.signalColor.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Imagen adjunta",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            url?.let { AttachmentPill(icon = Icons.Filled.Link, label = it, onClear = null) }
            if (text.isNotBlank()) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), color = DemoColors.Ink)
                }
            }
        }
    }
}

/** Marcadores que los backends meten en el texto de la respuesta (ver `CloudGeminiBackend`/`resolveLocalToolCall`). */
internal data class AssistantParts(val tool: String?, val body: String, val footer: String?)

private val TOOL_MARKER = Regex("^🔧 ([^\n]*)\n*")
private val GROUNDING_FOOTER = Regex("\n*🔎 ([^\n]*)$")

internal fun splitAssistantText(raw: String): AssistantParts {
    var body = raw
    var tool: String? = null
    var footer: String? = null
    TOOL_MARKER.find(body)?.let {
        tool = it.groupValues[1].trim()
        body = body.substring(it.range.last + 1)
    }
    GROUNDING_FOOTER.find(body)?.let {
        footer = it.groupValues[1].trim()
        body = body.substring(0, it.range.first)
    }
    return AssistantParts(tool, body, footer)
}

/**
 * Turno del modelo: sin burbuja, a todo el ancho (como ChatGPT/Claude/Gemini), con el avatar del canal
 * a la izquierda. `streaming` agrega el cursor (o los puntitos de "pensando" si todavía no llegó texto).
 */
@Composable
internal fun AssistantMessage(
    text: String,
    tier: InferenceTier,
    streaming: Boolean,
    extra: @Composable (() -> Unit)? = null,
) {
    val parts = remember(text) { splitAssistantText(text) }
    val clipboard = LocalClipboardManager.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TierAvatar(tier, 28.dp, Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.tool?.let { AttachmentPill(icon = Icons.Filled.Build, label = it, onClear = null, tint = tier.signalColor) }
            when {
                parts.body.isNotBlank() -> SelectionContainer {
                    MarkdownText(parts.body.trim() + if (streaming) " ▍" else "")
                }
                streaming && parts.tool == null -> TypingDots(tier.signalColor)
            }
            parts.footer?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted) }
            extra?.invoke()
            if (!streaming && parts.body.isNotBlank()) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(parts.body.trim())) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar respuesta",
                        tint = DemoColors.Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Chip chico y de solo lectura: herramienta usada, link adjunto, imagen. Con `onClear` se puede quitar. */
@Composable
internal fun AttachmentPill(
    icon: ImageVector,
    label: String,
    onClear: (() -> Unit)?,
    tint: Color = DemoColors.Muted,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(start = 10.dp, end = if (onClear != null) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = DemoColors.Muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Estados vacíos / no listo
// ---------------------------------------------------------------------------------------------

private data class Suggestion(val icon: ImageVector, val text: String)

private val SUGGESTIONS = listOf(
    Suggestion(Icons.Filled.Info, "¿Qué es esta app y qué tiers tiene?"),
    Suggestion(Icons.Filled.Schedule, "¿Qué hora es ahora en Tokio?"),
    Suggestion(Icons.Filled.Code, "Dame un JSON con 3 países y sus capitales"),
    Suggestion(Icons.Filled.Lightbulb, "Explícame en una frase qué es un agente de IA"),
)

/** Pantalla de bienvenida: saludo + sugerencias que de paso muestran las features (contexto, herramientas, JSON). */
@Composable
internal fun EmptyChat(tier: InferenceTier, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TierAvatar(tier, 64.dp)
        Spacer(Modifier.size(8.dp))
        Text("¿En qué te ayudo?", style = MaterialTheme.typography.headlineSmall, color = DemoColors.Ink)
        Text(
            "Hablando con ${tier.label()}",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        SUGGESTIONS.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DemoColors.Panel)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clickable { onSuggestion(suggestion.text) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(suggestion.icon, contentDescription = null, tint = tier.signalColor, modifier = Modifier.size(20.dp))
                Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            }
        }
    }
}

/** El motor todavía no está listo (comprobando / no disponible / descargable / descargando). */
@Composable
internal fun TierStatusPanel(state: DemoState, tier: InferenceTier, onDownload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TierAvatar(tier, 56.dp)
        when (state) {
            is DemoState.CheckingAvailability -> {
                Text("Comprobando disponibilidad de ${tier.label()}…", color = DemoColors.Ink, textAlign = TextAlign.Center)
                CircularProgressIndicator(color = tier.signalColor)
            }
            is DemoState.Unavailable ->
                Text(tier.unavailableMessage(), color = DemoColors.Ink, textAlign = TextAlign.Center)
            is DemoState.Downloadable -> {
                Text(tier.downloadableMessage(), color = DemoColors.Ink, textAlign = TextAlign.Center)
                Button(onClick = onDownload) { Text("Descargar modelo") }
            }
            is DemoState.Downloading -> {
                val percent = state.progressPercent
                if (percent != null) {
                    Text("Descargando ${tier.label()}… $percent%", color = DemoColors.Ink)
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        color = tier.signalColor,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
                    )
                } else {
                    Text("Preparando ${tier.label()}… (puede tardar)", color = DemoColors.Ink)
                    CircularProgressIndicator(color = tier.signalColor)
                }
            }
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------------------------

/**
 * Barra de entrada tipo ChatGPT/Claude: campo en píldora que crece hasta 6 líneas, "+" para adjuntar
 * (imagen / link) y botón redondo de enviar. Los adjuntos pendientes viajan como chips arriba del texto.
 */
@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    tier: InferenceTier,
    imageSupported: Boolean,
    pendingImageUri: Uri?,
    attachedUrl: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onAttachUrl: (String) -> Unit,
    onClearUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var urlDialogOpen by remember { mutableStateOf(false) }
    val thumbnail = rememberImageBitmap(pendingImageUri)

    PromptBar(
        value = value,
        onValueChange = onValueChange,
        onSend = onSend,
        accent = tier.signalColor,
        isBusy = isGenerating,
        placeholder = "Escribe un mensaje…",
        modifier = modifier,
        topContent = if (thumbnail != null || attachedUrl != null) {
            {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    thumbnail?.let {
                        Box {
                            Image(
                                bitmap = it,
                                contentDescription = "Imagen a adjuntar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            IconButton(
                                onClick = onClearImage,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar imagen", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    attachedUrl?.let { AttachmentPill(icon = Icons.Filled.Link, label = it, onClear = onClearUrl) }
                }
            }
        } else {
            null
        },
        leading = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Adjuntar", tint = DemoColors.Ink)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (imageSupported) {
                        DropdownMenuItem(
                            text = { Text("Adjuntar imagen") },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            onClick = { menuOpen = false; onPickImage() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Pegar un link") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        onClick = { menuOpen = false; urlDialogOpen = true },
                    )
                }
            }
        },
    )

    if (urlDialogOpen) {
        UrlDialog(
            onDismiss = { urlDialogOpen = false },
            onConfirm = { onAttachUrl(it); urlDialogOpen = false },
        )
    }
}

/** "Pegar un link y preguntar": la app descarga la página y la manda de contexto al generar — sirve en los 5 tiers. */
@Composable
private fun UrlDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pegar un link") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.trim()) }, enabled = input.isNotBlank()) { Text("Adjuntar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** CPU o GPU para LiteRT-LM: cambiarlo recrea el motor (y reinicia la charla). Si la GPU no inicializa, vuelve a CPU sola. */
@Composable
private fun LiteRtLmAcceleratorRow(
    selected: LiteRtLmAccelerator,
    onSelect: (LiteRtLmAccelerator) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Acelerador")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiteRtLmAccelerator.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.displayName) },
                )
            }
        }
        Text(
            "Cambiarlo recarga el modelo y empieza una conversación nueva. GPU suele ser más rápida pero no anda en todos los dispositivos; si falla, vuelve a CPU.",
            style = MaterialTheme.typography.labelSmall,
            color = DemoColors.Muted,
        )
    }
}
