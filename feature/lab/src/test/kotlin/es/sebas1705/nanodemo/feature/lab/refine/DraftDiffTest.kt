package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftDiffTest {

    @Test
    fun `identical drafts have similarity one and no changes`() {
        val d = diffDrafts("One. Two. Three.", "One. Two. Three.")
        assertTrue(d.identical)
        assertEquals(1f, d.similarity, 0.001f)
        assertEquals(3, d.kept)
    }

    @Test
    fun `an added and a removed sentence are reported in order`() {
        val d = diffDrafts("Alpha is first. Beta is second.", "Alpha is first. Gamma is new. Beta is second.")
        assertEquals(1, d.added)
        assertEquals(0, d.removed)
        assertEquals(listOf(DiffKind.SAME, DiffKind.ADDED, DiffKind.SAME), d.lines.map { it.kind })
        assertEquals("Gamma is new.", d.lines[1].text)
    }

    @Test
    fun `a rewritten line shows as removed plus added`() {
        val d = diffDrafts("1. Mix flour\n2. Bake 10 min\n3. Serve", "1. Mix flour\n2. Bake 25 min\n3. Serve")
        assertEquals(1, d.added)
        assertEquals(1, d.removed)
        assertEquals(2, d.kept)
        assertEquals(2f / 3f, d.similarity, 0.001f)
    }

    @Test
    fun `comparison ignores case and spacing`() {
        val d = diffDrafts("The  answer is 42.", "the answer is 42.")
        assertTrue(d.identical)
    }

    @Test
    fun `completely different drafts have zero similarity`() {
        val d = diffDrafts("Cats purr.", "Dogs bark loudly.")
        assertEquals(0f, d.similarity, 0.001f)
        assertEquals(1, d.added)
        assertEquals(1, d.removed)
    }

    @Test
    fun `length delta is signed and empty drafts are handled`() {
        assertEquals(6, diffDrafts("abc", "abcdefghi").lengthDelta)
        assertEquals(-3, diffDrafts("abc", "").lengthDelta)
        val empty = diffDrafts("", "")
        assertTrue(empty.identical)
        assertEquals(1f, empty.similarity, 0.001f)
        assertEquals(1, diffDrafts("", "Something.").added)
    }
}
