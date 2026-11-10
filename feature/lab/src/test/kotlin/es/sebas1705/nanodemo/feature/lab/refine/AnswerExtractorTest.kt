package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerExtractorTest {

    private fun key(text: String, kind: AnswerKind) = extractAnswer(text, kind)?.key

    // ---- final segment ----

    @Test
    fun `the segment after the last marker wins`() {
        assertEquals("29", finalSegment("Change is 50 - 21.\nFinal answer: 29"))
        assertEquals("7", finalSegment("Answer: 3\nWait, recount.\nFinal answer: 7"))
        assertEquals("42", finalSegment("So the answer is 42."))
        assertEquals("17", finalSegment("Resultado: 12 + 5\nRespuesta final: 17"))
    }

    @Test
    fun `an answer on the line after the marker is found`() {
        assertEquals("Canberra", finalSegment("Final answer:\nCanberra\n"))
    }

    @Test
    fun `without a marker the last non-empty line is used`() {
        assertEquals("Lee", finalSegment("Tom > Sam > Lee\nLee\n\n"))
        assertEquals("", finalSegment("   \n  "))
    }

    @Test
    fun `markdown emphasis, quotes and boxed answers are stripped`() {
        assertEquals("29", finalSegment("**Final answer: 29**"))
        assertEquals("negative", finalSegment("Final answer: \"negative\"."))
        assertEquals("12", finalSegment("Thus \\boxed{12}"))
        assertEquals("1234", finalSegment("#### 1234"))
    }

    // ---- numbers ----

    @Test
    fun `numbers in both conventions normalise to one form`() {
        assertEquals("1234.5", normalizeNumber("1,234.5"))
        assertEquals("1234.5", normalizeNumber("1.234,5"))
        assertEquals("3.5", normalizeNumber("3,5"))
        assertEquals("1234", normalizeNumber("1,234"))
        assertEquals("1234567", normalizeNumber("1.234.567"))
        assertEquals("12", normalizeNumber("12.0"))
        assertEquals("12", normalizeNumber("012"))
        assertEquals("0", normalizeNumber("0.00"))
        assertEquals("-7", normalizeNumber("-7"))
        assertEquals("-7.25", normalizeNumber("−7.25"))
        assertNull(normalizeNumber("abc"))
    }

    @Test
    fun `a number is read from a sentence and equal numbers share a key`() {
        assertEquals("29", key("Final answer: 29 euros", AnswerKind.NUMBER))
        assertEquals("29", key("Final answer: 29.00", AnswerKind.NUMBER))
        assertEquals("29", key("Respuesta: son 29 €", AnswerKind.NUMBER))
        assertEquals("1500", key("Final answer: 1,500 km", AnswerKind.NUMBER))
        assertEquals("3.5", key("Final answer: 3,5 horas", AnswerKind.NUMBER))
        assertEquals("15", key("Final answer: 15%", AnswerKind.NUMBER))
    }

    @Test
    fun `after an equals sign the result is taken, not the operands`() {
        assertEquals("7", key("Final answer: 3 + 4 = 7", AnswerKind.NUMBER))
        assertEquals("17", key("She has 24/2 = 12, then 12 + 5 = 17 candies", AnswerKind.NUMBER))
    }

    @Test
    fun `no number means no answer`() {
        assertNull(extractAnswer("Final answer: unknown", AnswerKind.NUMBER))
        assertNull(extractAnswer("", AnswerKind.NUMBER))
    }

    // ---- yes / no ----

    @Test
    fun `yes and no in English and Spanish`() {
        assertEquals("yes", key("Final answer: Yes, all bloops are lazzies.", AnswerKind.YES_NO))
        assertEquals("yes", key("Respuesta final: Sí", AnswerKind.YES_NO))
        assertEquals("no", key("Final answer: No", AnswerKind.YES_NO))
        assertEquals("no", key("Final answer: False", AnswerKind.YES_NO))
        assertEquals("yes", key("Final answer: True.", AnswerKind.YES_NO))
        assertEquals("sí", extractAnswer("yes", AnswerKind.YES_NO)!!.display)
        assertNull(extractAnswer("Final answer: maybe", AnswerKind.YES_NO))
    }

    // ---- letters ----

    @Test
    fun `option letters are read in their usual spellings`() {
        assertEquals("B", key("Final answer: B", AnswerKind.LETTER))
        assertEquals("C", key("Final answer: (c) Paris", AnswerKind.LETTER))
        assertEquals("D", key("Final answer: D) 42", AnswerKind.LETTER))
        assertEquals("A", key("Final answer: option A", AnswerKind.LETTER))
        assertEquals("E", key("The correct answer is E.", AnswerKind.LETTER))
        assertNull(extractAnswer("Final answer: none of them", AnswerKind.LETTER))
    }

    // ---- phrases ----

    @Test
    fun `phrases ignore case, accents, punctuation and leading articles`() {
        assertEquals("canberra", key("Final answer: Canberra.", AnswerKind.PHRASE))
        assertEquals("canberra", key("Final answer: la CANBERRA", AnswerKind.PHRASE))
        assertEquals("sabado", key("Respuesta: Sábado", AnswerKind.PHRASE))
        assertEquals("sabado", key("Final answer: \"el sábado!\"", AnswerKind.PHRASE))
        assertEquals("marialopezexamplecom", key("Final answer: maria.lopez@example.com", AnswerKind.PHRASE))
    }

    @Test
    fun `a long phrase is capped`() {
        val e = extractAnswer("Final answer: " + "word ".repeat(40), AnswerKind.PHRASE)!!
        assertTrue(e.key.length <= 60)
        assertTrue(e.display.length <= 60)
    }

    // ---- auto ----

    @Test
    fun `auto detects the shape of one answer`() {
        assertEquals(AnswerKind.NUMBER, detectKind("Final answer: 29"))
        assertEquals(AnswerKind.YES_NO, detectKind("Final answer: Yes"))
        assertEquals(AnswerKind.YES_NO, detectKind("Respuesta: no, no lo es"))
        assertEquals(AnswerKind.LETTER, detectKind("Final answer: B"))
        assertEquals(AnswerKind.LETTER, detectKind("Final answer: (c)"))
        assertEquals(AnswerKind.PHRASE, detectKind("Final answer: Canberra"))
    }

    @Test
    fun `auto uses the most common shape so a stray answer cannot split the vote`() {
        val replies = listOf("Final answer: 12", "Final answer: about 12 apples", "Final answer: twelve", "Final answer: 12.0")
        val all = extractAll(replies, AnswerKind.AUTO)
        // three numbers vs one phrase: everything is read as a number; "twelve" has none
        assertEquals(listOf("12", "12", null, "12"), all.map { it?.key })
        assertTrue(all.filterNotNull().all { it.kind == AnswerKind.NUMBER })
    }

    @Test
    fun `auto ties prefer number over yes-no over letter over phrase`() {
        val all = extractAll(listOf("Final answer: 5", "Final answer: Madrid"), AnswerKind.AUTO)
        assertEquals(AnswerKind.NUMBER, all[0]!!.kind)
        assertNull(all[1])
    }

    @Test
    fun `an explicit kind overrides detection`() {
        val all = extractAll(listOf("Final answer: 12", "Final answer: Twelve"), AnswerKind.PHRASE)
        assertEquals(listOf("12", "twelve"), all.map { it!!.key })
    }

    @Test
    fun `empty replies give no answer`() {
        assertEquals(listOf(null, null), extractAll(listOf("", "  "), AnswerKind.AUTO))
    }

    // ---- matching the reference ----

    @Test
    fun `the reference answer is compared with the same normalisation`() {
        val num = extractAnswer("Final answer: 29.0", AnswerKind.NUMBER)
        assertTrue(matchesExpected("29", num))
        assertTrue(matchesExpected("Final answer: 29", num))
        assertFalse(matchesExpected("30", num))

        val phrase = extractAnswer("Final answer: la Canberra.", AnswerKind.PHRASE)
        assertTrue(matchesExpected("Canberra", phrase))
        assertFalse(matchesExpected("Sídney", phrase))

        assertFalse(matchesExpected("29", null))
        assertFalse(matchesExpected("", num))
    }
}
