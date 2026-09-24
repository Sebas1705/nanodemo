package es.sebas1705.nanodemo.feature.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.PromptBar
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.TypingDots
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PROMPT_BANK

/**
 * Comparar: el mismo prompt en los 5 motores a la vez. Mismo esquema que el Chat — respuestas arriba (una
 * tarjeta por motor, en streaming), barra de entrada abajo — y el banco de prompts como sugerencias.
 */
@Composable
fun CompareScreen(
    sizeClass: ScreenSizeClass,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val compareStates by viewModel.compareStates.collectAsState()
    var promptText by remember { mutableStateOf("") }
    var sentPrompt by remember { mutableStateOf<String?>(null) }
    val isBusy = compareStates.values.any { it is DemoState.Generating }
    val accent = DemoColors.TierA

    fun run(text: String, fromComposer: Boolean) {
        val prompt = text.trim()
        if (prompt.isEmpty() || isBusy) return
        sentPrompt = prompt
        if (fromComposer) promptText = ""
        viewModel.generateCompare(prompt)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val prompt = sentPrompt
            if (prompt == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyCompare(onPreset = { run(it, fromComposer = false) })
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PromptBubble(prompt)
                    // Solo corren los tiers ya comprobados como disponibles (se prueban desde Chat);
                    // los demás muestran "No disponible".
                    AdaptiveGrid(items = InferenceTier.entries, columns = sizeClass.tierColumns) { tier, cardModifier ->
                        CompareCard(tier = tier, tierState = compareStates[tier], modifier = cardModifier)
                    }
                }
            }
        }

        if (sentPrompt != null) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PROMPT_BANK) { preset -> Pill(preset.name) { promptText = preset.text } }
            }
        }

        PromptBar(
            value = promptText,
            onValueChange = { promptText = it },
            onSend = { run(promptText, fromComposer = true) },
            accent = accent,
            isBusy = isBusy,
            placeholder = "Mismo prompt a los ${InferenceTier.entries.size} motores…",
            modifier = Modifier.imePadding(),
            leading = {
                TextButton(
                    onClick = {
                        sentPrompt = "Benchmark: banco de prompts completo (${PROMPT_BANK.size})"
                        viewModel.runBenchmark()
                    },
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Benchmark (${PROMPT_BANK.size} prompts)", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/** Bienvenida: qué hace la pantalla + el banco de prompts como tarjetas que se mandan al toque. */
@Composable
private fun EmptyCompare(onPreset: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            InferenceTier.entries.forEach { tier -> TierAvatar(tier, 40.dp) }
        }
        Text(
            "Compará los ${InferenceTier.entries.size} motores",
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Mandá el mismo prompt a todos a la vez y mirá cuál responde mejor y más rápido.",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PROMPT_BANK.forEach { preset ->
                AppCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    onClick = { onPreset(preset.text) },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(preset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = DemoColors.Ink)
                    Text(
                        preset.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = DemoColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = DemoColors.Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DemoColors.Panel)
            .border(1.dp, DemoColors.Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** El prompt enviado, como burbuja a la derecha — igual que un turno del usuario en el Chat. */
@Composable
private fun PromptBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = DemoColors.Ink,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(DemoColors.TierA.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CompareCard(tier: InferenceTier, tierState: DemoState?, modifier: Modifier) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TierAvatar(tier, 28.dp)
            Text(
                tier.label(),
                style = MaterialTheme.typography.titleMedium,
                color = DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            val status = when (tierState) {
                is DemoState.Generating -> "escribiendo…"
                is DemoState.Result -> "listo"
                else -> null
            }
            status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tier.signalColor) }
        }
        when (tierState) {
            is DemoState.Generating ->
                if (tierState.partialText.isBlank()) TypingDots(tier.signalColor) else MarkdownText(tierState.partialText.trim() + " ▍")
            is DemoState.Result -> MarkdownText(tierState.text.trim())
            is DemoState.Error -> Text("Error: ${tierState.message}", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Danger)
            is DemoState.Unavailable -> Text("No disponible", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
            else -> Text("Esperando…", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
    }
}
