package es.sebas1705.nanodemo.feature.config

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.delay

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
            stringResource(R.string.config_intro),
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
            stringResource(R.string.config_editing, selectedTier.label(), selectedTier.channelLetter()),
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )

        var copied by remember(selectedTier) { mutableStateOf(false) }
        LaunchedEffect(copied) { if (copied) { delay(2_500); copied = false } }
        OutlinedButton(
            onClick = { viewModel.applyToAll(selectedTier); copied = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (copied) R.string.config_copied_to_all else R.string.config_apply_to_all))
        }
        Text(
            stringResource(R.string.config_apply_to_all_hint),
            style = MaterialTheme.typography.bodySmall,
            color = DemoColors.Muted,
        )

        SectionLabel(stringResource(R.string.config_section_generation))
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigSlider(
                label = stringResource(R.string.config_temperature),
                description = stringResource(R.string.config_temperature_desc),
                value = config.temperature ?: 1.0f,
                range = 0f..2f,
                enabled = capabilities.temperature,
                valueText = String.format(Locale.US, "%.2f", config.temperature ?: 1.0f),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(temperature = it)) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = stringResource(R.string.config_top_k),
                description = stringResource(R.string.config_top_k_desc),
                value = (config.topK ?: 40).toFloat(),
                range = 1f..100f,
                enabled = capabilities.topK,
                valueText = "${config.topK ?: 40}",
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(topK = it.toInt())) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = stringResource(R.string.config_top_p),
                description = stringResource(R.string.config_top_p_desc),
                value = config.topP ?: 0.95f,
                range = 0f..1f,
                enabled = capabilities.topP,
                valueText = String.format(Locale.US, "%.2f", config.topP ?: 0.95f),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(topP = it)) },
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigSlider(
                label = stringResource(R.string.config_max_tokens),
                description = stringResource(R.string.config_max_tokens_desc),
                value = (config.maxOutputTokens ?: 512).toFloat(),
                range = 16f..2048f,
                enabled = capabilities.maxOutputTokens,
                valueText = "${config.maxOutputTokens ?: 512}",
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(maxOutputTokens = it.toInt())) },
            )
        }

        SectionLabel(stringResource(R.string.config_section_instructions))
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = config.systemPrompt.orEmpty(),
                onValueChange = { viewModel.updateConfig(selectedTier, config.copy(systemPrompt = it.ifBlank { null })) },
                label = {
                    Text(stringResource(if (capabilities.systemPrompt) R.string.config_system_prompt else R.string.config_system_prompt_unsupported))
                },
                enabled = capabilities.systemPrompt,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.config_system_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }

        SectionLabel(stringResource(R.string.config_section_capabilities))
        AppCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfigToggle(
                title = stringResource(R.string.config_json_mode),
                supported = capabilities.structuredOutput,
                checked = config.jsonMode,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(jsonMode = it)) },
                description = stringResource(R.string.config_json_mode_desc),
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigToggle(
                title = stringResource(R.string.config_tools),
                supported = capabilities.functionCalling,
                checked = config.functionCalling,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(functionCalling = it)) },
                description = stringResource(R.string.config_tools_desc),
            )
            HorizontalDivider(color = DemoColors.Outline)
            ConfigToggle(
                title = stringResource(R.string.config_web_search),
                supported = capabilities.webSearch,
                checked = config.webSearch,
                onCheckedChange = { viewModel.updateConfig(selectedTier, config.copy(webSearch = it)) },
                description = stringResource(R.string.config_web_search_desc),
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
                if (supported) title else stringResource(R.string.config_unsupported_tier, title),
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
                if (enabled) label else stringResource(R.string.config_unsupported, label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) DemoColors.Ink else DemoColors.Muted,
            )
            Text(valueText, style = readoutStyle, color = DemoColors.Muted)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled)
        Text(description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }
}
