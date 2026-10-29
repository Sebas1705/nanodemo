package es.sebas1705.nanodemo.feature.config

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.common.QuotaPeriod
import es.sebas1705.nanodemo.core.common.QuotaService
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
            stringResource(R.string.usage_intro),
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
            text = stringResource(R.string.usage_note_unlimited),
        )
        NoteCard(
            icon = Icons.Filled.WarningAmber,
            tint = DemoColors.TierB,
            text = stringResource(R.string.usage_note_grounding),
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
                stringResource(service.labelRes()),
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
        val perLabel = stringResource(service.period.perRes())
        val resetLabel = stringResource(service.period.resetRes())
        Text(
            when {
                state.reached && state.enforced -> stringResource(R.string.usage_limit_blocked, resetLabel)
                state.reached -> stringResource(R.string.usage_limit_warn_only)
                else -> stringResource(R.string.usage_period_resets, perLabel.replaceFirstChar { it.uppercase() }, resetLabel)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.reached) DemoColors.Danger else DemoColors.Muted,
        )
        Text(stringResource(service.noteRes()), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.usage_block_at_limit), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
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
                label = { Text(stringResource(R.string.usage_limit_label, perLabel)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(170.dp),
            )
            TextButton(onClick = onReset) { Text(stringResource(R.string.usage_reset_counter)) }
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

@StringRes
private fun QuotaService.labelRes(): Int = when (this) {
    QuotaService.GEMINI_CLOUD -> R.string.quota_gemini_cloud_label
    QuotaService.GEMINI_FIREBASE -> R.string.quota_gemini_firebase_label
    QuotaService.BRAVE_SEARCH -> R.string.quota_brave_label
}

@StringRes
private fun QuotaService.noteRes(): Int = when (this) {
    QuotaService.GEMINI_CLOUD -> R.string.quota_gemini_cloud_note
    QuotaService.GEMINI_FIREBASE -> R.string.quota_gemini_firebase_note
    QuotaService.BRAVE_SEARCH -> R.string.quota_brave_note
}

@StringRes
private fun QuotaPeriod.perRes(): Int = when (this) {
    QuotaPeriod.DAY -> R.string.quota_per_day
    QuotaPeriod.MONTH -> R.string.quota_per_month
}

@StringRes
private fun QuotaPeriod.resetRes(): Int = when (this) {
    QuotaPeriod.DAY -> R.string.quota_reset_day
    QuotaPeriod.MONTH -> R.string.quota_reset_month
}
