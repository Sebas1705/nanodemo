package es.sebas1705.nanodemo.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.Conversation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "HISTORIAL",
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
        )

        SemanticSearchBar(
            query = query,
            onQueryChange = { query = it },
            searchState = searchState,
            isSearchActive = isSearchActive,
            onSearch = { viewModel.search(query) },
            onClear = { query = ""; viewModel.clearSearch() },
            onDownloadModel = { viewModel.downloadSearchModel() },
        )

        if (conversations.isEmpty() && !isSearchActive) {
            Text(
                "Todavía no guardaste ninguna conversación. Empezá a chatear en " +
                    "Chat — cada conversación se guarda sola acá.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            return@Column
        }

        if (isSearchActive && shownConversations.isEmpty()) {
            Text(
                "Ninguna conversación se parece a \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shownConversations, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onOpenConversation(conversation.id) },
                    onDelete = { viewModel.delete(conversation.id) },
                )
            }
        }
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (searchState) {
            SemanticSearchState.NotDownloaded -> {
                Text(
                    "Buscar por significado (no por texto exacto) necesita un modelo aparte, ~6MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
                Button(onClick = onDownloadModel) { Text("Descargar modelo de búsqueda") }
            }

            is SemanticSearchState.Downloading -> {
                val percent = searchState.progressPercent
                if (percent != null) {
                    Text("Descargando modelo de búsqueda… $percent%", style = readoutStyle, color = DemoColors.Ink)
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        Text("Descargando modelo de búsqueda…", style = readoutStyle, color = DemoColors.Ink)
                    }
                }
            }

            is SemanticSearchState.Error -> {
                Text("Error: ${searchState.message}", color = DemoColors.Danger, style = readoutStyle)
                Button(onClick = onDownloadModel) { Text("Reintentar descarga") }
            }

            SemanticSearchState.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("Buscar por significado…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onSearch, enabled = query.isNotBlank()) { Text("Buscar") }
                }
                if (isSearchActive) {
                    TextButton(onClick = onClear) { Text("Ver todo") }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, conversation.tier.signalColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "[${conversation.tier.channelLetter()}]",
                style = readoutStyle,
                color = conversation.tier.signalColor,
                fontWeight = FontWeight.Bold,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(conversation.title, color = DemoColors.Ink, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${conversation.tier.label()} · ${TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(conversation.updatedAtMs))} " +
                        "· ${conversation.messageCount} mensajes",
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar conversación", tint = DemoColors.Muted)
            }
        }
    }
}
