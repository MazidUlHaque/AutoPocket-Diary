package com.mazidulhaque.autopocket.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val data = "{\"transactions\":[{\"title\":\"Shoes ৳\"}]}".repeat(50).toByteArray()

    @Test fun roundTrip() {
        val file = BackupCrypto.encrypt(data, "correct horse".toCharArray())
        assertArrayEquals(data, BackupCrypto.decrypt(file, "correct horse".toCharArray()))
    }

    @Test fun sameDataGivesDifferentFiles() {
        assertNotEquals(
            BackupCrypto.encrypt(data, "pw123456".toCharArray()).toList(),
            BackupCrypto.encrypt(data, "pw123456".toCharArray()).toList(),
        )
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun wrongPassword() {
        val file = BackupCrypto.encrypt(data, "correct horse".toCharArray())
        BackupCrypto.decrypt(file, "wrong horse".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun tamperedFileRejected() {
        val file = BackupCrypto.encrypt(data, "correct horse".toCharArray())
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()
        BackupCrypto.decrypt(file, "correct horse".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackup::class)
    fun randomFileRejected() {
        BackupCrypto.decrypt("hello, this is a photo".toByteArray(), "x".toCharArray())
    }
}

class CsvTest {
    @Test fun quotesAndEscapes() {
        assertEquals("plain", Csv.cell("plain"))
        assertEquals("\"a, b\"", Csv.cell("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", Csv.cell("line1\nline2"))
    }

    @Test fun blocksFormulas() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, Csv.cell("=HYPERLINK(\"x\")"))
        assertEquals("'+123", Csv.cell("+123"))
        assertEquals("'@SUM(A1)", Csv.cell("@SUM(A1)"))
    }

    @Test fun buildsFile() {
        val csv = Csv.build(listOf("Date", "Amount"), listOf(listOf("2026-10-30", Csv.number(1500.0))))
        assertTrue(csv.startsWith("﻿Date,Amount\r\n"))
        assertTrue(csv.endsWith("2026-10-30,1500.00\r\n"))
    }
}
