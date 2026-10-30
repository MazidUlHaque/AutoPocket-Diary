package com.mazidulhaque.autopocket.logic

/**
 * Built-in categories and the rules that guess one from a transaction.
 * Names are stable English keys (translated only on screen). Guesses are only a starting point:
 * users can change any category, and the app remembers it per merchant.
 */
object Categories {
    const val OTHER = "Other"
    const val OTHER_INCOMING = "Other Incoming"
    const val SALARY = "Salary"
    const val BONUS = "Bonus"
    const val INTEREST = "Interest"
    const val SEND_MONEY = "Send Money"
    const val DPS = "DPS / Savings"
    const val INSURANCE = "Insurance"
    const val INVESTMENTS = "Investments"
    const val CHARGES = "Bank Charges & Tax"

    /** Money out that is still the user's money (shown on its own "Savings & investments" line). */
    val savings = setOf(DPS, INSURANCE, INVESTMENTS)

    val expense = listOf(
        "Groceries", "Food & Dining", "Clothing & Shoes", "Shopping", "Electricity", "Gas", "Water", "Internet",
        "Mobile Recharge", "Transport", "Health", "Education", "Entertainment", "Subscriptions", "Rent", "Bills",
        "Cash Withdrawal", SEND_MONEY, CHARGES, DPS, INSURANCE, INVESTMENTS, OTHER,
    )
    val incoming = listOf(SALARY, BONUS, INTEREST, "Received Money", "Cash In", "Refund & Cashback", OTHER_INCOMING)

    /** Older versions used other names; these are renamed when the app updates. */
    val renamed = mapOf("Other Income" to OTHER_INCOMING, "Sent to People" to SEND_MONEY)

    private fun rx(vararg p: String) = p.map { Regex(it, RegexOption.IGNORE_CASE) }

    // Checked in order, so more specific names come first ("pathao food" before "pathao").
    private val merchantRules: List<Pair<String, List<Regex>>> = listOf(
        "Food & Dining" to rx(
            "food\\s*panda", "pathao\\s*food", "\\bkfc\\b", "pizza", "domino", "burger", "\\bbfc\\b",
            "chillox", "takeout", "sultan'?s\\s*dine", "kacchi", "kabab", "kebab", "shawarma", "steak", "biriyani",
            "restaurant", "\\brest\\b", "\\bcafe\\b", "coffee", "bakery", "sweets", "catering", "teriyaki", "dera\\b",
            "dawat", "healthy\\s*food", "tasty", "meat\\s*theory", "rizq", "\\bhotel\\b", "food",
        ),
        "Groceries" to rx(
            "shwapno", "swapno", "meena\\s*bazar", "mina\\s*bazar", "\\bagora\\b", "unimart", "chaldal",
            "daily\\s*shopping", "prince\\s*bazar", "khulshi\\s*mart", "lavender", "super\\s*shop", "grocery",
        ),
        "Clothing & Shoes" to rx(
            "\\bbata\\b", "\\bapex\\b", "\\bbay\\b", "bay\\s*emporium", "\\blotto\\b", "aarong", "\\byellow\\b",
            "ecstasy", "cats?\\s*eye", "sailor", "richman", "infinity", "easy\\s*fashion", "le\\s*reve", "zipper",
            "style\\s*mart", "\\bshoe", "footwear", "fashion", "boutique", "tailor", "lifestyl",
        ),
        "Shopping" to rx(
            "daraz", "rokomari", "othoba", "pickaboo", "star\\s*tech", "ryans", "singer", "walton", "aliexpress",
            "amazon", "\\bmall\\b", "shopping", "gift",
        ),
        "Electricity" to rx(
            "\\bdesco\\b", "\\bdpdc\\b", "\\bnesco\\b", "\\bbpdb\\b", "wzpdcl", "west\\s*zone\\s*power", "palli\\s*bidyut",
            "\\breb\\b", "electricity", "prepaid\\s*meter",
        ),
        "Gas" to rx("titas", "karnaphuli\\s*gas", "jalalabad\\s*gas", "bakhrabad", "paschimanchal", "sundarban\\s*gas", "gas\\s*bill"),
        "Water" to rx("\\bwasa\\b", "water\\s*bill"),
        "Internet" to rx(
            "link3", "carnival", "amber\\s*it", "dot\\s*internet", "samonline", "bdcom", "internet\\s*bill", "broadband", "\\bisp\\b",
        ),
        "Mobile Recharge" to rx("grameenphone", "\\bgp\\b", "\\brobi\\b", "banglalink", "\\bairtel\\b", "teletalk", "recharge", "top\\s*up"),
        "Transport" to rx(
            "\\bpathao\\b", "\\buber\\b", "obhai", "shohoz", "railway", "\\bbus\\b", "biman", "us[\\s-]*bangla",
            "novoair", "air\\s*astra", "filling\\s*station", "petrol", "fuel", "\\bcng\\b", "parking", "logistics",
        ),
        "Health" to rx(
            "pharma", "pharmacy", "\\bph\\b", "hospital", "clinic", "diagnos", "ibn\\s*sina", "labaid", "evercare",
            "medical", "doctor", "skin",
        ),
        "Education" to rx(
            "school", "college", "university", "\\.edu\\b", "tuition", "exam\\s*fee", "admission", "coaching",
            "10\\s*minute\\s*school",
        ),
        "Subscriptions" to rx("anthropic", "claude", "openai", "chatgpt", "netflix", "spotify", "youtube", "google\\s*play", "apple\\.com", "icloud"),
        "Entertainment" to rx("cineplex", "blockbuster", "hoichoi", "chorki", "bioscope", "steam", "klimb"),
        "Rent" to rx("\\brent\\b", "house\\s*rent"),
    )

