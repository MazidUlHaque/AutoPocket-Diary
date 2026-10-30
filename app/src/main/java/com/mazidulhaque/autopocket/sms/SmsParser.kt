package com.mazidulhaque.autopocket.sms

/**
 * Turns banking / mobile-wallet SMS into transactions.
 *
 * Pure Kotlin (no Android imports) so it can be unit tested on any JVM.
 * Banks word their SMS differently, so this works on rules and keywords rather than exact
 * templates. Anything it gets wrong can be fixed by hand in the app.
 */
object SmsParser {

    enum class SourceType { BANK, MOBILE_WALLET }
    enum class Direction { IN, OUT }

    data class Parsed(
        /** Bank or wallet name, e.g. "Dutch-Bangla Bank", "bKash". */
        val source: String,
        val sourceType: SourceType,
        val direction: Direction,
        /** Amount in [currency]. */
        val amount: Double,
        val fee: Double,
        /** Stable English key, e.g. "Card Purchase", "Salary" (translated only on screen). */
        val kind: String,
        val counterparty: String?,
        /** Certainly money moving between the user's own accounts (e.g. card → bKash Add Money). */
        val transfer: Boolean = false,
        /** Last 4 digits of the account or card the SMS is about. */
        val accountLast4: String? = null,
        val isCard: Boolean = false,
        /** true = credit card, false = debit card, null = not a card / unknown. */
        val creditCard: Boolean? = null,
        /** "BDT" normally; e.g. "USD" for a foreign purchase (the taka amount is then unknown). */
        val currency: String = "BDT",
        /** Balance after the transaction, when the SMS shows it (helps spot duplicate SMS). */
        val balance: Double? = null,
        /** Another bank named in the SMS (e.g. "from DUTCH-BANGLA BANK"), a strong own-transfer hint. */
        val otherBank: String? = null,
    )

    private data class KnownSource(val name: String, val type: SourceType, val patterns: List<Regex>)

    private fun rx(vararg p: String) = p.map { Regex(it, RegexOption.IGNORE_CASE) }

