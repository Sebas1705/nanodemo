package es.sebas1705.nanodemo.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.EmptyState
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.Conversation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

/** Historial: conversaciones guardadas localmente, más nuevas primero — tocar una la reabre en Chat. */
@Composable
fun HistoryScreen(
    onOpenConversation: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    val searchState by viewModel.searchState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }

    // Con una búsqueda activa se muestran sus resultados (posiblemente vacíos); si no, la lista
    // cronológica de siempre — son dos modos, no un filtro sobre la misma lista.
    val shownConversations = searchResults ?: conversations
    val isSearchActive = searchResults != null

    Column(modifier = Modifier.fillMaxSize()) {
        SemanticSearchBar(
            query = query,
            onQueryChange = { query = it },
            searchState = searchState,
            isSearchActive = isSearchActive,
            onSearch = { viewModel.search(query) },
            onClear = { query = ""; viewModel.clearSearch() },
            onDownloadModel = { viewModel.downloadSearchModel() },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )

        when {
            conversations.isEmpty() && !isSearchActive -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "Todavía no hay conversaciones",
                    message = "Empezá a chatear: cada conversación se guarda sola acá y la podés retomar cuando quieras.",
                )
            }

            isSearchActive && shownConversations.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "Sin coincidencias",
                    message = "Ninguna conversación se parece a \"$query\".",
                )
            }

            else -> {
                // Agrupar por fecha (como el historial de otros chats de IA) solo en la vista cronológica:
                // en una búsqueda el orden es por parecido, y los encabezados de fecha no tendrían sentido.
                val groups = remember(shownConversations, isSearchActive) {
                    if (isSearchActive) {
                        listOf<Pair<String?, List<Conversation>>>(null to shownConversations)
                    } else {
                        val today = LocalDate.now()
                        shownConversations.groupBy { dateGroup(it.updatedAtMs, today) }.toList()
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // El respiro de abajo va como contentPadding: viaja con el scroll en vez de ser un hueco
                    // fijo — así el último ítem no queda pegado a la barra de navegación al llegar al final.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (header, items) ->
                        if (header != null) {
                            item(key = "header-$header") { SectionLabel(header, Modifier.padding(top = 8.dp, start = 4.dp)) }
                        }
                        items(items, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                showDay = header == null,
                                onClick = { onOpenConversation(conversation.id) },
                                onDelete = { viewModel.delete(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dateGroup(updatedAtMs: Long, today: LocalDate): String {
    val date = Instant.ofEpochMilli(updatedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (ChronoUnit.DAYS.between(date, today)) {
        in Long.MIN_VALUE..0L -> "Hoy"
        1L -> "Ayer"
        in 2L..6L -> "Esta semana"
        in 7L..29L -> "Este mes"
        else -> "Antes"
    }
}

/**
 * Búsqueda semántica ("¿qué le pregunté sobre X?"), no por coincidencia literal de texto — 100%
 * on-device vía `LocalEmbeddingService` (MediaPipe TextEmbedder). Antes de descargar su modelo
 * (~6MB, aparte de cualquier modelo de los tiers de IA) el buscador queda deshabilitado con un
 * botón para bajarlo, mismo patrón que "Descargar modelo" en Chat.
 */
@Composable
private fun SemanticSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchState: SemanticSearchState,
    isSearchActive: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (searchState) {
        SemanticSearchState.Ready -> {
            val shape = RoundedCornerShape(50)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(DemoColors.Panel)
                    .border(1.dp, DemoColors.Outline, shape)
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = DemoColors.Muted, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = DemoColors.Ink),
                    cursorBrush = SolidColor(DemoColors.TierA),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Buscar por significado…", style = MaterialTheme.typography.bodyLarge, color = DemoColors.Muted)
                            }
                            inner()
                        }
                    },
                )
                if (isSearchActive || query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda", tint = DemoColors.Muted)
                    }
                }
            }
        }

        SemanticSearchState.NotDownloaded -> AppCard(modifier = modifier.fillMaxWidth()) {
            Text(
                "Buscar por significado (no por texto exacto) necesita un modelo aparte, ~6MB.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            Button(onClick = onDownloadModel) { Text("Descargar modelo de búsqueda") }
        }

        is SemanticSearchState.Downloading -> AppCard(modifier = modifier.fillMaxWidth()) {
            val percent = searchState.progressPercent
            if (percent != null) {
                Text("Descargando modelo de búsqueda… $percent%", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Descargando modelo de búsqueda…", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                }
            }
        }

        is SemanticSearchState.Error -> AppCard(modifier = modifier.fillMaxWidth()) {
            Text("Error: ${searchState.message}", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Danger)
            Button(onClick = onDownloadModel) { Text("Reintentar descarga") }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    showDay: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = Instant.ofEpochMilli(conversation.updatedAtMs)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierAvatar(conversation.tier, 36.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(conversation.title, color = DemoColors.Ink, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    "${conversation.tier.label()} · " +
                        "${(if (showDay) DATE_FORMATTER else TIME_FORMATTER).format(time)} · ${conversation.messageCount} mensajes",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar conversación", tint = DemoColors.Muted)
            }
        }
    }
}
