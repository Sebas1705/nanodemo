package es.sebas1705.nanodemo.feature.lab

import es.sebas1705.nanodemo.feature.lab.bench.benchExperiment
import es.sebas1705.nanodemo.feature.lab.memory.memoryExperiment
import es.sebas1705.nanodemo.feature.lab.router.routerExperiment
import es.sebas1705.nanodemo.feature.lab.scaling.scalingExperiment
import es.sebas1705.nanodemo.feature.lab.soak.soakExperiment
import es.sebas1705.nanodemo.feature.lab.rag.ragExperiment
import es.sebas1705.nanodemo.feature.lab.agent.agentExperiment
import es.sebas1705.nanodemo.feature.lab.refine.judgeExperiment
import es.sebas1705.nanodemo.feature.lab.refine.refineExperiment
import es.sebas1705.nanodemo.feature.lab.refine.strategiesExperiment
import es.sebas1705.nanodemo.feature.lab.sandbox.sandboxExperiment

/**
 * Every experiment the Lab shows, in display order within its category. To add one: create
 * `<name>Experiment` (a top-level `val ... = Experiment(...)`) in its own package and add it here.
 */
object ExperimentRegistry {
    val all: List<Experiment> = listOf(
        sandboxExperiment,
        routerExperiment,
        memoryExperiment,
        benchExperiment,
        scalingExperiment,
        soakExperiment,
        ragExperiment,
        agentExperiment,
        // Reasoning strategies (package refine).
        refineExperiment,
        judgeExperiment,
        strategiesExperiment,
    )

    fun find(id: String): Experiment? = all.firstOrNull { it.id == id }
}
