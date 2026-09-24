package es.sebas1705.nanodemo.feature.config

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import java.util.Locale

/**
 * Parámetros de generación (temperature/top-K/top-P/max tokens/system prompt). Cada uno de los
 * 5 tiers guarda su propia copia (`ConfigViewModel.configs: Map<InferenceTier, GenerationConfig>`)
 * — elegir un tier acá NO cambia el tier activo de Chat, solo cuáles parámetros se ven/editan.
 * Ya no es una pantalla propia con su propio scroll — vive dentro de la hoja "Ajustes"
 * (`feature:main/SettingsSheet.kt`), que aporta el scroll y el padding compartidos con
 * `DeviceScreen`.
 */
@Composable
fun ConfigScreen(viewModel: ConfigViewModel = hiltViewModel()) {
    val configs by viewModel.configs.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    var selectedTier by remember { mutableStateOf(activeTier) }
    val config = configs[selectedTier] ?: GenerationConfig()
    val capabilities = viewModel.capabilitiesOf(selectedTier)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Cada motor guarda sus propios parámetros — elegí uno para ver o cambiar los suyos.",
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InferenceTier.entries.forEach { tier ->
                TierChip(tier = tier, selected = tier == selectedTier, onClick = { selectedTier = tier })
            }
        }
        Text(
            "Editando ${selectedTier.label()} [${selectedTier.channelLetter()}]. En Comparar, " +
                "cada tier aplica solo lo suyo (y solo lo que soporta, ver TierCapabilities).",
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )

        SectionLabel("Generación")
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigSlider(
                label = "Temperature",
                description = "Aleatoriedad de la respuesta. Cerca de 0: siempre elige la palabra " +
                    "más probable (predecible, repetitivo). Cerca de 2: se anima con opciones " +
                    "menos probables (más creativo, pero puede desvariar).",
                value = config.temperature ?: 1.0f,
                range = 0f..2f,
                enabled = capabilities.temperature,
                valueText = String.format(Locale.US, "%.2f", config.temperature ?: 1.0f),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(temperature = it)) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = "Top-K",
                description = "En cada palabra, el modelo solo puede elegir entre las K opciones " +
                    "más probables. K bajo (ej. 5) = respuestas más conservadoras; K alto = más variedad.",
                value = (config.topK ?: 40).toFloat(),
                range = 1f..100f,
                enabled = capabilities.topK,
                valueText = "${config.topK ?: 40}",
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(topK = it.toInt())) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = "Top-P",
                description = "Parecido a Top-K, pero en vez de un número fijo de opciones, corta " +
                    "cuando la probabilidad acumulada llega a P (ej. 0.95 = las opciones que " +
                    "suman el 95% de probabilidad). Muchos backends combinan Top-K y Top-P a la vez.",
                value = config.topP ?: 0.95f,
                range = 0f..1f,
                enabled = capabilities.topP,
                valueText = String.format(Locale.US, "%.2f", config.topP ?: 0.95f),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(topP = it)) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = "Max output tokens",
                description = "Tope de longitud de la respuesta, en tokens (~4 caracteres cada uno, " +
                    "aproximado). El modelo puede terminar antes solo; esto es un techo, no un objetivo.",
                value = (config.maxOutputTokens ?: 512).toFloat(),
                range = 16f..2048f,
                enabled = capabilities.maxOutputTokens,
                valueText = "${config.maxOutputTokens ?: 512}",
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(maxOutputTokens = it.toInt())) },
            )
        }

        SectionLabel("Instrucciones")
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = config.systemPrompt.orEmpty(),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(systemPrompt = it.ifBlank { null })) },
                label = {
                    Text(if (capabilities.systemPrompt) "System prompt" else "System prompt (no soportado en este tier)")
                },
                enabled = capabilities.systemPrompt,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Instrucción que se le da al modelo antes de cada prompt, para fijar su rol " +
                    "o estilo de respuesta (ej. \"Respondé siempre en 2 oraciones\"). Se suma a un " +
                    "contexto fijo que ya le cuenta al modelo qué es AIMobiDemo, en los 5 tiers.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }

        SectionLabel("Capacidades")
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigToggle(
                title = "Modo JSON",
                supported = capabilities.structuredOutput,
                checked = config.jsonMode,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(jsonMode = it)) },
                description = "Le pide al modelo que devuelva JSON en vez de texto libre. Cloud y Firebase AI lo " +
                    "garantizan (responseMimeType del API); Nano/MediaPipe/LiteRT-LM lo emulan por " +
                    "prompt (sin garantía sintáctica, depende de que el modelo lo respete). En Chat se " +
                    "marca si la respuesta terminó siendo JSON válido o no, en los 5 tiers.",
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigToggle(
                title = "Herramientas",
                supported = capabilities.functionCalling,
                checked = config.functionCalling,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(functionCalling = it)) },
                description = "Deja que el modelo pida la hora actual en una zona horaria en vez de inventarla — " +
                    "no tiene reloj propio. Cloud y Firebase AI lo hacen nativo (la API tiene tool-calling " +
                    "real); Nano/MediaPipe/LiteRT-LM lo emulan por prompt (menos confiable: depende de que " +
                    "el modelo respete el formato pedido). En Chat se ve cuándo la usó.",
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigToggle(
                title = "Búsqueda en internet",
                supported = capabilities.webSearch,
                checked = config.webSearch,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(webSearch = it)) },
                description = "Busca en internet antes de responder, para preguntas actuales que el modelo no puede saber. " +
                    "Cloud/Firebase AI usan grounding nativo (Google Search de la propia API; el modelo decide " +
                    "cuándo). Nano/MediaPipe/LiteRT-LM no tienen red propia: con esto activo la app SIEMPRE " +
                    "busca (Brave Search API), lee un extracto de las primeras páginas y se lo pasa al modelo, que " +
                    "responde con formato, citando [1], [2] y con la lista de fuentes clickeable abajo. Hace falta " +
                    "configurar BRAVE_SEARCH_API_KEY, si no queda deshabilitada. Si también prendiste Herramientas, " +
                    "esta gana (no se probaron juntas).",
            )
        }
    }
}

@Composable
private fun ConfigToggle(
    title: String,
    supported: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (supported) title else "$title (no soportado en este tier)",
                style = MaterialTheme.typography.bodyLarge,
                color = if (supported) DemoColors.Ink else DemoColors.Muted,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = supported)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
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
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) DemoColors.Ink else DemoColors.Muted,
            )
            Text(valueText, style = readoutStyle, color = DemoColors.Muted)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
        Text(description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }
}
