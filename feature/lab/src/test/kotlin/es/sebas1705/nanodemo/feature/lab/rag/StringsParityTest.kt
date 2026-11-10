package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.router.PromptClassifier
import es.sebas1705.nanodemo.feature.lab.router.RoutePlanner
import es.sebas1705.nanodemo.feature.lab.router.RoutingPolicy
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The English and Spanish copies of the RAG and router strings must define the same keys with the same placeholders. */
class StringsParityTest {

    private class Entry(val kind: String, val items: Map<String, String>)

    private fun load(dir: String, file: String): Map<String, Entry> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$dir/$file")).documentElement
        val result = LinkedHashMap<String, Entry>()
        for (i in 0 until root.childNodes.length) {
            val node = root.childNodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            result[name] = when (node.tagName) {
                "string" -> Entry("string", mapOf("" to node.textContent))
                else -> {
                    val items = node.getElementsByTagName("item")
                    Entry("plurals", (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    })
                }
            }
        }
        return result
    }

    private fun placeholders(text: String): List<String> =
        Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    private fun checkParity(file: String) {
        val en = load("values", file)
        val es = load("values-es", file)
        assertEquals("keys of $file", en.keys, es.keys)
        for ((name, entry) in en) {
            val other = es.getValue(name)
            assertEquals("$name kind", entry.kind, other.kind)
            assertEquals("$name quantities", entry.items.keys, other.items.keys)
            if (entry.kind == "plurals") assertTrue("$name needs one and other", entry.items.keys.containsAll(listOf("one", "other")))
            // Every quantity of a plural, and the string itself, takes the same arguments in both languages.
            for ((quantity, text) in entry.items) {
                assertEquals("$name/$quantity placeholders", placeholders(text), placeholders(other.items.getValue(quantity)))
            }
        }
    }

    @Test
    fun `rag strings match in english and spanish`() = checkParity("strings_rag.xml")

    @Test
    fun `router strings match in english and spanish`() = checkParity("strings_router.xml")

    @Test
    fun `plurals read correctly for one and many in both languages`() {
        assertEquals("1 chunk", XmlTexts.english.plural(R.plurals.rag_stat_chunks, 1, 1))
        assertEquals("3 chunks", XmlTexts.english.plural(R.plurals.rag_stat_chunks, 3, 3))
        assertEquals("1 fragmento", XmlTexts.spanish.plural(R.plurals.rag_stat_chunks, 1, 1))
        assertEquals("2 escalados", XmlTexts.spanish.plural(R.plurals.router_escalations_count, 2, 2))
        assertEquals("1 escalation", XmlTexts.english.plural(R.plurals.router_escalations_count, 1, 1))
    }

    @Test
    fun `the router logic speaks the language of the texts it is given`() {
        val c = PromptClassifier.classify("¿Cuál es la capital de Francia?", false, XmlTexts.spanish)
        assertTrue(c.summary(XmlTexts.spanish).startsWith("complejidad simple"))
        assertTrue(c.summary(XmlTexts.english).startsWith("complexity simple"))
        val plan = RoutePlanner.plan(RoutingPolicy.MANUAL, c, emptyList(), XmlTexts.spanish)
        assertEquals("bloqueada: no hay ningún motor disponible", plan.describe(XmlTexts.spanish))
    }
}
