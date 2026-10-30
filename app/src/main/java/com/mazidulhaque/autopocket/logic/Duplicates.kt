package com.mazidulhaque.autopocket.logic

import kotlin.math.abs

/**
 * Some banks send two SMS for one transaction (IFIC: "BDT 10000.00 has been deposited…" and
 * "BDT 10,000.00 Deposit for NPSB Transfer Incoming…"). Two real ATM withdrawals of the same amount a
 * minute apart must still count twice. This tells them apart.
 */
object Duplicates {
    data class Sms(
        val source: String,
        val account: String?,
        val incoming: Boolean,
        val amount: Double,
        val timestamp: Long,
        val balance: Double?,
        val text: String,
    )

    const val WINDOW_MS = 3 * 60 * 1000L

    fun same(a: Sms, b: Sms): Boolean {
        if (a.source != b.source || a.incoming != b.incoming || abs(a.amount - b.amount) > 0.005) return false
        if (abs(a.timestamp - b.timestamp) > WINDOW_MS) return false
        if (a.account != null && b.account != null && a.account != b.account) return false
        if (a.text.trim() == b.text.trim()) return true
        // Same wording, different balance: two real transactions (e.g. two ATM withdrawals).
        if (template(a.text) == template(b.text)) return a.balance != null && a.balance == b.balance
        // Different wording for the same amount, account and minute: the bank's second SMS.
        return true
    }

    /** The SMS with its numbers removed, e.g. "debited (ATM Cash Withdrawal) by Tk# on #-#-# …". */
    fun template(text: String): String =
        text.replace(Regex("\\d[\\d,.:/-]*"), "#").replace(Regex("\\s+"), " ").trim().lowercase()
}
