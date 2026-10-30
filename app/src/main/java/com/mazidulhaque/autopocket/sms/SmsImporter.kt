package com.mazidulhaque.autopocket.sms

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Telephony
import android.util.Xml
import com.mazidulhaque.autopocket.data.Account
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.data.TransactionDao
import com.mazidulhaque.autopocket.data.isIncoming
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.logic.Duplicates
import com.mazidulhaque.autopocket.logic.SmsDate
import com.mazidulhaque.autopocket.logic.TransferMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.security.MessageDigest
import java.time.ZoneId

/**
 * Turns SMS into saved transactions. SMS can come from the inbox (Auto edition only), from text the
 * user shares or pastes, or from an SMS backup file. Already-saved SMS are always skipped, so the
 * user's edits are kept.
 */
class SmsImporter(private val context: Context, private val dao: TransactionDao) {

    /** Reads the whole SMS inbox. Needs the READ_SMS permission (Auto edition). Returns how many were new. */
    suspend fun importInbox(): Int = withContext(Dispatchers.IO) {
        val reader = reader()
        val found = mutableListOf<Transaction>()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(addrCol) ?: continue
                val body = c.getString(bodyCol) ?: continue
                reader.read(sender, body, c.getLong(dateCol), c.getLong(idCol))?.let { found += it }
            }
        }
        save(found)
    }

    /**
     * Finds transactions in shared or pasted text (one or more SMS, separated by blank lines).
     * [account] helps when the SMS doesn't name its bank. Nothing is saved yet.
     */
    suspend fun preview(text: String, account: String?): List<Transaction> = withContext(Dispatchers.IO) {
        val reader = reader()
        val hint = account?.trim()?.takeIf { it.isNotEmpty() }
        text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { body ->
            val date = SmsDate.find(body)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
            reader.read(hint ?: "", body, date, stableId(body), fallbackSource = hint)
        }.distinctBy { it.smsId }
    }

    /** Run when the app opens: fixes entries saved by older versions and fills in missing categories. */
    suspend fun maintenance() = withContext(Dispatchers.IO) {
        repairOldEntries()
        fillMissingCategories()
    }

    /** Saves transactions from [preview]. Returns how many were new. */
    suspend fun savePreview(items: List<Transaction>): Int = withContext(Dispatchers.IO) { save(items) }

    /** Imports an SMS backup file in the common "SMS Backup & Restore" XML format. */
    suspend fun importBackupXml(uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { importXml(it) } ?: error("Could not open the file.")
    }

    /**
     * Reads the newest SMS backup file in a folder the user chose (where "SMS Backup & Restore" saves
     * its scheduled backups). Does nothing if that file was already read. Returns how many were new.
     */
    suspend fun importFromFolder(tree: Uri, lastModifiedSeen: Long): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var newest: Pair<String, Long>? = null
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val modified = c.getLong(2)
                if (name.startsWith("sms", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) &&
                    modified > (newest?.second ?: 0L)
                ) newest = c.getString(0) to modified
            }
        }
        val (id, modified) = newest ?: return@withContext 0 to lastModifiedSeen
        if (modified <= lastModifiedSeen) return@withContext 0 to lastModifiedSeen
        val file = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val added = context.contentResolver.openInputStream(file)?.use { importXml(it) } ?: 0
        added to modified
    }

    private suspend fun importXml(input: InputStream): Int {
        val reader = reader()
        val found = mutableListOf<Transaction>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "sms") continue
            if (parser.getAttributeValue(null, "type") != "1") continue
            val sender = parser.getAttributeValue(null, "address") ?: continue
            val body = parser.getAttributeValue(null, "body") ?: continue
            val date = parser.getAttributeValue(null, "date")?.toLongOrNull() ?: continue
            reader.read(sender, body, date, stableId("$sender|$date|$body"))?.let { found += it }
        }
        return save(found.sortedBy { it.timestamp })
    }

    private suspend fun reader() = Reader(
        rules = dao.rules().associate { it.merchant to it.category },
        known = dao.smsKeys().map { Triple(it.timestamp, it.amount, it.source) }.toHashSet(),
    )

    /** Parses SMS into transactions, skipping ones already in the app (by date + amount + account). */
    private class Reader(private val rules: Map<String, String>, private val known: Set<Triple<Long, Double, String>>) {
        fun read(sender: String, body: String, date: Long, smsId: Long, fallbackSource: String? = null): Transaction? {
            val p = SmsParser.parse(sender, body) ?: return null
            val t = toTransaction(p, body, date, smsId, rules, fallbackSource)
            return t.takeUnless { Triple(date, t.amount, t.source) in known }
        }
    }

    /** Saves new transactions, then links accounts, removes duplicate SMS and matches own transfers. */
    private suspend fun save(found: List<Transaction>): Int {
        if (found.isEmpty()) {
            afterChanges(emptyList())
            return 0
        }
        val from = found.minOf { it.timestamp } - Duplicates.WINDOW_MS
        val to = found.maxOf { it.timestamp } + Duplicates.WINDOW_MS + 1
        val existing = dao.inRangeOnce(from, to).filter { it.smsId != null }
        val fresh = mutableListOf<Transaction>()
        for (t in found.sortedBy { it.timestamp }) {
            val k = dupKey(t)
            val keptIndex = fresh.indexOfFirst { Duplicates.same(dupKey(it), k) }
            val keptOld = existing.firstOrNull { Duplicates.same(dupKey(it), k) }
            when {
                // A bank's second SMS for the same transaction. Keep the first, but don't lose a clue only
                // the second one has (IFIC's "deposited … from DUTCH-BANGLA BANK" names the sending bank).
                keptIndex >= 0 -> if (fresh[keptIndex].otherBank == null && t.otherBank != null) {
                    fresh[keptIndex] = fresh[keptIndex].copy(otherBank = t.otherBank)
                }
                keptOld != null -> if (keptOld.otherBank == null && t.otherBank != null) dao.setOtherBank(keptOld.id, t.otherBank)
                else -> fresh += t
            }
        }
        val added = if (fresh.isEmpty()) 0 else dao.insertAll(fresh).count { it != -1L }
        afterChanges(fresh)
        return added
    }

    private fun dupKey(t: Transaction) = Duplicates.Sms(
        t.source, t.accountKey, t.isIncoming, t.foreignAmount ?: t.amount, t.timestamp, t.balance, t.rawMessage ?: "",
    )

    private suspend fun afterChanges(fresh: List<Transaction>) {
        repairOldEntries()
        registerAccounts(fresh)
        if (fresh.isNotEmpty()) matchTransfers(fresh.minOf { it.timestamp }, fresh.maxOf { it.timestamp })
        fillMissingCategories()
    }

    /** Adds newly seen accounts and cards (the user is asked what they are), and keeps balances up to date. */
    private suspend fun registerAccounts(fresh: List<Transaction>) {
        val withKey = fresh.filter { it.accountKey?.contains('|') == true }
        if (withKey.isEmpty()) return
        dao.addAccounts(
            withKey.groupBy { it.accountKey!! }.map { (key, items) ->
                val first = items.minBy { it.timestamp }
                val parts = key.split('|')
                val wallet = parts.getOrNull(1) == "W"
                val isCard = parts.getOrNull(1) == "C"
                val credit = items.mapNotNull { creditFlag(it) }.firstOrNull()
                Account(
                    key = key, source = first.source, sourceType = first.sourceType,
                    last4 = parts.getOrNull(2)?.ifEmpty { null }, isCard = isCard, creditCard = credit,
                    type = if (wallet) Account.WALLET else null, name = null, parentKey = null,
                    confirmed = wallet, firstSeen = first.timestamp, lastBalance = null,
                )
            },
        )
        withKey.filter { it.balance != null }.groupBy { it.accountKey!! }.forEach { (key, items) ->
            dao.setAccountBalance(key, items.maxBy { it.timestamp }.balance!!)
        }
    }

    private fun creditFlag(t: Transaction): Boolean? = t.rawMessage?.let { SmsParser.parse(t.source, it)?.creditCard }

    /** Marks own transfers (sure matches) and asks about likely ones, around the given time range. */
    private suspend fun matchTransfers(fromTime: Long, toTime: Long) {
        val day = 24 * 60 * 60 * 1000L
        val items = dao.inRangeOnce(fromTime - day, toTime + day).filter { !it.hidden }
        val result = TransferMatcher.match(
            items.map { t ->
                TransferMatcher.Item(
                    id = t.id, source = t.source, account = t.accountKey ?: t.source, incoming = t.isIncoming,
                    amount = t.amount, timestamp = t.timestamp, kind = t.kind, transfer = t.transfer,
                    otherBank = t.otherBank, settled = t.edited || t.review != null || t.pairId != null,
                )
            },
        )
        if (result.transfers.isNotEmpty()) dao.markTransfers(result.transfers.toList())
        result.ask.forEach { (out, inc) ->
            dao.setReview(out, Transaction.REVIEW_TRANSFER, inc)
            dao.setReview(inc, Transaction.REVIEW_TRANSFER, out)
        }
    }

    /**
     * Entries saved by older versions of the app are read again with the current reader, which fixes
     * things like DBBL shown as "Rocket" or an insurance receipt counted as income. The user's own
     * titles, notes and (if changed) categories are kept.
     */
    private suspend fun repairOldEntries() {
        val old = dao.needingRepair()
        if (old.isEmpty()) return
        Categories.renamed.forEach { (from, to) -> dao.renameCategory(from, to) }
        val rules = dao.rules().associate { it.merchant to it.category }
        val fixed = old.map { t ->
            // The sender wasn't stored; the old account name works as one ("Rocket" was always DBBL's short code).
            val sender = if (t.source == "Rocket") "16216" else t.source
            val p = SmsParser.parse(sender, t.rawMessage!!)
            if (p == null) {
                // Now known not to be a transaction (receipt, notice, insurance info): remove unless the user edited it.
                if (t.edited) t.copy(accountKey = t.source) else t.copy(deleted = true, accountKey = t.source)
            } else {
                val fresh = toTransaction(p, t.rawMessage, t.timestamp, t.smsId!!, rules, null)
                fresh.copy(
                    id = t.id,
                    title = t.title, note = t.note, ignored = t.ignored, hidden = t.hidden, deleted = t.deleted,
                    edited = t.edited,
                    category = if (t.edited) t.category else fresh.category,
                    amount = if (t.edited) t.amount else fresh.amount,
                    direction = if (t.edited) t.direction else fresh.direction,
                    transfer = if (t.edited) t.transfer else fresh.transfer,
                )
            }
        }
        dao.updateAll(fixed)
        registerAccounts(fixed.filter { !it.deleted })
        matchTransfers(fixed.minOf { it.timestamp }, fixed.maxOf { it.timestamp })
    }

    /** Older entries without a category get one. */
    private suspend fun fillMissingCategories() {
        val missing = dao.uncategorized()
        if (missing.isEmpty()) return
        dao.updateAll(
            missing.map { t -> t.copy(category = Categories.guess(t.isIncoming, t.kind, t.counterparty, t.rawMessage)) },
        )
    }

    companion object {
        fun toTransaction(
            p: SmsParser.Parsed, body: String, date: Long, smsId: Long, rules: Map<String, String>, fallbackSource: String?,
        ): Transaction {
            val source = p.source.ifBlank { fallbackSource ?: "SMS" }
            val incoming = p.direction == SmsParser.Direction.IN
            val wallet = p.sourceType == SmsParser.SourceType.MOBILE_WALLET
            val foreign = p.currency != "BDT"
            return Transaction(
                smsId = smsId,
                source = source,
                sourceType = if (wallet) Transaction.TYPE_WALLET else Transaction.TYPE_BANK,
                direction = if (incoming) Transaction.IN else Transaction.OUT,
                amount = if (foreign) 0.0 else p.amount,
                fee = p.fee,
                kind = p.kind,
                counterparty = p.counterparty,
                timestamp = date,
                rawMessage = body,
                category = Categories.merchantKey(p.counterparty)?.let { rules[it] }
                    ?: Categories.guess(incoming, p.kind, p.counterparty, body),
                transfer = p.transfer,
                accountKey = if (wallet || p.accountLast4 != null) Account.key(source, p.accountLast4, p.isCard, wallet) else source,
                currency = if (foreign) p.currency else null,
                foreignAmount = if (foreign) p.amount else null,
                balance = p.balance,
                otherBank = p.otherBank,
                review = if (foreign) Transaction.REVIEW_AMOUNT else null,
            )
        }

        /**
         * An id for SMS that didn't come from this phone's inbox, made from the SMS itself, so the
         * same SMS shared twice gets the same id and is only saved once. Kept in its own negative
         * range so it never clashes with inbox ids (positive) or restored ones.
         */
        fun stableId(text: String): Long {
            val h = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray())
            var v = 0L
            for (i in 0 until 5) v = (v shl 8) or (h[i].toLong() and 0xFF)
            return -(1_000_000_000_000L + v)
        }
    }
}
