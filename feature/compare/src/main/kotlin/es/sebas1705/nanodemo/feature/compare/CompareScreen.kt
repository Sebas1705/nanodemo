package es.sebas1705.nanodemo.feature.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.MetricsLine
import es.sebas1705.nanodemo.core.designsystem.PromptBar
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.TypingDots
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier

/**
 * Comparar: el mismo prompt en los 5 motores a la vez. Mismo esquema que el Chat — respuestas arriba (una
 * tarjeta por motor, en streaming), barra de entrada abajo — con los prompts recomendados para pruebas en
 * un desplegable (elegir uno lo carga en el campo para revisarlo/editarlo antes de enviarlo).
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
    val allMetrics by viewModel.metrics.collectAsState()
    // Cada ronda es independiente: el voto y las métricas se atan a la hora en que arrancó (ver metricsForRun).
    var runStartedAtMs by remember { mutableLongStateOf(0L) }
    var votedTier by remember { mutableStateOf<InferenceTier?>(null) }

    fun run() {
        val prompt = promptText.trim()
        if (prompt.isEmpty() || isBusy) return
        sentPrompt = prompt
        promptText = ""
        votedTier = null
        runStartedAtMs = System.currentTimeMillis()
        viewModel.generateCompare(prompt)
    }

    val roundMetrics = if (sentPrompt != null) metricsForRun(allMetrics, runStartedAtMs) else emptyMap()
    val badges = compareBadges(roundMetrics)
    val answered = compareStates.filterValues { it is DemoState.Result }.keys
    // Votar solo tiene sentido cuando la ronda terminó y hay al menos 2 respuestas para elegir.
    val canVote = !isBusy && answered.size >= 2

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val prompt = sentPrompt
            if (prompt == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyCompare() }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PromptBubble(prompt)
                    if (canVote && votedTier == null) {
                        Text(
                            stringResource(R.string.compare_vote_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = DemoColors.Muted,
                        )
                    }
                    // Solo corren los tiers ya comprobados como disponibles (se prueban desde Chat);
                    // los demás muestran "No disponible".
                    AdaptiveGrid(items = InferenceTier.entries, columns = sizeClass.tierColumns) { tier, cardModifier ->
                        CompareCard(
                            tier = tier,
                            tierState = compareStates[tier],
                            metric = roundMetrics[tier],
                            badges = badges[tier].orEmpty(),
                            voteStatus = when {
                                !canVote -> VoteStatus.NOT_VOTABLE
                                votedTier == tier -> VoteStatus.CHOSEN
                                else -> VoteStatus.VOTABLE
                            },
                            onVote = {
                                viewModel.voteBest(tier, answered, votedTier)
                                votedTier = tier
                            },
                            modifier = cardModifier,
                        )
                    }
                }
            }
        }

        PromptBar(
            value = promptText,
            onValueChange = { promptText = it },
            onSend = ::run,
            accent = DemoColors.TierA,
            isBusy = isBusy,
            placeholder = stringResource(R.string.compare_prompt_placeholder, InferenceTier.entries.size),
            modifier = Modifier.imePadding(),
            leading = { RecommendedPrompts(onPick = { promptText = it }) },
        )
    }
}

/** Desplegable con los prompts recomendados para pruebas: elegir uno lo carga en el campo de entrada. */
@Composable
private fun RecommendedPrompts(onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  " + stringResource(R.string.compare_recommended_prompts), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PROMPT_BANK.forEach { preset ->
                val presetText = stringResource(preset.textRes)
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.widthIn(max = 280.dp)) {
                            Text(stringResource(preset.nameRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                presetText,
                                style = MaterialTheme.typography.bodySmall,
                                color = DemoColors.Muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onPick(presetText)
                    },
                )
            }
        }
    }
}

/** Bienvenida: qué hace la pantalla y dónde están los prompts recomendados. */
@Composable
private fun EmptyCompare() {
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
            stringResource(R.string.compare_title, InferenceTier.entries.size),
            style = MaterialTheme.typography.headlineSmall,
            color = DemoColors.Ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.compare_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = DemoColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
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
private fun CompareCard(
    tier: InferenceTier,
    tierState: DemoState?,
    metric: GenerationMetric?,
    badges: Set<CompareBadge>,
    voteStatus: VoteStatus,
    onVote: () -> Unit,
    modifier: Modifier,
) {
    AppCard(modifier = modifier, accent = if (voteStatus == VoteStatus.CHOSEN) tier.signalColor else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TierAvatar(tier, 28.dp)
            Text(
                tier.label(),
                style = MaterialTheme.typography.titleMedium,
                color = DemoColors.Ink,
                modifier = Modifier.weight(1f),
            )
            val status = when (tierState) {
                is DemoState.Generating -> stringResource(R.string.compare_status_writing)
                is DemoState.Result -> stringResource(R.string.compare_status_done)
                else -> null
            }
            status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tier.signalColor) }
        }
        if (badges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { badge ->
                    Text(
                        stringResource(if (badge == CompareBadge.FASTEST) R.string.compare_badge_fastest else R.string.compare_badge_tokens),
                        style = MaterialTheme.typography.labelSmall,
                        color = tier.signalColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tier.signalColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        when (tierState) {
            is DemoState.Generating ->
                if (tierState.partialText.isBlank()) TypingDots(tier.signalColor) else MarkdownText(tierState.partialText.trim() + " ▍")
            is DemoState.Result -> {
                MarkdownText(tierState.text.trim())
                metric?.let { MetricsLine(it) }
                if (voteStatus != VoteStatus.NOT_VOTABLE) {
                    TextButton(onClick = onVote, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(
                            stringResource(if (voteStatus == VoteStatus.CHOSEN) R.string.compare_best_answer else R.string.compare_pick_best),
                            color = if (voteStatus == VoteStatus.CHOSEN) tier.signalColor else DemoColors.Muted,
                        )
                    }
                }
            }
            is DemoState.Error -> Text(stringResource(R.string.compare_error, tierState.message), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Danger)
            is DemoState.Unavailable -> Text(stringResource(R.string.compare_unavailable), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
            else -> Text(stringResource(R.string.compare_waiting), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
    }
}

/** Si esta tarjeta se puede votar (la ronda ya terminó y hay con qué comparar) y si es la elegida. */
private enum class VoteStatus { NOT_VOTABLE, VOTABLE, CHOSEN }

/** A recommended test prompt: its title and the text loaded into the prompt field. */
private data class PromptPreset(@StringRes val nameRes: Int, @StringRes val textRes: Int)

private val PROMPT_BANK = listOf(
    PromptPreset(R.string.prompt_summary_name, R.string.prompt_summary_text),
    PromptPreset(R.string.prompt_code_name, R.string.prompt_code_text),
    PromptPreset(R.string.prompt_translation_name, R.string.prompt_translation_text),
    PromptPreset(R.string.prompt_reasoning_name, R.string.prompt_reasoning_text),
    PromptPreset(R.string.prompt_extraction_name, R.string.prompt_extraction_text),
    PromptPreset(R.string.prompt_creativity_name, R.string.prompt_creativity_text),
    PromptPreset(R.string.prompt_classification_name, R.string.prompt_classification_text),
    PromptPreset(R.string.prompt_instructions_name, R.string.prompt_instructions_text),
)
