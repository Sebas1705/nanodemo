package es.sebas1705.nanodemo.feature.lab.agent.tools

import es.sebas1705.nanodemo.core.common.DemoTools
import es.sebas1705.nanodemo.core.common.WebSearchOutcome
import es.sebas1705.nanodemo.core.common.WebSearchTool
import java.text.Normalizer
import java.time.ZoneId

/** Evaluates arithmetic with [ExpressionEvaluator]. Percentages: the model rewrites "17% of 2350" or writes it as is. */
class CalculatorTool : Tool {
    override val name = "calculator"
    override val aliases = setOf("calc", "math", "calculate")
    override val description = "Exact arithmetic: + - * / ^ % ( ) sqrt abs round min max. Use it for any calculation; percentages like 17% of 2350 work."
    override val parameterHint = """{"expression": "17% of 2350"}"""

    override suspend fun execute(args: ToolArgs): ToolResult {
        val expression = args.text("expression", "expr", "input", "calculation", "formula", "query")
            ?: return ToolResult.Error("missing \"expression\"")
        return try {
            ToolResult.Ok(ExpressionEvaluator.evaluateToString(expression))
        } catch (e: ExpressionException) {
            ToolResult.Error("${e.message}. Write a plain arithmetic expression, e.g. (125+75)*3-40/8")
        }
    }
}

/** Converts length, mass, volume and temperature units with [UnitConverter]. */
class UnitConverterTool : Tool {
    override val name = "unit_converter"
    override val aliases = setOf("convert", "converter", "unit_conversion", "units")
    override val description = "Converts units: ${UnitConverter.supportedSummary}."
    override val parameterHint = """{"value": 72, "from": "F", "to": "C"}"""

    override suspend fun execute(args: ToolArgs): ToolResult {
        val value = args.number("value", "amount", "number", "quantity")
        val from = args.string("from", "from_unit", "unit", "source")
        val to = args.string("to", "to_unit", "target", "into")
        val (v, f, t) = if (value != null && from != null && to != null) {
            Triple(value, from, to)
        } else {
            val text = args.text("query", "input", "text") ?: return ToolResult.Error("need value, from and to")
            UnitConverter.parseQuery(text) ?: return ToolResult.Error("need value, from and to, e.g. {\"value\": 5, \"from\": \"km\", \"to\": \"mi\"}")
        }
        return try {
            ToolResult.Ok(UnitConverter.convert(v, f, t).describe())
        } catch (e: ConversionException) {
            ToolResult.Error(e.message ?: "conversion failed")
        }
    }
}

/** Maps what a model writes ("Madrid", "tokio", "europe/madrid", "UTC+2") to an IANA zone id. */
object ZoneResolver {
    private val aliases = mapOf(
        "tokio" to "Asia/Tokyo", "japon" to "Asia/Tokyo",
        "nueva york" to "America/New_York", "new york" to "America/New_York", "nyc" to "America/New_York",
        "londres" to "Europe/London", "london" to "Europe/London", "reino unido" to "Europe/London",
        "paris" to "Europe/Paris", "francia" to "Europe/Paris",
        "roma" to "Europe/Rome", "italia" to "Europe/Rome",
        "berlin" to "Europe/Berlin", "alemania" to "Europe/Berlin",
        "espana" to "Europe/Madrid", "spain" to "Europe/Madrid", "peninsula" to "Europe/Madrid",
        "canarias" to "Atlantic/Canary", "islas canarias" to "Atlantic/Canary",
        "mexico" to "America/Mexico_City", "ciudad de mexico" to "America/Mexico_City",
        "california" to "America/Los_Angeles", "san francisco" to "America/Los_Angeles",
        "pekin" to "Asia/Shanghai", "beijing" to "Asia/Shanghai", "china" to "Asia/Shanghai",
        "moscu" to "Europe/Moscow", "el cairo" to "Africa/Cairo", "estambul" to "Europe/Istanbul",
        "sidney" to "Australia/Sydney", "sydney" to "Australia/Sydney",
        "bogota" to "America/Bogota", "lima" to "America/Lima", "santiago de chile" to "America/Santiago",
        "argentina" to "America/Argentina/Buenos_Aires", "buenos aires" to "America/Argentina/Buenos_Aires",
        "utc" to "UTC", "gmt" to "UTC", "z" to "UTC",
    )

    private val idsByLower: Map<String, String> by lazy { ZoneId.getAvailableZoneIds().associateBy { it.lowercase() } }

