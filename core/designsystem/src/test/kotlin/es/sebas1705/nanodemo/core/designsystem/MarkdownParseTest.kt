package es.sebas1705.nanodemo.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParseTest {

    @Test
    fun `un parrafo de varias lineas es un solo bloque`() {
        val blocks = parseMarkdown("uno\ndos")
        assertEquals(listOf(MdBlock.Paragraph("uno\ndos")), blocks)
    }

    @Test
    fun `una linea en blanco separa parrafos`() {
        val blocks = parseMarkdown("uno\n\ndos")
        assertEquals(listOf(MdBlock.Paragraph("uno"), MdBlock.Paragraph("dos")), blocks)
    }

    @Test
    fun `titulos de nivel 1 a 3`() {
        val blocks = parseMarkdown("# A\n## B\n### C")
        assertEquals(
            listOf(MdBlock.Heading(1, "A"), MdBlock.Heading(2, "B"), MdBlock.Heading(3, "C")),
            blocks,
        )
    }

    @Test
    fun `viñetas con guion asterisco y punto`() {
        val blocks = parseMarkdown("- a\n* b\n• c")
        assertEquals(listOf("a", "b", "c"), blocks.map { (it as MdBlock.ListItem).text })
        assertTrue(blocks.all { (it as MdBlock.ListItem).marker == "•" })
    }

    @Test
    fun `listas numeradas conservan el numero`() {
        val blocks = parseMarkdown("1. a\n2) b")
        assertEquals(listOf("1.", "2."), blocks.map { (it as MdBlock.ListItem).marker })
    }

    @Test
    fun `bloque de codigo con lenguaje`() {
        val blocks = parseMarkdown("```kotlin\nval x = 1\n```")
        assertEquals(listOf(MdBlock.Code("kotlin", "val x = 1")), blocks)
    }

    @Test
    fun `un bloque de codigo sin cerrar se emite igual mientras el modelo sigue escribiendo`() {
        val blocks = parseMarkdown("texto\n```\nfun a()")
        assertEquals(listOf(MdBlock.Paragraph("texto"), MdBlock.Code("", "fun a()")), blocks)
    }

    @Test
    fun `dentro del codigo no se interpreta markdown`() {
        val blocks = parseMarkdown("```\n# no es titulo\n- ni lista\n```")
        assertEquals(listOf(MdBlock.Code("", "# no es titulo\n- ni lista")), blocks)
    }

    @Test
    fun `texto vacio no produce bloques`() {
        assertTrue(parseMarkdown("").isEmpty())
    }

    @Test
    fun `negrita cursiva y codigo en linea se aplican sin dejar los marcadores`() {
        val text = inlineMarkdown("**fuerte** y *suave* y `code`").text
        assertEquals("fuerte y suave y code", text)
    }

    @Test
    fun `los links muestran solo la etiqueta`() {
        assertEquals("ver Fuente 1", inlineMarkdown("ver [Fuente 1](https://example.com/a)").text)
    }

    @Test
    fun `un asterisco suelto no se confunde con cursiva`() {
        assertEquals("2 * 3 * 4", inlineMarkdown("2 * 3 * 4").text)
    }
}
