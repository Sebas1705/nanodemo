package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.router.RouterTexts
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Texts for unit tests, read straight from the module's string resources (`values/` is English, `values-es/`
 * Spanish) so tests assert on the same text the app shows and the English copy lives in one place only. Resource
 * ids are mapped back to names by reflecting over the generated `R` class. Applies the Android escapes the
 * resources use (`\'`, `\"`, `\n`, `\uXXXX`).
 */
class XmlTexts(valuesDir: String = "values") : RagTexts, RouterTexts {

    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()
    private val stringNames = namesOf(R.string::class.java)
    private val pluralNames = namesOf(R.plurals::class.java)

    init {
        val dir = File("src/main/res/$valuesDir")
        dir.listFiles { f -> f.name.startsWith("strings_") && f.extension == "xml" }.orEmpty().forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                when (node.tagName) {
                    "string" -> strings[node.getAttribute("name")] = unescape(node.textContent)
                    "plurals" -> {
                        val items = node.getElementsByTagName("item")
                        plurals[node.getAttribute("name")] = (0 until items.length).associate {
                            val item = items.item(it) as Element
                            item.getAttribute("quantity") to unescape(item.textContent)
                        }
                    }
                }
            }
        }
    }

    override fun string(id: Int, vararg args: Any): String {
        val name = stringNames.getValue(id)
        return String.format(Locale.ENGLISH, strings[name] ?: error("Missing string $name"), *args)
    }

    override fun plural(id: Int, quantity: Int, vararg args: Any): String {
        val name = pluralNames.getValue(id)
        val items = plurals[name] ?: error("Missing plurals $name")
        val text = if (quantity == 1) items["one"] ?: items.getValue("other") else items.getValue("other")
        return String.format(Locale.ENGLISH, text, *args)
    }

    companion object {
        val english = XmlTexts("values")
        val spanish = XmlTexts("values-es")

        private fun namesOf(type: Class<*>): Map<Int, String> =
            type.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

        /** Android string rules without quoting: collapse whitespace, then resolve backslash escapes. */
        private fun unescape(raw: String): String {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    val n = text[i + 1]
                    when (n) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> {
                            out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(n)
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
