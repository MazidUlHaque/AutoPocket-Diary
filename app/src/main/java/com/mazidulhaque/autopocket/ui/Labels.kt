package com.mazidulhaque.autopocket.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.logic.Period
import com.mazidulhaque.autopocket.logic.PeriodType

// Categories and transaction types are stored in English (so backups and rules work in any language)
// and translated only when shown.

private val categoryNames = mapOf(
    "Groceries" to R.string.cat_groceries, "Food & Dining" to R.string.cat_food, "Clothing & Shoes" to R.string.cat_clothing,
    "Shopping" to R.string.cat_shopping, "Electricity" to R.string.cat_electricity, "Gas" to R.string.cat_gas,
    "Water" to R.string.cat_water, "Internet" to R.string.cat_internet, "Mobile Recharge" to R.string.cat_mobile,
    "Transport" to R.string.cat_transport, "Health" to R.string.cat_health, "Education" to R.string.cat_education,
    "Entertainment" to R.string.cat_entertainment, "Subscriptions" to R.string.cat_subscriptions, "Rent" to R.string.cat_rent,
    "Bills" to R.string.cat_bills, "Cash Withdrawal" to R.string.cat_cash_withdrawal, "Send Money" to R.string.cat_send_money,
    "Bank Charges & Tax" to R.string.cat_charges, "DPS / Savings" to R.string.cat_dps, "Insurance" to R.string.cat_insurance,
    "Investments" to R.string.cat_investments, "Other" to R.string.cat_other,
    "Salary" to R.string.cat_salary, "Bonus" to R.string.cat_bonus, "Interest" to R.string.cat_interest,
    "Received Money" to R.string.cat_received, "Cash In" to R.string.cat_cash_in,
    "Refund & Cashback" to R.string.cat_refund, "Other Incoming" to R.string.cat_other_incoming,
)

private val kindNames = mapOf(
    "Salary" to R.string.kind_salary, "Bonus" to R.string.cat_bonus, "Interest" to R.string.cat_interest,
    "Send Money" to R.string.kind_send_money, "Cash Out" to R.string.kind_cash_out,
    "Cash In" to R.string.kind_cash_in, "Add Money" to R.string.kind_add_money, "Mobile Recharge" to R.string.kind_recharge,
    "Bill Payment" to R.string.kind_bill, "ATM Withdrawal" to R.string.kind_atm, "Card Purchase" to R.string.kind_card,
    "Payment" to R.string.kind_payment, "Payment Received" to R.string.kind_payment_received,
    "Money Received" to R.string.kind_money_received, "Transfer" to R.string.kind_transfer, "Money In" to R.string.kind_money_in,
    "Money Out" to R.string.kind_money_out, "Income" to R.string.kind_income, "Expense" to R.string.kind_expense,
    "Manual" to R.string.kind_expense, "Remittance" to R.string.kind_remittance, "Cashback" to R.string.kind_cashback,
    "DPS" to R.string.cat_dps, "Insurance" to R.string.cat_insurance, "Investment" to R.string.cat_investments,
    "Tax" to R.string.kind_tax, "Bank Charges" to R.string.kind_charges, "Card Bill Payment" to R.string.kind_card_bill,
)

@Composable
fun categoryLabel(category: String): String = categoryNames[category]?.let { stringResource(it) } ?: category

@Composable
fun kindLabel(kind: String): String = kindNames[kind]?.let { stringResource(it) } ?: kind

/** "Cash" is the default account for manual entries; other account names are shown as they are. */
@Composable
fun accountName(source: String): String = if (source == "Cash") stringResource(R.string.account_cash) else source

@Composable
fun accountTypeLabel(type: String): String = stringResource(
    when (type) {
        Transaction.TYPE_BANK -> R.string.bank
        Transaction.TYPE_WALLET -> R.string.mobile_wallet
        else -> R.string.cash_other
    },
)

@Composable
fun periodTypeLabel(type: PeriodType): String = stringResource(
    when (type) {
        PeriodType.WEEK -> R.string.period_week
        PeriodType.MONTH -> R.string.period_month
        PeriodType.TWO_MONTHS -> R.string.period_two_months
        PeriodType.YEAR -> R.string.period_year
        PeriodType.CUSTOM -> R.string.period_custom
    },
)

@Composable
fun periodLabel(period: Period): String = period.label(LocalConfiguration.current.locales[0])

@Composable
fun displayTitle(t: Transaction): String =
    t.title?.takeIf { it.isNotBlank() } ?: listOfNotNull(kindLabel(t.kind), t.counterparty).joinToString(" · ")

@Composable
fun pluralText(n: Int, one: Int, many: Int): String = if (n == 1) stringResource(one, n) else stringResource(many, n)

@Composable
fun accountTypeName(type: String?): String? = when (type) {
    com.mazidulhaque.autopocket.data.Account.SAVINGS -> stringResource(R.string.acct_savings)
    com.mazidulhaque.autopocket.data.Account.CURRENT -> stringResource(R.string.acct_current)
    com.mazidulhaque.autopocket.data.Account.SALARY -> stringResource(R.string.acct_salary)
    com.mazidulhaque.autopocket.data.Account.CREDIT_CARD -> stringResource(R.string.acct_credit_card)
    com.mazidulhaque.autopocket.data.Account.DEBIT_CARD -> stringResource(R.string.acct_debit_card)
    com.mazidulhaque.autopocket.data.Account.DPS -> stringResource(R.string.acct_dps)
    com.mazidulhaque.autopocket.data.Account.LOAN -> stringResource(R.string.acct_loan)
    com.mazidulhaque.autopocket.data.Account.WALLET -> stringResource(R.string.acct_wallet)
    com.mazidulhaque.autopocket.data.Account.OTHER -> stringResource(R.string.acct_other)
    else -> null
}

/** e.g. "Salary account", "Visa Gold" (the user's name), "bKash", "Cash". */
@Composable
fun accountTitle(a: com.mazidulhaque.autopocket.data.Account?, source: String): String = when {
    a == null -> accountName(source)
    a.type == com.mazidulhaque.autopocket.data.Account.WALLET -> a.name ?: a.source
    else -> a.name ?: accountTypeName(a.type)
        ?: stringResource(if (a.isCard) R.string.acct_card_unknown else R.string.acct_unknown)
}

/** e.g. "Dutch-Bangla Bank ••1111". */
@Composable
fun accountSubtitle(a: com.mazidulhaque.autopocket.data.Account?, source: String): String = when {
    a == null -> if (source == "Cash") stringResource(R.string.cash_other) else source
    a.type == com.mazidulhaque.autopocket.data.Account.WALLET -> stringResource(R.string.mobile_wallet)
    else -> listOfNotNull(a.source, a.last4?.let { "••$it" }).joinToString(" ")
}
