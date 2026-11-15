package com.jfaf.irc.domain.chat

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Cómo se rotula un día en el separador de fecha (UI-7). */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    /** Otro día: [month] de 1 a 12; el año sólo se muestra si no es el actual ([showYear]). */
    data class Date(val day: Int, val month: Int, val year: Int, val showYear: Boolean) : DayLabel

    companion object {
        /** Día local de una hora, en días desde 1970-01-01. */
        fun epochDay(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
            Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date.toEpochDays()

        fun of(epochDay: Long, todayEpochDay: Long): DayLabel = when (epochDay) {
            todayEpochDay -> Today
            todayEpochDay - 1 -> Yesterday
            else -> {
                val date = LocalDate.fromEpochDays(epochDay)
                val today = LocalDate.fromEpochDays(todayEpochDay)
                Date(date.day, date.month.ordinal + 1, date.year, showYear = date.year != today.year)
            }
        }
    }
}