    // Checked against the sender first, then the message. Patterns are strict on purpose:
    // "Sonali Life" (insurance) must not become "Sonali Bank", "Trust Shawarma" must not become "Trust Bank".
    private val knownSources = listOf(
        KnownSource("bKash", SourceType.MOBILE_WALLET, rx("^bkash$", "\\bbkash\\b(?!_?\\s*add)")),
        KnownSource("Nagad", SourceType.MOBILE_WALLET, rx("^nagad$", "\\bnagad\\b")),
        KnownSource("Upay", SourceType.MOBILE_WALLET, rx("^upay$", "\\bupay\\b")),
        KnownSource("Tap", SourceType.MOBILE_WALLET, rx("^tap$", "trust\\s*axiata\\s*pay")),
        KnownSource("Dutch-Bangla Bank", SourceType.BANK, rx("^16216$", "^dbbl", "\\bdbbl\\b", "dutch[\\s-]*bangla")),
        KnownSource("BRAC Bank", SourceType.BANK, rx("^brac\\s*bank", "brac\\s*bank")),
        KnownSource("City Bank", SourceType.BANK, rx("^city\\s*bank", "^cbl$", "city\\s*bank")),
        KnownSource("Eastern Bank", SourceType.BANK, rx("^ebl", "eastern\\s*bank")),
        KnownSource("Islami Bank", SourceType.BANK, rx("^ibbl", "islami\\s*bank\\s*bangladesh", "^islami\\s*bank")),
        KnownSource("Standard Chartered", SourceType.BANK, rx("^scb", "^stanchart", "standard\\s*chartered")),
        KnownSource("Prime Bank", SourceType.BANK, rx("^prime\\s*bank", "prime\\s*bank")),
        KnownSource("Mutual Trust Bank", SourceType.BANK, rx("^mtb", "mutual\\s*trust\\s*bank")),
        KnownSource("Sonali Bank", SourceType.BANK, rx("^sonali\\s*bank", "sonali\\s*bank")),
        KnownSource("Janata Bank", SourceType.BANK, rx("^janata", "janata\\s*bank")),
        KnownSource("Agrani Bank", SourceType.BANK, rx("^agrani", "agrani\\s*bank")),
        KnownSource("Pubali Bank", SourceType.BANK, rx("^pubali", "pubali\\s*bank")),
        KnownSource("UCB", SourceType.BANK, rx("^ucb", "united\\s*commercial\\s*bank")),
        KnownSource("Bank Asia", SourceType.BANK, rx("^bank\\s*asia", "bank\\s*asia")),
        KnownSource("Dhaka Bank", SourceType.BANK, rx("^dhaka\\s*bank", "dhaka\\s*bank")),
        KnownSource("Southeast Bank", SourceType.BANK, rx("^sebl", "southeast\\s*bank")),
        KnownSource("Trust Bank", SourceType.BANK, rx("^trust\\s*bank", "^tbl$", "trust\\s*bank")),
        KnownSource("NCC Bank", SourceType.BANK, rx("^ncc", "ncc\\s*bank")),
        KnownSource("One Bank", SourceType.BANK, rx("^one\\s*bank", "one\\s*bank")),
        KnownSource("Jamuna Bank", SourceType.BANK, rx("^jamuna", "jamuna\\s*bank")),
        KnownSource("HSBC", SourceType.BANK, rx("^hsbc", "\\bhsbc\\b")),
        KnownSource("Mercantile Bank", SourceType.BANK, rx("^mercantile", "mercantile\\s*bank")),
        KnownSource("IFIC Bank", SourceType.BANK, rx("^ific", "\\bific\\b")),
        KnownSource("EXIM Bank", SourceType.BANK, rx("^exim", "exim\\s*bank")),
        KnownSource("Al-Arafah Islami Bank", SourceType.BANK, rx("^aibl", "al[\\s-]*arafah")),
        KnownSource("Social Islami Bank", SourceType.BANK, rx("^sibl")),
        KnownSource("First Security Islami Bank", SourceType.BANK, rx("^fsibl", "first\\s*security")),
        KnownSource("Shahjalal Islami Bank", SourceType.BANK, rx("^sjibl", "shahjalal\\s*islami")),
        KnownSource("LankaBangla", SourceType.BANK, rx("^lankabangla", "lankabangla")),
        KnownSource("AB Bank", SourceType.BANK, rx("^ab\\s*bank", "^abbl")),
        KnownSource("Premier Bank", SourceType.BANK, rx("^premier\\s*bank", "premier\\s*bank")),
        KnownSource("Midland Bank", SourceType.BANK, rx("^midland", "midland\\s*bank")),
    )

    /** Rocket is DBBL's mobile wallet; only SMS that say "Rocket" belong to it. */
    private val rocket = Regex("\\brocket\\b", RegexOption.IGNORE_CASE)

    // A sender that is a normal phone number is a person, not a bank.
    private val personalNumber = Regex("^\\+?(?:88)?01\\d{9}$")

    /** Senders whose SMS are receipts or notices that repeat a payment already seen from a bank or wallet. */
    private val ignoredSenders = rx(
        "life", "insurance", "takaful", "bima", "^idra", "meter\\s*token", "flexi\\s*load", "offer", "deal", "promo",
    )

    private val skipPatterns = rx(
        "\\botp\\b", "one[\\s-]*time\\s*(?:password|pin|code)", "verification\\s*code", "security\\s*code",
        "activation\\s*code", "\\bpassword\\b", "\\bfailed\\b", "unsuccessful", "declined", "insufficient",
        "\\bwill\\s+be\\s+(?:debited|credited)", "being\\s+reserved", "\\brequest(?:ed)?\\s+(?:money|tk|bdt)",
        "payment\\s+due", "due\\s+date", "minimum\\s+(?:amount\\s+)?due", "min\\.?\\s*payment", "\\bbill\\s+for\\s+\\w+-?\\d",
        "^balance\\s+of\\s+your", "sum\\s+assured", "maturity", "thanks\\s+for\\s+shopping", "\\bstatement\\b",
    )

