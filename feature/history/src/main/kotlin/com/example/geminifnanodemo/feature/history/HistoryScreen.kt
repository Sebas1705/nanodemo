package com.example.geminifnanodemo.feature.history

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.geminifnanodemo.core.designsystem.channelLetter
import com.example.geminifnanodemo.core.designsystem.label
import com.example.geminifnanodemo.core.designsystem.signalColor
import com.example.geminifnanodemo.core.ui.DemoColors
import com.example.geminifnanodemo.core.ui.readoutStyle
import com.example.geminifnanodemo.domain.models.Conversation
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

        if (conversations.isEmpty()) {
            Text(
                "Todavía no guardaste ninguna conversación. Empezá a chatear en " +
                    "Chat — cada conversación se guarda sola acá.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onOpenConversation(conversation.id) },
                    onDelete = { viewModel.delete(conversation.id) },
                )
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
