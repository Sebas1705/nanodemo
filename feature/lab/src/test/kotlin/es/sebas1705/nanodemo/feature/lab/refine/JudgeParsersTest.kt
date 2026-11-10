package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JudgeParsersTest {

    // ---- rubric ----

    @Test
    fun `clean rubric JSON is read`() {
        val s = parseRubric("""{"correctness":4,"completeness":3,"clarity":5}""")!!
        assertEquals(RubricScores(4, 3, 5), s)
        assertEquals(4.0, s.average!!, 0.0001)
    }

    @Test
    fun `rubric inside a fence with prose around it`() {
        val s = parseRubric("Here you go:\n```json\n{\"correctness\": 5, \"completeness\": 4, \"clarity\": 4}\n```")
        assertEquals(RubricScores(5, 4, 4), s)
    }

    @Test
    fun `scores nested under another key are found`() {
        val s = parseRubric("""{"scores":{"accuracy":2,"completeness":3,"clarity":4},"comment":"meh"}""")
        assertEquals(RubricScores(2, 3, 4), s)
    }

    @Test
    fun `string scores, fractions and decimals are normalised`() {
        val s = parseRubric("""{"correctness":"4/5","completeness":"3","clarity":4.4}""")
        assertEquals(RubricScores(4, 3, 4), s)
        assertEquals(5, parseRubric("""{"correctness":4.6}""")!!.correctness)
    }

    @Test
    fun `out of range scores are discarded and the others kept`() {
        val s = parseRubric("""{"correctness":9,"completeness":0,"clarity":3}""")!!
        assertNull(s.correctness)
        assertNull(s.completeness)
        assertEquals(3, s.clarity)
        assertEquals(3.0, s.average!!, 0.0001)
    }

    @Test
    fun `Spanish keys are understood`() {
        assertEquals(RubricScores(4, 5, 3), parseRubric("""{"corrección":4,"completitud":5,"claridad":3}"""))
    }

    @Test
    fun `rubric in prose lines is recovered`() {
        val s = parseRubric("Correctness: 4\nCompleteness - 3\nClarity = 5")
        assertEquals(RubricScores(4, 3, 5), s)
    }

    @Test
    fun `an unreadable rubric is null`() {
        listOf("", "I cannot judge this.", "{\"verdict\":\"good\"}", "{\"correctness\":\"high\"}").forEach {
            assertNull("for <$it>", parseRubric(it))
        }
    }

    // ---- pairwise pick ----

    @Test
    fun `JSON winners map to positions`() {
        assertEquals(Pick.FIRST, parsePick("""{"winner":"A","reason":"better"}"""))
        assertEquals(Pick.SECOND, parsePick("""{"winner":"B"}"""))
        assertEquals(Pick.TIE, parsePick("""{"winner":"tie"}"""))
        assertEquals(Pick.FIRST, parsePick("""{"winner":"Answer A"}"""))
        assertEquals(Pick.SECOND, parsePick("""{"best":2}"""))
        assertEquals(Pick.TIE, parsePick("""{"ganador":"empate"}"""))
        assertEquals(Pick.FIRST, parsePick("""{"winner":"first"}"""))
    }

    @Test
    fun `prose verdicts are recovered`() {
        assertEquals(Pick.SECOND, parsePick("Winner: B"))
        assertEquals(Pick.FIRST, parsePick("The winner is Answer A because it is complete."))
        assertEquals(Pick.FIRST, parsePick("Answer A is better than B."))
        assertEquals(Pick.SECOND, parsePick("The better answer is B."))
        assertEquals(Pick.TIE, parsePick("Winner: tie"))
        assertEquals(Pick.FIRST, parsePick("Ganador: A"))
    }

    @Test
    fun `a bare letter is a pick`() {
        assertEquals(Pick.FIRST, parsePick("A"))
        assertEquals(Pick.SECOND, parsePick(" **B**. "))
        assertEquals(Pick.TIE, parsePick("tie"))
    }

    @Test
    fun `the article a is not a vote`() {
        assertNull(parsePick("This is a good answer overall, hard to say."))
        assertNull(parsePick("It depends on a number of things."))
    }

    @Test
    fun `an unreadable pick is null`() {
        listOf("", "???", "{\"reason\":\"both fine\"}", "{\"winner\":\"maybe\"}").forEach {
            assertNull("for <$it>", parsePick(it))
        }
    }

    @Test
    fun `JSON takes precedence over prose`() {
        assertNotNull(parsePick("Winner: B\n{\"winner\":\"A\"}"))
        assertEquals(Pick.FIRST, parsePick("Winner: B\n{\"winner\":\"A\"}"))
    }
}
