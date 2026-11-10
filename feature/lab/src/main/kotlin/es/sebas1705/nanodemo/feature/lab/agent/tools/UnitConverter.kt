package es.sebas1705.nanodemo.feature.lab.agent.tools

import java.text.Normalizer

/** A readable reason why a conversion is impossible; the agent receives it as the observation. */
class ConversionException(message: String) : Exception(message)

enum class UnitCategory { LENGTH, MASS, VOLUME, TEMPERATURE }

/** One unit: `toBase` converts a value to the category's base unit (metre, kilogram, litre). Temperature is special-cased. */
data class UnitDef(val symbol: String, val category: UnitCategory, val toBase: Double = 1.0)

data class Conversion(val input: Double, val from: UnitDef, val output: Double, val to: UnitDef) {
    fun describe(): String = "${formatNumber(input, 8)} ${from.symbol} = ${formatNumber(output, 8)} ${to.symbol}"
}

/**
 * Unit conversion for length, mass, volume (US customary) and temperature, with English and Spanish names
 * ("miles", "millas", "pulgadas", "grados Celsius"...). Pure and table-driven.
 */
object UnitConverter {
    private val units: Map<String, UnitDef> = buildMap {
        fun add(def: UnitDef, vararg names: String) {
            put(normaliseName(def.symbol), def)
            names.forEach { put(normaliseName(it), def) }
        }

        val m = UnitDef("m", UnitCategory.LENGTH)
        add(m, "meter", "meters", "metre", "metres", "metro", "metros")
        add(UnitDef("km", UnitCategory.LENGTH, 1000.0), "kilometer", "kilometers", "kilometre", "kilometres", "kilometro", "kilometros")
        add(UnitDef("cm", UnitCategory.LENGTH, 0.01), "centimeter", "centimeters", "centimetre", "centimetro", "centimetros")
        add(UnitDef("mm", UnitCategory.LENGTH, 0.001), "millimeter", "millimeters", "millimetre", "milimetro", "milimetros")
        add(UnitDef("mi", UnitCategory.LENGTH, 1609.344), "mile", "miles", "milla", "millas")
        add(UnitDef("yd", UnitCategory.LENGTH, 0.9144), "yard", "yards", "yarda", "yardas")
        add(UnitDef("ft", UnitCategory.LENGTH, 0.3048), "foot", "feet", "pie", "pies")
        add(UnitDef("in", UnitCategory.LENGTH, 0.0254), "inch", "inches", "pulgada", "pulgadas")

        add(UnitDef("kg", UnitCategory.MASS), "kilogram", "kilograms", "kilogramo", "kilogramos", "kilo", "kilos")
        add(UnitDef("g", UnitCategory.MASS, 0.001), "gram", "grams", "gramo", "gramos")
        add(UnitDef("mg", UnitCategory.MASS, 0.000001), "milligram", "milligrams", "miligramo", "miligramos")
        add(UnitDef("t", UnitCategory.MASS, 1000.0), "tonne", "tonnes", "tonelada", "toneladas", "ton")
        add(UnitDef("lb", UnitCategory.MASS, 0.45359237), "lbs", "pound", "pounds", "libra", "libras")
        add(UnitDef("oz", UnitCategory.MASS, 0.028349523125), "ounce", "ounces", "onza", "onzas")

        add(UnitDef("l", UnitCategory.VOLUME), "liter", "liters", "litre", "litres", "litro", "litros")
        add(UnitDef("ml", UnitCategory.VOLUME, 0.001), "milliliter", "milliliters", "mililitro", "mililitros")
        add(UnitDef("cl", UnitCategory.VOLUME, 0.01), "centiliter", "centilitro", "centilitros")
        add(UnitDef("gal", UnitCategory.VOLUME, 3.785411784), "gallon", "gallons", "galon", "galones")
        add(UnitDef("qt", UnitCategory.VOLUME, 0.946352946), "quart", "quarts", "cuarto", "cuartos")
        add(UnitDef("pt", UnitCategory.VOLUME, 0.473176473), "pint", "pints", "pinta", "pintas")
        add(UnitDef("cup", UnitCategory.VOLUME, 0.2365882365), "cups", "taza", "tazas")
        add(UnitDef("fl oz", UnitCategory.VOLUME, 0.0295735295625), "floz", "fluid ounce", "fluid ounces", "onza liquida")
        add(UnitDef("tbsp", UnitCategory.VOLUME, 0.01478676478125), "tablespoon", "tablespoons", "cucharada", "cucharadas")
        add(UnitDef("tsp", UnitCategory.VOLUME, 0.00492892159375), "teaspoon", "teaspoons", "cucharadita", "cucharaditas")

        add(UnitDef("C", UnitCategory.TEMPERATURE), "celsius", "centigrade", "centigrados", "grados celsius", "grado celsius", "degrees celsius")
        add(UnitDef("F", UnitCategory.TEMPERATURE), "fahrenheit", "grados fahrenheit", "grado fahrenheit", "degrees fahrenheit")
        add(UnitDef("K", UnitCategory.TEMPERATURE), "kelvin", "kelvins")
    }