    /** The IANA id for `input`, or `null` if nothing matches. */
    fun resolve(input: String): String? {
        val trimmed = input.trim().trim('"', '\'', '`')
        if (trimmed.isEmpty()) return null
        idsByLower[trimmed.lowercase()]?.let { return it }
        val key = normalise(trimmed)
        aliases[key]?.let { return it }
        // Offsets such as "UTC+2" or "GMT-5" are valid for java.time.
        runCatching { ZoneId.of(trimmed.uppercase().replace(" ", "")) }.getOrNull()?.let { return it.id }
        // "Madrid" / "Buenos Aires" -> the zone whose last segment is that city.
        val city = key.replace(' ', '_')
        idsByLower.entries.firstOrNull { it.key.substringAfterLast('/') == city }?.let { return it.value }
        return null
    }

    private fun normalise(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[_\\s]+"), " ").trim()
}

/** Current date and time in a time zone; reuses [DemoTools] so the tools tab and the agent agree. */
class CurrentTimeTool(
    private val now: (String) -> Map<String, String> = { DemoTools.executeCurrentTime(it) },
) : Tool {
    override val name = "current_time"
    override val aliases = setOf(DemoTools.GET_CURRENT_TIME, "time", "clock", "date", "now", "current_date")
    override val description = "Current date and time in a time zone. You have no clock: always use it for 'what time/day is it'."
    override val parameterHint = """{"timezone": "Europe/Madrid"}"""

    override suspend fun execute(args: ToolArgs): ToolResult {
        val requested = args.text("timezone", "zone", "tz", "city", "location", "place") ?: "UTC"
        val zone = ZoneResolver.resolve(requested)
            ?: return ToolResult.Error("unknown time zone \"$requested\". Use an IANA name such as Europe/Madrid or Asia/Tokyo")
        val result = now(zone)
        return ToolResult.Ok("$zone: ${result["legible"]} (${result["iso8601"]})")
    }
}

enum class NetworkKind(val label: String) {
    WIFI("wifi"), CELLULAR("cellular"), ETHERNET("ethernet"), OTHER("other"), NONE("offline"), UNKNOWN("unknown"),
}

/** A reading of the device state; `null` fields could not be read. */
data class DeviceSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val network: NetworkKind = NetworkKind.UNKNOWN,
    val freeRamMb: Long? = null,
    val totalRamMb: Long? = null,
    val freeStorageMb: Long? = null,
    val totalStorageMb: Long? = null,
)

/** Source of [DeviceSnapshot]; the Android implementation reads system services, tests and the benchmark use a fake. */
fun interface DeviceInfoProvider {
    fun snapshot(): DeviceSnapshot
}

/** Battery, connectivity, memory and storage of this phone. */
class DeviceInfoTool(private val provider: DeviceInfoProvider) : Tool {
    override val name = "device_info"
    override val aliases = setOf("device", "battery", "phone_info", "system_info", "get_device_info")
    override val description = "State of this phone: battery level and charging, network type, free RAM, free storage."
    override val parameterHint = """{"field": "battery|network|memory|storage|all"}"""

    override suspend fun execute(args: ToolArgs): ToolResult {
        val field = (args.text("field", "info", "what", "query") ?: "all").lowercase()
        val s = provider.snapshot()
        val battery = s.batteryPercent?.let { "battery: $it%" + (s.charging?.let { c -> if (c) ", charging" else ", not charging" } ?: "") } ?: "battery: unavailable"
        val network = "network: ${s.network.label}"
        val memory = if (s.freeRamMb != null && s.totalRamMb != null) "free RAM: ${s.freeRamMb} MB of ${s.totalRamMb} MB" else "RAM: unavailable"
        val storage = if (s.freeStorageMb != null && s.totalStorageMb != null) {
            "free storage: ${formatNumber(s.freeStorageMb / 1024.0, 4)} GB of ${formatNumber(s.totalStorageMb / 1024.0, 4)} GB"
        } else {
            "storage: unavailable"
        }
        val lines = when {
            "batter" in field || "charg" in field || "bater" in field -> listOf(battery)
            "net" in field || "wifi" in field || "conn" in field || "red" in field -> listOf(network)
            "mem" in field || "ram" in field -> listOf(memory)
            "stor" in field || "disk" in field || "alm" in field -> listOf(storage)
            else -> listOf(battery, network, memory, storage)
        }
        return ToolResult.Ok(lines.joinToString("\n"))
    }
}

