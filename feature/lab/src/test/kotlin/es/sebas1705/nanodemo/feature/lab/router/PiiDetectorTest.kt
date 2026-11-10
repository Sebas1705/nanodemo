package es.sebas1705.nanodemo.feature.lab.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiDetectorTest {

    private fun kinds(text: String) = PiiDetector.detect(text).map { it.kind }
    private fun values(text: String, kind: PiiKind) = PiiDetector.detect(text).filter { it.kind == kind }.map { it.value }
    private fun assertClean(text: String) = assertEquals("expected no PII in: $text", emptyList<PiiMatch>(), PiiDetector.detect(text))

    // ---- validators ---------------------------------------------------------------------------------------

    @Test
    fun `luhn accepts real test cards and rejects altered ones`() {
        assertTrue(PiiValidators.luhn("4111111111111111"))
        assertTrue(PiiValidators.luhn("5500000000000004"))
        assertTrue(PiiValidators.luhn("378282246310005"))
        assertFalse(PiiValidators.luhn("4111111111111112"))
        assertFalse(PiiValidators.luhn("1234567890123456"))
    }

    @Test
    fun `iban mod 97 accepts the published examples and rejects a changed digit`() {
        assertTrue(PiiValidators.iban("ES9121000418450200051332"))
        assertTrue(PiiValidators.iban("GB82WEST12345698765432"))
        assertTrue(PiiValidators.iban("DE89370400440532013000"))
        assertFalse(PiiValidators.iban("ES9121000418450200051333"))
        assertFalse(PiiValidators.iban("ES91210004184502000513")) // right checksum family but wrong Spanish length
    }

    @Test
    fun `dni and nie control letters`() {
        assertTrue(PiiValidators.dni("12345678", 'Z'))
        assertTrue(PiiValidators.dni("12345678", 'z'))
        assertFalse(PiiValidators.dni("12345678", 'A'))
        assertTrue(PiiValidators.nie('X', "1234567", 'L'))
        assertFalse(PiiValidators.nie('X', "1234567", 'A'))
        assertFalse(PiiValidators.nie('W', "1234567", 'L'))
    }

    // ---- e-mail -------------------------------------------------------------------------------------------

    @Test
    fun `emails are found inside sentences without the trailing dot`() {
        assertEquals(listOf("ana.garcia+work@mail.example.com"), values("Escribe a ana.garcia+work@mail.example.com.", PiiKind.EMAIL))
        assertEquals(2, values("a@b.es y C.D@Example.ORG", PiiKind.EMAIL).size)
    }

    @Test
    fun `handles and incomplete addresses are not emails`() {
        assertClean("sígueme en @sebas1705 o escribe a nadie@")
        assertClean("la variable user@host sin dominio")
    }

    // ---- IBAN ---------------------------------------------------------------------------------------------

    @Test
    fun `iban is found with spaces without them and in lower case`() {
        assertEquals(listOf("ES91 2100 0418 4502 0005 1332"), values("Mi IBAN es ES91 2100 0418 4502 0005 1332 gracias", PiiKind.IBAN))
        assertEquals(listOf("ES9121000418450200051332"), values("cuenta ES9121000418450200051332.", PiiKind.IBAN))
        assertEquals(listOf("gb82 west 1234 5698 7654 32"), values("iban gb82 west 1234 5698 7654 32 ok", PiiKind.IBAN))
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), values("DE89 3704 0044 0532 0130 00", PiiKind.IBAN))
    }

    @Test
    fun `iban does not swallow words that follow it`() {
        assertEquals(listOf("ES91 2100 0418 4502 0005 1332"), values("ES91 2100 0418 4502 0005 1332 AND more", PiiKind.IBAN))
        assertEquals(listOf("ES91 2100 0418 4502 0005 1332"), values("ES91 2100 0418 4502 0005 1332 4821", PiiKind.IBAN))
    }

    @Test
    fun `iban shaped strings with a bad checksum are ignored`() {
        assertFalse(PiiKind.IBAN in kinds("ES91 2100 0418 4502 0005 1333"))
        assertClean("referencia ES00 1234 5678 9012 3456 7890")
    }

    // ---- cards --------------------------------------------------------------------------------------------

    @Test
    fun `cards are found with spaces dashes and without separators`() {
        assertEquals(listOf("4111 1111 1111 1111"), values("Pago con 4111 1111 1111 1111 caduca 12/28", PiiKind.CARD))
        assertEquals(listOf("5500-0000-0000-0004"), values("tarjeta 5500-0000-0000-0004", PiiKind.CARD))
        assertEquals(listOf("378282246310005"), values("amex 378282246310005", PiiKind.CARD))
    }

    @Test
    fun `sixteen digit order numbers that fail luhn are not cards`() {
        assertClean("Pedido 1234567890123456 enviado")
        assertClean("Tracking 9400111899223197428490 en tránsito")
        assertClean("Epoch 1767225600000 es la marca de tiempo")
    }

    @Test
    fun `a card number is not also reported as a phone`() {
        val found = PiiDetector.detect("4111 1111 1111 1111")
        assertEquals(listOf(PiiKind.CARD), found.map { it.kind })
    }

    // ---- DNI / NIE ----------------------------------------------------------------------------------------

    @Test
    fun `dni and nie with a valid control letter are found`() {
        assertEquals(listOf("12345678Z"), values("Mi DNI es 12345678Z.", PiiKind.DNI))
        assertEquals(listOf("12345678-z"), values("dni 12345678-z", PiiKind.DNI))
        assertEquals(listOf("X1234567L"), values("NIE X1234567L", PiiKind.NIE))
        assertEquals(listOf("x-1234567-l"), values("nie x-1234567-l", PiiKind.NIE))
    }

    @Test
    fun `dni and nie with a wrong control letter are not flagged`() {
        assertClean("Código 12345678A")
        assertClean("Ref X1234567A")
        assertClean("Matrícula 1234BCD y pieza 12345678 sin letra")
    }

    // ---- IPv4 ---------------------------------------------------------------------------------------------

    @Test
    fun `ipv4 addresses are found`() {
        assertEquals(listOf("192.168.1.42"), values("El servidor 192.168.1.42 no responde", PiiKind.IPV4))
        assertEquals(listOf("8.8.8.8"), values("DNS 8.8.8.8.", PiiKind.IPV4))
    }

    @Test
    fun `versions loopback and out of range octets are not ip addresses`() {
        assertClean("Actualiza a la versión 2.13.0 cuanto antes")
        assertClean("build v1.2.3.4 lanzado")
        assertClean("version 10.0.19045.5555")
        assertClean("escucha en 127.0.0.1 y 0.0.0.0")
        assertClean("rango 300.1.1.1 inválido")
        assertClean("capítulo 1.2.3 y 4.5")
    }

    // ---- phones -------------------------------------------------------------------------------------------

    @Test
    fun `spanish phone numbers in common groupings`() {
        assertEquals(listOf("+34 612 345 678"), values("Llámame al +34 612 345 678.", PiiKind.PHONE))
        assertEquals(listOf("612345678"), values("tel 612345678", PiiKind.PHONE))
        assertEquals(listOf("612 34 56 78"), values("tel 612 34 56 78", PiiKind.PHONE))
        assertEquals(listOf("0034 612-345-678"), values("fuera: 0034 612-345-678", PiiKind.PHONE))
        assertEquals(listOf("91 234 56 78"), values("oficina 91 234 56 78", PiiKind.PHONE))
    }

    @Test
    fun `international phone numbers`() {
        assertEquals(listOf("+44 20 7946 0958"), values("UK +44 20 7946 0958", PiiKind.PHONE))
        assertEquals(listOf("+1 (415) 555-2671"), values("US +1 (415) 555-2671 cell", PiiKind.PHONE))
        assertEquals(listOf("(415) 555-2671"), values("call (415) 555-2671", PiiKind.PHONE))
        assertEquals(listOf("415-555-2671"), values("call 415-555-2671", PiiKind.PHONE))
        assertEquals(listOf("+49 30 123456"), values("DE +49 30 123456", PiiKind.PHONE))
    }

    @Test
    fun `order numbers dates versions and amounts are not phones`() {
        assertClean("Pedido #4829103 confirmado")
        assertClean("ORD-2024-000123 y ORD-612345678")
        assertClean("Fecha 2026-05-12 o 12/05/2026 o 12.05.2026")
        assertClean("Reunión a las 10:30:45")
        assertClean("Total 1.234.567,89 € y 7.654.321.987 unidades")
        assertClean("Versión 3.14.159 del SDK")
        assertClean("Tiene 12345678 elementos")
        assertClean("Número 512345678 de socio") // 9 digits but not a Spanish phone prefix
    }

    // ---- URLs with credentials ----------------------------------------------------------------------------

    @Test
    fun `urls with a token a key or a password are found as a whole`() {
        assertEquals(
            listOf("https://api.example.com/v1/items?id=3&access_token=abcd1234efgh"),
            values("GET https://api.example.com/v1/items?id=3&access_token=abcd1234efgh y listo", PiiKind.URL),
        )
        assertEquals(listOf("https://maps.example.com/embed?key=AIzaSyA-FAKE-FAKE"), values("(https://maps.example.com/embed?key=AIzaSyA-FAKE-FAKE)", PiiKind.URL))
        assertEquals(listOf("postgres://admin:s3cr3t@db.internal:5432/app"), values("conn postgres://admin:s3cr3t@db.internal:5432/app", PiiKind.URL))
        assertEquals(1, values("https://example.com/reset/Ab12Cd34Ef56Gh78Ij90Kl12Mn", PiiKind.URL).size)
    }

    @Test
    fun `ordinary urls are not flagged`() {
        assertClean("Mira https://developer.android.com/guide/topics/ui/layout/constraintlayout para más")
        assertClean("https://example.com/search?q=kotlin&page=2")
        assertClean("https://example.com/docs/getting-started-with-the-sdk")
        assertClean("https://example.com/a?token=abc") // value too short to be a real token
    }

    // ---- API keys and secrets -----------------------------------------------------------------------------

    @Test
    fun `provider api keys are found`() {
        assertEquals(1, values("OPENAI=sk-proj-AbCdEfGhIjKlMnOpQrStUvWx", PiiKind.SECRET).size)
        assertEquals(1, values("clave AIzaSyFAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKE0 puesta", PiiKind.SECRET).size)
        assertEquals(1, values("aws AKIAIOSFODNN7EXAMPLE", PiiKind.SECRET).size)
        assertEquals(1, values("token ghp_FAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKE", PiiKind.SECRET).size)
        assertEquals(1, values("Authorization: Bearer abcdef0123456789abcdef0123456789", PiiKind.SECRET).size)
        assertEquals(1, values("jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.SflKxwRJSMeKKF2QT4fwpM", PiiKind.SECRET).size)
    }

    @Test
    fun `key value assignments mask only the value`() {
        val found = PiiDetector.detect("api_key = \"Zx81-qw9Lp0\" y password: hunter2!")
        assertEquals(listOf("Zx81-qw9Lp0", "hunter2!"), found.filter { it.kind == PiiKind.SECRET }.map { it.value })
    }

    @Test
    fun `plain words after a secret keyword are prose`() {
        assertClean("password: required")
        assertClean("el token: unidad de texto")
        assertClean("clave: segura")
    }

    // ---- ordering and overlap -----------------------------------------------------------------------------

    @Test
    fun `matches come back in text order without overlaps`() {
        val text = "ana@x.es 612345678 ES9121000418450200051332 12345678Z"
        val found = PiiDetector.detect(text)
        assertEquals(listOf(PiiKind.EMAIL, PiiKind.PHONE, PiiKind.IBAN, PiiKind.DNI), found.map { it.kind })
        assertTrue(found.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test
    fun `the kinds filter limits what is detected`() {
        val found = PiiDetector.detect("ana@x.es 612345678", setOf(PiiKind.EMAIL))
        assertEquals(listOf(PiiKind.EMAIL), found.map { it.kind })
    }

    @Test
    fun `a url token is not also reported as a secret`() {
        val found = PiiDetector.detect("https://x.example.com/p?api_key=sk-proj-AbCdEfGhIjKlMnOpQrStUvWx")
        assertEquals(listOf(PiiKind.URL), found.map { it.kind })
    }

    @Test
    fun `ordinary text has no pii`() {
        assertClean("¿Cuál es la capital de Francia? Resume el texto en tres puntos.")
        assertClean("Escribe una función en Kotlin que invierta una lista sin usar reversed().")
    }
}
