package es.sebas1705.nanodemo.feature.lab.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability

/** True when the engine can be used right now (model present / API configured). */
fun Map<InferenceTier, TierAvailability>.isUsable(tier: InferenceTier): Boolean =
    this[tier] == TierAvailability.AVAILABLE

/** Engines that are usable right now, in tier order. Experiments that need N engines use this to size themselves. */
fun Map<InferenceTier, TierAvailability>.usableTiers(): List<InferenceTier> =
    InferenceTier.entries.filter { isUsable(it) }

/**
 * Row of engine chips to pick which one runs a pipeline step. Engines that are not usable right now stay
 * visible (so the user sees why a tier is missing) but dimmed and not selectable.
 */
@Composable
fun LabTierSelector(
    selected: InferenceTier,
    availability: Map<InferenceTier, TierAvailability>,
    onSelect: (InferenceTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InferenceTier.entries) { tier ->
            val usable = availability.isUsable(tier)
            TierChip(
                tier = tier,
                selected = tier == selected,
                onClick = { if (usable) onSelect(tier) },
                modifier = Modifier.alpha(if (usable) 1f else 0.4f),
            )
        }
    }
}
