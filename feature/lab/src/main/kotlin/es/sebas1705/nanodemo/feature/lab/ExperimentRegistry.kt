package es.sebas1705.nanodemo.feature.lab

import es.sebas1705.nanodemo.feature.lab.router.routerExperiment
import es.sebas1705.nanodemo.feature.lab.sandbox.sandboxExperiment

/**
 * Every experiment the Lab shows, in display order within its category. To add one: create
 * `<name>Experiment` (a top-level `val ... = Experiment(...)`) in its own package and add it here.
 */
object ExperimentRegistry {
    val all: List<Experiment> = listOf(
        sandboxExperiment,
        routerExperiment,
    )

    fun find(id: String): Experiment? = all.firstOrNull { it.id == id }
}
