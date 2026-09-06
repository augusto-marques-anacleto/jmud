package br.com.augusto.jmud.util

import kotlin.math.roundToInt

object IntervalFormat {

    const val INHERIT = -1
    const val DEFAULT_INTERVAL_MS = 400
    const val MAX_INTERVAL_MS = 10000

    fun parseToMillis(text: String): Int? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val seconds = normalized.toDoubleOrNull() ?: return null
        if (seconds.isNaN() || seconds < 0.0) return null
        val millis = (seconds * 1000.0).roundToInt()
        return millis.coerceIn(0, MAX_INTERVAL_MS)
    }

    fun formatMillis(millis: Int, decimalSeparator: Char = '.'): String {
        val safe = millis.coerceIn(0, MAX_INTERVAL_MS)
        val whole = safe / 1000
        val fraction = safe % 1000
        if (fraction == 0) return whole.toString()
        val text = fraction.toString().padStart(3, '0').trimEnd('0')
        return "$whole$decimalSeparator$text"
    }

    fun sanitize(millis: Int): Int =
        if (millis == INHERIT) INHERIT else millis.coerceIn(0, MAX_INTERVAL_MS)

    fun effectiveMillis(macroMillis: Int, defaultMillis: Int): Int =
        if (macroMillis == INHERIT) defaultMillis.coerceIn(0, MAX_INTERVAL_MS) else macroMillis.coerceIn(0, MAX_INTERVAL_MS)
}
