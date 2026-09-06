package br.com.augusto.jmud

import br.com.augusto.jmud.util.IntervalFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalFormatTest {

    @Test
    fun acceptsCommaAsDecimalSeparator() {
        assertEquals(400, IntervalFormat.parseToMillis("0,4"))
    }

    @Test
    fun acceptsDotAsDecimalSeparator() {
        assertEquals(400, IntervalFormat.parseToMillis("0.4"))
    }

    @Test
    fun acceptsIntegerSeconds() {
        assertEquals(2000, IntervalFormat.parseToMillis("2"))
    }

    @Test
    fun acceptsIntMudInterval() {
        assertEquals(333, IntervalFormat.parseToMillis("0,333"))
    }

    @Test
    fun acceptsZero() {
        assertEquals(0, IntervalFormat.parseToMillis("0"))
    }

    @Test
    fun ignoresSurroundingSpaces() {
        assertEquals(500, IntervalFormat.parseToMillis("  0,5  "))
    }

    @Test
    fun rejectsEmptyText() {
        assertNull(IntervalFormat.parseToMillis(""))
        assertNull(IntervalFormat.parseToMillis("   "))
    }

    @Test
    fun rejectsNonNumericText() {
        assertNull(IntervalFormat.parseToMillis("abc"))
        assertNull(IntervalFormat.parseToMillis("0,4s"))
    }

    @Test
    fun rejectsNegativeValues() {
        assertNull(IntervalFormat.parseToMillis("-1"))
    }

    @Test
    fun clampsVeryLargeValues() {
        assertEquals(IntervalFormat.MAX_INTERVAL_MS, IntervalFormat.parseToMillis("999"))
    }

    @Test
    fun formatsWholeSecondsWithoutDecimals() {
        assertEquals("2", IntervalFormat.formatMillis(2000))
        assertEquals("0", IntervalFormat.formatMillis(0))
    }

    @Test
    fun formatsFractionsTrimmingTrailingZeros() {
        assertEquals("0,4", IntervalFormat.formatMillis(400, ','))
        assertEquals("0.333", IntervalFormat.formatMillis(333))
        assertEquals("1,05", IntervalFormat.formatMillis(1050, ','))
    }

    @Test
    fun formatIsReversibleByParse() {
        for (millis in listOf(0, 333, 400, 500, 1000, 1050, 2500)) {
            assertEquals(millis, IntervalFormat.parseToMillis(IntervalFormat.formatMillis(millis, ',')))
        }
    }

    @Test
    fun inheritedMacroIntervalFallsBackToDefault() {
        assertEquals(400, IntervalFormat.effectiveMillis(IntervalFormat.INHERIT, 400))
        assertEquals(0, IntervalFormat.effectiveMillis(0, 400))
        assertEquals(1000, IntervalFormat.effectiveMillis(1000, 400))
    }

    @Test
    fun sanitizeKeepsInheritMarkerAndClampsRest() {
        assertEquals(IntervalFormat.INHERIT, IntervalFormat.sanitize(IntervalFormat.INHERIT))
        assertEquals(0, IntervalFormat.sanitize(-50))
        assertEquals(IntervalFormat.MAX_INTERVAL_MS, IntervalFormat.sanitize(999999))
    }
}
