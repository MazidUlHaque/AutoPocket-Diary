package com.mazidulhaque.autopocket.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CategoriesTest {
    private fun out(kind: String, who: String?, msg: String? = null) = Categories.guess(false, kind, who, msg)

    @Test fun knownMerchants() {
        assertEquals("Clothing & Shoes", out("Card Purchase", "BATA SHOE STORE"))
        assertEquals("Clothing & Shoes", out("Card Purchase", "APEX FOOTWEAR LTD"))
        assertEquals("Groceries", out("Payment", "Shwapno"))
        assertEquals("Groceries", out("Card Purchase", "MEENA BAZAR GULSHAN"))
        assertEquals("Electricity", out("Bill Payment", "DESCO"))
        assertEquals("Gas", out("Bill Payment", "TITAS GAS"))
        assertEquals("Food & Dining", out("Payment", "foodpanda"))
        assertEquals("Food & Dining", out("Payment", "Pathao Food"))
        assertEquals("Transport", out("Payment", "Pathao"))
        assertEquals("Health", out("Card Purchase", "LAZZ PHARMA"))
    }

    @Test fun byTransactionType() {
        assertEquals("Cash Withdrawal", out("ATM Withdrawal", "ATM GULSHAN-1"))
        assertEquals("Cash Withdrawal", out("Cash Out", "01912345678"))
        assertEquals(Categories.SEND_MONEY, out("Send Money", "01812345678"))
        assertEquals("Mobile Recharge", out("Mobile Recharge", null))
        assertEquals("Bills", out("Bill Payment", "XYZ SERVICES"))
        assertEquals(Categories.OTHER, out("Card Purchase", "UNKNOWN SHOP"))
    }

    @Test fun referenceTextHelps() {
        assertEquals("Rent", out("Send Money", "01712345678", "Send Money Tk 15,000.00 to 01712345678 successful. Ref house rent."))
    }

    @Test fun internetBankingIsNotInternetBill() {
        assertEquals(Categories.OTHER, out("Transfer", "ABC", "Fund transferred via Internet Banking to ABC"))
    }

    @Test fun income() {
        assertEquals("Salary", Categories.guess(true, "Salary", null, "credited ... SALARY"))
        assertEquals("Cash In", Categories.guess(true, "Cash In", "01612345678", null))
        assertEquals("Received Money", Categories.guess(true, "Money Received", "01712345678", null))
        assertEquals("Refund & Cashback", Categories.guess(true, "Money In", null, "Cashback Tk 20 received"))
    }

    @Test fun merchantKey() {
        assertEquals("BATA SHOE ST DHK", Categories.merchantKey("Bata Shoe St-DHK."))
        assertEquals("01712345678", Categories.merchantKey("01712345678"))
    }
}

class PeriodTest {
    private val day = LocalDate.of(2026, 10, 30) // a Friday

    @Test fun weekStartsSaturday() {
        val p = Period.containing(PeriodType.WEEK, day)
        assertEquals(LocalDate.of(2026, 10, 24), p.start)
        assertEquals(LocalDate.of(2026, 10, 31), p.end)
        assertEquals("24 Oct – 30 Oct 2026", p.label)
        assertEquals(LocalDate.of(2026, 10, 31), p.next().start)
    }

    @Test fun month() {
        val p = Period.containing(PeriodType.MONTH, day)
        assertEquals("October 2026", p.label)
        assertEquals("September 2026", p.previous().label)
        assertEquals("January 2027", Period.containing(PeriodType.MONTH, LocalDate.of(2026, 12, 5)).next().label)
    }

    @Test fun twoMonths() {
        val p = Period.containing(PeriodType.TWO_MONTHS, day)
        assertEquals(LocalDate.of(2026, 9, 1), p.start)
        assertEquals(LocalDate.of(2026, 11, 1), p.end)
        assertEquals("Sep – October 2026", p.label)
        assertEquals("Jul – August 2026", p.previous().label)
    }

    @Test fun year() {
        assertEquals("2026", Period.containing(PeriodType.YEAR, day).label)
        assertEquals("2025", Period.containing(PeriodType.YEAR, day).previous().label)
    }

    @Test fun customIncludesBothDays() {
        val p = Period.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))
        assertEquals(LocalDate.of(2026, 10, 16), p.end)
        assertEquals("1 Oct – 15 Oct 2026", p.label)
        assertEquals(LocalDate.of(2026, 10, 16), p.next().start)
        assertEquals(Period.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)), Period.custom(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1)))
    }
}

class TransferMatcherTest {
    private val m = 60 * 1000L
    private fun item(id: Long, source: String, acct: String, incoming: Boolean, amount: Double, t: Long, kind: String,
                     transfer: Boolean = false, otherBank: String? = null, settled: Boolean = false) =
        TransferMatcher.Item(id, source, "$source|$acct", incoming, amount, t, kind, transfer, otherBank, settled)

