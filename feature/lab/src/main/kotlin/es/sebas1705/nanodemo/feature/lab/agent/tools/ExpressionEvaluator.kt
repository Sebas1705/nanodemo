package es.sebas1705.nanodemo.feature.lab.agent.tools

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** A readable reason why an expression could not be evaluated; the agent receives it as the observation. */
class ExpressionException(message: String) : Exception(message)

/**
 * Safe arithmetic evaluator: a recursive-descent parser over a closed grammar, with no `eval`, no script
 * engine and no reflection.
 *
 * ```
 * expr    := term (('+' | '-') term)*
 * term    := unary (('*' | '/' | '%' | 'of') unary)*
 * unary   := ('-' | '+') unary | power
 * power   := postfix ('^' unary)?                    right associative, so 2^3^2 = 2^9 and 2^-1 works
 * postfix := primary '%'*                            "17%" = 0.17
 * primary := number | constant | name '(' args ')' | '(' expr ')'
 * ```
 * `%` is a percent sign when nothing that starts an operand follows it (`17%`, `(10 + 5)% * 2`) and the
 * modulo operator otherwise (`17 % 5`). `17% of 2350` is `0.17 * 2350`. Functions: sqrt, abs, round (optionally
 * with digits), floor, ceil, min, max. Constants: pi, e.
 */
object ExpressionEvaluator {
    const val MAX_LENGTH = 200
    private const val MAX_DEPTH = 64
    private val FUNCTIONS = setOf("sqrt", "abs", "round", "floor", "ceil", "min", "max")

    /** Evaluates `expression`; throws [ExpressionException] on any problem (syntax, domain, overflow). */
    fun evaluate(expression: String): Double {
        val cleaned = normalise(expression)
        if (cleaned.isEmpty()) throw ExpressionException("empty expression")
        if (cleaned.length > MAX_LENGTH) throw ExpressionException("expression too long (max $MAX_LENGTH characters)")
        val result = Parser(cleaned).parseAll()
        if (result.isNaN() || result.isInfinite()) throw ExpressionException("result is not a finite number")
        return result
    }

    /** [evaluate] formatted for an observation (see [formatNumber]). */
    fun evaluateToString(expression: String): String = formatNumber(evaluate(expression))

    private fun normalise(raw: String): String = raw.trim()
        .replace('×', '*').replace('·', '*').replace('÷', '/').replace('−', '-')
        .replace("**", "^")
        .trim()
        .removePrefix("=").trim()
        .trimEnd('=', '?', '.', ' ')

    private class Parser(private val s: String) {
        private var pos = 0
        private var depth = 0

        fun parseAll(): Double {
            val value = expr()
            skipWs()
            if (pos < s.length) throw ExpressionException("unexpected '${s[pos]}' at position ${pos + 1}")
            return value
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipWs()
            return s.getOrNull(pos)
        }

        private fun enter() {
            if (++depth > MAX_DEPTH) throw ExpressionException("expression nested too deeply")
        }

        private fun leave() {
            depth--
        }

        private fun expr(): Double {
            var value = term()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + term() }
                    '-' -> { pos++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                val c = peek()
                value = when {
                    c == '*' -> { pos++; value * unary() }
                    c == '/' -> {
                        pos++
                        val divisor = unary()
                        if (divisor == 0.0) throw ExpressionException("division by zero")
                        value / divisor
                    }
                    c == '%' -> {
                        // Reached only when postfix() decided this is modulo.
                        pos++
                        val divisor = unary()
                        if (divisor == 0.0) throw ExpressionException("division by zero (modulo)")
                        value % divisor
                    }
                    atWord("of") -> { pos += 2; value * unary() }
                    else -> return value
                }
            }
        }