/** Where the agent keeps its short notes; the Android implementation persists them, tests use memory. */
interface NotesStore {
    fun all(): List<String>

    /** Adds a note (the oldest is dropped beyond [MAX_NOTES]); returns how many notes there are now. */
    fun add(text: String): Int

    fun clear()

    companion object {
        const val MAX_NOTES = 30
        const val MAX_NOTE_CHARS = 300
    }
}

class InMemoryNotesStore(initial: List<String> = emptyList()) : NotesStore {
    private val notes = initial.toMutableList()

    @Synchronized override fun all(): List<String> = notes.toList()

    @Synchronized override fun add(text: String): Int {
        notes += text
        while (notes.size > NotesStore.MAX_NOTES) notes.removeAt(0)
        return notes.size
    }

    @Synchronized override fun clear() = notes.clear()
}

/** Saves and reads short notes. Input is JSON with `action` ("save" | "read") or plain text. */
class NotesTool(private val store: NotesStore) : Tool {
    override val name = "notes"
    override val aliases = setOf("note", "memo", "notepad", "save_note", "read_notes")
    override val description = "Short persistent notes. action=save stores a note, action=read lists all saved notes."
    override val parameterHint = """{"action": "save", "text": "buy milk"} or {"action": "read"}"""
    override val maxOutputChars = 900

    private val saveWords = Regex("""^(save|add|write|store|guardar|guarda|anota|anotar|apunta)\b[\s:\-]*(.*)$""", RegexOption.IGNORE_CASE)
    private val readWords = Regex("""^(read|list|show|get|view|leer|lee|ver|listar)\b.*$""", RegexOption.IGNORE_CASE)

    override suspend fun execute(args: ToolArgs): ToolResult {
        val explicit = args.string("action", "operation", "op", "mode", "command")?.lowercase()
        val text = args.string("text", "note", "content", "body", "message", "value")
        val isSave: Boolean
        val noteText: String?
        when {
            explicit != null -> {
                isSave = saveWords.matches(explicit) || explicit.startsWith("sav") || explicit.startsWith("add") || explicit.startsWith("writ")
                noteText = text
            }
            text != null -> { isSave = true; noteText = text }
            args.isObject -> { isSave = false; noteText = null }
            else -> {
                val raw = args.text().orEmpty()
                val save = saveWords.matchEntire(raw)
                when {
                    raw.isBlank() -> { isSave = false; noteText = null }
                    save != null -> { isSave = true; noteText = save.groupValues[2].trim().trim('"', '\'').ifEmpty { null } }
                    readWords.matches(raw) -> { isSave = false; noteText = null }
                    else -> { isSave = true; noteText = raw }
                }
            }
        }
        if (!isSave) {
            val notes = store.all()
            return ToolResult.Ok(
                if (notes.isEmpty()) "no notes saved" else notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n"),
            )
        }
        val clean = noteText?.replace(Regex("\\s+"), " ")?.trim()?.take(NotesStore.MAX_NOTE_CHARS)
        if (clean.isNullOrEmpty()) return ToolResult.Error("missing \"text\" to save")
        val count = store.add(clean)
        return ToolResult.Ok("saved. you now have $count note(s)")
    }
}

/** Web search through the Brave integration of the app; only offered when `WebSearchTool.isConfigured`. */
class WebSearchAgentTool(
    private val search: suspend (String) -> WebSearchOutcome = WebSearchTool::search,
) : Tool {
    override val name = "web_search"
    override val aliases = setOf("search", "web", "google", "internet_search", "browse")
    override val description = "Searches the web for current facts, news or anything you do not know. Returns the top results."
    override val parameterHint = """{"query": "latest Android version"}"""
    override val timeoutMs = 20_000L
    override val maxOutputChars = 900

    override suspend fun execute(args: ToolArgs): ToolResult {
        val query = args.text("query", "q", "search", "text", "input") ?: return ToolResult.Error("missing \"query\"")
        return when (val outcome = search(query)) {
            is WebSearchOutcome.Unavailable -> ToolResult.Error("search unavailable: ${outcome.reason}")
            is WebSearchOutcome.Found -> ToolResult.Ok(
                outcome.results.take(RESULTS).mapIndexed { i, r ->
                    "${i + 1}. ${r.title} - ${r.description.take(DESCRIPTION_CHARS)} (${r.url})"
                }.joinToString("\n"),
            )
        }
    }

    private companion object {
        const val RESULTS = 3
        const val DESCRIPTION_CHARS = 150
    }
}
