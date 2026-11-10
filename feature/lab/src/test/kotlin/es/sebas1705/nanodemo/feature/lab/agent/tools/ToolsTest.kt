package es.sebas1705.nanodemo.feature.lab.agent.tools

import es.sebas1705.nanodemo.core.common.WebResult
import es.sebas1705.nanodemo.core.common.WebSearchOutcome
import es.sebas1705.nanodemo.core.common.WebSearchFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {

    private fun ToolResult.text(): String = when (this) {
        is ToolResult.Ok -> text
        is ToolResult.Error -> message
    }

    private fun ToolResult.isOk() = this is ToolResult.Ok

    private fun run(tool: Tool, input: String) = runBlocking { tool.execute(ToolArgs(input)) }

    // ---- registry ----

    private class FakeTool(
        override val name: String,
        override val aliases: Set<String> = emptySet(),
        override val timeoutMs: Long = 5_000,
        override val maxOutputChars: Int = 600,
        private val body: suspend () -> ToolResult,
    ) : Tool {
        override val description = "fake"
        override val parameterHint = "{}"
        override suspend fun execute(args: ToolArgs) = body()
    }

    @Test
    fun `registry finds tools by name, alias and sloppy spelling`() {
        val registry = ToolRegistry(listOf(FakeTool("current_time", setOf("clock")) { ToolResult.Ok("t") }))
        assertEquals("current_time", registry.find("Current Time")?.name)
        assertEquals("current_time", registry.find("current-time")?.name)
        assertEquals("current_time", registry.find("clock")?.name)
        assertNull(registry.find("nope"))
    }

    @Test
    fun `an unknown tool is an error that lists the available ones`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("a") { ToolResult.Ok("x") }, FakeTool("b") { ToolResult.Ok("x") }))
        val result = registry.invoke("zzz", "")
        assertFalse(result.ok)
        assertTrue(result.text.contains("Available tools: a, b"))
        assertTrue(result.text.contains("Final Answer"))
    }

    @Test
    fun `a slow tool times out instead of blocking the loop`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("slow", timeoutMs = 50) { delay(5_000); ToolResult.Ok("late") }))
        val result = registry.invoke("slow", "")
        assertFalse(result.ok)
        assertTrue(result.text, result.text.contains("timed out"))
    }

    @Test
    fun `the caller can lower the timeout`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("slow", timeoutMs = 60_000) { delay(5_000); ToolResult.Ok("late") }))
        assertFalse(registry.invoke("slow", "", timeoutCapMs = 40).ok)
    }

    @Test
    fun `an exception inside a tool becomes an error`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("boom") { error("kaput") }))
        val result = registry.invoke("boom", "")
        assertFalse(result.ok)
        assertEquals("kaput", result.text)
    }

    @Test
    fun `output is capped by the tool and by the caller`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("big", maxOutputChars = 100) { ToolResult.Ok("x".repeat(5_000)) }))
        val tool = registry.invoke("big", "")
        assertTrue(tool.text.length <= 100)
        assertTrue(tool.text.endsWith(TRUNCATION_MARK))
        val caller = registry.invoke("big", "", outputCap = 40)
        assertTrue(caller.text.length <= 40)
    }

    @Test
    fun `error messages are capped too`() = runBlocking {
        val registry = ToolRegistry(listOf(FakeTool("big", maxOutputChars = 50) { ToolResult.Error("e".repeat(500)) }))
        assertTrue(registry.invoke("big", "").text.length <= 50)
    }

    @Test
    fun `truncateWithMark leaves short text alone`() {
        assertEquals("short", "short".truncateWithMark(10))
        assertEquals(20, "x".repeat(50).truncateWithMark(20).length)
    }

    // ---- calculator ----

    @Test
    fun `calculator accepts json, raw expressions and reports errors`() {
        val calc = CalculatorTool()
        assertEquals("399.5", run(calc, "{\"expression\": \"17% of 2350\"}").text())
        assertEquals("4", run(calc, "{'expr': '2+2',}").text())
        assertEquals("14", run(calc, "2+3*4").text())
        assertEquals("14", run(calc, "\"2+3*4\"").text())
        val divide = run(calc, "{\"expression\": \"1/0\"}")
        assertFalse(divide.isOk())
        assertTrue(divide.text().contains("division by zero"))
        assertFalse(run(calc, "").isOk())
        assertFalse(run(calc, "{}").isOk())
    }

    // ---- unit converter ----

    @Test
    fun `unit converter tool takes json or text`() {
        val tool = UnitConverterTool()
        assertTrue(run(tool, "{\"value\": 72, \"from\": \"F\", \"to\": \"C\"}").text().contains("22.2222"))
        assertTrue(run(tool, "{\"value\": \"5\", \"from_unit\": \"km\", \"to_unit\": \"mi\"}").text().contains("3.10685"))
        assertTrue(run(tool, "72 F to C").text().contains("22.2222"))
        assertTrue(run(tool, "{\"query\": \"5 km a millas\"}").text().contains("3.10685"))
        assertFalse(run(tool, "banana").isOk())
        assertFalse(run(tool, "{\"value\": 1, \"from\": \"km\", \"to\": \"kg\"}").isOk())
        assertFalse(run(tool, "{\"value\": 1, \"from\": \"km\"}").isOk())
    }

    // ---- time ----

    @Test
    fun `zone resolver understands ids, cities, spanish names and offsets`() {
        assertEquals("Europe/Madrid", ZoneResolver.resolve("Europe/Madrid"))
        assertEquals("Europe/Madrid", ZoneResolver.resolve("europe/madrid"))
        assertEquals("Europe/Madrid", ZoneResolver.resolve("Madrid"))
        assertEquals("Asia/Tokyo", ZoneResolver.resolve("Tokio"))
        assertEquals("Asia/Tokyo", ZoneResolver.resolve("Tokyo"))
        assertEquals("America/New_York", ZoneResolver.resolve("Nueva York"))
        assertEquals("America/Argentina/Buenos_Aires", ZoneResolver.resolve("Buenos Aires"))
        assertEquals("UTC", ZoneResolver.resolve("utc"))
        assertEquals("UTC+02:00", ZoneResolver.resolve("UTC+2"))
        assertNull(ZoneResolver.resolve("Atlantis"))
        assertNull(ZoneResolver.resolve(""))
    }

    @Test
    fun `current time tool resolves the zone and formats the answer`() {
        val seen = mutableListOf<String>()
        val tool = CurrentTimeTool { zone ->
            seen += zone
            mapOf("iso8601" to "2026-11-09T21:00+09:00[Asia/Tokyo]", "readable" to "lunes 9 de noviembre de 2026, 21:00")
        }
        val ok = run(tool, "{\"timezone\": \"Tokio\"}")
        assertEquals(listOf("Asia/Tokyo"), seen)
        assertTrue(ok.text().startsWith("Asia/Tokyo: lunes 9 de noviembre"))
        assertEquals("UTC", run(tool, "").let { seen.last() })
        assertTrue(run(tool, "Madrid").isOk())
        val bad = run(tool, "{\"timezone\": \"Atlantis\"}")
        assertFalse(bad.isOk())
        assertTrue(bad.text().contains("Europe/Madrid"))
    }

    @Test
    fun `current time tool works with the real clock`() {
        val text = run(CurrentTimeTool(), "Europe/Madrid").text()
        assertTrue(text, text.startsWith("Europe/Madrid: "))
    }

    // ---- device info ----

    private val snapshot = DeviceSnapshot(
        batteryPercent = 73, charging = true, network = NetworkKind.WIFI,
        freeRamMb = 3100, totalRamMb = 11800, freeStorageMb = 42_000, totalStorageMb = 128_000,
    )

    @Test
    fun `device info reports everything or one field`() {
        val tool = DeviceInfoTool { snapshot }
        val all = run(tool, "").text()
        assertTrue(all.contains("battery: 73%, charging"))
        assertTrue(all.contains("network: wifi"))
        assertTrue(all.contains("free RAM: 3100 MB of 11800 MB"))
        assertTrue(all.contains("free storage: 41.02 GB of 125 GB"))
        assertEquals("battery: 73%, charging", run(tool, "{\"field\": \"battery\"}").text())
        assertEquals("network: wifi", run(tool, "{\"field\": \"network\"}").text())
        assertEquals("free RAM: 3100 MB of 11800 MB", run(tool, "memory").text())
        assertTrue(run(tool, "{\"field\": \"storage\"}").text().startsWith("free storage"))
        assertEquals(4, run(tool, "{\"field\": \"all\"}").text().lines().size)
    }

    @Test
    fun `device info survives missing readings`() {
        val tool = DeviceInfoTool { DeviceSnapshot() }
        val text = run(tool, "").text()
        assertTrue(text.contains("battery: unavailable"))
        assertTrue(text.contains("network: unknown"))
        assertTrue(text.contains("RAM: unavailable"))
        assertTrue(text.contains("storage: unavailable"))
        assertEquals("battery: 50%, not charging", DeviceInfoTool { DeviceSnapshot(batteryPercent = 50, charging = false) }.let { run(it, "battery").text() })
    }

    // ---- notes ----

    @Test
    fun `notes save and read`() {
        val tool = NotesTool(InMemoryNotesStore())
        assertEquals("no notes saved", run(tool, "{\"action\": \"read\"}").text())
        assertEquals("saved. you now have 1 note(s)", run(tool, "{\"action\": \"save\", \"text\": \"buy milk\"}").text())
        assertEquals("saved. you now have 2 note(s)", run(tool, "{\"action\": \"save\", \"note\": \"call Ana\"}").text())
        assertEquals("1. buy milk\n2. call Ana", run(tool, "{\"action\": \"read\"}").text())
    }

    @Test
    fun `notes accept plain text inputs`() {
        val store = InMemoryNotesStore()
        val tool = NotesTool(store)
        run(tool, "save: call Ana")
        run(tool, "buy bread")
        run(tool, "{\"text\": \"water plants\"}")
        run(tool, "guarda: leer un libro")
        assertEquals(listOf("call Ana", "buy bread", "water plants", "leer un libro"), store.all())
        assertTrue(run(tool, "read").text().startsWith("1. call Ana"))
        assertTrue(run(tool, "").text().startsWith("1. call Ana"))
        assertTrue(run(tool, "{}").text().startsWith("1. call Ana"))
        assertTrue(run(tool, "list").text().startsWith("1. call Ana"))
    }

    @Test
    fun `saving without text is an error and does not store anything`() {
        val store = InMemoryNotesStore()
        val tool = NotesTool(store)
        assertFalse(run(tool, "{\"action\": \"save\"}").isOk())
        assertFalse(run(tool, "save:").isOk())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `notes are cleaned and bounded`() {
        val store = InMemoryNotesStore()
        val tool = NotesTool(store)
        run(tool, "{\"action\": \"save\", \"text\": \"  a\\n\\n b   c \"}")
        assertEquals(listOf("a b c"), store.all())
        run(tool, "{\"action\": \"save\", \"text\": \"${"x".repeat(1_000)}\"}")
        assertEquals(NotesStore.MAX_NOTE_CHARS, store.all().last().length)
        repeat(NotesStore.MAX_NOTES + 5) { run(tool, "{\"action\": \"save\", \"text\": \"n$it\"}") }
        assertEquals(NotesStore.MAX_NOTES, store.all().size)
        assertEquals("n${NotesStore.MAX_NOTES + 4}", store.all().last())
    }

    @Test
    fun `in memory store clears`() {
        val store = InMemoryNotesStore(listOf("a"))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    // ---- web search ----

    @Test
    fun `web search formats the top results`() {
        val results = (1..5).map { WebResult("Title $it", "Description $it " + "x".repeat(300), "https://e.com/$it") }
        val tool = WebSearchAgentTool { WebSearchOutcome.Found(results) }
        val text = run(tool, "{\"query\": \"android\"}").text()
        assertEquals(3, text.lines().size)
        assertTrue(text.startsWith("1. Title 1 - Description 1"))
        assertTrue(text.contains("(https://e.com/1)"))
    }

    @Test
    fun `web search reports unavailability and missing query`() {
        val tool = WebSearchAgentTool { WebSearchOutcome.Unavailable(WebSearchFailure.QuotaReached) }
        val failed = run(tool, "kotlin")
        assertFalse(failed.isOk())
        assertTrue(failed.text().contains("QuotaReached"))
        assertFalse(run(tool, "").isOk())
    }

    @Test
    fun `web search passes the query through`() {
        var seen: String? = null
        val tool = WebSearchAgentTool { query -> seen = query; WebSearchOutcome.Unavailable(WebSearchFailure.NoResults) }
        run(tool, "{\"q\": \"weather madrid\"}")
        assertEquals("weather madrid", seen)
    }
}
