package es.sebas1705.nanodemo.feature.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Spanish file of each Lab resource group must define exactly the keys of the English default, with the
 * same format placeholders, so no screen falls back to English in Spanish or crashes on a missing argument.
 */
class LabStringsParityTest {

    private val groups = listOf("strings_refine.xml", "strings_agent.xml")
    private val placeholder = Regex("%(?:(\\d+)\\$)?[+\\-#0,(]*\\d*[sd]")

    private fun load(folder: String, file: String): Map<String, List<String>> {
        val xml = File("src/main/res/$folder/$file")
        assertTrue("missing ${xml.path}", xml.exists())
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement
        val out = sortedMapOf<String, List<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out["string:$name"] = placeholders(node.textContent)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        out["plurals:$name:${item.getAttribute("quantity")}"] = placeholders(item.textContent)
                    }
                }
            }
        }
        return out
    }

    /** Sorted placeholders of a text: "%1$d", "%2$s", or the bare conversion for unpositioned ones. */
    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text.replace("%%", "")).map { it.groupValues[1].ifEmpty { it.value.last().toString() } + it.value.last() }.sorted().toList()

    @Test
    fun `spanish defines the same keys and placeholders as english`() {
        groups.forEach { file ->
            val en = load("values", file)
            val es = load("values-es", file)
            assertEquals("keys of $file", en.keys, es.keys)
            en.forEach { (key, args) -> assertEquals("placeholders of $key in $file", args, es.getValue(key)) }
        }
    }

    @Test
    fun `every Lab key of these groups carries its package prefix`() {
        val prefixes = listOf("refine_", "judge_", "strategies_", "agent_")
        groups.forEach { file ->
            load("values", file).keys.forEach { key ->
                val name = key.substringAfter(':').substringBefore(':')
                assertTrue("$name has no expected prefix", prefixes.any { name.startsWith(it) })
            }
        }
    }
}
