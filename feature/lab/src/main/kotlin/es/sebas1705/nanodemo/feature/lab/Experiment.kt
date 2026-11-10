package es.sebas1705.nanodemo.feature.lab

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class ExperimentCategory(@StringRes val label: Int) {
    ARCHITECTURE(R.string.lab_category_architecture),
    MEASUREMENT(R.string.lab_category_measurement),
}

/**
 * One entry of the Lab hub. `title` and `subtitle` are string resources (`lab_<id>_title`/`lab_<id>_subtitle`)
 * so the hub follows the device language; `content` renders the experiment's own screen (it owns its ViewModel
 * via `hiltViewModel()`) and the shell adds the header with the back button. Register new experiments in
 * [ExperimentRegistry].
 */
data class Experiment(
    val id: String,
    val category: ExperimentCategory,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)
