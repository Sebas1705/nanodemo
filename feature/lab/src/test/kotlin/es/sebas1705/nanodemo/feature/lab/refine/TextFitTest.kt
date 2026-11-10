package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {

    @Test
    fun `fit leaves short text alone and cuts long text with an ellipsis`() {
        assertEquals("hola", "  hola ".fit(10))
        val cut = "abcdefghij".fit(5)
        assertEquals(5, cut.length)
        assertTrue(cut.endsWith("…"))
        assertEquals("", "abc".fit(0))
        assertEquals("…", "abc".fit(1))
    }

    @Test
    fun `fitShares keeps short texts whole and gives the leftover room to long ones`() {
        val task = "short task"
        val draft = "x".repeat(1000)
        val fitted = fitShares(listOf(task, draft), room = 100)
        assertEquals(task, fitted[0])
        assertEquals(90, fitted[1].length)
    }

    @Test
    fun `fitShares splits evenly when every text is too long`() {
        val fitted = fitShares(listOf("a".repeat(500), "b".repeat(500)), room = 100)
        assertEquals(listOf(50, 50), fitted.map { it.length })
    }

    @Test
    fun `fitShares never exceeds the room`() {
        val fitted = fitShares(listOf("a".repeat(70), "b".repeat(300), "c".repeat(900)), room = 400)
        assertTrue(fitted.sumOf { it.length } <= 400)
        assertEquals(70, fitted[0].length)
    }

    @Test
    fun `small contexts reserve room for the answer and shrink the prompt budget`() {
        val small = EngineProfile(contextTokens = 1024)
        assertEquals(256, small.outputTokens(512))
        assertEquals(100, small.outputTokens(100))
        // (1024 - 256 - 64) tokens * 4 chars
        assertEquals(2816, small.promptChars(outputTokens = 256))
        assertEquals(2816 - 100, small.promptChars(outputTokens = 256, fixedChars = 100))
        assertEquals(512, EngineProfile().outputTokens(512))
        assertTrue(EngineProfile().promptChars(256) > small.promptChars(256))
    }

    @Test
    fun `the prompt budget has a floor so a tiny context still gets some room`() {
        assertEquals(200, EngineProfile(contextTokens = 100).promptChars(outputTokens = 25, fixedChars = 5_000))
    }

    @Test
    fun `approxTokens rounds up`() {
        assertEquals(0, approxTokens(""))
        assertEquals(1, approxTokens("abc"))
        assertEquals(2, approxTokens("abcde"))
    }
}