        private fun unary(): Double {
            enter()
            try {
                return when (peek()) {
                    '-' -> { pos++; -unary() }
                    '+' -> { pos++; unary() }
                    else -> power()
                }
            } finally {
                leave()
            }
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                pos++
                val exponent = unary()
                val result = base.pow(exponent)
                if (result.isNaN()) throw ExpressionException("invalid power")
                if (result.isInfinite()) throw ExpressionException("result too large")
                return result
            }
            return base
        }

        private fun postfix(): Double {
            var value = primary()
            while (peek() == '%' && percentFollows()) {
                pos++
                value /= 100.0
            }
            return value
        }

        /** True when the '%' at `pos` is a percent sign rather than the modulo operator. */
        private fun percentFollows(): Boolean {
            var i = pos + 1
            while (i < s.length && s[i].isWhitespace()) i++
            val next = s.getOrNull(i) ?: return true
            if (startsWord(i, "of")) return true
            return !(next.isDigit() || next == '.' || next == '(' || next.isLetter())
        }

        private fun startsWord(at: Int, word: String): Boolean =
            s.startsWith(word, at, ignoreCase = true) && !s.getOrElse(at + word.length) { ' ' }.isLetterOrDigit()

        private fun atWord(word: String): Boolean {
            skipWs()
            return pos < s.length && startsWord(pos, word)
        }

        private fun primary(): Double {
            val c = peek() ?: throw ExpressionException("unexpected end of expression")
            return when {
                c == '(' -> {
                    pos++
                    enter()
                    val value = expr()
                    leave()
                    expect(')')
                    value
                }
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> nameOrCall()
                else -> throw ExpressionException("unexpected '$c' at position ${pos + 1}")
            }
        }

        private fun expect(ch: Char) {
            if (peek() != ch) throw ExpressionException("expected '$ch' at position ${pos + 1}")
            pos++
        }

        private fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                var i = pos + 1
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i < s.length && s[i].isDigit()) {
                    while (i < s.length && s[i].isDigit()) i++
                    pos = i
                }
            }
            val text = s.substring(start, pos)
            return text.toDoubleOrNull() ?: throw ExpressionException("invalid number '$text'")
        }

        private fun nameOrCall(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isLetter() || s[pos].isDigit() || s[pos] == '_')) pos++
            val name = s.substring(start, pos).lowercase()
            if (peek() == '(') {
                if (name !in FUNCTIONS) {
                    throw ExpressionException("unknown function '$name' (allowed: ${FUNCTIONS.sorted().joinToString()})")
                }
                pos++
                enter()
                val args = mutableListOf<Double>()
                if (peek() != ')') {
                    args += expr()
                    while (peek() == ',') {
                        pos++
                        args += expr()
                    }
                }
                leave()
                expect(')')
                return call(name, args)
            }
            return when (name) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> throw ExpressionException("unknown name '$name' (constants: pi, e)")
            }
        }

        private fun call(name: String, args: List<Double>): Double {
            fun need(count: Int) {
                if (args.size != count) throw ExpressionException("$name() takes $count argument(s), got ${args.size}")
            }
            return when (name) {
                "sqrt" -> {
                    need(1)
                    if (args[0] < 0) throw ExpressionException("sqrt of a negative number")
                    sqrt(args[0])
                }
                "abs" -> { need(1); abs(args[0]) }
                "floor" -> { need(1); floor(args[0]) }
                "ceil" -> { need(1); ceil(args[0]) }
                "round" -> {
                    if (args.size !in 1..2) throw ExpressionException("round() takes 1 or 2 arguments, got ${args.size}")
                    val digits = args.getOrElse(1) { 0.0 }
                    if (digits != Math.rint(digits) || digits < 0 || digits > 10) {
                        throw ExpressionException("round() digits must be a whole number from 0 to 10")
                    }
                    BigDecimal(args[0]).setScale(digits.toInt(), RoundingMode.HALF_UP).toDouble()
                }
                "min" -> {
                    if (args.isEmpty()) throw ExpressionException("min() needs at least one argument")
                    args.min()
                }
                "max" -> {
                    if (args.isEmpty()) throw ExpressionException("max() needs at least one argument")
                    args.max()
                }
                else -> throw ExpressionException("unknown function '$name'")
            }
        }
    }
}
