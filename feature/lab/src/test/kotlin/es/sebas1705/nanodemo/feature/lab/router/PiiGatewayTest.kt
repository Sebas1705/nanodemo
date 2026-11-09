package es.sebas1705.nanodemo.feature.lab.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiGatewayTest {

    private val sample =
        "Escribe a ana.garcia@example.com (tel. +34 612 345 678, DNI 12345678Z) y copia a ANA.GARCIA@example.com. " +
            "Mi IBAN es ES91 2100 0418 4502 0005 1332."

    // ---- redaction ----------------------------------------------------------------------------------------

    @Test
    fun `every datum is replaced by a stable numbered placeholder`() {
        val r = PiiGateway.redact(sample)

        assertEquals(
            "Escribe a <EMAIL_1> (tel. <PHONE_1>, DNI <DNI_1>) y copia a <EMAIL_1>. Mi IBAN es <IBAN_1>.",
            r.redacted,
        )
        assertEquals(listOf("<EMAIL_1>", "<PHONE_1>", "<DNI_1>", "<IBAN_1>"), r.replacements.map { it.placeholder })
        assertEquals(4, r.distinctCount)
        assertEquals(5, r.occurrenceCount)
        assertEquals(2, r.replacements.first { it.kind == PiiKind.EMAIL }.occurrences)
    }

    @Test
    fun `the redacted text contains none of the original values`() {
        val r = PiiGateway.redact(sample)
        r.replacements.forEach { assertFalse("leaked ${it.original}", r.redacted.contains(it.original, ignoreCase = true)) }
        assertTrue(PiiGateway.residual(r.redacted).isEmpty())
    }

    @Test
    fun `different values of the same kind are numbered in order of appearance`() {
        val r = PiiGateway.redact("a@x.es, b@x.es, a@x.es, c@x.es")
        assertEquals("<EMAIL_1>, <EMAIL_2>, <EMAIL_1>, <EMAIL_3>", r.redacted)
    }

    @Test
    fun `the same phone with and without the country code shares a placeholder`() {
        val r = PiiGateway.redact("+34 612 345 678 o 612345678 o 0034 612345678")
        assertEquals("<PHONE_1> o <PHONE_1> o <PHONE_1>", r.redacted)
    }

    @Test
    fun `text without pii comes back untouched`() {
        val text = "¿Cuál es la capital de Francia? Pedido 4821903, versión 2.1.0, fecha 2026-05-12."
        val r = PiiGateway.redact(text)
        assertEquals(text, r.redacted)
        assertTrue(r.isEmpty)
    }

    @Test
    fun `secrets keep their key name and lose only the value`() {
        val r = PiiGateway.redact("password: hunter2! y api_key=Zx81-qw9Lp0")
        assertEquals("password: <SECRET_1> y api_key=<SECRET_2>", r.redacted)
    }

    @Test
    fun `a placeholder already present in the user text is never reused`() {
        val r = PiiGateway.redact("El texto menciona <EMAIL_1> literalmente y ana@x.es")
        assertEquals("El texto menciona <EMAIL_1> literalmente y <EMAIL_2>", r.redacted)
        assertEquals("<EMAIL_2>", r.replacements.single().placeholder)
    }

    @Test
    fun `only the requested kinds are masked`() {
        val r = PiiGateway.redact("ana@x.es 612345678", setOf(PiiKind.EMAIL))
        assertEquals("<EMAIL_1> 612345678", r.redacted)
    }

    // ---- rehydration --------------------------------------------------------------------------------------

    @Test
    fun `an answer that keeps the placeholders is restored exactly`() {
        val r = PiiGateway.redact(sample)
        val answer = "Hecho: enviaré el correo a <EMAIL_1> y te llamaré al <PHONE_1>. Tu DNI <DNI_1> e <IBAN_1> constan."
        val back = PiiGateway.rehydrate(answer, r)

        assertEquals(
            "Hecho: enviaré el correo a ana.garcia@example.com y te llamaré al +34 612 345 678. " +
                "Tu DNI 12345678Z e ES91 2100 0418 4502 0005 1332 constan.",
            back.text,
        )
        assertEquals(4, back.restored.size)
        assertTrue(back.missing.isEmpty())
        assertTrue(back.unknown.isEmpty())
    }

    @Test
    fun `placeholders the model rewrites slightly are still restored`() {
        val r = PiiGateway.redact("ana@x.es y 612345678 y ES9121000418450200051332")
        val variants = listOf(
            "[EMAIL_1]", "{EMAIL_1}", "<email_1>", "<Email_1>", "EMAIL_1", "< EMAIL_1 >", "<EMAIL 1>", "<EMAIL-1>",
            "<EMAIL_1", "EMAIL_1>", "\\<EMAIL\\_1\\>", "&lt;EMAIL_1&gt;", "«EMAIL_1»",
        )
        variants.forEach { variant ->
            val back = PiiGateway.rehydrate("Escribe a $variant ahora", r)
            assertEquals("variant $variant", "Escribe a ana@x.es ahora", back.text)
        }
    }

    @Test
    fun `punctuation and markdown around a placeholder are preserved`() {
        val r = PiiGateway.redact("ana@x.es")
        assertEquals("**ana@x.es**, `ana@x.es`.", PiiGateway.rehydrate("**<EMAIL_1>**, `<EMAIL_1>`.", r).text)
        assertEquals("(ana@x.es)", PiiGateway.rehydrate("(<EMAIL_1>)", r).text)
    }

    @Test
    fun `bare words that merely resemble a placeholder are left alone`() {
        val r = PiiGateway.redact("ana@x.es")
        val prose = "El email 1 y el IP 2, o la url 3 y secret 4, o MY_EMAIL_1 no cambian."
        val back = PiiGateway.rehydrate(prose, r)
        assertEquals(prose, back.text)
        assertTrue(back.restored.isEmpty())
    }

    @Test
    fun `a placeholder the model invented is not substituted and is reported`() {
        val r = PiiGateway.redact("ana@x.es")
        val back = PiiGateway.rehydrate("Para <EMAIL_1> y también <EMAIL_7> o <PHONE_1>", r)
        assertEquals("Para ana@x.es y también <EMAIL_7> o <PHONE_1>", back.text)
        assertEquals(listOf("<EMAIL_7>", "<PHONE_1>"), back.unknown)
    }

    @Test
    fun `masks the model dropped are reported as missing`() {
        val r = PiiGateway.redact("ana@x.es y 612345678")
        val back = PiiGateway.rehydrate("Solo hablo de <EMAIL_1>.", r)
        assertEquals(listOf("<PHONE_1>"), back.missing.map { it.placeholder })
        assertEquals(listOf("<EMAIL_1>"), back.restored.map { it.placeholder })
    }

    @Test
    fun `adjacent placeholders and double digits do not collide`() {
        val emails = (1..12).joinToString(" ") { "u$it@x.es" }
        val r = PiiGateway.redact(emails)
        val back = PiiGateway.rehydrate(r.redacted, r)
        assertEquals(emails, back.text)
        // EMAIL_1 must not eat the prefix of EMAIL_10..12
        assertEquals("u10@x.es", PiiGateway.rehydrate("<EMAIL_10>", r).text)
        assertEquals("u1@x.es", PiiGateway.rehydrate("<EMAIL_1>0", r).text.take(7))
    }

    @Test
    fun `rehydrating with nothing redacted returns the answer as is`() {
        val r = PiiGateway.redact("hola")
        val back = PiiGateway.rehydrate("Respuesta con <EMAIL_1> inventado", r)
        assertEquals("Respuesta con <EMAIL_1> inventado", back.text)
    }

    @Test
    fun `redact then rehydrate round trips the original text`() {
        val r = PiiGateway.redact(sample)
        assertEquals(sample.replace("ANA.GARCIA@example.com", "ana.garcia@example.com"), PiiGateway.rehydrate(r.redacted, r).text)
    }

    @Test
    fun `no leak check flags a datum that slipped through`() {
        val leaked = PiiGateway.residual("Hola, soy ana@x.es")
        assertEquals(listOf(PiiKind.EMAIL), leaked.map { it.kind })
    }
}
