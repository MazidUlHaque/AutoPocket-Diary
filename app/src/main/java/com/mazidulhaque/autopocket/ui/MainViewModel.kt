package com.mazidulhaque.autopocket.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mazidulhaque.autopocket.data.Account
import com.mazidulhaque.autopocket.data.AppDatabase
import com.mazidulhaque.autopocket.data.BackupFile
import com.mazidulhaque.autopocket.data.Budget
import com.mazidulhaque.autopocket.data.CategoryRule
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.data.counted
import com.mazidulhaque.autopocket.data.isIncoming
import com.mazidulhaque.autopocket.data.isManual
import com.mazidulhaque.autopocket.data.isSavings
import com.mazidulhaque.autopocket.data.total
import com.mazidulhaque.autopocket.logic.BackupCrypto
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.logic.Csv
import com.mazidulhaque.autopocket.logic.Period
import com.mazidulhaque.autopocket.logic.PeriodType
import com.mazidulhaque.autopocket.sms.SmsImporter
import com.mazidulhaque.autopocket.widget.MoneyWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** One account (with any debit cards inside it), a credit card, a wallet, or "Cash". */
data class AccountSummary(
    val key: String,
    val account: Account?,
    val source: String,
    val sourceType: String,
    val moneyIn: Double,
    val moneyOut: Double,
    /** Credit cards: bill payments received in this period. */
    val billPaid: Double,
    val cards: List<Account>,
)

data class CategorySummary(val category: String, val amount: Double, val count: Int)

data class BudgetStatus(val category: String, val budget: Double, val spent: Double) {
    val ratio get() = if (budget > 0) spent / budget else 0.0
    val warning get() = ratio >= 0.8
    val over get() = ratio >= 1.0
}

