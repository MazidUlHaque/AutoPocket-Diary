package com.mazidulhaque.autopocket.sms

import com.mazidulhaque.autopocket.sms.SmsParser.Direction
import com.mazidulhaque.autopocket.sms.SmsParser.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private fun parse(sender: String, body: String): SmsParser.Parsed {
        val parsed = SmsParser.parse(sender, body)
        assertNotNull("should parse: $body", parsed)
        return parsed!!
    }

    @Test fun bkashReceived() {
        val p = parse("bKash", "You have received Tk 1,500.00 from 01712345678. Ref rent. Fee Tk 0.00. Balance Tk 3,250.50. TrxID BJ12AB34CD at 29/10/2026 18:20")
        assertEquals("bKash", p.source)
        assertEquals(SourceType.MOBILE_WALLET, p.sourceType)
        assertEquals(Direction.IN, p.direction)
        assertEquals(1500.0, p.amount, 0.001)
        assertEquals("Money Received", p.kind)
        assertEquals("01712345678", p.counterparty)
    }

    @Test fun bkashSendMoney() {
        val p = parse("bKash", "Send Money Tk 500.00 to 01812345678 successful. Ref gift. Fee Tk 5.00. Balance Tk 2,745.50. TrxID BJ12AB34CE at 29/10/2026 19:00")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(500.0, p.amount, 0.001)
        assertEquals(5.0, p.fee, 0.001)
        assertEquals("Send Money", p.kind)
        assertEquals("01812345678", p.counterparty)
    }

    @Test fun bkashCashOut() {
        val p = parse("bKash", "Cash Out Tk 2,000.00 to 01912345678 successful. Fee Tk 37.00. Balance Tk 708.50. TrxID BJ12AB34CF at 30/10/2026 09:10")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(2000.0, p.amount, 0.001)
        assertEquals(37.0, p.fee, 0.001)
        assertEquals("Cash Out", p.kind)
    }

    @Test fun bkashPaymentToMerchant() {
        val p = parse("bKash", "Payment Tk 850.00 to Shwapno successful. Balance Tk 1,200.00. TrxID BJ12AB34CG at 30/10/2026 12:00")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(850.0, p.amount, 0.001)
        assertEquals("Shwapno", p.counterparty)
    }

    @Test fun bkashCashIn() {
        val p = parse("bKash", "Cash In Tk 3,000.00 from 01612345678 successful. Fee Tk 0.00. Balance Tk 4,200.00. TrxID BJ12AB34CH at 30/10/2026 13:00")
        assertEquals(Direction.IN, p.direction)
        assertEquals(3000.0, p.amount, 0.001)
        assertEquals(0.0, p.fee, 0.001)
    }

    @Test fun nagadReceived() {
        val p = parse("NAGAD", "Money Received.\nAmount: Tk 700.00\nSender: 01712345678\nRef: lunch\nTxnID: 7A1B2C3D\nBalance: Tk 1,450.00\n29/10/2026 14:22")
        assertEquals("Nagad", p.source)
        assertEquals(Direction.IN, p.direction)
        assertEquals(700.0, p.amount, 0.001)
    }

    @Test fun bankSalaryCredit() {
        val p = parse("DBBL", "Your A/C XXXX5678 has been credited with BDT 55,000.00 on 28-10-2026 for SALARY OCT-26 from ACME LTD. Available Balance BDT 61,230.75")
        assertEquals("Dutch-Bangla Bank", p.source)
        assertEquals(SourceType.BANK, p.sourceType)
        assertEquals(Direction.IN, p.direction)
        assertEquals(55000.0, p.amount, 0.001)
        assertEquals("Salary", p.kind)
    }

    @Test fun bankDebitTransfer() {
        val p = parse("BRAC BANK", "Dear Customer, BDT 10,000.00 has been debited from your A/C ***1234 on 27-OCT-26 for Fund Transfer to bKash. Avl Bal: BDT 45,120.00")
        assertEquals("BRAC Bank", p.source)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(10000.0, p.amount, 0.001)
    }

    @Test fun cardPurchaseWithMerchant() {
        val p = parse("CityBank", "Your card ending 4321 has been used for BDT 3,500.00 at BATA SHOE STORE on 26-10-2026 17:45. Available limit BDT 96,500.00")
        assertEquals("City Bank", p.source)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(3500.0, p.amount, 0.001)
        assertEquals("BATA SHOE STORE", p.counterparty)
        assertEquals("Card Purchase", p.kind)
    }

    @Test fun atmWithdrawal() {
        val p = parse("EBL", "Tk5,000.00 withdrawn from A/C ...9876 at ATM GULSHAN-1 on 25/10/2026. Bal Tk20,000.00")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(5000.0, p.amount, 0.001)
        assertEquals("ATM Withdrawal", p.kind)
    }

    @Test fun amountAfterNumber() {
        val p = parse("SCB", "Your account 01-1234567-01 is debited 1,250.50 BDT for POS purchase at AARONG. Balance 8,000.00 BDT")
        assertEquals(1250.5, p.amount, 0.001)
        assertEquals(Direction.OUT, p.direction)
    }

    @Test fun unknownBankStillParsed() {
        val p = parse("XYZBANK", "Your A/C **5555 credited by BDT 2,000.00 on 20-10-2026. Bal BDT 5,000.00")
        assertEquals("XYZBANK", p.source)
        assertEquals(Direction.IN, p.direction)
        assertEquals(2000.0, p.amount, 0.001)
    }

    @Test fun ignoresOtp() {
        assertNull(SmsParser.parse("bKash", "Your bKash verification code is 123456. Never share it. Payment Tk 100"))
        assertNull(SmsParser.parse("DBBL", "Your OTP for transaction of BDT 5,000.00 is 998877"))
    }

    @Test fun ignoresFailed() {
        assertNull(SmsParser.parse("bKash", "Send Money Tk 500.00 to 01812345678 failed. Insufficient balance."))
    }

    @Test fun ignoresPersonalMessages() {
        assertNull(SmsParser.parse("+8801712345678", "I sent you Tk 500 on bKash, check"))
        assertNull(SmsParser.parse("01712345678", "received 200 tk"))
    }

    @Test fun ignoresPromotions() {
        assertNull(SmsParser.parse("bKash", "Enjoy 20% discount at Daraz this weekend with bKash! T&C apply."))
        assertNull(SmsParser.parse("Grameenphone", "Get 10GB internet for Tk 199 only. Dial *121#"))
    }

    @Test fun billPaid() {
        val p = parse("bKash", "Bill paid successfully. Tk 1,250.00 paid to DESCO for account 12345678. Fee Tk 0.00. Balance Tk 3,000.00. TrxID ABC at 30/10/2026 11:00")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(1250.0, p.amount, 0.001)
        assertEquals("Bill Payment", p.kind)
    }

    @Test fun paymentReceivedIsIncoming() {
        val p = parse("bKash", "Payment of Tk 2,000.00 received from 01712345678. Balance Tk 5,000.00. TrxID XYZ at 30/10/2026 12:00")
        assertEquals(Direction.IN, p.direction)
        assertEquals(2000.0, p.amount, 0.001)
        assertEquals("Payment Received", p.kind)
    }

    @Test fun bankToWalletIsTransfer() {
        val p = parse("BRAC BANK", "Dear Customer, BDT 10,000.00 has been debited from your A/C ***1234 on 27-OCT-26 for Fund Transfer to bKash. Avl Bal: BDT 45,120.00")
        assertEquals(true, p.transfer)
    }

    @Test fun addMoneyIsTransfer() {
        val p = parse("bKash", "Add Money Tk 5,000.00 from City Bank A/C ***9999 successful. Balance Tk 7,000.00. TrxID ABC at 30/10/2026 10:00")
        assertEquals(Direction.IN, p.direction)
        assertEquals(true, p.transfer)
    }

    @Test fun creditCardBillIsTransfer() {
        val p = parse("EBL", "Your A/C ...9876 is debited BDT 15,000.00 for credit card bill payment. Bal BDT 20,000.00")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(true, p.transfer)
    }

    @Test fun normalSpendingIsNotTransfer() {
        val p = parse("CityBank", "Your card ending 4321 has been used for BDT 3,500.00 at BATA SHOE STORE on 26-10-2026 17:45. Available limit BDT 96,500.00")
        assertEquals(false, p.transfer)
    }

    // ----- Formats seen from Bangladeshi banks (made-up numbers) -----

    @Test fun dbblShortCodeIsDbblNotRocket() {
        val p = parse("16216", "Dear Sir, your A/C ***1234 debited (ATM Cash Withdrawal) by Tk5,000.00 on 25-04-2026 03:23:45 PM C/B Tk10,282.11. NexusPay https://bit.ly/x")
        assertEquals("Dutch-Bangla Bank", p.source)
        assertEquals("ATM Withdrawal", p.kind)
        assertEquals("1234", p.accountLast4)
        assertEquals(10282.11, p.balance!!, 0.001)
    }

    @Test fun rocketOnlyWhenSmsSaysRocket() {
        val p = parse("16216", "Rocket: Cash In Tk 1,000.00 from 01712345678 successful. Balance Tk 1,500.00")
        assertEquals("Rocket", p.source)
        assertEquals(SourceType.MOBILE_WALLET, p.sourceType)
    }

    @Test fun minusSignInAmountIsNotTheBalance() {
        val p = parse("16216", "Dear Sir, your A/C ***1234 debited (ATM Cash Withdrawal) by Tk-20,000.00 on 01-08-2026 09:33:26 PM C/B Tk29,233.99.")
        assertEquals(20000.0, p.amount, 0.001)
    }

    @Test fun dbblCreditCardPurchase() {
        val p = parse("16216", "Thank you for using DBBL Cr. Card# 4**1111 for BDT 1,319.00 at PIZZA HUT DELIV on 24/10/2026 20:31. Available limit BDT 65,208.22. Helpline 16216")
        assertEquals(Direction.OUT, p.direction)
        assertEquals("Card Purchase", p.kind)
        assertEquals(1319.0, p.amount, 0.001)
        assertEquals("1111", p.accountLast4)
        assertEquals(true, p.creditCard)
        assertEquals("PIZZA HUT DELIV", p.counterparty)
    }

    @Test fun cardToBkashIsTransfer() {
        val p = parse("16216", "Thank you for using DBBL Cr. Card# 4**1111 for BDT 500.00 at bKash_AddMoney DH on 30/10/2026 07:32. Available limit BDT 64,708.22.")
        assertEquals(Direction.OUT, p.direction)
        assertEquals(true, p.transfer)
        assertEquals("Dutch-Bangla Bank", p.source)
        val b = parse("bKash", "You have received deposit of Tk 2,000.00 from VISA Card. Fee Tk 0.00. Balance Tk 12,371.94. TrxID ABC at 18/08/2026 10:39")
        assertEquals(Direction.IN, b.direction)
        assertEquals(true, b.transfer)
    }

    @Test fun cardBillPaymentIsTransfer() {
        val p = parse("16216", "Dear Sir, Your DBBL Cr. Card 444400****1111 has been credited (NexusPay- Bill Pay) by BDT 25,207.00 on 30/04/2026. HelpLine 16216")
        assertEquals(Direction.IN, p.direction)
        assertEquals("Card Bill Payment", p.kind)
        assertEquals(true, p.transfer)
        assertEquals("1111", p.accountLast4)
    }

    @Test fun cardStatementIsIgnored() {
        assertNull(SmsParser.parse("16216", "Dear Sir, your DBBL VISA Gold Card #1111 bill for Apr-26 is: BDT 20,127.50, Min Payment:BDT 1,006.38, Due date: 14-May-2026"))
    }

    @Test fun salaryBonusInterestTaxAndFee() {
        assertEquals("Salary", parse("16216", "Dear Sir, your A/C ***1234 credited(Salary for Apr-2026) by Tk1,03,317.00 on 30-04-2026 05:43:13 PM C/B Tk1,13,599.11.").kind)
        assertEquals(103317.0, parse("16216", "Dear Sir, your A/C ***1234 credited(Salary for Apr-2026) by Tk1,03,317.00 on 30-04-2026 05:43:13 PM C/B Tk1,13,599.11.").amount, 0.001)
        assertEquals("Bonus", parse("16216", "Dear Sir, your A/C ***1234 credited(Bonus for Eid May-2026) by Tk42,877.00 on 14-05-2026 04:13:53 PM C/B Tk55,969.11.").kind)
        assertEquals("Interest", parse("IFIC BANK", "16-05-2026 2:21:13 AM\nBDT 3,666.67 Interest for\nMIS0000000001\nA/C 01234***5678\nBalance: BDT 53,590.72").kind)
        val tax = parse("IFIC BANK", "16-05-2026 2:21:13 AM\nBDT 366.67 Tax on Interest for\nMIS0000000001\nA/C 01234***5678\nBalance: BDT 53,224.05")
        assertEquals("Tax", tax.kind); assertEquals(Direction.OUT, tax.direction); assertEquals(366.67, tax.amount, 0.001)
        assertEquals("Bank Charges", parse("16216", "Dear Sir, your A/C ***1234 debited (Fund Transfer) by Tk10.00 on 30-04-2026 05:55:28 PM C/B Tk61,382.11.").kind)
        assertEquals("Bank Charges", parse("IFIC BANK", "12-06-2026 8:04:08 AM\nBDT 300.00 A/C Maintenance Fee\nA/C 01234***5678\nBalance: BDT 89,959.93").kind)
    }

    @Test fun dpsInsuranceInvestment() {
        assertEquals("DPS", parse("IFIC BANK", "17-05-2026 9:57:27 PM\nBDT 5,000.00 Transfer for\nMonthly DPS Payment\nPAG0000000002\nA/C 01234***5678\nBalance: BDT 48,224.05").kind)
        assertEquals("Insurance", parse("bKash", "Insurance Premium successfully paid.\nInsurance Partner: SonaliLife\nPolicy Number: 1234567\nAmount: Tk 5,068.00\nFee: Tk 0.00\nTrxID ABC at 18/08/2026 15:14").kind)
        assertEquals("Investment", parse("IFIC BANK", "22-09-2026 3:54:27 PM\nBDT 200,000.00 Transfer for\nPurchase of Govt. Securities\nA/C 01234***5678\nBalance: BDT 35,367.70").kind)
    }

    @Test fun insuranceCompanyIsNotABank() {
        assertNull(SmsParser.parse("SONALI LIFE", "Your SLICL Policy Premium received.\nName: X\nPolicy No: 12345\nAmount:5068\nInstallment No: 2"))
        assertNull(SmsParser.parse("SONALI LIFE", "Congrats! Your SLICL Policy approved, payment received.\nSum Assured: 700000 BDT\nPremium: 5068 BDT"))
        assertNull(SmsParser.parse("IDRA_SONALI", "৳৫০৬৮.০০ গৃহীত\nপলিসি **০৮২৬\nপ্রিমিয়াম ১"))
    }

    @Test fun receiptsThatRepeatAPaymentAreIgnored() {
        assertNull(SmsParser.parse("Meter Token", "Successful!Your DESCO Prepaid Token is 0810-1571,Account No:12345678,Vending Amt:500.0,Enrg Cost: 478.56"))
        assertNull(SmsParser.parse("BATA", "Thanks for shopping at Bata. Invoice 12345. TK 2921 Details: https://x"))
        assertNull(SmsParser.parse("bKash", "Payment of Tk 400.00 is being reserved for ROBI AXIATA LIMITED-RM9564. Balance Tk 221.89. TrxID ABC at 30/10/2026 07:33"))
    }

    @Test fun ificFormats() {
        val dep = parse("IFIC BANK", "BDT 10000.00 has been deposited to your A/C 0190000005678 on 30.04.26 at 17:55 from DUTCH-BANGLA BANK LTD., BD. For query, please dial 16255")
        assertEquals(Direction.IN, dep.direction); assertEquals(10000.0, dep.amount, 0.001)
        assertEquals("5678", dep.accountLast4); assertEquals("DUTCH-BANGLA BANK", dep.otherBank)
        val card = parse("IFIC BANK", "22.08.26 at 13:35\nBDT 2700.00 Purchase from\nCard 4000***4321\nBalance: BDT 74091.04\nSHWAPNO, BD,\nIFIC Helpline-16255")
        assertEquals("Card Purchase", card.kind); assertEquals("4321", card.accountLast4); assertEquals(false, card.creditCard)
        assertEquals("SHWAPNO", card.counterparty)
        val out = parse("IFIC BANK", "Jun 13, 2026 at 8:31:39 PM\n6000.00 BDT Digital Transfer\nTo: 1234567890011\nFrom A/C: *********5678\nBalance: 83914.93 BDT")
        assertEquals(Direction.OUT, out.direction); assertEquals(6000.0, out.amount, 0.001); assertEquals("Send Money", out.kind)
    }

    @Test fun foreignCurrencyPurchase() {
        val p = parse("IFIC BANK", "15.09.26 at 20:02\nUSD 23.00 Purchase from\nCard 4000***4321\nBalance: BDT 88107.70\nANTHROPIC* CLAUDE SUB, US,\nIFIC Helpline-16255")
        assertEquals("USD", p.currency); assertEquals(23.0, p.amount, 0.001); assertEquals("ANTHROPIC* CLAUDE SUB", p.counterparty)
    }

    @Test fun bkashBillAndRemittance() {
        val bill = parse("bKash", "Bill successfully paid.\nBiller: DESCO (PREPAID)\nAccount: 12345678\nAmount: Tk 500.00\nFee: Tk 5.00\nTrxID ABC at 25/04/2026 23:44")
        assertEquals("Bill Payment", bill.kind); assertEquals(500.0, bill.amount, 0.001); assertEquals(5.0, bill.fee, 0.001); assertEquals("DESCO", bill.counterparty)
        val rem = parse("bKash", "You have received remittance.\nTotal: Tk 1,008.97\nGovt. incentive: Tk 24.61\nTrxID ABC at 07/06/2026 16:11.")
        assertEquals("Remittance", rem.kind); assertEquals(1008.97, rem.amount, 0.001)
    }
}
