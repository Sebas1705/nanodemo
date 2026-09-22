package es.sebas1705.nanodemo.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import java.util.Locale

/**
 * Parámetros de generación (temperature/top-K/top-P/max tokens/system
 * prompt), a pantalla completa. Los controles se deshabilitan según lo que
 * soporte el tier activo en Chat — en Comparar, cada backend igual ignora
 * en silencio los campos que no soporta (ver TierCapabilities).
 */
@Composable
fun ConfigScreen(viewModel: ConfigViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    val capabilities = viewModel.capabilitiesOf(activeTier)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "PARÁMETROS",
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
        )
        Text(
            "Los controles reflejan lo que soporta el tier activo en Chat " +
                "(${activeTier.label()}). En Comparar, cada tier aplica solo lo suyo.",
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )

        ConfigSlider(
            label = "Temperature",
            description = "Aleatoriedad de la respuesta. Cerca de 0: siempre elige la palabra " +
                "más probable (predecible, repetitivo). Cerca de 2: se anima con opciones " +
                "menos probables (más creativo, pero puede desvariar).",
            value = config.temperature ?: 1.0f,
            range = 0f..2f,
            enabled = capabilities.temperature,
            valueText = String.format(Locale.US, "%.2f", config.temperature ?: 1.0f),
            onValueChange = { viewModel.updateConfig(config.copy(temperature = it)) },
        )
        ConfigSlider(
            label = "Top-K",
            description = "En cada palabra, el modelo solo puede elegir entre las K opciones " +
                "más probables. K bajo (ej. 5) = respuestas más conservadoras; K alto = más variedad.",
            value = (config.topK ?: 40).toFloat(),
            range = 1f..100f,
            enabled = capabilities.topK,
            valueText = "${config.topK ?: 40}",
            onValueChange = { viewModel.updateConfig(config.copy(topK = it.toInt())) },
        )
        ConfigSlider(
            label = "Top-P",
            description = "Parecido a Top-K, pero en vez de un número fijo de opciones, corta " +
                "cuando la probabilidad acumulada llega a P (ej. 0.95 = las opciones que " +
                "suman el 95% de probabilidad). Muchos backends combinan Top-K y Top-P a la vez.",
            value = config.topP ?: 0.95f,
            range = 0f..1f,
            enabled = capabilities.topP,
            valueText = String.format(Locale.US, "%.2f", config.topP ?: 0.95f),
            onValueChange = { viewModel.updateConfig(config.copy(topP = it)) },
        )
        ConfigSlider(
            label = "Max output tokens",
            description = "Tope de longitud de la respuesta, en tokens (~4 caracteres cada uno, " +
                "aproximado). El modelo puede terminar antes solo; esto es un techo, no un objetivo.",
            value = (config.maxOutputTokens ?: 512).toFloat(),
            range = 16f..2048f,
            enabled = capabilities.maxOutputTokens,
            valueText = "${config.maxOutputTokens ?: 512}",
            onValueChange = { viewModel.updateConfig(config.copy(maxOutputTokens = it.toInt())) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = config.systemPrompt.orEmpty(),
                onValueChange = { viewModel.updateConfig(config.copy(systemPrompt = it.ifBlank { null })) },
                label = {
                    Text(if (capabilities.systemPrompt) "System prompt" else "System prompt (no soportado en este tier)")
                },
                enabled = capabilities.systemPrompt,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Instrucción que se le da al modelo antes de cada prompt, para fijar su rol " +
                    "o estilo de respuesta (ej. \"Respondé siempre en 2 oraciones\").",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (capabilities.structuredOutput) "Modo JSON" else "Modo JSON (no soportado en este tier)",
                    style = readoutStyle,
                    color = if (capabilities.structuredOutput) DemoColors.Ink else DemoColors.Muted,
                )
                Switch(
                    checked = config.jsonMode,
                    onCheckedChange = { viewModel.updateConfig(config.copy(jsonMode = it)) },
                    enabled = capabilities.structuredOutput,
                )
            }
            Text(
                "Le pide al modelo que devuelva JSON en vez de texto libre. Solo Cloud y Firebase AI " +
                    "lo garantizan (responseMimeType del API); en Chat se marca si la respuesta terminó " +
                    "siendo JSON válido o no.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (enabled) label else "$label (no soportado)",
                style = readoutStyle,
                color = if (enabled) DemoColors.Ink else DemoColors.Muted,
            )
            Text(valueText, style = readoutStyle, color = DemoColors.Muted)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
        Text(description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }
}
