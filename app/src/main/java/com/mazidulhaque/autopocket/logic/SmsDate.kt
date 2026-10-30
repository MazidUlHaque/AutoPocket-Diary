package com.mazidulhaque.autopocket.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Finds the date (and time, if given) written inside an SMS, e.g. "at 29/10/2026 18:20",
 * "on 28-10-2026", "27-OCT-26". Used for SMS that are shared or pasted, which arrive without one.
 */
object SmsDate {
    private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val time = "(?:\\s*,?\\s*(?:at\\s+)?(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*([AaPp][Mm])?)?"
    private val numeric = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})\\b$time")
    private val iso = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b$time")
    private val named = Regex("\\b(\\d{1,2})[\\s/-]?([A-Za-z]{3})[A-Za-z]*[\\s/,-]*(\\d{4}|\\d{2})\\b$time")

    fun find(text: String): LocalDateTime? {
        iso.find(text)?.let { m ->
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m, 4)?.let { return it }
        }
        numeric.find(text)?.let { m ->
            build(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt(), m, 4)?.let { return it }
        }
        named.findAll(text).forEach { m ->
            val month = months.indexOf(m.groupValues[2].uppercase()) + 1
            if (month > 0) build(year(m.groupValues[3]), month, m.groupValues[1].toInt(), m, 4)?.let { return it }
        }
        return null
    }

    private fun year(s: String) = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    private fun build(y: Int, mo: Int, d: Int, m: MatchResult, timeGroup: Int): LocalDateTime? {
        val date = runCatching { LocalDate.of(y, mo, d) }.getOrNull() ?: return null
        val h = m.groupValues[timeGroup].toIntOrNull()
        val min = m.groupValues[timeGroup + 1].toIntOrNull()
        if (h == null || min == null) return date.atTime(12, 0)
        var hour = h
        when (m.groupValues[timeGroup + 2].uppercase()) {
            "PM" -> if (hour < 12) hour += 12
            "AM" -> if (hour == 12) hour = 0
        }
        val t = runCatching { LocalTime.of(hour, min) }.getOrNull() ?: LocalTime.NOON
        return date.atTime(t)
    }
}
