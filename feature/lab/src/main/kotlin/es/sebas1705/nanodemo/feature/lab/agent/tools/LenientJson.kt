package es.sebas1705.nanodemo.feature.lab.agent.tools

/**
 * A forgiving JSON reader for what small models put after `Action Input:`. It accepts what strict parsers
 * reject and models produce all the time: single or smart quotes, unquoted keys, `=` instead of `:`, trailing
 * commas, missing commas between members, bare words as values and a truncated tail (open braces and strings
 * are closed at the end of the text). Values are `Map<String, Any?>`, `List<Any?>`, `String`, `Long`,
 * `Double`, `Boolean` or `null`.
 *
 * Pure Kotlin on purpose: `org.json` is only a stub in JVM unit tests and its strictness is the problem here.
 */
object LenientJson {
    private const val MAX_DEPTH = 20

    /** The top-level value of `text` (an object or an array), or `null` if there is none. */
    fun parse(text: String): Any? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            Reader(trimmed).readRoot()
        } catch (_: ParseFailure) {
            null
        }
    }

    /** `text` as an object, or `null` if it is not one. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?>? = parse(text) as? Map<String, Any?>

    private class ParseFailure : Exception()

    private class Reader(private val s: String) {
        private var pos = 0

        fun readRoot(): Any? {
            skipWs()
            return when (peek()) {
                '{', '[' -> readValue(0)
                else -> null
            }
        }

        private fun peek(): Char? = s.getOrNull(pos)

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseFailure()
            skipWs()
            val c = peek() ?: return null
            return when {
                c == '{' -> readObject(depth)
                c == '[' -> readArray(depth)
                isQuote(c) -> readString()
                c == '-' || c == '+' || c == '.' || c.isDigit() -> readNumberOrWord()
                else -> readBareWord()
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            while (true) {
                skipWs()
                val c = peek() ?: return map
                when (c) {
                    '}' -> { pos++; return map }
                    ',' -> pos++
                    else -> {
                        val key = readKey()
                        skipWs()
                        if (peek() == ':' || peek() == '=') pos++
                        val value = readValue(depth + 1)
                        if (key.isNotEmpty()) map[key] = value
                    }
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            while (true) {
                skipWs()
                val c = peek() ?: return list
                when (c) {
                    ']' -> { pos++; return list }
                    ',' -> pos++
                    else -> list += readValue(depth + 1)
                }
            }
        }

        private fun readKey(): String {
            skipWs()
            val c = peek() ?: return ""
            if (isQuote(c)) return readString()
            val start = pos
            while (pos < s.length && s[pos] != ':' && s[pos] != '=' && s[pos] != ',' && s[pos] != '}' && !s[pos].isWhitespace()) pos++
            if (pos == start) pos++ // never stall on a stray character
            return s.substring(start, pos).trim()
        }

        private fun readString(): String {
            val open = s[pos++]
            val close = closingQuote(open)
            val out = StringBuilder()
            while (pos < s.length) {
                val c = s[pos]
                if (c == '\\' && pos + 1 < s.length) {
                    pos++
                    when (val e = s[pos]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'u' -> {
                            val hex = s.substring(pos + 1, minOf(pos + 5, s.length))
                            val code = hex.toIntOrNull(16)
                            if (hex.length == 4 && code != null) {
                                out.append(code.toChar())
                                pos += 4
                            } else {
                                out.append('u')
                            }
                        }
                        else -> out.append(e) // \" \' \\ \/ and unknown escapes: the character itself
                    }
                    pos++
                } else if (c == close) {
                    pos++
                    return out.toString()
                } else {
                    out.append(c)
                    pos++
                }
            }
            return out.toString() // unterminated: keep what we have
        }

        private fun readNumberOrWord(): Any? {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] in "+-.")) pos++
            val token = s.substring(start, pos)
            token.toLongOrNull()?.let { return it }
            token.toDoubleOrNull()?.let { if (it.isFinite()) return it }
            // Something like "5km" or "-": keep it as text, a tool decides what to do with it.
            return token
        }

        private fun readBareWord(): Any? {
            val start = pos
            while (pos < s.length && s[pos] !in ",}]" && s[pos] != '\n') pos++
            if (pos == start) {
                pos++
                return null
            }
            return when (val word = s.substring(start, pos).trim()) {
                "true", "True", "TRUE" -> true
                "false", "False", "FALSE" -> false
                "null", "None", "nil", "NULL" -> null
                else -> word
            }
        }

        private fun isQuote(c: Char) = c == '"' || c == '\'' || c == '`' || c == '“' || c == '‘'

        private fun closingQuote(open: Char) = when (open) {
            '“' -> '”'
            '‘' -> '’'
            else -> open
        }
    }
}

/**
 * The input of a tool call: the raw text the model wrote plus, when it is an object, its members
 * (looked up case-insensitively). Tools ask for named parameters with a few synonyms and fall back to the raw
 * text, so `{"expression": "2+2"}`, `{'expr': '2+2'}` and a bare `2+2` all work.
 */
class ToolArgs(raw: String) {
    val raw: String = raw.trim()
    private val parsed: Map<String, Any?>? = LenientJson.parseObject(this.raw)
    private val members: Map<String, Any?> = parsed?.mapKeys { it.key.lowercase() }.orEmpty()
    val isObject: Boolean get() = parsed != null

    /** First non-blank parameter among [names], as text. */
    fun string(vararg names: String): String? {
        for (name in names) {
            val value = members[name.lowercase()] ?: continue
            val text = scalarText(value)?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    fun number(vararg names: String): Double? {
        for (name in names) {
            when (val value = members[name.lowercase()]) {
                is Number -> return value.toDouble()
                is String -> parseDecimal(value)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    /**
     * The main text input: a named parameter if present; otherwise the only member's value; otherwise the raw
     * input when it was not an object (quotes around it removed). `null` when there is nothing.
     */
    fun text(vararg names: String): String? {
        string(*names)?.let { return it }
        if (isObject) {
            val only = members.values.singleOrNull()
            return scalarText(only)?.trim()?.takeIf { it.isNotEmpty() }
        }
        return raw.trim('"', '\'', '`', '“', '”').trim().takeIf { it.isNotEmpty() }
    }

    private fun scalarText(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Double -> formatNumber(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> null // nested structures are not a scalar parameter
    }
}
