package es.sebas1705.nanodemo.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.common.SEARCH_CONTEXT_OVERHEAD_TOKENS
import es.sebas1705.nanodemo.core.common.SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT
import es.sebas1705.nanodemo.core.ui.DemoColors

private const val CHARS_PER_TOKEN = 4

// Mismo umbral que el contador de cuota: ámbar desde el 80%, rojo al llenarse.
private const val WARN_FRACTION = 0.8f

/**
 * Estimación de cuánto del contexto del modelo ya se usó: el texto de la conversación más el system prompt
 * (que MediaPipe/LiteRT-LM guardan una vez al crear la sesión), a razón de ~4 caracteres por token. Con
 * búsqueda activa cada turno del usuario suma además el bloque de resultados (no está en el transcript, pero sí
 * en el contexto del motor). Es una estimación ("~"): el motor no expone cuántos tokens lleva acumulados.
 */
internal fun estimateContextTokens(
    turnTexts: List<String>,
    systemPromptChars: Int,
    userTurns: Int,
    searchOn: Boolean,
    compactSearch: Boolean,
): Int {
    val textTokens = (turnTexts.sumOf { it.length } + systemPromptChars) / CHARS_PER_TOKEN
    val perSearchTurn = if (compactSearch) SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT else SEARCH_CONTEXT_OVERHEAD_TOKENS
    return textTokens + if (searchOn) userTurns * perSearchTurn else 0
}

internal fun contextFraction(usedTokens: Int, maxTokens: Int): Float =
    if (maxTokens <= 0) 1f else (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f)

/**
 * Barra "~620 / 1024 tokens de contexto" arriba de la entrada, solo para los motores con un presupuesto chico
 * y conocido (`TierCapabilities.contextTokens`). Explica por qué un modelo chico se rompe en conversaciones
 * largas: no es que "se vuelva tonto", es que se le acabó el contexto.
 */
@Composable
internal fun ContextMeter(usedTokens: Int, maxTokens: Int, modifier: Modifier = Modifier) {
    val fraction = contextFraction(usedTokens, maxTokens)
    val color = when {
        fraction >= 1f -> DemoColors.Danger
        fraction >= WARN_FRACTION -> DemoColors.TierB
        else -> DemoColors.Muted
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = DemoColors.Panel,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (fraction >= 1f) stringResource(R.string.chat_context_full, usedTokens, maxTokens) else stringResource(R.string.chat_context_tokens, usedTokens, maxTokens),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
