package com.dansemur.constructioncalc

data class CalculatorUIData(
    val expression: String,
    val mainText: String,
    val precisionLabel: String
)

data class ConversionData(
    val fractional: String,
    val decimal: String,
    val metricMm: String,
    val feetInches: String
)

data class Measurement(
    var `val`: Double,
    var isMeas: Boolean,
    var raw: String = ""
)

/**
 * ConstructionCalculatorEngine
 *
 * Dedicated fractional inches construction calculator.
 * - Always calculates and displays in inches (never feet).
 * - Fractions automatically snap to current precision (1/16, 1/32, 1/8, or DEC).
 * - '&' separates whole inches from fractions (e.g. 6 & 1/2).
 * - '/' specifies fractions (e.g. 3/16).
 */
class ConstructionCalculatorEngine {

    companion object {
        const val MAX_PARTS = 4
        val PRECISIONS = listOf("16", "32", "64", "8", "dec")

        fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val t = y
                y = x % y
                x = t
            }
            return if (x < 0) -x else x
        }

        fun parseNumber(value: String): Double {
            if (value.isEmpty()) return 0.0
            if (value.contains('/')) {
                val parts = value.split('/')
                val n = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                val d = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                return if (d != 0.0) n / d else 0.0
            }
            return value.toDoubleOrNull() ?: 0.0
        }

        fun formatDecimal(value: Double): String {
            val rounded = Math.round(value * 10000.0) / 10000.0
            return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
        }

        fun formatFraction(value: Double, precision: Int): String {
            val absValue = Math.abs(value)
            var total = Math.floor(absValue).toLong()
            val remainder = absValue - total
            var numerator = Math.round(remainder * precision)
            var denominator = precision.toLong()

            if (numerator == denominator) {
                total += 1
                numerator = 0
            }

            val g = gcd(numerator, denominator)
            numerator /= if (g != 0L) g else 1L
            denominator /= if (g != 0L) g else 1L

            val fraction = if (numerator > 0) "$numerator/$denominator" else ""
            val sign = if (value < 0) "-" else ""
            var main = sign + if (total > 0) "$total" else if (fraction.isNotEmpty()) "" else "0"
            if (fraction.isNotEmpty()) {
                main += (if (total > 0) " " else "") + fraction
            }
            return main
        }
    }

    val stack = mutableListOf<Any>() // Measurement or String operator
    var currentInput = ""
    val currentParts = mutableListOf<String>()
    var lastResult: Measurement? = null
    var currentPrecision = "16" // default 1/16

    private fun partsToMeasurement(parts: List<String>): Measurement {
        val raw = parts.joinToString(" ")
        if (parts.isEmpty()) return Measurement(0.0, false, raw)

        val sum = parts.fold(0.0) { acc, part ->
            acc + parseNumber(part.replace("\"", "").trim())
        }
        return Measurement(sum, true, raw)
    }

    fun formatValue(value: Double, isMeas: Boolean, roundResult: Boolean = false): String {
        if (value.isNaN()) return "Error"

        val precInt = currentPrecision.toIntOrNull()
        val suffix = "\""

        if (precInt == null) {
            return formatDecimal(value) + suffix
        }

        if (!roundResult) {
            return formatFraction(value, precInt) + suffix
        }

        val step = 1.0 / precInt
        val roundedValue = Math.round(value / step) * step
        return formatFraction(roundedValue, precInt) + suffix
    }

    fun getInputDisplay(): String {
        val parts = currentParts.toMutableList()
        if (currentInput.isNotEmpty()) parts.add(currentInput)
        if (parts.isEmpty()) return "0\""
        val text = parts.joinToString(" ")
        return (if (currentInput.isNotEmpty()) text else "$text ") + "\""
    }

    fun applyOperator(result: Measurement, operator: String, next: Measurement): Measurement {
        var rVal = result.`val`
        var isMeas = result.isMeas || next.isMeas

        when (operator) {
            "+" -> rVal += next.`val`
            "-" -> rVal -= next.`val`
            "×", "*" -> rVal *= next.`val`
            "÷", "/" -> {
                rVal /= if (next.`val` != 0.0) next.`val` else 1.0
                if (result.isMeas && next.isMeas) isMeas = false
            }
        }
        return Measurement(rVal, isMeas)
    }

    fun evaluateExpression(items: List<Any>): Measurement {
        if (items.isEmpty()) return Measurement(0.0, false)
        var result = (items[0] as? Measurement)?.copy() ?: Measurement(0.0, false)
        var i = 1
        while (i < items.size) {
            val op = items[i] as? String ?: ""
            val next = items.getOrNull(i + 1) as? Measurement
            if (next != null) {
                result = applyOperator(result, op, next)
            }
            i += 2
        }
        return result
    }

    fun getLiveItems(): List<Any> {
        val items = stack.toMutableList()
        val parts = currentParts.toMutableList()
        if (currentInput.isNotEmpty()) parts.add(currentInput)
        if (parts.isNotEmpty()) items.add(partsToMeasurement(parts))
        return items
    }

    fun formatExpression(items: List<Any>): String {
        return items.joinToString(" ") { item ->
            when (item) {
                is String -> item
                is Measurement -> if (item.raw.isNotEmpty()) item.raw else formatValue(item.`val`, item.isMeas, false)
                else -> item.toString()
            }
        }
    }

    fun evaluateLive(): Measurement? {
        val items = getLiveItems().toMutableList()
        if (items.isEmpty()) return null
        if (items.lastOrNull() is String) items.removeAt(items.size - 1)
        if (items.isEmpty()) return null
        return evaluateExpression(items)
    }

    fun computeLiveResult(): String? {
        val result = evaluateLive() ?: return null
        return formatValue(result.`val`, result.isMeas, true)
    }

    fun resetCalculator() {
        stack.clear()
        currentInput = ""
        currentParts.clear()
        lastResult = null
    }

    // --- Keypad Handlers ---

    fun handleDigit(digit: String) {
        if (lastResult != null) resetCalculator()
        currentInput += digit
    }

    fun handleSpace() { // '&' key (separator between whole inches and fraction)
        lastResult = null
        if (currentInput.isNotEmpty()) {
            if (currentParts.size < MAX_PARTS) {
                currentParts.add(currentInput)
                currentInput = ""
            }
        }
    }

    fun handleSlash() { // '/' key
        lastResult = null
        currentInput += "/"
    }

    fun handleOperator(operator: String) {
        val parts = currentParts.toMutableList()
        if (currentInput.isNotEmpty()) parts.add(currentInput)

        if (parts.isNotEmpty()) {
            stack.add(partsToMeasurement(parts))
            stack.add(operator)
            currentParts.clear()
            currentInput = ""
        } else if (lastResult != null) {
            stack.clear()
            stack.add(lastResult!!)
            stack.add(operator)
            lastResult = null
        } else if (stack.isNotEmpty()) {
            stack[stack.size - 1] = operator
        }
    }

    fun handleEquals() {
        val parts = currentParts.toMutableList()
        if (currentInput.isNotEmpty()) parts.add(currentInput)

        val items = stack.toMutableList()
        if (parts.isNotEmpty()) items.add(partsToMeasurement(parts))

        if (items.isEmpty()) return
        if (items.lastOrNull() is String) items.removeAt(items.size - 1)
        if (items.isEmpty()) return

        val result = evaluateExpression(items)
        lastResult = result
        stack.clear()
        currentParts.clear()
        currentInput = ""
    }

    fun handleClear() {
        resetCalculator()
    }

    fun handleBackspace() {
        if (lastResult != null) {
            handleClear()
            return
        }
        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length - 1)
        } else if (currentParts.isNotEmpty()) {
            currentInput = currentParts.removeAt(currentParts.size - 1)
        } else if (stack.isNotEmpty()) {
            stack.removeAt(stack.size - 1)
            lastResult = (stack.removeLastOrNull() as? Measurement)
        }
    }

    fun cyclePrecision() {
        val idx = PRECISIONS.indexOf(currentPrecision)
        currentPrecision = PRECISIONS[(idx + 1) % PRECISIONS.size]
    }

    fun setPrecision(prec: String) {
        if (prec in PRECISIONS) {
            currentPrecision = prec
        }
    }

    fun getCurrentValue(): Double {
        if (lastResult != null) return lastResult!!.`val`
        val live = evaluateLive()
        if (live != null) return live.`val`
        val parts = currentParts.toMutableList()
        if (currentInput.isNotEmpty()) parts.add(currentInput)
        return partsToMeasurement(parts).`val`
    }

    fun getConversionData(): ConversionData {
        val v = getCurrentValue()
        val absV = Math.abs(v)
        val precInt = currentPrecision.toIntOrNull() ?: 16
        val fracStr = formatFraction(v, precInt) + "″"
        val decStr = String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.') + "″"
        val mmStr = String.format(java.util.Locale.US, "%.1f mm", v * 25.4)

        val feet = (absV / 12.0).toInt()
        val remInches = absV - (feet * 12.0)
        val feetInchesStr = if (feet > 0) {
            val sign = if (v < 0) "-" else ""
            "$sign$feet′ " + formatFraction(remInches, precInt) + "″"
        } else {
            fracStr
        }

        return ConversionData(
            fractional = fracStr,
            decimal = decStr,
            metricMm = mmStr,
            feetInches = feetInchesStr
        )
    }

    fun handleClearEntry() {
        if (currentInput.isNotEmpty() || currentParts.isNotEmpty()) {
            currentInput = ""
            currentParts.clear()
        } else if (lastResult != null) {
            resetCalculator()
        }
    }

    fun insertQuickFraction(frac: String) {
        if (lastResult != null) resetCalculator()
        if (currentInput.isNotEmpty()) {
            currentParts.add(currentInput)
            currentInput = ""
        }
        currentParts.add(frac)
    }

    fun handleSquare() {
        val v = getCurrentValue()
        val squared = v * v
        lastResult = Measurement(squared, true)
        stack.clear()
        currentParts.clear()
        currentInput = ""
    }

    fun handleSquareRoot() {
        val v = getCurrentValue()
        val root = if (v >= 0) Math.sqrt(v) else Double.NaN
        lastResult = Measurement(root, true)
        stack.clear()
        currentParts.clear()
        currentInput = ""
    }

    fun getUIData(): CalculatorUIData {
        val liveItems = getLiveItems()
        val liveExpr = if (liveItems.isNotEmpty()) formatExpression(liveItems) else ""
        val liveRes = computeLiveResult()
        val expr = if (liveRes != null && stack.isNotEmpty()) "$liveExpr = $liveRes" else liveExpr

        val rawMain = if (lastResult != null) {
            formatValue(lastResult!!.`val`, lastResult!!.isMeas, true)
        } else {
            getInputDisplay()
        }

        val precLabel = if (currentPrecision == "dec") "DEC" else "1/$currentPrecision″"

        return CalculatorUIData(
            expression = expr,
            mainText = rawMain,
            precisionLabel = precLabel
        )
    }
}
