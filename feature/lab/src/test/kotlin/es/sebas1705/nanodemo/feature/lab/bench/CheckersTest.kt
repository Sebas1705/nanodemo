package es.sebas1705.nanodemo.feature.lab.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CheckersTest {

    @Test
    fun `numbers are read in both decimal conventions`() {
        assertEquals(listOf(399.5), extractNumbers("Son 399,5 euros"))
        assertEquals(listOf(399.5), extractNumbers("It is 399.5 euros"))
        assertEquals(listOf(1234.5), extractNumbers("1,234.5"))
        assertEquals(listOf(1234.5), extractNumbers("1.234,5"))
        assertEquals(listOf(1234.0), extractNumbers("1.234 personas"))
        assertEquals(listOf(3.0, -2.0, 0.75), extractNumbers("3 y -2 y 0.75"))
    }

    @Test
    fun `contains ignores case and accents and gives partial credit`() {
        assertEquals(1.0, Checkers.contains("Canberra").score("La capital es CANBERRA."), 0.0)
        assertEquals(1.0, Checkers.contains("estacion").score("¿Dónde está la estación?"), 0.0)
        assertEquals(0.5, Checkers.contains("good morning", "station").score("Good morning!"), 0.0)
    }

    @Test
    fun `word needs a whole word`() {
        assertEquals(1.0, Checkers.word("no").score("No."), 0.0)
        assertEquals(0.0, Checkers.word("no").score("Mi nombre es Ana"), 0.0)
        assertEquals(1.0, Checkers.word("carla").score("La más joven es Carla"), 0.0)
    }

    @Test
    fun `number accepts the first or the last number of the answer`() {
        val is150 = Checkers.number(150.0)
        assertEquals(1.0, is150.score("150"), 0.0)
        assertEquals(1.0, is150.score("150 minutos (2,5 horas)"), 0.0)
        assertEquals(1.0, is150.score("De 9:15 a 11:45 hay 150"), 0.0)
        assertEquals(0.0, is150.score("Unos 120 minutos"), 0.0)
        assertEquals(0.0, is150.score("no lo sé"), 0.0)
    }

    @Test
    fun `json checks fields through fences and surrounding prose`() {
        val checker = Checkers.json(mapOf("nombre" to "Lucía Pérez", "edad" to 34, "ciudad" to "Valencia"))
        assertEquals(1.0, checker.score("""{"nombre":"Lucia Perez","edad":34,"ciudad":"Valencia"}"""), 0.0)
        assertEquals(1.0, checker.score("Aquí tienes:\n```json\n{\"nombre\": \"Lucía Pérez\", \"edad\": 34.0, \"ciudad\": \"valencia\"}\n```"), 0.0)
        assertEquals(0.0, checker.score("""{"nombre":"Otra","edad":"34","ciudad":"Madrid"}"""), 0.0)
        assertEquals(0.0, checker.score("no es json"), 0.0)
    }

    @Test
    fun `json booleans are compared as booleans`() {
        val checker = Checkers.json(mapOf("ok" to true, "n" to 7))
        assertEquals(1.0, checker.score("""{"ok": true, "n": 7}"""), 0.0)
        assertEquals(0.5, checker.score("""{"ok": "true", "n": 7}"""), 0.0)
    }

    @Test
    fun `extractJson finds arrays and rejects broken json`() {
        assertNotNull(extractJson("Result: [1, 2, 3]"))
        assertNull(extractJson("{ not json"))
        assertNull(extractJson("nothing here"))
    }

    @Test
    fun `length and shape constraints`() {
        assertEquals(1.0, Checkers.wordsAtMost(10).score("Nieva suave sobre los tejados."), 0.0)
        assertEquals(0.0, Checkers.wordsAtMost(3).score("una frase bastante más larga que tres"), 0.0)
        assertEquals(0.0, Checkers.wordsAtMost(10).score(""), 0.0)
        assertEquals(1.0, Checkers.wordsExactly(3).score("azul, profundo, salado"), 0.0)
        assertEquals(1.0, Checkers.lines(3).score("manzana\npera\n\nnaranja\n"), 0.0)
        assertEquals(0.0, Checkers.lines(3).score("manzana, pera, naranja"), 0.0)
        assertEquals(1.0, Checkers.doesNotEndWith('.').score("azul, verde, gris"), 0.0)
        assertEquals(0.0, Checkers.doesNotEndWith('.').score("azul, verde, gris."), 0.0)
    }

    @Test
    fun `language tells a translation from an echo`() {
        assertEquals(1.0, Checkers.language("en").score("Good morning, where is the station?"), 0.0)
        assertEquals(0.0, Checkers.language("en").score("Buenos días, ¿dónde está la estación?"), 0.0)
        assertEquals(1.0, Checkers.language("es").score("Hoy hace buen tiempo en la ciudad"), 0.0)
    }

    @Test
    fun `allOf averages so a task passes only when every constraint holds`() {
        val both = Checkers.allOf(Checkers.contains("def suma"), Checkers.contains("return"))
        assertEquals(1.0, both.score("def suma(a, b):\n    return a + b"), 0.0)
        assertEquals(0.5, both.score("def suma(a, b): pass"), 0.0)
    }

    @Test
    fun `regex is case insensitive and spans lines`() {
        assertEquals(1.0, Checkers.regex("def\\s+suma").score("DEF   suma(a,b)"), 0.0)
        assertEquals(0.0, Checkers.regex("^4$").score("cuatro"), 0.0)
    }
}
