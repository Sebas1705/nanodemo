package es.sebas1705.nanodemo.feature.lab.agent.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Formats a double for a tool observation: integers without a decimal point, everything else rounded to
 * [significant] significant digits with trailing zeros removed, so floating point noise such as
 * `0.1 + 0.2 = 0.30000000000000004` or `0.17 * 2350 = 399.50000000000006` never reaches the model.
 */
fun formatNumber(value: Double, significant: Int = 12): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    if (abs(value) < 1e15 && value == Math.rint(value)) return value.toLong().toString()
    val rounded = BigDecimal(value).round(MathContext(significant, RoundingMode.HALF_UP)).stripTrailingZeros()
    return if (abs(value) >= 1e15 || abs(value) < 1e-6) rounded.toString() else rounded.toPlainString()
}

/** "72", "72.5", "2,5" (decimal comma) -> number; anything else -> `null`. */
internal fun parseDecimal(text: String): Double? {
    val t = text.trim().replace(" ", "")
    val normalised = if (t.count { it == ',' } == 1 && '.' !in t) t.replace(',', '.') else t.replace(",", "")
    return normalised.toDoubleOrNull()?.takeIf { it.isFinite() }
}