    /** Best guess for a transaction's category. */
    fun guess(incoming: Boolean, kind: String, counterparty: String?, message: String?): String {
        val text = listOfNotNull(counterparty, message).joinToString(" ")
        if (incoming) {
            return when (kind) {
                "Salary" -> SALARY
                "Bonus" -> BONUS
                "Interest" -> INTEREST
                "Cash In" -> "Cash In"
                "Cashback" -> "Refund & Cashback"
                "Remittance", "Money Received", "Payment Received" -> "Received Money"
                else -> if (Regex("refund|cashback|reversal", RegexOption.IGNORE_CASE).containsMatchIn(text)) "Refund & Cashback" else OTHER_INCOMING
            }
        }
        when (kind) {
            "ATM Withdrawal", "Cash Out" -> return "Cash Withdrawal"
            "DPS" -> return DPS
            "Insurance" -> return INSURANCE
            "Investment" -> return INVESTMENTS
            "Tax", "Bank Charges" -> return CHARGES
        }
        // A known merchant (or a hint like "Ref house rent") beats the generic transaction type.
        counterparty?.let { c -> merchantRules.firstOrNull { (_, ps) -> ps.any { it.containsMatchIn(c) } }?.let { return it.first } }
        if (kind != "Send Money") {
            merchantRules.firstOrNull { (_, ps) -> ps.any { it.containsMatchIn(text) } }?.let { return it.first }
        } else {
            // For transfers only the reference note counts, e.g. "Ref house rent".
            Regex("\\bref\\.?\\s*:?\\s*([^.\\n]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { ref ->
                merchantRules.firstOrNull { (_, ps) -> ps.any { it.containsMatchIn(ref) } }?.let { return it.first }
            }
        }
        return when (kind) {
            "Mobile Recharge" -> "Mobile Recharge"
            "Send Money" -> SEND_MONEY
            "Bill Payment" -> "Bills"
            else -> OTHER
        }
    }

    /** A stable key for "the same merchant / person", used to remember the user's category choice. */
    fun merchantKey(counterparty: String?): String? =
        counterparty?.uppercase()?.replace(Regex("[^A-Z0-9]+"), " ")?.trim()?.takeIf { it.length >= 2 }
}
