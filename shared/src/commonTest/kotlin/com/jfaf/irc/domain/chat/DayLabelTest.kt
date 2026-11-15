package com.jfaf.irc.domain.chat

import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.FixedOffsetTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DayLabelTest {
    private val hour = 3_600_000L

    @Test
    fun elDiaDeLaEpoca() {
        assertEquals(0L, DayLabel.epochDay(0, TimeZone.UTC))
        assertEquals(0L, DayLabel.epochDay(23 * hour + 59 * 60_000, TimeZone.UTC))
        assertEquals(1L, DayLabel.epochDay(24 * hour, TimeZone.UTC))
    }

    @Test
    fun respetaLaZonaHoraria() {
        val plus2 = FixedOffsetTimeZone(UtcOffset(hours = 2))
        val minus5 = FixedOffsetTimeZone(UtcOffset(hours = -5))
        // 23:00 UTC del día 0 ya es el día 1 en UTC+2 y sigue siendo el 0 en UTC-5
        assertEquals(1L, DayLabel.epochDay(23 * hour, plus2))
        assertEquals(0L, DayLabel.epochDay(23 * hour, minus5))
        // 03:00 UTC del día 1 todavía es el día 0 en UTC-5
        assertEquals(0L, DayLabel.epochDay(27 * hour, minus5))
    }

    @Test
    fun hoyYAyer() {
        assertEquals(DayLabel.Today, DayLabel.of(20_000, 20_000))
        assertEquals(DayLabel.Yesterday, DayLabel.of(19_999, 20_000))
    }

    @Test
    fun otraFechaConYearSoloSiNoEsElActual() {
        // 20_453 = 2025-12-31 ; 20_456 = 2026-01-03 (días desde 1970-01-01)
        val sameYear = DayLabel.of(20_400, 20_405) as DayLabel.Date
        assertEquals(false, sameYear.showYear)
        val otherYear = DayLabel.of(20_453, 20_456) as DayLabel.Date
        assertEquals(true, otherYear.showYear)
        assertEquals(31, otherYear.day)
        assertEquals(12, otherYear.month)
        assertEquals(2025, otherYear.year)
    }
}
