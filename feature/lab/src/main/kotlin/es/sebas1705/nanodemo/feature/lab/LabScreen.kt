package es.sebas1705.nanodemo.feature.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors

/** Lab tab: the hub of experiments, and the experiment itself once one is opened (back returns to the hub). */
@Composable
fun LabScreen() {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val experiment = openId?.let(ExperimentRegistry::find)
    BackHandler(enabled = experiment != null) { openId = null }

    if (experiment == null) {
        LabHub(onOpen = { openId = it.id })
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al laboratorio", tint = DemoColors.Ink)
                }
                Text(experiment.title, style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
            }
            Column(Modifier.weight(1f).fillMaxWidth()) { experiment.content() }
        }
    }
}

@Composable
private fun LabHub(onOpen: (Experiment) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Arquitecturas de IA y medición sobre los cinco motores. Cada experimento muestra el recorrido paso a paso.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
        }
        ExperimentCategory.entries.forEach { category ->
            val experiments = ExperimentRegistry.all.filter { it.category == category }
            if (experiments.isEmpty()) return@forEach
            item(key = "header-${category.name}") { SectionLabel(category.label) }
            items(experiments, key = { it.id }) { experiment ->
                AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(experiment) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(experiment.icon, contentDescription = null, tint = DemoColors.TierA, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(experiment.title, style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
                            Text(experiment.subtitle, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                        }
                    }
                }
            }
        }
    }
}