    private val bankHint = Regex("a/c|acct|account|card", RegexOption.IGNORE_CASE)
    private val insuranceWords = Regex("\\blife\\s*ins|insurance|takaful", RegexOption.IGNORE_CASE)

    /** Specific rules, checked in order before the general keywords. */
    private data class Rule(val regex: Regex, val direction: Direction?, val kind: String, val transfer: Boolean = false)

    private fun rule(p: String, d: Direction?, kind: String, transfer: Boolean = false) =
        Rule(Regex(p, RegexOption.IGNORE_CASE), d, kind, transfer)

    private val rules = listOf(
        rule("tax\\s+on\\s+interest|tax\\s+deduction|\\bvat\\s+on\\b|excise\\s+duty|source\\s+tax", Direction.OUT, "Tax"),
        rule("maintenance\\s+fee|txn\\s+charge|sms\\s+charge|annual\\s+fee|card\\s+fee|service\\s+charge|\\bcharges?\\s+(?:of|tk|bdt)", Direction.OUT, "Bank Charges"),
        rule("card[^\\n]{0,40}credited[^\\n]{0,40}bill\\s*pay|credited\\s*\\(\\s*[^)]*bill\\s*pay", Direction.IN, "Card Bill Payment", transfer = true),
        rule("credit\\s*card\\s*(?:bill\\s*)?payment|card\\s*bill\\s*payment|card\\s*payment\\s*received", null, "Card Bill Payment", transfer = true),
        rule("received\\s+deposit\\s+of[^\\n]{0,40}from\\s+(?:visa|master|amex|card|bank)", Direction.IN, "Add Money", transfer = true),
        rule("bkash[_\\s-]*add\\s*money|nagad[_\\s-]*add\\s*money|\\badd\\s*money\\b", null, "Add Money", transfer = true),
        rule("\\bown\\s+(?:a/c|acc|account)|between\\s+(?:your|own)\\s+accounts?", null, "Transfer", transfer = true),
        rule("\\bsalary\\b", Direction.IN, "Salary"),
        rule("\\bbonus\\b", Direction.IN, "Bonus"),
        rule("interest\\s+credit|\\binterest\\s+for\\b|interest\\s+(?:has\\s+been\\s+)?credited|profit\\s+credit", Direction.IN, "Interest"),
        rule("\\bdps\\b|deposit\\s+pension|monthly\\s+savings\\s+scheme", Direction.OUT, "DPS"),
        rule("insurance\\s+premium|premium\\s+(?:successfully\\s+)?paid|\\blife\\s*ins", Direction.OUT, "Insurance"),
        rule("govt\\.?\\s*securit|sanchay|savings\\s+certificate|treasury\\s+(?:bill|bond)", Direction.OUT, "Investment"),
        rule("\\bremittance\\b", Direction.IN, "Remittance"),
        rule("\\bcashback\\b", Direction.IN, "Cashback"),
        rule("thank\\s+you\\s+for\\s+using[^\\n]{0,40}card|card[^\\n]{0,40}(?:been\\s+)?(?:used|spent)\\s+(?:for|at)", Direction.OUT, "Card Purchase"),
        rule("purchase\\s+from\\s*(?:\\n\\s*)?card|pos\\s+purchase|nexuspay-?\\s*purchase|e-?com(?:merce)?\\s+(?:txn|transaction|purchase)", Direction.OUT, "Card Purchase"),
        rule("\\batm\\b|cash\\s+withdrawal", Direction.OUT, "ATM Withdrawal"),
        rule("bill\\s+successfully\\s+paid|bill\\s*pay(?:ment)?\\b|\\bbill\\s+paid", Direction.OUT, "Bill Payment"),
        rule("\\btop\\s*-?\\s*up\\b|\\brecharge\\b", Direction.OUT, "Mobile Recharge"),
    )