data class PeriodSummary(
    val transactions: List<Transaction> = emptyList(),
    val incoming: Double = 0.0,
    val outgoing: Double = 0.0,
    val salary: Double = 0.0,
    /** DPS, insurance and investments: part of outgoing, but still the user's money. */
    val savings: Double = 0.0,
    val transfers: Double = 0.0,
    val accounts: List<AccountSummary> = emptyList(),
    val spending: List<CategorySummary> = emptyList(),
    val incomingByCategory: List<CategorySummary> = emptyList(),
) {
    val net get() = incoming - outgoing
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.transactions()
    private val importer = SmsImporter(app, dao)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val period = MutableStateFlow(Period.containing(PeriodType.MONTH, LocalDate.now()))
    val importing = MutableStateFlow(false)
    val lastImportCount = MutableStateFlow<Int?>(null)

    /** Whether the hidden zone is open. Never saved: it locks again whenever the app is left. */
    val hiddenUnlocked = MutableStateFlow(false)

    val accounts: StateFlow<List<Account>> =
        dao.accounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Accounts and cards the user hasn't described yet ("New account found"). */
    val newAccounts: StateFlow<List<Account>> = accounts.map { list -> list.filter { !it.confirmed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val needsReview: StateFlow<List<Transaction>> =
        dao.needsReview().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<PeriodSummary> = summaryFlow(hidden = false)

    /** The hidden zone's own totals. Only loaded while it is unlocked. */
    val hiddenSummary: StateFlow<PeriodSummary> = hiddenUnlocked
        .flatMapLatest { open -> if (open) summaryFlow(hidden = true) else flowOf(PeriodSummary()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodSummary())

    private fun range(p: Period): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return p.start.atStartOfDay(zone).toInstant().toEpochMilli() to p.end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun summaryFlow(hidden: Boolean): StateFlow<PeriodSummary> = period
        .flatMapLatest { p -> range(p).let { (from, to) -> dao.inRange(from, to, hidden) } }
        .combine(dao.accounts()) { list, accts -> summarize(list, accts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodSummary())

    fun lockHidden() { hiddenUnlocked.value = false }
    fun unlockHidden() { hiddenUnlocked.value = true }

    fun setHidden(t: Transaction, hidden: Boolean) = viewModelScope.launch { dao.setHidden(t.id, hidden) }

    /** After a forgotten PIN with no phone lock: erase everything in the hidden zone. */
    fun eraseHiddenZone() = viewModelScope.launch {
        dao.deleteHiddenManual()
        dao.deleteHiddenSms()
    }

    val budgets: StateFlow<List<Budget>> =
        dao.budgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This calendar month's spending against each budget (whatever period is on screen). */
    val budgetStatus: StateFlow<List<BudgetStatus>> = dao.budgets()
        .flatMapLatest { budgets ->
            val (from, to) = range(Period.containing(PeriodType.MONTH, LocalDate.now()))
            dao.inRange(from, to, false).map { list ->
                val spent = summarize(list, emptyList()).spending.associate { it.category to it.amount }
                budgets.map { BudgetStatus(it.category, it.amount, spent[it.category] ?: 0.0) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBudget(category: String, amount: Double?) = viewModelScope.launch {
        if (amount == null || amount <= 0) dao.deleteBudget(category) else dao.saveBudget(Budget(category, amount))
    }

    init {
        // Keep the home-screen widget up to date while the app is open.
        viewModelScope.launch { dao.changes().collect { MoneyWidget.refresh(getApplication()) } }
        viewModelScope.launch { runCatching { importer.maintenance() } }
    }

    val sources: StateFlow<List<String>> =
        dao.sources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousPeriod() { period.value = period.value.previous() }
    fun nextPeriod() { period.value = period.value.next() }

    /** Switch to a period type, keeping roughly the same dates in view. */
    fun setPeriodType(type: PeriodType) {
        val today = LocalDate.now()
        val current = period.value
        val anchor = if (today >= current.start && today < current.end) today else current.end.minusDays(1)
        period.value = Period.containing(type, anchor)
    }

    fun setCustomPeriod(from: LocalDate, to: LocalDate) { period.value = Period.custom(from, to) }

    // ----- bringing SMS in -----

    fun importSms() = runImport { importer.importInbox() }

    /** Transactions found in shared or pasted SMS text, for the user to check before adding. */
    suspend fun previewSms(text: String, account: String?): List<Transaction> = importer.preview(text, account)

    fun addFromPreview(items: List<Transaction>) = runImport { importer.savePreview(items) }

    /** Imports an "SMS Backup & Restore" XML file. Returns how many transactions were new. */
    suspend fun importSmsFile(uri: Uri): Int = importer.importBackupXml(uri)

    /** The folder "SMS Backup & Restore" saves to, read every time the app opens (standard edition). */
    val autoImportFolder = MutableStateFlow(prefs.getString(PREF_FOLDER, null))

    fun setAutoImportFolder(tree: Uri?) {
        val resolver = getApplication<Application>().contentResolver
        autoImportFolder.value?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        if (tree != null) resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(PREF_FOLDER, tree?.toString()).putLong(PREF_FOLDER_SEEN, 0).apply()
        autoImportFolder.value = tree?.toString()
        if (tree != null) importFromFolder()
    }

    fun importFromFolder() {
        val tree = autoImportFolder.value?.let(Uri::parse) ?: return
        runImport {
            val (added, seen) = importer.importFromFolder(tree, prefs.getLong(PREF_FOLDER_SEEN, 0))
            prefs.edit().putLong(PREF_FOLDER_SEEN, seen).apply()
            added
        }
    }

    private fun runImport(task: suspend () -> Int) {
        if (importing.value) return
        viewModelScope.launch {
            importing.value = true
            try {
                lastImportCount.value = task()
            } catch (e: Exception) {
                lastImportCount.value = 0
            } finally {
                importing.value = false
            }
        }
    }

    // ----- accounts -----

    /** Saves what the user said an account is. Debit cards can be placed inside an account. */
    fun describeAccount(account: Account, type: String?, name: String?, parentKey: String?) = viewModelScope.launch {
        dao.updateAccount(
            account.copy(
                type = type, name = name?.trim()?.ifEmpty { null },
                parentKey = parentKey?.takeIf { type == Account.DEBIT_CARD }, confirmed = true,
            ),
        )
    }

    /** "Ask me later": stop asking about this account for now. */
    fun skipAccount(account: Account) = viewModelScope.launch { dao.updateAccount(account.copy(confirmed = true)) }

    // ----- review -----

    /** The user's answer for a possible own transfer (both halves). */
    fun answerTransfer(t: Transaction, ownTransfer: Boolean) = viewModelScope.launch {
        dao.settleTransfer(listOfNotNull(t.id, t.pairId), ownTransfer)
    }

    /** The taka amount for a foreign-currency purchase. */
    fun setTakaAmount(t: Transaction, amount: Double) = viewModelScope.launch {
        dao.update(t.copy(amount = amount, review = null, edited = true))
    }

    // ----- editing -----

    /**
     * Saves an entry. With [rememberCategory], the category is also used for every other entry
     * from the same merchant or person, now and in future SMS.
     */
    fun save(t: Transaction, rememberCategory: Boolean) = viewModelScope.launch {
        val item = t.copy(edited = true, review = if (t.review == Transaction.REVIEW_AMOUNT && t.amount > 0) null else t.review)
        if (item.id == 0L) dao.insert(item) else dao.update(item)
        val key = Categories.merchantKey(item.counterparty)
        val category = item.category
        if (rememberCategory && key != null && category != null) {
            dao.saveRule(CategoryRule(key, category))
            val same = dao.allOnce().filter {
                it.id != item.id && it.category != category && Categories.merchantKey(it.counterparty) == key
            }
            if (same.isNotEmpty()) dao.updateAll(same.map { it.copy(category = category) })
        }
    }

    /** Manual entries are removed. SMS entries are kept hidden, so re-scanning doesn't bring them back. */
    fun delete(t: Transaction) = viewModelScope.launch {
        // The text is wiped; only the SMS id is kept so the entry isn't imported again.
        if (t.isManual) dao.deleteForever(t.id)
        else dao.update(t.copy(deleted = true, rawMessage = null, title = null, note = null, counterparty = null, review = null))
    }

    // ----- backup & export -----

    /** Writes an encrypted backup file. Returns how many entries it holds. */
    suspend fun makeBackup(uri: Uri, password: String, includeHidden: Boolean): Int = withContext(Dispatchers.IO) {
        val items = dao.everything().filter { includeHidden || !it.hidden }
        val json = BackupFile.write(items, dao.rules(), dao.accountsOnce())
        val bytes = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password.toCharArray())
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Could not save the file.")
        items.count { !it.deleted }
    }

    /** Checks a backup file and its password. Nothing in the app changes yet. */
    suspend fun readBackup(uri: Uri, password: String): BackupFile.Contents = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > BackupCrypto.MAX_FILE_BYTES) throw BackupCrypto.NotABackup()
            }
            out.toByteArray()
        } ?: error("Could not open the file.")
        BackupFile.read(String(BackupCrypto.decrypt(bytes, password.toCharArray()), Charsets.UTF_8))
    }

    /** Replaces everything in the app with the backup's contents. */
    suspend fun restore(contents: BackupFile.Contents) = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.deleteAllTransactions()
            dao.deleteAllRules()
            // Inbox SMS ids (positive) only mean something on the phone that made the backup, so they are
            // stored negated and can't clash with this phone's SMS. Ids of shared/imported SMS (already
            // negative, made from the SMS text) are kept, so sharing the same SMS again is still skipped.
            dao.insertMany(contents.transactions.map { t -> t.copy(id = 0, smsId = t.smsId?.let { if (it > 0) -it else it }, pairId = null) })
            dao.saveRules(contents.rules)
            dao.addAccounts(contents.accounts)
            contents.accounts.forEach { dao.updateAccount(it) }
        }
    }

    /** Writes a spreadsheet (CSV) of the current period, or everything. Hidden-zone entries are never included. */
    suspend fun exportCsv(uri: Uri, everything: Boolean): Int = withContext(Dispatchers.IO) {
        val (from, to) = range(period.value)
        val zone = ZoneId.systemDefault()
        val accountsByKey = dao.accountsOnce().associateBy { it.key }
        val items = dao.everything()
            .filter { !it.deleted && !it.hidden && (everything || it.timestamp in from until to) }
            .sortedBy { it.timestamp }
        val dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val rows = items.map { t ->
            val at = java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone)
            val acct = t.accountKey?.let { accountsByKey[it] }
            listOf(
                at.format(dateFmt), at.format(timeFmt), t.source,
                listOfNotNull(acct?.name, acct?.last4?.let { "••$it" }).joinToString(" "),
                if (t.isIncoming) "In" else "Out",
                Csv.number(t.amount), Csv.number(t.fee), t.currency?.let { "${t.foreignAmount} $it" } ?: "",
                t.category ?: "", t.title ?: "", t.kind, t.counterparty ?: "", t.note ?: "",
                if (t.transfer) "Yes" else "No",
                if (t.counted) "Yes" else "No",
            )
        }
        val csv = Csv.build(
            listOf("Date", "Time", "Bank / wallet", "Account", "In/Out", "Amount (BDT)", "Fee", "Foreign amount", "Category",
                "Title", "Type", "To/From", "Note", "Own transfer", "Counted in totals"),
            rows,
        )
        resolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: error("Could not save the file.")
        items.size
    }

    private val resolver get() = getApplication<Application>().contentResolver

    private fun summarize(list: List<Transaction>, accts: List<Account>): PeriodSummary {
        val byKey = accts.associateBy { it.key }
        // Debit cards are shown inside the account they take money from.
        fun groupKey(t: Transaction): String {
            val k = t.accountKey ?: t.source
            return byKey[k]?.parentKey ?: k
        }
        val counted = list.filter { it.counted }
        val accounts = list.filter { !it.ignored }.groupBy(::groupKey).map { (key, items) ->
            val countedHere = items.filter { it.counted }
            AccountSummary(
                key = key,
                account = byKey[key],
                source = items.first().source,
                sourceType = items.map { it.sourceType }.firstOrNull { it != Transaction.TYPE_CASH } ?: Transaction.TYPE_CASH,
                moneyIn = countedHere.filter { it.isIncoming }.sumOf { it.amount },
                moneyOut = countedHere.filter { !it.isIncoming }.sumOf { it.total },
                billPaid = items.filter { it.isIncoming && it.kind == "Card Bill Payment" }.sumOf { it.amount },
                cards = accts.filter { it.parentKey == key },
            )
        }.sortedByDescending { it.moneyIn + it.moneyOut + it.billPaid }

        fun byCategory(items: List<Transaction>, fallback: String, value: (Transaction) -> Double) =
            items.groupBy { it.category ?: fallback }
                .map { (c, xs) -> CategorySummary(c, xs.sumOf(value), xs.size) }
                .sortedByDescending { it.amount }

        val ins = counted.filter { it.isIncoming }
        val outs = counted.filter { !it.isIncoming }
        return PeriodSummary(
            transactions = list,
            incoming = ins.sumOf { it.amount },
            outgoing = outs.sumOf { it.total },
            salary = ins.filter { it.category == Categories.SALARY }.sumOf { it.amount },
            savings = outs.filter { it.isSavings }.sumOf { it.total },
            // Each transfer has an out side and an in side (when both SMS exist); count it once.
            transfers = list.filter { it.transfer && !it.ignored }.partition { it.isIncoming }
                .let { (i, o) -> maxOf(i.sumOf { it.amount }, o.sumOf { it.amount }) },
            accounts = accounts,
            spending = byCategory(outs, Categories.OTHER) { it.total },
            incomingByCategory = byCategory(ins, Categories.OTHER_INCOMING) { it.amount },
        )
    }

    companion object {
        private const val PREF_FOLDER = "auto_import_folder"
        private const val PREF_FOLDER_SEEN = "auto_import_seen"
    }
}
