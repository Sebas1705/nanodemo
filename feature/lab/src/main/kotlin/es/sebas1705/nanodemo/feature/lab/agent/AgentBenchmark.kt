package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.agent.tools.CalculatorTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.CurrentTimeTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.DeviceInfoTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.DeviceSnapshot
import es.sebas1705.nanodemo.feature.lab.agent.tools.InMemoryNotesStore
import es.sebas1705.nanodemo.feature.lab.agent.tools.NetworkKind
import es.sebas1705.nanodemo.feature.lab.agent.tools.NotesTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolRegistry
import es.sebas1705.nanodemo.feature.lab.agent.tools.UnitConverterTool
import kotlin.math.abs

/** Verdict of a checker. `reason` is shown in the UI (Spanish) when the check fails. */
data class CheckResult(val passed: Boolean, val reason: String = "")

/** One task of the suite. The checker is a pure function of the finished run. */
class BenchmarkCase(
    val id: String,
    val title: String,
    val prompt: String,
    val check: (AgentRunResult) -> CheckResult,
)

/** Reusable, deterministic checks. */
object Checks {
    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")

    /** Numbers written in `text`; a decimal comma counts as a point ("399,5" -> 399.5). */
    fun numbersIn(text: String): List<Double> =
        NUMBER.findAll(text).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

    fun containsNumber(answer: String, expected: Double, tolerance: Double = 0.05): CheckResult =
        if (numbersIn(answer).any { abs(it - expected) <= tolerance }) {
            CheckResult(true)
        } else {
            CheckResult(false, "faltó el valor ${expected.toString().removeSuffix(".0")} en la respuesta")
        }

    /** Accent- and case-insensitive containment of any of `words`. */
    fun containsAny(answer: String, vararg words: String): CheckResult {
        val flat = fold(answer)
        return if (words.any { fold(it) in flat }) CheckResult(true) else CheckResult(false, "faltó «${words.first()}» en la respuesta")
    }

    fun usedTool(run: AgentRunResult, tool: String): CheckResult =
        if (tool in run.toolsUsed()) CheckResult(true) else CheckResult(false, "no usó la herramienta $tool")

    fun usedNoTools(run: AgentRunResult): CheckResult =
        if (run.toolCalls.isEmpty()) CheckResult(true) else CheckResult(false, "usó herramientas sin necesitarlas (${run.toolsUsed().joinToString()})")

    /** All checks must pass; the first failure's reason is reported. */
    fun all(vararg checks: CheckResult): CheckResult = checks.firstOrNull { !it.passed } ?: CheckResult(true)

    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/**
 * The built-in mini suite: ~a dozen tasks with known answers, covering a calculation, unit conversion, a time
 * zone lookup, device state, notes (save then read), two tools in sequence and tasks that must NOT call a tool.
 * Prompts are in Spanish, like the app. The device is a fixed fake ([BenchmarkEnvironment]) so answers are
 * deterministic.
 */
object BenchmarkSuite {
    val cases: List<BenchmarkCase> = listOf(
        BenchmarkCase("percent", "Porcentaje", "¿Cuánto es el 17% de 2350?") { run ->
            Checks.containsNumber(run.answer, 399.5)
        },
        BenchmarkCase("expression", "Expresión con paréntesis", "Calcula (125 + 75) * 3 - 40 / 8") { run ->
            Checks.containsNumber(run.answer, 595.0)
        },
        BenchmarkCase("minutes", "Minutos entre horas", "¿Cuántos minutos hay entre las 09:15 y las 17:50?") { run ->
            Checks.containsNumber(run.answer, 515.0)
        },
        BenchmarkCase("fahrenheit", "Fahrenheit a Celsius", "Convierte 72 grados Fahrenheit a Celsius.") { run ->
            Checks.containsNumber(run.answer, 22.2, tolerance = 0.1)
        },
        BenchmarkCase("miles", "Millas a kilómetros", "¿Cuántos kilómetros son 26.2 millas?") { run ->
            Checks.containsNumber(run.answer, 42.16, tolerance = 0.1)
        },
        BenchmarkCase("time", "Hora en otra ciudad", "¿Qué hora es ahora mismo en Tokio?") { run ->
            val usedTime = Checks.usedTool(run, "current_time")
            val zoneOk = if (run.toolCalls.any { it.tool == "current_time" && "tokyo" in it.input.lowercase() + it.output.lowercase() }) {
                CheckResult(true)
            } else {
                CheckResult(false, "no pidió la zona Asia/Tokyo")
            }
            val hasClock = if (Regex("""\d{1,2}[:.]\d{2}""").containsMatchIn(run.answer)) CheckResult(true) else CheckResult(false, "la respuesta no trae una hora")
            Checks.all(usedTime, zoneOk, hasClock)
        },
        BenchmarkCase("battery", "Batería del móvil", "¿Cuál es mi nivel de batería?") { run ->
            Checks.all(Checks.usedTool(run, "device_info"), Checks.containsNumber(run.answer, BenchmarkEnvironment.BATTERY_PERCENT.toDouble(), 0.0))
        },
        BenchmarkCase("notes", "Guardar y leer una nota", "Guarda la nota «comprar leche» y después léeme mis notas.") { run ->
            val notes = run.toolCalls.filter { it.tool == "notes" }
            val saved = notes.any { it.ok && it.output.startsWith("saved") }
            val readAfterSave = notes.dropWhile { !(it.ok && it.output.startsWith("saved")) }.drop(1).any { it.ok && "leche" in it.output.lowercase() }
            when {
                !saved -> CheckResult(false, "no llegó a guardar la nota")
                !readAfterSave -> CheckResult(false, "no leyó las notas después de guardar")
                else -> Checks.containsAny(run.answer, "leche")
            }
        },
        BenchmarkCase("two-tools", "Dos herramientas en secuencia", "Mira cuánta batería tengo y calcula cuántos puntos porcentuales me faltan para llegar al 100%.") { run ->
            Checks.all(
                Checks.usedTool(run, "device_info"),
                Checks.containsNumber(run.answer, (100 - BenchmarkEnvironment.BATTERY_PERCENT).toDouble(), 0.0),
            )
        },
        BenchmarkCase("no-tool-capital", "Sin herramienta: capital", "¿Cuál es la capital de Francia?") { run ->
            Checks.all(Checks.containsAny(run.answer, "París"), Checks.usedNoTools(run))
        },
        BenchmarkCase("no-tool-week", "Sin herramienta: días", "¿Cuántos días tiene una semana?") { run ->
            Checks.all(Checks.containsAny(run.answer, "7", "siete"), Checks.usedNoTools(run))
        },
    )
}

/** A fixed, fake world for the suite: known battery, network and a fresh in-memory notes store per task. */
object BenchmarkEnvironment {
    const val BATTERY_PERCENT = 73

