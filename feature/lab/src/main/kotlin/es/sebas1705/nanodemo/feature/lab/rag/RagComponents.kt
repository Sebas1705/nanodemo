package es.sebas1705.nanodemo.feature.lab.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.feature.lab.R
import java.util.Locale

internal fun Double.format(decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f", this)

internal fun Double.asPercent(): String = "${(this * 100).toInt()}%"

/** Milliseconds as `12 ms` below a second and `1.4 s` above. */
internal fun Double.asDuration(): String = if (this < 1_000) "${format(1)} ms" else "${(this / 1000).format(1)} s"

/** The embedding model's state: download button, progress, error or a one-line "ready". */
@Composable
internal fun EmbedderCard(state: EmbedderState, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        EmbedderState.Ready -> Text(
            stringResource(R.string.rag_embedder_ready),
            style = readoutStyle, color = DemoColors.TierA, modifier = modifier,
        )
        EmbedderState.NotDownloaded -> AppCard(modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rag_embedder_title), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
            Text(
                stringResource(R.string.rag_embedder_needed),
                style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted,
            )
            Button(onClick = onDownload) { Text(stringResource(R.string.rag_embedder_download)) }
        }
        is EmbedderState.Downloading -> AppCard(modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rag_embedder_downloading), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
            val percent = state.percent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.rag_embedder_percent, percent), style = readoutStyle, color = DemoColors.Muted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.rag_embedder_connecting), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
                }
            }
        }
        is EmbedderState.Error -> AppCard(modifier.fillMaxWidth()) {
            ErrorCard(state.message)
            Button(onClick = onDownload) { Text(stringResource(R.string.rag_embedder_retry)) }
        }
    }
}

/**
 * Top-k, MMR and similarity threshold: the retrieval settings that can change between questions (chunk size is
 * fixed at indexing time and lives in the Documents tab). Shared by the Ask and Evaluate tabs.
 */
@Composable
internal fun RetrievalSettingsCard(
    settings: RagSettings,
    onTopK: (Int) -> Unit,
    onMmr: (Boolean) -> Unit,
    onMinScore: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.rag_settings_top_k))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..MAX_TOP_K_OPTION).forEach { k ->
                FilterChip(selected = settings.topK == k, onClick = { onTopK(k) }, label = { Text("$k") }, enabled = enabled)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = settings.mmr, onCheckedChange = onMmr, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rag_settings_mmr_title), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Text(
                    stringResource(R.string.rag_settings_mmr_hint),
                    style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
                )
            }
        }
        var slider by remember(settings.minScore) { mutableFloatStateOf(settings.minScore) }
        Text(
            if (settings.minScore <= 0f) stringResource(R.string.rag_settings_threshold_none) else stringResource(R.string.rag_settings_threshold_value, settings.minScore.toDouble().format()),
            style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink,
        )
        Slider(
            value = slider,
            onValueChange = { slider = (it * 20).toInt() / 20f },
            onValueChangeFinished = { onMinScore(slider) },
            valueRange = 0f..0.6f,
            enabled = enabled,
        )
        Text(
            stringResource(R.string.rag_settings_threshold_hint),
            style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
        )
    }
}

internal const val MAX_TOP_K_OPTION = 5

/** A big number with a caption and a thin bar for ratios; the unit of the evaluation summary. */
@Composable
internal fun MetricTile(value: String, caption: String, ratio: Double?, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(caption, style = readoutStyle, color = DemoColors.Muted)
        if (ratio != null) LinearProgressIndicator(progress = { ratio.coerceIn(0.0, 1.0).toFloat() }, color = color, modifier = Modifier.fillMaxWidth())
    }
}
