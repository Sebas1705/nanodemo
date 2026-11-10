package es.sebas1705.nanodemo.feature.lab.router

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView

val routerExperiment = Experiment(
    id = "router",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_router_title,
    subtitle = R.string.lab_router_subtitle,
    icon = Icons.Filled.AltRoute,
    content = { RouterScreen() },
)

@Composable
fun RouterScreen(viewModel: RouterViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val form by viewModel.form.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val running by viewModel.running.collectAsState()
    val compare by viewModel.compare.collectAsState()
    val sizeClass = rememberScreenSizeClass()
    var prompt by rememberSaveable { mutableStateOf("") }
    val busy = running || compare.running

    val inputs: @Composable ColumnScope.() -> Unit = {
        EngineSummaryCard(availability)
        PolicyPicker(form, availability, viewModel::update)
        OptionsCard(form, availability, viewModel::update)
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.router_prompt_label)) },
            placeholder = { Text(stringResource(R.string.router_prompt_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { viewModel.run(prompt) }, enabled = prompt.isNotBlank() && !busy) {
                Text(stringResource(if (running) R.string.router_running else R.string.router_run))
            }
            if (running) OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.router_cancel)) }
        }
    }
    val results: @Composable ColumnScope.() -> Unit = {
        PipelineTraceView(trace)
        outcome?.let { result ->
            AnswerCard(result)
            RouteCard(result)
            ExposurePanel(result)
        }
        ComparePanel(compare, sizeClass, disabled = running, onRun = viewModel::runComparison, onCancel = viewModel::cancel)
    }

    if (sizeClass == ScreenSizeClass.COMPACT) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            inputs()
            results()
        }
    } else {
        // Tablet / foldable / landscape: the form on the left, the live trace and results on the right, each scrolling on its own.
        Row(modifier = Modifier.fillMaxSize().imePadding(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = inputs,
            )
            Column(
                modifier = Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = results,
            )
        }
    }
}