    val snapshot = DeviceSnapshot(
        batteryPercent = BATTERY_PERCENT, charging = true, network = NetworkKind.WIFI,
        freeRamMb = 3_100, totalRamMb = 11_800, freeStorageMb = 42_000, totalStorageMb = 128_000,
    )

    /** A new registry for one task: its own notes, so tasks cannot leak state into each other. Web search is never included. */
    fun newRegistry(): ToolRegistry = ToolRegistry(
        listOf(
            CalculatorTool(),
            CurrentTimeTool(),
            DeviceInfoTool { snapshot },
            NotesTool(InMemoryNotesStore()),
            UnitConverterTool(),
        ),
    )
}

/** One task run on one tier. A run passes only if it ended with an answer AND the checker accepts it. */
data class BenchmarkOutcome(val case: BenchmarkCase, val tier: InferenceTier, val run: AgentRunResult, val check: CheckResult) {
    val passed: Boolean get() = run.succeeded && check.passed

    /** Why it failed, for the UI. */
    val failureReason: String?
        get() = when {
            passed -> null
            !run.succeeded -> run.outcome.describe()
            else -> check.reason
        }
}

fun AgentOutcome.describe(): String = when (this) {
    AgentOutcome.ANSWERED -> "respondió"
    AgentOutcome.MAX_STEPS -> "se quedó sin pasos"
    AgentOutcome.TIMEOUT -> "se acabó el tiempo"
    AgentOutcome.LOOP -> "entró en un bucle"
    AgentOutcome.FORMAT_FAILURE -> "no siguió el formato"
    AgentOutcome.MODEL_ERROR -> "error del motor"
}

/** Aggregated figures of one tier over the tasks it ran. */
data class TierSummary(
    val tier: InferenceTier,
    val runs: Int,
    val passed: Int,
    val avgSteps: Double,
    val parseFailures: Int,
    val toolErrors: Int,
    val loops: Int,
    val avgDurationMs: Long,
) {
    val successRate: Double get() = if (runs == 0) 0.0 else passed.toDouble() / runs
}

object BenchmarkAggregator {
    fun check(case: BenchmarkCase, run: AgentRunResult): BenchmarkOutcome =
        BenchmarkOutcome(case, run.tier, run, if (run.succeeded) case.check(run) else CheckResult(false, run.outcome.describe()))

    /** One summary per tier that has outcomes, in tier order. */
    fun summarize(outcomes: List<BenchmarkOutcome>): List<TierSummary> =
        InferenceTier.entries.mapNotNull { tier ->
            val mine = outcomes.filter { it.tier == tier }
            if (mine.isEmpty()) return@mapNotNull null
            TierSummary(
                tier = tier,
                runs = mine.size,
                passed = mine.count { it.passed },
                avgSteps = mine.map { it.run.steps }.average(),
                parseFailures = mine.sumOf { it.run.parseFailures },
                toolErrors = mine.sumOf { it.run.toolErrors },
                loops = mine.sumOf { it.run.loopsDetected },
                avgDurationMs = mine.map { it.run.durationMs }.average().toLong(),
            )
        }
}
