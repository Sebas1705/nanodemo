package es.sebas1705.nanodemo.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.QuotaState
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle

/**
 * "Uso y límites": cuánto llevás gastado de cada plan gratis (Gemini en Cloud/Firebase, Brave para la
 * búsqueda de los tiers locales) y, opcionalmente, un freno al llegar al tope. Los topes son editables:
 * Google no publica el número exacto del plan gratis de Gemini (se ve en AI Studio) — ver `QuotaService`.
 */
@Composable
fun UsageLimitsScreen(viewModel: UsageLimitsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val states by viewModel.states.collectAsState()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Contador local de lo que gastás de cada plan gratis. Es solo de esta app y de este dispositivo: " +
                "no reemplaza los topes de gasto de las consolas de Google/Brave.",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
        )
        states.values.forEach { state ->
            QuotaCard(
                state = state,
                onLimitChange = { viewModel.setLimit(state.service, it) },
                onEnforcedChange = { viewModel.setEnforced(state.service, it) },
                onReset = { viewModel.reset(state.service) },
            )
        }
        NoteCard(
            icon = Icons.Filled.Info,
            tint = DemoColors.Muted,
            text = "Sin límite: Nano, MediaPipe y LiteRT-LM (on-device), pegar un link y la búsqueda " +
                "semántica del historial corren en el teléfono y no gastan cuota.",
        )
        NoteCard(
            icon = Icons.Filled.WarningAmber,
            tint = DemoColors.TierB,
            text = "Búsqueda en internet en Cloud/Firebase AI (grounding con Google Search): no está incluida en " +
                "el plan gratis de Gemini — hace falta plan pago (5.000 búsquedas/mes gratis, después $14 cada " +
                "1.000). Sin plan pago, prender ese toggle en esos tiers puede fallar.",
        )
    }
}

@Composable
private fun QuotaCard(
    state: QuotaState,
    onLimitChange: (Int) -> Unit,
    onEnforcedChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val service = state.service
    val barColor = when {
        state.reached -> DemoColors.Danger
        state.nearLimit -> DemoColors.TierB
        else -> DemoColors.TierA
    }
    AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                service.label,
                style = MaterialTheme.typography.bodyLarge,
                color = DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Text("${state.used} / ${state.limit}", style = readoutStyle, color = barColor)
        }
        LinearProgressIndicator(
            progress = { state.fraction },
            color = barColor,
            trackColor = DemoColors.Background,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                state.reached && state.enforced -> "Límite alcanzado — bloqueado hasta ${service.period.resetLabel}."
                state.reached -> "Límite alcanzado (solo aviso: el bloqueo está desactivado)."
                else -> "${service.period.perLabel.replaceFirstChar { it.uppercase() }} · se reinicia ${service.period.resetLabel}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.reached) DemoColors.Danger else DemoColors.Muted,
        )
        Text(service.note, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Bloquear al llegar al límite", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            Switch(checked = state.enforced, onCheckedChange = onEnforcedChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var text by remember(state.limit) { mutableStateOf(state.limit.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(6)
                    text = digits
                    digits.toIntOrNull()?.takeIf { it > 0 }?.let(onLimitChange)
                },
                label = { Text("Límite ${service.period.perLabel}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(170.dp),
            )
            TextButton(onClick = onReset) { Text("Reiniciar contador") }
        }
    }
}

@Composable
private fun NoteCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        }
    }
}
