package es.sebas1705.nanodemo.feature.lab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class ExperimentCategory(val label: String) {
    ARCHITECTURE("Arquitecturas"),
    MEASUREMENT("Medición"),
}

/**
 * One entry of the Lab hub. `content` renders the experiment's own screen (it owns its ViewModel via
 * `hiltViewModel()`); the shell adds the header with the back button. Register new experiments in
 * [ExperimentRegistry].
 */
data class Experiment(
    val id: String,
    val category: ExperimentCategory,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)