    private data class Keyword(val regex: Regex, val direction: Direction, val strong: Boolean)

    private fun kw(p: String, d: Direction, strong: Boolean = false, ignoreCase: Boolean = true) =
        Keyword(if (ignoreCase) Regex(p, RegexOption.IGNORE_CASE) else Regex(p), d, strong)

    private val keywords = listOf(
        kw("\\bcredited\\b", Direction.IN, strong = true),
        kw("\\bCR\\b", Direction.IN, strong = true, ignoreCase = false),
        kw("\\bdebited\\b", Direction.OUT, strong = true),
        kw("\\bDR\\b", Direction.OUT, strong = true, ignoreCase = false),
        kw("has\\s+been\\s+deposited|deposit\\s+for|transfer\\s+incoming", Direction.IN, strong = true),
        kw("transfer\\s+outgoing|digital\\s+transfer|transfer\\s+for", Direction.OUT, strong = true),
        kw("\\breceived\\b", Direction.IN),
        kw("\\bcash\\s*in\\b", Direction.IN),
        kw("\\bdeposit(?:ed)?\\b", Direction.IN),
        kw("\\brefund(?:ed)?\\b", Direction.IN),
        kw("\\bwithdrawn?\\b", Direction.OUT),
        kw("\\bwithdrawal\\b", Direction.OUT),
        kw("\\bcash\\s*out\\b", Direction.OUT),
        kw("\\bsend\\s*money\\b", Direction.OUT),
        kw("\\bsent\\b", Direction.OUT),
        kw("\\bpayment\\b", Direction.OUT),
        kw("\\bpaid\\b", Direction.OUT),
        kw("\\bpurchase[ds]?\\b", Direction.OUT),
        kw("\\bspent\\b", Direction.OUT),
        kw("\\bused\\s+(?:for|at)\\b", Direction.OUT),
        kw("\\btransferred\\b", Direction.OUT),
        kw("\\btransfer\\s+to\\b", Direction.OUT),
    )

