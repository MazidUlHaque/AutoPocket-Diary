package com.mazidulhaque.autopocket.logic

import kotlin.math.abs

/**
 * Finds money moving between the user's own accounts, so it isn't counted as both incoming and outgoing.
 *
 * Every account that sends SMS to this phone belongs to the user, so when money leaves one and the same
 * amount arrives in another, it is probably a transfer. How sure we are decides what happens:
 *  - sure (the SMS names the other bank, or it's a credit card bill payment): marked as a transfer;
 *  - only the amount and time match: put in "Needs a quick look" for the user to confirm.
 */
object TransferMatcher {
    data class Item(
        val id: Long,
        /** Bank or wallet, e.g. "Dutch-Bangla Bank". */
        val source: String,
        /** Account key, e.g. "Dutch-Bangla Bank|1234". */
        val account: String,
        val incoming: Boolean,
        val amount: Double,
        val timestamp: Long,
        val kind: String,
        /** Already a transfer (by the SMS itself, an earlier match, or the user). */
        val transfer: Boolean,
        /** Another bank named in this SMS, e.g. "DUTCH-BANGLA BANK". */
        val otherBank: String?,
        /** Changed by the user, or already reviewed: leave alone. */
        val settled: Boolean,
    )

    data class Result(
        /** Mark these as transfers. */
        val transfers: Set<Long>,
        /** Pairs to ask the user about (out id, in id). */
        val ask: List<Pair<Long, Long>>,
    )

    private const val SURE_WINDOW_MS = 30 * 60 * 1000L
    private const val BILL_WINDOW_MS = 24 * 60 * 60 * 1000L
    private const val ASK_WINDOW_MS = 2 * 60 * 60 * 1000L
    private const val ASK_MIN_AMOUNT = 100.0

    private val moneyMovingKinds = setOf("Send Money", "Money Received", "Money In", "Money Out", "Transfer", "Payment Received")

    fun match(items: List<Item>): Result {
        val used = mutableSetOf<Long>()
        val transfers = mutableSetOf<Long>()
        val ask = mutableListOf<Pair<Long, Long>>()
        fun sameAmount(a: Item, b: Item) = abs(a.amount - b.amount) < 0.01
        val open = items.filter { !it.settled }

        // 1. Credit card bill payment: the card says "credited (Bill Pay)"; find the account debit for it.
        for (bill in open.filter { it.incoming && it.kind == "Card Bill Payment" }) {
            val payer = open.filter { o ->
                !o.incoming && o.id !in used && !o.transfer && o.account != bill.account && sameAmount(o, bill) &&
                    abs(o.timestamp - bill.timestamp) <= BILL_WINDOW_MS
            }.minByOrNull { abs(it.timestamp - bill.timestamp) } ?: continue
            used += payer.id; used += bill.id
            transfers += payer.id
        }

        // 2. Incoming SMS that names the sending bank ("deposited … from DUTCH-BANGLA BANK").
        for (inc in open.filter { it.incoming && it.otherBank != null && it.id !in used }) {
            val named = bankWords(inc.otherBank!!)
            val sender = open.filter { o ->
                !o.incoming && o.id !in used && o.source != inc.source && sameAmount(o, inc) &&
                    abs(o.timestamp - inc.timestamp) <= SURE_WINDOW_MS && bankWords(o.source).any { it in named }
            }.minByOrNull { abs(it.timestamp - inc.timestamp) } ?: continue
            used += sender.id; used += inc.id
            transfers += sender.id; transfers += inc.id
        }

        // 3. Only amount and time match: ask.
        for (inc in open.filter { it.incoming && !it.transfer && it.id !in used && it.kind in moneyMovingKinds && it.amount >= ASK_MIN_AMOUNT }) {
            val out = open.filter { o ->
                !o.incoming && !o.transfer && o.id !in used && o.account != inc.account && o.kind in moneyMovingKinds &&
                    sameAmount(o, inc) && abs(o.timestamp - inc.timestamp) <= ASK_WINDOW_MS
            }.minByOrNull { abs(it.timestamp - inc.timestamp) } ?: continue
            used += out.id; used += inc.id
            ask += out.id to inc.id
        }
        return Result(transfers, ask)
    }

    /** "DUTCH-BANGLA BANK LTD." and "Dutch-Bangla Bank" share the word "dutch". */
    private fun bankWords(name: String): Set<String> =
        name.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 && it !in setOf("bank", "ltd", "plc", "limited") }.toSet()
}
