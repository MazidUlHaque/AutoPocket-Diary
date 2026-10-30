package com.mazidulhaque.autopocket.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodType(val label: String) {
    WEEK("Week"), MONTH("Month"), TWO_MONTHS("2 Months"), YEAR("Year"), CUSTOM("Custom")
}

/** A date range the whole app shows. [end] is exclusive. */
data class Period(val type: PeriodType, val start: LocalDate, val end: LocalDate) {

    fun previous() = shift(-1)
    fun next() = shift(1)

    private fun shift(n: Long): Period = when (type) {
        PeriodType.WEEK -> Period(type, start.plusWeeks(n), end.plusWeeks(n))
        PeriodType.MONTH -> Period(type, start.plusMonths(n), start.plusMonths(n + 1))
        PeriodType.TWO_MONTHS -> Period(type, start.plusMonths(2 * n), start.plusMonths(2 * n + 2))
        PeriodType.YEAR -> Period(type, start.plusYears(n), start.plusYears(n + 1))
        PeriodType.CUSTOM -> ChronoUnit.DAYS.between(start, end).let { days ->
            Period(type, start.plusDays(days * n), end.plusDays(days * n))
        }
    }

    val label: String get() = label(Locale.ENGLISH)

    /** e.g. "October 2026", "Sep – October 2026", "1 Oct – 15 Oct 2026", with month names in [locale]. */
    fun label(locale: Locale): String {
        val month = MONTH.withLocale(locale)
        val monthYear = MONTH_YEAR.withLocale(locale)
        val dayMonth = DAY_MONTH.withLocale(locale)
        val dayMonthYear = DAY_MONTH_YEAR.withLocale(locale)
        val last = end.minusDays(1)
        return when (type) {
            PeriodType.MONTH -> start.format(monthYear)
            PeriodType.YEAR -> start.year.toString()
            PeriodType.TWO_MONTHS ->
                if (start.year == last.year) "${start.format(month)} – ${last.format(monthYear)}"
                else "${start.format(monthYear)} – ${last.format(monthYear)}"
            else ->
                if (start == last) start.format(dayMonthYear)
                else if (start.year == last.year) "${start.format(dayMonth)} – ${last.format(dayMonthYear)}"
                else "${start.format(dayMonthYear)} – ${last.format(dayMonthYear)}"
        }
    }

    companion object {
        /** Weeks run Saturday to Friday, as is usual in Bangladesh. */
        val WEEK_START: DayOfWeek = DayOfWeek.SATURDAY

        private val MONTH = DateTimeFormatter.ofPattern("MMM")
        private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")
        private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")

        /** The period of [type] that contains [day]. Two months = the month of [day] and the one before. */
        fun containing(type: PeriodType, day: LocalDate): Period = when (type) {
            PeriodType.WEEK -> day.with(TemporalAdjusters.previousOrSame(WEEK_START)).let { Period(type, it, it.plusWeeks(1)) }
            PeriodType.MONTH -> day.withDayOfMonth(1).let { Period(type, it, it.plusMonths(1)) }
            PeriodType.TWO_MONTHS -> day.withDayOfMonth(1).let { Period(type, it.minusMonths(1), it.plusMonths(1)) }
            PeriodType.YEAR -> day.withDayOfYear(1).let { Period(type, it, it.plusYears(1)) }
            PeriodType.CUSTOM -> Period(type, day, day.plusDays(1))
        }

        /** A custom range, both days included. */
        fun custom(from: LocalDate, to: LocalDate): Period {
            val (a, b) = if (to.isBefore(from)) to to from else from to to
            return Period(PeriodType.CUSTOM, a, b.plusDays(1))
        }
    }
}
