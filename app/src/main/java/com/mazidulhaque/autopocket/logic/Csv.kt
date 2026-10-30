package com.mazidulhaque.autopocket.logic

/** Builds a CSV file that opens correctly in Excel and Google Sheets. */
object Csv {
    fun build(header: List<String>, rows: List<List<String>>): String = buildString {
        append('﻿') // tells Excel the file is UTF-8, so ৳ and Bangla show correctly
        (listOf(header) + rows).forEach { row -> append(row.joinToString(",") { cell(it) }).append("\r\n") }
    }

    /**
     * Quotes a cell. Text starting with = + - @ is prefixed with ' so a spreadsheet shows it
     * as text instead of running it as a formula (an SMS could contain "=HYPERLINK(...)").
     */
    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** For number columns: plain digits, no formula protection needed. */
    fun number(v: Double): String = "%.2f".format(java.util.Locale.ROOT, v)
}
