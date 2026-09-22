package es.sebas1705.nanodemo.feature.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PROMPT_BANK

/** Comparar: corre el mismo prompt en los 3 tiers a la vez, más el banco de prompts / benchmark. */
@Composable
fun CompareScreen(
    sizeClass: ScreenSizeClass,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val compareStates by viewModel.compareStates.collectAsState()
    var promptText by remember { mutableStateOf("Explícame en una frase qué es un agente de IA.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PromptBankRow(onPresetSelected = { promptText = it })

        val tierCount = InferenceTier.entries.size
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Prompt (se manda a los $tierCount tiers a la vez)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { viewModel.generateCompare(promptText) }, modifier = Modifier.fillMaxWidth()) {
            Text("Generar en los $tierCount tiers")
        }

        // Solo corren los tiers ya comprobados como disponibles (se prueban desde Chat);
        // los demás muestran "No disponible".
        AdaptiveGrid(items = InferenceTier.entries, columns = sizeClass.tierColumns) { tier, cardModifier ->
            CompareCard(tier = tier, tierState = compareStates[tier], modifier = cardModifier)
        }

        Button(
            onClick = { viewModel.runBenchmark() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Correr banco de prompts completo (${PROMPT_BANK.size} presets × $tierCount tiers)")
        }
    }
}

@Composable
private fun PromptBankRow(onPresetSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PROMPT_BANK) { preset ->
            AssistChip(onClick = { onPresetSelected(preset.text) }, label = { Text(preset.name) })
        }
    }
}

@Composable
private fun CompareCard(tier: InferenceTier, tierState: DemoState?, modifier: Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, tier.signalColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .background(DemoColors.Panel, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "[${tier.channelLetter()}] ${tier.label()}",
                style = readoutStyle,
                color = tier.signalColor,
                fontWeight = FontWeight.Bold,
            )
            when (tierState) {
                is DemoState.Generating -> Text(tierState.partialText, color = DemoColors.Ink)
                is DemoState.Result -> Text(tierState.text, color = DemoColors.Ink)
                is DemoState.Error -> Text("Error: ${tierState.message}", color = DemoColors.Danger)
                is DemoState.Unavailable -> Text("No disponible", color = DemoColors.Muted)
                else -> Text("Esperando…", color = DemoColors.Muted)
            }
        }
    }
}