    /** Unit symbols for the tool description. */
    val supportedSummary = "length (m km cm mm mi yd ft in), mass (kg g mg t lb oz), " +
        "volume (l ml gal qt pt cup floz tbsp tsp), temperature (C F K)"

    fun findUnit(name: String): UnitDef? {
        val key = normaliseName(name)
        if (key.isEmpty()) return null
        units[key]?.let { return it }
        if (key.endsWith("es")) units[key.removeSuffix("es")]?.let { return it }
        if (key.endsWith("s")) units[key.removeSuffix("s")]?.let { return it }
        return null
    }

    fun convert(value: Double, fromName: String, toName: String): Conversion {
        val from = findUnit(fromName) ?: throw ConversionException("unknown unit '$fromName' (supported: $supportedSummary)")
        val to = findUnit(toName) ?: throw ConversionException("unknown unit '$toName' (supported: $supportedSummary)")
        if (from.category != to.category) {
            throw ConversionException(
                "cannot convert ${from.category.name.lowercase()} (${from.symbol}) to ${to.category.name.lowercase()} (${to.symbol})",
            )
        }
        val result = if (from.category == UnitCategory.TEMPERATURE) {
            fromKelvin(toKelvin(value, from.symbol), to.symbol)
        } else {
            value * from.toBase / to.toBase
        }
        if (!result.isFinite()) throw ConversionException("result is not a finite number")
        return Conversion(value, from, result, to)
    }

    private fun toKelvin(value: Double, symbol: String): Double = when (symbol) {
        "C" -> value + 273.15
        "F" -> (value - 32.0) * 5.0 / 9.0 + 273.15
        else -> value
    }.also { if (it < 0) throw ConversionException("below absolute zero") }

    private fun fromKelvin(kelvin: Double, symbol: String): Double = when (symbol) {
        "C" -> kelvin - 273.15
        "F" -> (kelvin - 273.15) * 9.0 / 5.0 + 32.0
        else -> kelvin
    }

    private val QUERY = Regex(
        """^\s*(-?\d+(?:[.,]\d+)?)\s*(.+?)\s+(?:to|in|into|as|a|en|->|→|=)\s+(.+?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** "72 F to C", "5 km a millas", "72°F en °C" -> (72.0, "F", "C"); `null` when it does not look like a conversion. */
    fun parseQuery(text: String): Triple<Double, String, String>? {
        val m = QUERY.matchEntire(text.trim().trimEnd('?', '.')) ?: return null
        val value = parseDecimal(m.groupValues[1]) ?: return null
        return Triple(value, m.groupValues[2], m.groupValues[3])
    }

    private fun normaliseName(raw: String): String =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("°", "").replace("º", "").replace(".", "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
