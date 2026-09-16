package com.example.geminifnanodemo.feature.compare

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
import androidx.compose.material3.MaterialTheme
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
import com.example.geminifnanodemo.core.designsystem.ScreenSizeClass
import com.example.geminifnanodemo.core.designsystem.channelLetter
import com.example.geminifnanodemo.core.designsystem.label
import com.example.geminifnanodemo.core.designsystem.signalColor
import com.example.geminifnanodemo.core.ui.DemoColors
import com.example.geminifnanodemo.core.ui.readoutStyle
import com.example.geminifnanodemo.domain.models.DemoState
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.PROMPT_BANK

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
        Text(
            text = "COMPARAR",
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
        )

        PromptBankRow(onPresetSelected = { promptText = it })

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Prompt (se manda a los 3 tiers a la vez)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { viewModel.generateCompare(promptText) }, modifier = Modifier.fillMaxWidth()) {
            Text("Generar en los 3 tiers")
        }

        if (sizeClass == ScreenSizeClass.COMPACT) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InferenceTier.entries.forEach { tier ->
                    CompareCard(tier = tier, tierState = compareStates[tier], modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InferenceTier.entries.forEach { tier ->
                    CompareCard(tier = tier, tierState = compareStates[tier], modifier = Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = { viewModel.runBenchmark() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Correr banco de prompts completo (${PROMPT_BANK.size} presets × 3 tiers)")
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
