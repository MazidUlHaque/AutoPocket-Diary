package com.mazidulhaque.autopocket.logic

/**
 * Decides which account or card number in a bank SMS is the user's own.
 *
 * A transfer SMS can name both sides, and some phones get SMS for an account that isn't the user's
 * (a family member's). The user can say "not my account" for a number, or list their own numbers; then
 * only those count as theirs. Pure Kotlin so it can be unit tested.
 */
object AccountChooser {

    /** A number found in the SMS: last digits, and whether it's a card. */
    data class Candidate(val last4: String, val isCard: Boolean)

    sealed interface Choice {
        /** This number is the user's account or card. */
        data class Use(val candidate: Candidate) : Choice
        /** None of the numbers is the user's: count it for the bank, without a particular account. */
        data object BankOnly : Choice
        /** The SMS is about an account the user said belongs to someone else: don't count it at all. */
        data object Drop : Choice
    }

    /** Same format as the account keys stored in the app ("Bank|A|1234", "Bank|C|1234"). */
    fun key(source: String, c: Candidate) = "$source|${if (c.isCard) "C" else "A"}|${c.last4}"

    /**
     * [notMine]: numbers the user said are someone else's (the other side of their transfers).
     * [ignored]: accounts the user said are entirely someone else's (their SMS are dropped).
     * [own]: the user's own numbers (last digits); when not empty, only these are theirs.
     */
    fun choose(source: String, candidates: List<Candidate>, notMine: Set<String>, ignored: Set<String>, own: List<String>): Choice {
        val usable = candidates.filter { key(source, it) !in notMine && key(source, it) !in ignored }
        val pick = if (own.isEmpty()) usable.firstOrNull() else usable.firstOrNull { c -> own.any { matches(c.last4, it) } }
        return when {
            pick != null -> Choice.Use(pick)
            candidates.any { key(source, it) in ignored } -> Choice.Drop
            else -> Choice.BankOnly
        }
    }

    /** Whether an account's last digits match one of the user's numbers ("1234" matches "0012345671234"). */
    fun matches(last4: String, own: String): Boolean {
        val a = last4.filter(Char::isDigit)
        val b = own.filter(Char::isDigit)
        val n = minOf(a.length, b.length, 4)
        return n >= 3 && a.takeLast(n) == b.takeLast(n)
    }

    /** Whether an account counts as the user's when they've listed their own numbers ([own] empty = every account). */
    fun isOwn(last4: String?, own: List<String>): Boolean =
        own.isEmpty() || last4 == null || own.any { matches(last4, it) }

    /** Cleans what the user typed: digits only, the last 4 kept. Null if fewer than 3 digits. */
    fun clean(typed: String): String? = typed.filter(Char::isDigit).takeLast(4).takeIf { it.length >= 3 }
}
