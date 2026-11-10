package es.sebas1705.nanodemo.feature.lab.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExpressionEvaluatorTest {

    private fun eval(expression: String) = ExpressionEvaluator.evaluateToString(expression)

    private fun assertFails(expression: String, messagePart: String? = null) {
        try {
            val value = ExpressionEvaluator.evaluate(expression)
            fail("expected a failure for \"$expression\" but got $value")
        } catch (e: ExpressionException) {
            if (messagePart != null) assertTrue("message was: ${e.message}", e.message!!.contains(messagePart))
        }
    }

    @Test
    fun `basic arithmetic and precedence`() {
        assertEquals("14", eval("2+3*4"))
        assertEquals("20", eval("(2+3)*4"))
        assertEquals("2.5", eval("10/4"))
        assertEquals("1", eval("10-4-5"))
        assertEquals("595", eval("(125 + 75) * 3 - 40 / 8"))
    }

    @Test
    fun `power is right associative and binds tighter than unary minus`() {
        assertEquals("512", eval("2^3^2"))
        assertEquals("-4", eval("-2^2"))
        assertEquals("4", eval("(-2)^2"))
        assertEquals("0.5", eval("2^-1"))
        assertEquals("8", eval("2**3"))
    }

    @Test
    fun `unary operators`() {
        assertEquals("-5", eval("-5"))
        assertEquals("5", eval("--5"))
        assertEquals("3", eval("+3"))
        assertEquals("1", eval("4+-3"))
    }

    @Test
    fun `modulo and percent signs`() {
        assertEquals("2", eval("17 % 5"))
        assertEquals("2", eval("5%3"))
        assertEquals("0.17", eval("17%"))
        assertEquals("1.5", eval("50% * 3"))
        assertEquals("0.3", eval("(10 + 5)% * 2"))
        assertEquals("99.9", eval("100 - 10%"))
    }

    @Test
    fun `percent of works as a multiplication`() {
        assertEquals("399.5", eval("17% of 2350"))
        assertEquals("399.5", eval("17 % of 2350"))
        assertEquals("30", eval("15% OF 200"))
    }

    @Test
    fun `functions`() {
        assertEquals("7", eval("sqrt(16)+abs(-3)"))
        assertEquals("3", eval("round(2.5)"))
        assertEquals("2.57", eval("round(2.567, 2)"))
        assertEquals("1", eval("min(3,1,2)"))
        assertEquals("5", eval("max(1,5)"))
        assertEquals("3", eval("ceil(2.1)"))
        assertEquals("2", eval("floor(2.9)"))
        assertEquals("4", eval("SQRT(16)"))
        assertEquals("10", eval("max(1, min(20, 10))"))
        assertEquals("5", eval("max(5)"))
    }

    @Test
    fun `constants`() {
        assertEquals(Math.PI, ExpressionEvaluator.evaluate("pi"), 1e-12)
        assertEquals(Math.E, ExpressionEvaluator.evaluate("e"), 1e-12)
        assertEquals("6.28318530718", eval("2*pi"))
    }

    @Test
    fun `numbers`() {
        assertEquals("0.5", eval(".5"))
        assertEquals("1001", eval("1e3+1"))
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333", eval("1/3"))
        assertEquals("1000000", eval("1000000"))
    }

    @Test
    fun `friendly notation is normalised`() {
        assertEquals("42", eval("6×7"))
        assertEquals("2", eval("10÷5"))
        assertEquals("3", eval("5−2"))
        assertEquals("4", eval("2+2="))
        assertEquals("4", eval("= 2+2"))
        assertEquals("4", eval("2 + 2 ?"))
    }

    @Test
    fun `division by zero is an error`() {
        assertFails("1/0", "division by zero")
        assertFails("5 % 0", "zero")
        assertFails("1/(2-2)", "division by zero")
    }

    @Test
    fun `syntax errors are errors`() {
        assertFails("")
        assertFails("   ")
        assertFails("2+")
        assertFails("*3")
        assertFails("(1+2")
        assertFails("1+2)")
        assertFails("2 3")
        assertFails("1.2.3")
        assertFails("2 +* 3")
        assertFails("()")
    }

    @Test
    fun `unknown names and functions are errors`() {
        assertFails("abc", "unknown name")
        assertFails("foo(1)", "unknown function")
        assertFails("sin(1)", "unknown function")
        assertFails("x+1")
    }

    @Test
    fun `domain and arity errors`() {
        assertFails("sqrt(-1)", "negative")
        assertFails("sqrt(1,2)")
        assertFails("round(1,2,3)")
        assertFails("round(1, 99)")
        assertFails("round(1, 0.5)")
        assertFails("min()")
        assertFails("abs()")
    }

    @Test
    fun `overflow and non finite results are errors`() {
        assertFails("9^9^9", "too large")
        assertFails("1e308*10", "finite")
        assertFails("(-8)^0.5")
    }

    @Test
    fun `there is no way to run code`() {
        assertFails("System.exit(0)")
        assertFails("Runtime.getRuntime()")
        assertFails("1; 2")
        assertFails("\"a\"+\"b\"")
        assertFails("`ls`")
        assertFails("\${1+1}")
    }

    @Test
    fun `absurd inputs are bounded`() {
        assertFails("1+".repeat(200), "too long")
        assertFails("(".repeat(80) + "1" + ")".repeat(80), "deeply")
        assertFails("-".repeat(100) + "1", "deeply")
    }

    @Test
    fun `reasonable nesting works`() {
        assertEquals("1", eval("(".repeat(20) + "1" + ")".repeat(20)))
    }
}
