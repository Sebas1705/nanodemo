package es.sebas1705.nanodemo.feature.lab.agent

import androidx.annotation.StringRes
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.R
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

/** Verdict of a checker. `reason` is shown in the UI (already localized by the [AgentTexts] of the check) when it fails. */
data class CheckResult(val passed: Boolean, val reason: String = "")

/** One task of the suite. The checker is a pure function of the finished run. */
class BenchmarkCase(
    val id: String,
    @StringRes val title: Int,
    val prompt: String,
    val check: (AgentRunResult, AgentTexts) -> CheckResult,
)

/** Reusable, deterministic checks. */
object Checks {
    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")

    /** Numbers written in `text`; a decimal comma counts as a point ("399,5" -> 399.5). */
    fun numbersIn(text: String): List<Double> =
        NUMBER.findAll(text).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

    fun containsNumber(texts: AgentTexts, answer: String, expected: Double, tolerance: Double = 0.05): CheckResult =
        if (numbersIn(answer).any { abs(it - expected) <= tolerance }) {
            CheckResult(true)
        } else {
            CheckResult(false, texts.missingValue(expected.toString().removeSuffix(".0")))
        }

    /** Accent- and case-insensitive containment of any of `words`. */
    fun containsAny(texts: AgentTexts, answer: String, vararg words: String): CheckResult {
        val flat = fold(answer)
        return if (words.any { fold(it) in flat }) CheckResult(true) else CheckResult(false, texts.missingWord(words.first()))
    }

    fun usedTool(texts: AgentTexts, run: AgentRunResult, tool: String): CheckResult =
        if (tool in run.toolsUsed()) CheckResult(true) else CheckResult(false, texts.toolNotUsed(tool))

    fun usedNoTools(texts: AgentTexts, run: AgentRunResult): CheckResult =
        if (run.toolCalls.isEmpty()) CheckResult(true) else CheckResult(false, texts.toolsNotNeeded(run.toolsUsed().joinToString()))

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
        BenchmarkCase("percent", R.string.agent_case_percent, "¿Cuánto es el 17% de 2350?") { run, texts ->
            Checks.containsNumber(texts, run.answer, 399.5)
        },
        BenchmarkCase("expression", R.string.agent_case_expression, "Calcula (125 + 75) * 3 - 40 / 8") { run, texts ->
            Checks.containsNumber(texts, run.answer, 595.0)
        },
        BenchmarkCase("minutes", R.string.agent_case_minutes, "¿Cuántos minutos hay entre las 09:15 y las 17:50?") { run, texts ->
            Checks.containsNumber(texts, run.answer, 515.0)
        },
        BenchmarkCase("fahrenheit", R.string.agent_case_fahrenheit, "Convierte 72 grados Fahrenheit a Celsius.") { run, texts ->
            Checks.containsNumber(texts, run.answer, 22.2, tolerance = 0.1)
        },
        BenchmarkCase("miles", R.string.agent_case_miles, "¿Cuántos kilómetros son 26.2 millas?") { run, texts ->
            Checks.containsNumber(texts, run.answer, 42.16, tolerance = 0.1)
        },
        BenchmarkCase("time", R.string.agent_case_time, "¿Qué hora es ahora mismo en Tokio?") { run, texts ->
            val usedTime = Checks.usedTool(texts, run, "current_time")
            val zoneOk = if (run.toolCalls.any { it.tool == "current_time" && "tokyo" in it.input.lowercase() + it.output.lowercase() }) {
                CheckResult(true)
            } else {
                CheckResult(false, texts.noZone)
            }
            val hasClock = if (Regex("""\d{1,2}[:.]\d{2}""").containsMatchIn(run.answer)) CheckResult(true) else CheckResult(false, texts.noClock)
            Checks.all(usedTime, zoneOk, hasClock)
        },
        BenchmarkCase("battery", R.string.agent_case_battery, "¿Cuál es mi nivel de batería?") { run, texts ->
            Checks.all(Checks.usedTool(texts, run, "device_info"), Checks.containsNumber(texts, run.answer, BenchmarkEnvironment.BATTERY_PERCENT.toDouble(), 0.0))
        },
        BenchmarkCase("notes", R.string.agent_case_notes, "Guarda la nota «comprar leche» y después léeme mis notas.") { run, texts ->
            val notes = run.toolCalls.filter { it.tool == "notes" }
            val saved = notes.any { it.ok && it.output.startsWith("saved") }
            val readAfterSave = notes.dropWhile { !(it.ok && it.output.startsWith("saved")) }.drop(1).any { it.ok && "leche" in it.output.lowercase() }
            when {
                !saved -> CheckResult(false, texts.noteNotSaved)
                !readAfterSave -> CheckResult(false, texts.noteNotRead)
                else -> Checks.containsAny(texts, run.answer, "leche")
            }
        },
        BenchmarkCase("two-tools", R.string.agent_case_two_tools, "Mira cuánta batería tengo y calcula cuántos puntos porcentuales me faltan para llegar al 100%.") { run, texts ->
            Checks.all(
                Checks.usedTool(texts, run, "device_info"),
                Checks.containsNumber(texts, run.answer, (100 - BenchmarkEnvironment.BATTERY_PERCENT).toDouble(), 0.0),
            )
        },
        BenchmarkCase("no-tool-capital", R.string.agent_case_no_tool_capital, "¿Cuál es la capital de Francia?") { run, texts ->
            Checks.all(Checks.containsAny(texts, run.answer, "París"), Checks.usedNoTools(texts, run))
        },
        BenchmarkCase("no-tool-week", R.string.agent_case_no_tool_week, "¿Cuántos días tiene una semana?") { run, texts ->
            Checks.all(Checks.containsAny(texts, run.answer, "7", "siete"), Checks.usedNoTools(texts, run))
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
            !run.succeeded && check.reason.isBlank() -> run.outcome.name
            else -> check.reason
        }
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
    fun check(case: BenchmarkCase, run: AgentRunResult, texts: AgentTexts = AgentTexts): BenchmarkOutcome =
        BenchmarkOutcome(case, run.tier, run, if (run.succeeded) case.check(run, texts) else CheckResult(false, texts.outcome(run.outcome)))

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