    // "Payment received" style messages are money IN, even though they contain "payment".
    private val paymentReceived = rx(
        "\\bpayment\\s+(?:of\\s+)?(?:(?:tk|bdt|৳)\\.?\\s*[0-9][0-9,.]*\\s+)?(?:has\\s+been\\s+|was\\s+|is\\s+)?received",
        "\\breceived\\s+(?:a\\s+)?payment",
    )

    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)"
    private const val CUR = "(tk\\.?|bdt|৳|taka|usd|us\\$|eur|gbp|inr|sgd|aed|myr)"
    private val moneyBefore = Regex("$CUR\\s*-?\\s*$NUM", RegexOption.IGNORE_CASE)
    // Same line only, and not a clock time ("20:02\nUSD 23.00" must not read as 2).
    private val moneyAfter = Regex("(?<![\\w.:])$NUM[ \\t]*$CUR(?![a-z])", RegexOption.IGNORE_CASE)
    private val amountLabel = Regex("amount\\s*[:=]?\\s*(?:tk|bdt|৳)?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val notTheAmountBefore = Regex(
        "(?:balance|\\bbal\\b|c/b|avl|available|limit|fee|charge|vat|outstanding|due|minimum|min\\.?|payment\\s*:|cost)\\W*(?:is|of|:)?\\W*$",
        RegexOption.IGNORE_CASE,
    )
    private val notTheAmountAfter = Regex("^\\s*(?:bdt\\s*)?balance", RegexOption.IGNORE_CASE)
    private val feePattern = Regex("(?:\\bfee|charge)\\s*(?:of|:)?\\s*(?:tk\\.?|bdt|৳)?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val balancePattern = Regex(
        "(?:c/b|balance|\\bbal\\b|avl\\.?\\s*bal|available\\s+limit)\\s*:?\\s*(?:is\\s*)?(?:tk\\.?|bdt)?\\s*$NUM|$NUM\\s*(?:bdt\\s*)?balance",
        RegexOption.IGNORE_CASE,
    )

    private val cardNumber = Regex("card\\s*(?:no\\.?)?\\s*#?\\s*:?\\s*([0-9xX*]{4,24})", RegexOption.IGNORE_CASE)
    private val cardEnding = Regex("card\\s+ending\\s+(?:with\\s+)?(\\d{4})", RegexOption.IGNORE_CASE)
    private val accountNumber = Regex("a/c(?:count)?\\s*(?:no\\.?)?\\s*:?\\s*([0-9xX*]{3,24})", RegexOption.IGNORE_CASE)
    private val creditCardWords = Regex("\\bcr\\.?\\s*card|credit\\s*card|available\\s+limit", RegexOption.IGNORE_CASE)
    private val debitCardWords = Regex("debit\\s*card|\\bbalance\\b", RegexOption.IGNORE_CASE)

    private val counterpartyPattern = Regex(
        "\\b(?:to|from|at)\\s+(?:a/c\\s+)?([A-Za-z0-9][A-Za-z0-9 &'*/._-]{1,40}?)" +
            "(?=\\s*[,;:(]|\\.\\s|\\.$|\\s+(?:on|ref|fee|trxid|txnid|trx|successful|is|was|balance|bal|at|via|for|date)\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val billerPattern = Regex("biller\\s*:\\s*([^\\n/]+)", RegexOption.IGNORE_CASE)
    private val bracketDescription = Regex("(?:debited|credited)\\s*\\(([^)]{3,80})\\)", RegexOption.IGNORE_CASE)
    private val merchantLine = Regex("^\\s*([A-Za-z0-9][^\\n]{1,60}?),\\s*[A-Z]{2},?\\s*$", RegexOption.MULTILINE)
    private val phoneLike = Regex("^\\+?(?:88)?01\\d{9}$")
    private val looksLikeDate = Regex("^\\d{1,4}[/.-]\\d{1,2}([/.-]\\d{1,4})?")
    private val looksLikeMoney = Regex("^(?:tk|bdt|taka|usd)\\b", RegexOption.IGNORE_CASE)
    private val otherBankPattern = Regex(
        "\\bfrom\\s+([A-Z][A-Za-z.&-]+(?:\\s+[A-Z][A-Za-z.&-]+){0,3}\\s+BANK)",
        RegexOption.IGNORE_CASE,
    )

    private val kinds = listOf(
        "Send Money" to rx("send\\s*money"),
        "Cash Out" to rx("cash\\s*out"),
        "Cash In" to rx("cash\\s*in\\b"),
        "Payment" to rx("\\bpayment\\b", "\\bpaid\\b"),
        "Money Received" to rx("\\breceived\\b"),
    )
    private val outKinds = setOf("Send Money", "Cash Out", "Mobile Recharge", "Bill Payment", "ATM Withdrawal", "Card Purchase", "Payment")
    private val transferWords = Regex("npsb|beftn|\\beft\\b|rtgs|fund\\s*transfer|digital\\s*transfer|transfer\\s+for|acc\\s*to\\s*acc|\\btransfer", RegexOption.IGNORE_CASE)

    fun parse(sender: String, rawBody: String): Parsed? {
        val body = toAsciiDigits(rawBody)
        val cleanSender = sender.trim()
        if (personalNumber.matches(cleanSender.replace(" ", "").replace("-", ""))) return null
        if (ignoredSenders.any { it.containsMatchIn(cleanSender) }) return null
        if (skipPatterns.any { it.containsMatchIn(body) }) return null

        val known = knownSources.firstOrNull { s -> s.patterns.any { it.containsMatchIn(cleanSender) } }
            ?: knownSources.takeUnless { insuranceWords.containsMatchIn(cleanSender) }
                ?.firstOrNull { s -> s.patterns.any { it.containsMatchIn(body) } }
        var (sourceName, sourceType) = when {
            known != null -> known.name to known.type
            insuranceWords.containsMatchIn(cleanSender) -> return null
            bankHint.containsMatchIn(body) -> cleanSender to SourceType.BANK
            else -> return null
        }
        if (sourceName == "Dutch-Bangla Bank" && rocket.containsMatchIn(body)) {
            sourceName = "Rocket"
            sourceType = SourceType.MOBILE_WALLET
        }

        val matching = rules.filter { it.regex.containsMatchIn(body) }
        val rule = matching.firstOrNull()
        // e.g. "Thank you for using … Card … at bKash_AddMoney": the Add Money rule has no direction,
        // the card-purchase rule after it does.
        val ruleDirection = matching.firstNotNullOfOrNull { it.direction }
        val keywordDirection = findDirection(body)
        val direction = when {
            paymentReceived.any { it.containsMatchIn(body) } -> Direction.IN
            // A specific rule knows the direction, except when the SMS itself says credited/debited.
            rule?.direction != null && keywordDirection?.second != true -> rule.direction
            keywordDirection != null -> keywordDirection.first
            ruleDirection != null -> ruleDirection
            // "Add Money Tk 5,000 from City Bank": money arriving in the wallet.
            rule?.kind == "Add Money" -> if (sourceType == SourceType.MOBILE_WALLET) Direction.IN else Direction.OUT
            else -> return null
        }
        val (amount, currency) = findAmount(body) ?: return null
        if (amount <= 0.0) return null
        val fee = if (direction == Direction.OUT) findFee(body) else 0.0

        var kind = rule?.kind
            ?: kinds.firstOrNull { (_, ps) -> ps.any { it.containsMatchIn(body) } }?.first
            ?: when {
                transferWords.containsMatchIn(body) -> if (direction == Direction.IN) "Money Received" else "Send Money"
                direction == Direction.IN -> "Money In"
                else -> "Money Out"
            }
        if (direction == Direction.IN && kind in outKinds) {
            kind = if (paymentReceived.any { it.containsMatchIn(body) }) "Payment Received" else "Money Received"
        }
        // DBBL charges a small fee as its own "(Fund Transfer) by Tk10.00" SMS next to each NPSB transfer.
        if (kind == "Send Money" && amount <= 30.0 && Regex("\\(\\s*fund\\s+transfer\\s*\\)", RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            kind = "Bank Charges"
        }

        val card = cardEnding.find(body)?.groupValues?.get(1)
            ?: cardNumber.find(body)?.groupValues?.get(1)?.takeLast(4)?.takeIf { it.all(Char::isDigit) }
        val account = accountNumber.find(body)?.groupValues?.get(1)?.takeLast(4)?.takeIf { it.all(Char::isDigit) }
        val isCard = card != null
        val creditCard = when {
            !isCard -> null
            creditCardWords.containsMatchIn(body) -> true
            debitCardWords.containsMatchIn(body) -> false
            else -> null
        }

        // Bank → own mobile wallet ("Fund Transfer to bKash") is money moving between the user's own accounts.
        val toWallet = sourceType == SourceType.BANK && direction == Direction.OUT &&
            Regex("\\bto\\s+(?:your\\s+)?(?:bkash|nagad|rocket|upay)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)

        return Parsed(
            source = sourceName,
            sourceType = sourceType,
            direction = direction,
            amount = amount,
            fee = fee,
            kind = kind,
            counterparty = findCounterparty(body, kind),
            transfer = rule?.transfer == true || toWallet,
            accountLast4 = card ?: account,
            isCard = isCard,
            creditCard = creditCard,
            currency = currency,
            balance = balancePattern.find(body)?.let { m -> toNumber(m.groupValues[1].ifEmpty { m.groupValues[2] }) },
            otherBank = otherBankPattern.find(body)?.groupValues?.get(1)?.trim(),
        )
    }

    /** Direction from general keywords, and whether it came from a "strong" word (credited/debited/deposit). */
    private fun findDirection(body: String): Pair<Direction, Boolean>? {
        val hits = keywords.mapNotNull { k -> k.regex.find(body)?.let { Triple(it.range.first, k.direction, k.strong) } }
        if (hits.isEmpty()) return null
        val strong = hits.filter { it.third }
        val best = strong.ifEmpty { hits }.minBy { it.first }
        return best.second to best.third
    }

    private fun findAmount(body: String): Pair<Double, String>? {
        val candidates = (moneyBefore.findAll(body).map { it to it.groupValues[2] } + moneyAfter.findAll(body).map { it to it.groupValues[1] })
            .sortedBy { it.first.range.first }
            .filter { (m, _) ->
                val before = body.substring(maxOf(0, m.range.first - 25), m.range.first)
                val after = body.substring(m.range.last + 1, minOf(body.length, m.range.last + 20))
                !notTheAmountBefore.containsMatchIn(before) && !notTheAmountAfter.containsMatchIn(after)
            }
        candidates.firstOrNull()?.let { (m, number) ->
            val cur = (if (m.groupValues[1].first().isDigit()) m.groupValues[2] else m.groupValues[1]).lowercase()
            val currency = when {
                cur.startsWith("usd") || cur == "us$" -> "USD"
                cur in setOf("eur", "gbp", "inr", "sgd", "aed", "myr") -> cur.uppercase()
                else -> "BDT"
            }
            return toNumber(number)?.let { it to currency }
        }
        return amountLabel.find(body)?.let { m -> toNumber(m.groupValues[1])?.let { it to "BDT" } }
    }

    private fun findFee(body: String): Double =
        feePattern.find(body)?.let { toNumber(it.groupValues[1]) } ?: 0.0

    private fun findCounterparty(body: String, kind: String): String? {
        billerPattern.find(body)?.let { return it.groupValues[1].replace(Regex("\\(.*?\\)"), "").trim() }
        if (kind == "Card Purchase" || kind == "Add Money") {
            merchantLine.find(body)?.let { return it.groupValues[1].trim() }
        }
        val fromText = counterpartyPattern.findAll(body)
            .map { it.groupValues[1].trim().trimEnd('.', '-', '_') }
            .firstOrNull {
                it.length >= 2 && (it.any(Char::isLetter) || phoneLike.matches(it)) && !looksLikeDate.containsMatchIn(it) && !looksLikeMoney.containsMatchIn(it) &&
                    !Regex("^(?:your|a/c|card)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
            }
            ?.replace(Regex("-RM\\d+$", RegexOption.IGNORE_CASE), "")
        if (fromText != null) return fromText
        // Bank SMS like "credited(Salary for Apr-2026)" or "debited (NPSB Acc To Acc Txn)".
        return bracketDescription.find(body)?.groupValues?.get(1)?.substringBefore('|')?.trim()
            ?.removePrefix("EFT CR -By ")?.removePrefix("EFT CR - By ")
            // Generic bank wording ("POS Purchase", "Fund Transfer Debit") says what happened, not who: no name.
            ?.takeUnless { Regex("purchase|transfer|withdrawal|\\batm\\b|npsb|\\btxn\\b|interest|tax|deduction", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    }

    private fun toNumber(s: String): Double? = s.replace(",", "").trimEnd('.').toDoubleOrNull()

    /** Bangla digits (০-৯) → 0-9, so amounts written in Bangla are read too. */
    private fun toAsciiDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '০'..'৯') '0' + (c - '০') else c)
    }
}
