package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CritiqueParserTest {

    @Test
    fun `clean JSON is read as structured`() {
        val c = parseCritique("""{"verdict":"revise","issues":["Missing the units","Step 3 is wrong"]}""")
        assertEquals(Verdict.REVISE, c.verdict)
        assertEquals(listOf("Missing the units", "Step 3 is wrong"), c.issues)
        assertTrue(c.structured)
    }

    @Test
    fun `JSON inside a fence and surrounded by prose is found`() {
        val c = parseCritique("Sure! Here is my review:\n```json\n{\"verdict\": \"accept\", \"issues\": []}\n```\nHope it helps.")
        assertEquals(Verdict.ACCEPT, c.verdict)
        assertTrue(c.issues.isEmpty())
        assertTrue(c.structured)
    }

    @Test
    fun `trailing commas, single quotes and unquoted keys are tolerated`() {
        val c = parseCritique("{verdict: 'revise', issues: ['too long', 'no example',],}")
        assertEquals(Verdict.REVISE, c.verdict)
        assertEquals(listOf("too long", "no example"), c.issues)
    }

    @Test
    fun `verdict synonyms and Spanish words are understood`() {
        assertEquals(Verdict.ACCEPT, parseCritique("""{"verdict":"Approved"}""").verdict)
        assertEquals(Verdict.ACCEPT, parseCritique("""{"decision":"aceptar"}""").verdict)
        assertEquals(Verdict.ACCEPT, parseCritique("""{"veredicto":"Correcto."}""").verdict)
        assertEquals(Verdict.REVISE, parseCritique("""{"verdict":"needs_revision"}""").verdict)
        assertEquals(Verdict.REVISE, parseCritique("""{"verdict":"incorrect"}""").verdict)
        assertEquals(Verdict.REVISE, parseCritique("""{"status":"Rechazado"}""").verdict)
    }

    @Test
    fun `a boolean accept flag is understood`() {
        assertEquals(Verdict.ACCEPT, parseCritique("""{"approved": true}""").verdict)
        assertEquals(Verdict.REVISE, parseCritique("""{"accept": false, "issues": []}""").verdict)
    }

    @Test
    fun `issues may be objects, a single string with bullets, or under another key`() {
        val objects = parseCritique("""{"verdict":"revise","problems":[{"issue":"a"},{"description":"b"}]}""")
        assertEquals(listOf("a", "b"), objects.issues)
        val text = parseCritique("{\"verdict\":\"revise\",\"feedback\":\"- first\\n- second\"}")
        assertEquals(listOf("first", "second"), text.issues)
    }

    @Test
    fun `issues without a verdict mean revise`() {
        val c = parseCritique("""{"issues":["wrong total"]}""")
        assertEquals(Verdict.REVISE, c.verdict)
        assertEquals(listOf("wrong total"), c.issues)
    }

    @Test
    fun `accept with notes keeps the notes`() {
        val c = parseCritique("""{"verdict":"accept","issues":["could use a title"]}""")
        assertEquals(Verdict.ACCEPT, c.verdict)
        assertEquals(1, c.issues.size)
    }

    @Test
    fun `at most five issues and each is capped`() {
        val many = (1..9).joinToString(",") { "\"issue $it ${"x".repeat(400)}\"" }
        val c = parseCritique("""{"verdict":"revise","issues":[$many]}""")
        assertEquals(5, c.issues.size)
        assertTrue(c.issues.all { it.length <= 200 })
    }

    @Test
    fun `free text with a verdict line is recovered`() {
        val c = parseCritique("Verdict: REVISE\n- The answer ignores the second part\n- No units")
        assertEquals(Verdict.REVISE, c.verdict)
        assertEquals(listOf("The answer ignores the second part", "No units"), c.issues)
        assertFalse(c.structured)
    }

    @Test
    fun `free text with a bare keyword is recovered`() {
        assertEquals(Verdict.ACCEPT, parseCritique("I would accept this answer, it is complete.").verdict)
        assertEquals(Verdict.ACCEPT, parseCritique("LGTM").verdict)
    }

    @Test
    fun `bullets alone mean revise`() {
        val c = parseCritique("1. Wrong date\n2. Missing the name")
        assertEquals(Verdict.REVISE, c.verdict)
        assertEquals(2, c.issues.size)
    }

    @Test
    fun `garbage is unknown with no issues`() {
        listOf("", "???", "The weather is nice today.", "{ not json at all", "{\"foo\": 1}").forEach { raw ->
            val c = parseCritique(raw)
            assertEquals("for <$raw>", Verdict.UNKNOWN, c.verdict)
            assertTrue(c.issues.isEmpty())
        }
    }

    @Test
    fun `a truncated JSON object is not found but its keywords still are`() {
        assertNull(extractJsonObject("{\"verdict\":\"revise\",\"issues\":[\"cut off"))
        assertEquals(Verdict.REVISE, parseCritique("{\"verdict\":\"revise\",\"issues\":[\"cut off").verdict)
    }

    @Test
    fun `extractJsonObject skips a brace pair that is not JSON`() {
        val json = extractJsonObject("use {braces} like so, then {\"a\": 1}")
        assertNotNull(json)
        assertTrue(json!!.containsKey("a"))
    }

    @Test
    fun `braces inside strings do not confuse the matcher`() {
        val json = extractJsonObject("""{"verdict":"revise","issues":["missing } at the end"]}""")
        assertNotNull(json)
        assertEquals(Verdict.REVISE, parseCritique("""{"verdict":"revise","issues":["missing } at the end"]}""").verdict)
    }
}
