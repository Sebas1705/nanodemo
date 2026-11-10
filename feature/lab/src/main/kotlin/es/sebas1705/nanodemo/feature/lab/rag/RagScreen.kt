package es.sebas1705.nanodemo.feature.lab.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory

val ragExperiment = Experiment(
    id = "rag",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_rag_title,
    subtitle = R.string.lab_rag_subtitle,
    icon = Icons.Filled.Description,
    content = { RagScreen() },
)

/** Widest the content grows on tablets: lines of text and the two A/B cards stay readable. */
private val MaxContentWidth = 880.dp

/**
 * Three tabs over one view model: Documents (store, index), Ask (A/B with and without retrieval) and Evaluate
 * (retrieval quality on the sample questions). The tab row stays on screen; each tab scrolls on its own, and the
 * whole column lifts above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(viewModel: RagViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(RagTab.DOCUMENTS) }
    val sizeClass = rememberScreenSizeClass()
    val busy by viewModel.busy.collectAsState()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RagTab.entries.forEach { entry ->
                FilterChip(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    label = { Text(entry.label) },
                    enabled = tab == entry || !busy,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
                when (tab) {
                    RagTab.DOCUMENTS -> RagDocumentsTab(viewModel)
                    RagTab.ASK -> RagAskTab(viewModel, sizeClass, onGoToDocuments = { tab = RagTab.DOCUMENTS })
                    RagTab.EVALUATE -> RagEvalTab(viewModel, onGoToDocuments = { tab = RagTab.DOCUMENTS })
                }
            }
        }
    }
}
