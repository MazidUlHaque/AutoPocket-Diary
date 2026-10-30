package com.mazidulhaque.autopocket.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["smsId"], unique = true)])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Android's id for the SMS this came from; null for manual entries. Stops re-importing the same SMS. */
    val smsId: Long? = null,
    /** Account name, e.g. "bKash", "BRAC Bank", "Cash". */
    val source: String,
    val sourceType: String,
    /** "IN" or "OUT". */
    val direction: String,
    val amount: Double,
    val fee: Double = 0.0,
    /** What the app worked out from the SMS, e.g. "Card Purchase". */
    val kind: String,
    val counterparty: String? = null,
    /** The user's own title, e.g. "Shoes". */
    val title: String? = null,
    val note: String? = null,
    val timestamp: Long,
    val rawMessage: String? = null,
    /** Excluded from totals by the user. */
    val ignored: Boolean = false,
    /** e.g. "Groceries". Null until the app has guessed one. */
    val category: String? = null,
    /** Money moving between the user's own accounts: shown, but not counted as income or spending. */
    @ColumnInfo(defaultValue = "0") val transfer: Boolean = false,
    /** Deleted by the user. Kept (hidden) for SMS entries so a re-scan doesn't bring them back. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    /** Changed by the user, so automatic rules leave it alone. */
    @ColumnInfo(defaultValue = "0") val edited: Boolean = false,
    /** In the locked hidden zone: not shown or counted in the normal view. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** Which account or card, e.g. "Dutch-Bangla Bank|C|1111" (see [Account]). Null for cash / unknown. */
    val accountKey: String? = null,
    /** Set for a foreign purchase (e.g. "USD"); [amount] is then the taka amount the user entered, or 0. */
    val currency: String? = null,
    val foreignAmount: Double? = null,
    /** Balance after this transaction, from the SMS. */
    val balance: Double? = null,
    /** Another bank named in the SMS ("from DUTCH-BANGLA BANK"), used to match own transfers. */
    val otherBank: String? = null,
    /** Why the user should take a look: [REVIEW_TRANSFER] or [REVIEW_AMOUNT]. */
    val review: String? = null,
    /** The other half of a possible own transfer. */
    val pairId: Long? = null,
) {
    companion object {
        const val IN = "IN"
        const val OUT = "OUT"
        const val TYPE_BANK = "BANK"
        const val TYPE_WALLET = "MOBILE_WALLET"
        const val TYPE_CASH = "CASH"
        const val REVIEW_TRANSFER = "TRANSFER"
        const val REVIEW_AMOUNT = "AMOUNT"
    }
}

val Transaction.isIncoming get() = direction == Transaction.IN
val Transaction.isManual get() = smsId == null
val Transaction.total get() = amount + fee
val Transaction.counted get() = !ignored && !transfer
val Transaction.isSavings get() = category in com.mazidulhaque.autopocket.logic.Categories.savings

val Transaction.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(kind, counterparty).joinToString(" · ")

/** A monthly spending limit for one category. */
@Entity(tableName = "budgets")
data class Budget(
    @androidx.room.PrimaryKey val category: String,
    val amount: Double,
)

/** The user's remembered category for a merchant or person. */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @androidx.room.PrimaryKey val merchant: String,
    val category: String,
)

/**
 * A bank account, card or wallet found in the SMS. The user tells the app what it is the first time
 * ("New account found"); debit cards can be put inside the account they take money from.
 */
@Entity(tableName = "accounts")
data class Account(
    /** "Bank|A|1234" for an account, "Bank|C|1234" for a card, "Wallet|W|" for a wallet. */
    @androidx.room.PrimaryKey val key: String,
    val source: String,
    val sourceType: String,
    val last4: String?,
    val isCard: Boolean,
    /** From the SMS: true = credit card, false = debit card, null = unknown. */
    val creditCard: Boolean?,
    /** One of [TYPES], chosen by the user (null until then). */
    val type: String?,
    val name: String?,
    /** For a debit card: the account it takes money from. */
    val parentKey: String?,
    /** The user has answered "what is this?" (or chose not to be asked). */
    @ColumnInfo(defaultValue = "0") val confirmed: Boolean = false,
    val firstSeen: Long,
    /** Latest balance (or credit card available limit) seen in an SMS. */
    val lastBalance: Double?,
) {
    companion object {
        const val SAVINGS = "SAVINGS"
        const val CURRENT = "CURRENT"
        const val SALARY = "SALARY"
        const val CREDIT_CARD = "CREDIT_CARD"
        const val DEBIT_CARD = "DEBIT_CARD"
        const val DPS = "DPS"
        const val LOAN = "LOAN"
        const val WALLET = "WALLET"
        const val OTHER = "OTHER"
        val TYPES = listOf(SAVINGS, CURRENT, SALARY, CREDIT_CARD, DEBIT_CARD, DPS, LOAN, WALLET, OTHER)

        fun key(source: String, last4: String?, isCard: Boolean, wallet: Boolean): String = when {
            wallet -> "$source|W|"
            else -> "$source|${if (isCard) "C" else "A"}|${last4 ?: ""}"
        }
    }
}
