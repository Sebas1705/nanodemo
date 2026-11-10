package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBenchmarkTest {

    private fun run(
        answer: String = "",
        tools: List<ToolCallRecord> = emptyList(),
        outcome: AgentOutcome = AgentOutcome.ANSWERED,
        tier: InferenceTier = InferenceTier.CLOUD_GEMINI,
        steps: Int = 1,
        parseFailures: Int = 0,
        loops: Int = 0,
        durationMs: Long = 1_000,
    ) = AgentRunResult(
        task = "t", tier = tier, outcome = outcome, answer = answer, steps = steps, maxSteps = 6, toolCalls = tools,
        parseFailures = parseFailures, loopsDetected = loops, durationMs = durationMs,
    )

    private fun call(tool: String, input: String = "{}", output: String = "ok", ok: Boolean = true) = ToolCallRecord(tool, input, output, ok)

    private fun case(id: String) = BenchmarkSuite.cases.single { it.id == id }

    private fun verdict(id: String, run: AgentRunResult) = BenchmarkAggregator.check(case(id), run)

    // ---- helpers ----

    @Test
    fun `numbers are read with decimal points or commas`() {
        assertEquals(listOf(17.0, 2350.0, 399.5), Checks.numbersIn("17% of 2350 is 399.5"))
        assertEquals(listOf(399.5), Checks.numbersIn("Son 399,5"))
        assertEquals(listOf(-3.0), Checks.numbersIn("-3 grados"))
        assertTrue(Checks.numbersIn("no numbers").isEmpty())
    }

    @Test
    fun `containsNumber honours the tolerance`() {
        assertTrue(Checks.containsNumber("es 399,5", 399.5).passed)
        assertTrue(Checks.containsNumber("22.22 C", 22.2, 0.1).passed)
        assertFalse(Checks.containsNumber("22.5", 22.2, 0.1).passed)
        assertFalse(Checks.containsNumber("no idea", 5.0).passed)
        assertFalse(Checks.containsNumber("400", 399.5).passed)
    }

    @Test
    fun `containsAny ignores case and accents`() {
        assertTrue(Checks.containsAny("La capital es PARIS.", "París").passed)
        assertTrue(Checks.containsAny("Siete días", "7", "siete").passed)
        assertFalse(Checks.containsAny("Roma", "París").passed)
    }

    @Test
    fun `all reports the first failure`() {
        val failed = Checks.all(CheckResult(true), CheckResult(false, "first"), CheckResult(false, "second"))
        assertFalse(failed.passed)
        assertEquals("first", failed.reason)
        assertTrue(Checks.all(CheckResult(true), CheckResult(true)).passed)
        assertTrue(Checks.all().passed)
    }

    // ---- suite ----

    @Test
    fun `the suite is a dozen unique tasks`() {
        val cases = BenchmarkSuite.cases
        assertTrue(cases.size >= 10)
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertTrue(cases.all { it.prompt.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `percent task`() {
        assertTrue(verdict("percent", run("El 17% de 2350 es 399,5.")).passed)
        assertFalse(verdict("percent", run("Es 400.")).passed)
    }

    @Test
    fun `expression task`() {
        assertTrue(verdict("expression", run("El resultado es 595")).passed)
        assertFalse(verdict("expression", run("594")).passed)
    }

    @Test
    fun `minutes task`() {
        assertTrue(verdict("minutes", run("Hay 515 minutos (8 h 35 min).")).passed)
        assertFalse(verdict("minutes", run("Hay 500 minutos.")).passed)
    }

    @Test
    fun `conversion tasks`() {
        assertTrue(verdict("fahrenheit", run("72 °F son 22.22 °C")).passed)
        assertFalse(verdict("fahrenheit", run("72 °F son 40 °C")).passed)
        assertTrue(verdict("miles", run("26.2 millas son unos 42.16 km")).passed)
        assertFalse(verdict("miles", run("26.2 millas son 26.2 km")).passed)
    }

    @Test
    fun `time task needs the tool, the right zone and a clock time`() {
        val tokyo = call("current_time", "{\"timezone\": \"Tokio\"}", "Asia/Tokyo: lunes, 21:05")
        assertTrue(verdict("time", run("Son las 21:05", listOf(tokyo))).passed)
        assertFalse(verdict("time", run("Son las 21:05")).passed)
        val wrongZone = call("current_time", "{}", "UTC: lunes, 12:05")
        assertFalse(verdict("time", run("Son las 21:05", listOf(wrongZone))).passed)
        assertFalse(verdict("time", run("No lo sé", listOf(tokyo))).passed)
    }

    @Test
    fun `battery task needs the tool and the number`() {
        val device = call("device_info", "{}", "battery: 73%, charging")
        assertTrue(verdict("battery", run("Tienes un 73% de batería.", listOf(device))).passed)
        assertFalse(verdict("battery", run("Tienes un 73% de batería.")).passed)
        assertFalse(verdict("battery", run("Tienes un 80%.", listOf(device))).passed)
        assertFalse(verdict("battery", run("Tienes 173 puntos.", listOf(device))).passed)
    }

    @Test
    fun `notes task needs a save, then a read that shows it, then the word in the answer`() {
        val save = call("notes", "{\"action\":\"save\",\"text\":\"comprar leche\"}", "saved. you now have 1 note(s)")
        val read = call("notes", "{\"action\":\"read\"}", "1. comprar leche")
        assertTrue(verdict("notes", run("Tu nota: comprar leche", listOf(save, read))).passed)
        val noSave = verdict("notes", run("Tu nota: comprar leche", listOf(read)))
        assertFalse(noSave.passed)
        assertEquals("no llegó a guardar la nota", noSave.check.reason)
        val noRead = verdict("notes", run("Guardada", listOf(save)))
        assertFalse(noRead.passed)
        assertEquals("no leyó las notas después de guardar", noRead.check.reason)
        assertFalse(verdict("notes", run("Hecho", listOf(save, read))).passed) // the answer does not mention it
        // Reading BEFORE saving does not count as reading after.
        assertFalse(verdict("notes", run("leche", listOf(read, save))).passed)
        // A failed save does not count.
        assertFalse(verdict("notes", run("leche", listOf(call("notes", "x", "missing text", ok = false), read))).passed)
    }

    @Test
    fun `two tools task`() {
        val device = call("device_info", "{}", "battery: 73%")
        val calc = call("calculator", "{\"expression\":\"100-73\"}", "27")
        assertTrue(verdict("two-tools", run("Te faltan 27 puntos.", listOf(device, calc))).passed)
        assertFalse(verdict("two-tools", run("Te faltan 27 puntos.", listOf(calc))).passed)
        assertFalse(verdict("two-tools", run("Te faltan 73 puntos.", listOf(device, calc))).passed)
    }

    @Test
    fun `tasks that need no tool fail if one is used`() {
        assertTrue(verdict("no-tool-capital", run("La capital de Francia es París.")).passed)
        val used = verdict("no-tool-capital", run("París", listOf(call("web_search"))))
        assertFalse(used.passed)
        assertTrue(used.check.reason.contains("herramientas"))
        assertFalse(verdict("no-tool-capital", run("Lyon")).passed)
        assertTrue(verdict("no-tool-week", run("Una semana tiene siete días.")).passed)
        assertTrue(verdict("no-tool-week", run("7")).passed)
        assertFalse(verdict("no-tool-week", run("7", listOf(call("calculator")))).passed)
    }

    // ---- outcome handling ----

    @Test
    fun `a run that did not end with an answer fails whatever the text says`() {
        val outcome = verdict("percent", run("399.5", outcome = AgentOutcome.MAX_STEPS))
        assertFalse(outcome.passed)
        assertEquals("se quedó sin pasos", outcome.failureReason)
        assertNull(verdict("percent", run("399.5")).failureReason)
    }

    @Test
    fun `every outcome has a description`() {
        AgentOutcome.entries.forEach { assertTrue(it.describe().isNotBlank()) }
    }

    // ---- aggregation ----

    @Test
    fun `summaries are per tier with rates and averages`() {
        val a = InferenceTier.ML_KIT_NANO
        val c = InferenceTier.CLOUD_GEMINI
        val outcomes = listOf(
            verdict("percent", run("399.5", tier = c, steps = 2, durationMs = 2_000, tools = listOf(call("calculator")))),
            verdict("minutes", run("515", tier = c, steps = 4, durationMs = 4_000, tools = listOf(call("calculator", ok = false), call("calculator")), loops = 1)),
            verdict("percent", run("???", tier = a, steps = 3, parseFailures = 2, durationMs = 3_000)),
            verdict("minutes", run("", tier = a, steps = 6, outcome = AgentOutcome.FORMAT_FAILURE, parseFailures = 3, durationMs = 9_000)),
            verdict("battery", run("", tier = a, steps = 1, outcome = AgentOutcome.MODEL_ERROR, durationMs = 0)),
        )

        val summaries = BenchmarkAggregator.summarize(outcomes)

        assertEquals(listOf(a, c), summaries.map { it.tier }) // tier order, not insertion order
        val nano = summaries[0]
        assertEquals(3, nano.runs)
        assertEquals(0, nano.passed)
        assertEquals(0.0, nano.successRate, 0.0)
        assertEquals(10.0 / 3, nano.avgSteps, 1e-9)
        assertEquals(5, nano.parseFailures)
        assertEquals(4_000L, nano.avgDurationMs)
        val cloud = summaries[1]
        assertEquals(2, cloud.runs)
        assertEquals(2, cloud.passed)
        assertEquals(1.0, cloud.successRate, 0.0)
        assertEquals(3.0, cloud.avgSteps, 0.0)
        assertEquals(1, cloud.toolErrors)
        assertEquals(1, cloud.loops)
        assertEquals(3_000L, cloud.avgDurationMs)
    }

    @Test
    fun `summaries of nothing are empty and a tier without runs is skipped`() {
        assertTrue(BenchmarkAggregator.summarize(emptyList()).isEmpty())
        val one = listOf(verdict("percent", run("399.5", tier = InferenceTier.FIREBASE_AI)))
        assertEquals(listOf(InferenceTier.FIREBASE_AI), BenchmarkAggregator.summarize(one).map { it.tier })
    }

    @Test
    fun `an empty summary has a zero success rate instead of dividing by zero`() {
        assertEquals(0.0, TierSummary(InferenceTier.ML_KIT_NANO, 0, 0, 0.0, 0, 0, 0, 0).successRate, 0.0)
    }

    // ---- environment and end to end ----

    @Test
    fun `the benchmark world has every tool except web search and fresh notes each time`() {
        val registry = BenchmarkEnvironment.newRegistry()
        assertEquals(listOf("calculator", "current_time", "device_info", "notes", "unit_converter"), registry.names)
        val first = runBlocking { registry.invoke("notes", "{\"action\":\"save\",\"text\":\"x\"}") }
        assertTrue(first.ok)
        val second = runBlocking { BenchmarkEnvironment.newRegistry().invoke("notes", "{\"action\":\"read\"}") }
        assertEquals("no notes saved", second.text)
        val battery = runBlocking { registry.invoke("device_info", "{\"field\": \"battery\"}") }
        assertTrue(battery.text.contains("73%"))
    }

    private fun endToEnd(id: String, vararg replies: String): BenchmarkOutcome {
        val pipeline = PipelineRunner(ScriptedExecutor(replies.toList()))
        val result = runBlocking {
            AgentRunner(pipeline).run(case(id).prompt, InferenceTier.CLOUD_GEMINI, BenchmarkEnvironment.newRegistry(), caps(), AgentSettings())
        }
        return BenchmarkAggregator.check(case(id), result)
    }

    @Test
    fun `a competent scripted agent passes the percent task through the real tools`() {
        assertTrue(endToEnd("percent", calc("17% of 2350"), finalAnswer("El 17% de 2350 es 399,5")).passed)
    }

    @Test
    fun `a competent scripted agent passes the two tools task`() {
        val outcome = endToEnd(
            "two-tools",
            action("device_info", "{\"field\": \"battery\"}"),
            calc("100-73"),
            finalAnswer("Tienes 73% y te faltan 27 puntos."),
        )
        assertTrue(outcome.failureReason, outcome.passed)
        assertEquals(3, outcome.run.steps)
    }

    @Test
    fun `a competent scripted agent passes the notes task`() {
        val outcome = endToEnd(
            "notes",
            action("notes", "{\"action\": \"save\", \"text\": \"comprar leche\"}"),
            action("notes", "{\"action\": \"read\"}"),
            finalAnswer("Guardada. Tus notas: 1. comprar leche"),
        )
        assertTrue(outcome.failureReason, outcome.passed)
    }

    @Test
    fun `a competent scripted agent passes the time task`() {
        val outcome = endToEnd("time", action("current_time", "{\"timezone\": \"Tokio\"}"), finalAnswer("Ahora mismo son las 21:30 en Tokio."))
        assertTrue(outcome.failureReason, outcome.passed)
    }

    @Test
    fun `an agent that skips the tool fails the battery task and one that guesses fails too`() {
        assertFalse(endToEnd("battery", finalAnswer("Tienes un 73%.")).passed)
        assertNotNull(endToEnd("battery", finalAnswer("Tienes un 73%.")).failureReason)
        assertFalse(endToEnd("battery", action("device_info", "{}"), finalAnswer("Tienes un 50%.")).passed)
    }

    @Test
    fun `an agent that calls a tool for a trivia question fails the no tool task`() {
        assertFalse(endToEnd("no-tool-capital", calc("1+1"), finalAnswer("París")).passed)
        assertTrue(endToEnd("no-tool-capital", finalAnswer("París")).passed)
    }
}
