package com.mazidulhaque.autopocket.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What goes inside a backup file (before encryption), and strict checks when reading one back,
 * so a damaged or crafted file can't put nonsense into the app.
 */
object BackupFile {
    enum class Problem { DAMAGED, NOT_OURS, TOO_NEW, TOO_LARGE }
    class Invalid(val problem: Problem) : Exception(problem.name)

    data class Contents(val transactions: List<Transaction>, val rules: List<CategoryRule>, val accounts: List<Account> = emptyList())

    private const val APP = "AutoPocketDiary"
    private const val FORMAT = 2
    private const val MAX_ENTRIES = 500_000

    fun write(transactions: List<Transaction>, rules: List<CategoryRule>, accounts: List<Account>): String {
        val list = JSONArray()
        transactions.forEach { t ->
            list.put(
                JSONObject()
                    .put("smsId", t.smsId ?: JSONObject.NULL)
                    .put("source", t.source)
                    .put("sourceType", t.sourceType)
                    .put("direction", t.direction)
                    .put("amount", t.amount)
                    .put("fee", t.fee)
                    .put("kind", t.kind)
                    .put("counterparty", t.counterparty ?: JSONObject.NULL)
                    .put("title", t.title ?: JSONObject.NULL)
                    .put("note", t.note ?: JSONObject.NULL)
                    .put("timestamp", t.timestamp)
                    .put("rawMessage", t.rawMessage ?: JSONObject.NULL)
                    .put("ignored", t.ignored)
                    .put("category", t.category ?: JSONObject.NULL)
                    .put("transfer", t.transfer)
                    .put("deleted", t.deleted)
                    .put("edited", t.edited)
                    .put("hidden", t.hidden)
                    .put("accountKey", t.accountKey ?: JSONObject.NULL)
                    .put("currency", t.currency ?: JSONObject.NULL)
                    .put("foreignAmount", t.foreignAmount ?: JSONObject.NULL)
                    .put("balance", t.balance ?: JSONObject.NULL)
                    .put("otherBank", t.otherBank ?: JSONObject.NULL)
                    .put("review", t.review?.takeIf { it == Transaction.REVIEW_AMOUNT } ?: JSONObject.NULL),
            )
        }
        val ruleList = JSONArray()
        rules.forEach { ruleList.put(JSONObject().put("merchant", it.merchant).put("category", it.category)) }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("transactions", list)
            .put("rules", ruleList)
            .put("accounts", JSONArray().also { arr ->
                accounts.forEach { a ->
                    arr.put(
                        JSONObject().put("key", a.key).put("source", a.source).put("sourceType", a.sourceType)
                            .put("last4", a.last4 ?: JSONObject.NULL).put("isCard", a.isCard)
                            .put("creditCard", a.creditCard ?: JSONObject.NULL).put("type", a.type ?: JSONObject.NULL)
                            .put("name", a.name ?: JSONObject.NULL).put("parentKey", a.parentKey ?: JSONObject.NULL)
                            .put("confirmed", a.confirmed).put("firstSeen", a.firstSeen)
                            .put("lastBalance", a.lastBalance ?: JSONObject.NULL),
                    )
                }
            })
            .toString()
    }

    fun read(json: String): Contents = try {
        readChecked(json)
    } catch (e: org.json.JSONException) {
        throw Invalid(Problem.DAMAGED)
    }

    private fun readChecked(json: String): Contents {
        val root = try { JSONObject(json) } catch (e: Exception) { throw Invalid(Problem.DAMAGED) }
        if (root.optString("app") != APP) throw Invalid(Problem.NOT_OURS)
        val format = root.optInt("format", -1)
        if (format > FORMAT) throw Invalid(Problem.TOO_NEW)
        if (format < 1) throw Invalid(Problem.DAMAGED)

        val list = root.optJSONArray("transactions") ?: throw Invalid(Problem.DAMAGED)
        if (list.length() > MAX_ENTRIES) throw Invalid(Problem.TOO_LARGE)
        val transactions = (0 until list.length()).map { i ->
            val o = list.optJSONObject(i) ?: throw Invalid(Problem.DAMAGED)
            val direction = o.getString("direction")
            val amount = o.getDouble("amount")
            val fee = o.optDouble("fee", 0.0)
            if (direction != Transaction.IN && direction != Transaction.OUT) throw Invalid(Problem.DAMAGED)
            if (!amount.isFinite() || amount < 0 || amount > 1e12 || !fee.isFinite() || fee < 0) throw Invalid(Problem.DAMAGED)
            Transaction(
                smsId = if (o.isNull("smsId")) null else o.getLong("smsId"),
                source = o.getString("source").clip(100),
                sourceType = o.getString("sourceType").let {
                    if (it in setOf(Transaction.TYPE_BANK, Transaction.TYPE_WALLET, Transaction.TYPE_CASH)) it else Transaction.TYPE_CASH
                },
                direction = direction,
                amount = amount,
                fee = fee,
                kind = o.getString("kind").clip(50),
                counterparty = o.text("counterparty", 200),
                title = o.text("title", 500),
                note = o.text("note", 2000),
                timestamp = o.getLong("timestamp"),
                rawMessage = o.text("rawMessage", 2000),
                ignored = o.optBoolean("ignored"),
                category = o.text("category", 100),
                transfer = o.optBoolean("transfer"),
                deleted = o.optBoolean("deleted"),
                edited = o.optBoolean("edited"),
                hidden = o.optBoolean("hidden"),
                accountKey = o.text("accountKey", 200),
                currency = o.text("currency", 8),
                foreignAmount = o.number("foreignAmount"),
                balance = o.number("balance"),
                otherBank = o.text("otherBank", 100),
                review = o.text("review", 20)?.takeIf { it == Transaction.REVIEW_AMOUNT },
            )
        }
        val ruleList = root.optJSONArray("rules") ?: JSONArray()
        val rules = (0 until minOf(ruleList.length(), MAX_ENTRIES)).mapNotNull { i ->
            val o = ruleList.optJSONObject(i) ?: return@mapNotNull null
            CategoryRule(o.getString("merchant").clip(200), o.getString("category").clip(100))
        }
        val accountList = root.optJSONArray("accounts") ?: JSONArray()
        val accounts = (0 until minOf(accountList.length(), 1000)).mapNotNull { i ->
            val o = accountList.optJSONObject(i) ?: return@mapNotNull null
            Account(
                key = o.getString("key").clip(200), source = o.getString("source").clip(100),
                sourceType = o.getString("sourceType").clip(30), last4 = o.text("last4", 4), isCard = o.optBoolean("isCard"),
                creditCard = if (o.isNull("creditCard")) null else o.optBoolean("creditCard"),
                type = o.text("type", 30)?.takeIf { it in Account.TYPES }, name = o.text("name", 60),
                parentKey = o.text("parentKey", 200), confirmed = o.optBoolean("confirmed"),
                firstSeen = o.optLong("firstSeen"), lastBalance = o.number("lastBalance"),
            )
        }
        return Contents(transactions, rules, accounts)
    }

    private fun String.clip(max: Int) = if (length > max) substring(0, max) else this
    private fun JSONObject.text(name: String, max: Int): String? = if (isNull(name)) null else optString(name).clip(max)
    private fun JSONObject.number(name: String): Double? =
        if (isNull(name)) null else optDouble(name).takeIf { it.isFinite() && kotlin.math.abs(it) < 1e13 }
}