    @Test fun bankToBankNamedIsSure() {
        val r = TransferMatcher.match(listOf(
            item(1, "Dutch-Bangla Bank", "1234", false, 10000.0, 100 * m, "Send Money"),
            item(2, "IFIC Bank", "5678", true, 10000.0, 101 * m, "Money In", otherBank = "DUTCH-BANGLA BANK LTD"),
        ))
        assertEquals(setOf(1L, 2L), r.transfers)
        assertEquals(emptyList<Pair<Long, Long>>(), r.ask)
    }

    @Test fun cardBillPaymentMarksTheAccountDebit() {
        val r = TransferMatcher.match(listOf(
            item(1, "Dutch-Bangla Bank", "1234", false, 25207.0, 100 * m, "Send Money"),
            item(2, "Dutch-Bangla Bank", "1111", true, 25207.0, 300 * m, "Card Bill Payment", transfer = true),
        ))
        assertEquals(setOf(1L), r.transfers)
    }

    @Test fun amountAndTimeOnlyAsks() {
        val r = TransferMatcher.match(listOf(
            item(1, "Dutch-Bangla Bank", "1234", false, 5000.0, 100 * m, "Send Money"),
            item(2, "bKash", "wallet", true, 5000.0, 102 * m, "Money Received"),
        ))
        assertEquals(emptySet<Long>(), r.transfers)
        assertEquals(listOf(1L to 2L), r.ask)
    }

    @Test fun leavesSettledAndFarApartAlone() {
        val r = TransferMatcher.match(listOf(
            item(1, "Dutch-Bangla Bank", "1234", false, 5000.0, 100 * m, "Send Money"),
            item(2, "bKash", "wallet", true, 5000.0, 400 * m, "Money Received"),
            item(3, "IFIC Bank", "5678", true, 5000.0, 101 * m, "Money Received", settled = true),
        ))
        assertEquals(emptySet<Long>(), r.transfers)
        assertEquals(emptyList<Pair<Long, Long>>(), r.ask)
    }
}

class DuplicatesTest {
    private fun sms(text: String, amount: Double, t: Long, balance: Double?, account: String? = "5678", incoming: Boolean = true) =
        Duplicates.Sms("IFIC Bank", account, incoming, amount, t, balance, text)

    @Test fun bankSendsTwoDifferentSmsForOneDeposit() {
        val a = sms("BDT 10000.00 has been deposited to your A/C 0190000005678 on 30.04.26 at 17:55 from DUTCH-BANGLA BANK LTD.", 10000.0, 0, null)
        val b = sms("30-04-2026 5:55:49 PM\nBDT 10,000.00 Deposit for\nNPSB Transfer Incoming\nA/C 01234***5678\nBalance: BDT 62,902.32", 10000.0, 20_000, 62902.32)
        assertEquals(true, Duplicates.same(a, b))
    }

    @Test fun twoRealAtmWithdrawalsStaySeparate() {
        val a = sms("your A/C ***1234 debited (ATM Cash Withdrawal) by Tk20,000.00 on 01-08-2026 09:30:01 PM C/B Tk49,233.99", 20000.0, 0, 49233.99, "1234", false)
        val b = sms("your A/C ***1234 debited (ATM Cash Withdrawal) by Tk20,000.00 on 01-08-2026 09:31:10 PM C/B Tk29,233.99", 20000.0, 69_000, 29233.99, "1234", false)
        assertEquals(false, Duplicates.same(a, b))
    }

    @Test fun differentAccountsAreNotDuplicates() {
        val a = sms("Deposit BDT 500.00 A/C ***1111", 500.0, 0, null, "1111")
        val b = sms("Deposited BDT 500 to A/C ***2222", 500.0, 0, null, "2222")
        assertEquals(false, Duplicates.same(a, b))
    }
}

class SmsDateTest {
    private fun d(s: String) = SmsDate.find(s)?.toString()

    @Test fun bkashStyle() = assertEquals("2026-10-29T18:20", d("Balance Tk 3,250.50. TrxID BJ12AB34CD at 29/10/2026 18:20"))
    @Test fun dashDate() = assertEquals("2026-10-28T12:00", d("credited with BDT 55,000.00 on 28-10-2026 for SALARY"))
    @Test fun namedMonth() = assertEquals("2026-10-27T12:00", d("debited from your A/C ***1234 on 27-OCT-26 for Fund Transfer"))
    @Test fun withPm() = assertEquals("2026-10-26T17:45", d("at BATA on 26-10-2026 05:45 PM. Available"))
    @Test fun iso() = assertEquals("2026-10-25T09:05", d("Txn on 2026-10-25 09:05:12"))
    @Test fun noDate() = assertEquals(null, d("Send Money Tk 500.00 to 01812345678 successful."))
    @Test fun invalidDateIgnored() = assertEquals(null, d("Ref 45/99/2026"))
}
