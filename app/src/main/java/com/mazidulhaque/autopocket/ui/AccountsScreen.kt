@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Account
import com.mazidulhaque.autopocket.logic.AccountChooser

@Composable
fun AccountsScreen(vm: MainViewModel, onOwnNumbers: () -> Unit) {
    val summary by vm.summary.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val own by vm.ownNumbers.collectAsState()
    val period by vm.period.collectAsState()
    val reassigning by vm.reassigning.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    val c = AppTheme.colors
    val byKey = accounts.associateBy { it.key }
    fun mine(a: Account) = MainViewModel.isMine(a, own)

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(periodLabel(period), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text(stringResource(R.string.accounts_and_cards), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            AppCard(onClick = onOwnNumbers, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphBadge(Glyphs.Key, MaterialTheme.colorScheme.primary, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.my_numbers), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (own.isEmpty()) stringResource(R.string.my_numbers_sub_all) else stringResource(R.string.my_numbers_sub_n, own.size),
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                    Icon(Glyphs.Chevron, null, Modifier.size(18.dp), tint = c.muted)
                }
            }
        }
        if (reassigning) item { Sorting() }

        val active = summary.accounts.associateBy { it.key }
        // Every account in use (not debit cards, which sit inside theirs), plus Cash / bank-only entries.
        val keys = (accounts.filter { it.parentKey == null && mine(it) && it.status == null }.map { it.key } +
            summary.accounts.map { it.key }.filter { k -> byKey[k]?.let { mine(it) && it.status == null } ?: true }).distinct()
        val groups = keys.groupBy { k -> byKey[k]?.source ?: active[k]?.source ?: k }
        groups.forEach { (bank, bankKeys) ->
            item { Text(bank.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            items(bankKeys, key = { it }) { key ->
                val acct = byKey[key]
                AccountCard(acct, active[key], bank, cards = accounts.filter { it.parentKey == key }) { if (acct != null) editing = acct }
            }
        }

        val dormant = accounts.filter { it.status == Account.DORMANT && mine(it) && it.parentKey == null }
        if (dormant.isNotEmpty()) {
            item { Text(stringResource(R.string.dormant_accounts), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            items(dormant, key = { "d" + it.key }) { a ->
                AccountCard(a, active[a.key], a.source, cards = accounts.filter { it.parentKey == a.key }) { editing = a }
            }
        }

        val notYours = accounts.filter { !mine(it) }
        if (notYours.isNotEmpty()) {
            item { Text(stringResource(R.string.not_your_accounts), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            items(notYours, key = { "n" + it.key }) { a -> NotYoursRow(a, enabled = !reassigning) { vm.setAccountStatus(a, null) } }
        }
        item {
            Text(stringResource(R.string.accounts_footer), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }

    editing?.let { acct ->
        AccountSheet(
            account = acct, accounts = accounts, position = null, total = null,
            onSave = { type, name, parent -> vm.describeAccount(acct, type, name, parent); editing = null },
            onLater = { editing = null },
            onStatus = { vm.setAccountStatus(acct, it); editing = null },
        )
    }
}

@Composable
private fun Sorting() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.sorting_entries), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

/** An account that isn't the user's, with a way to undo. */
@Composable
private fun NotYoursRow(a: Account, enabled: Boolean, onMine: () -> Unit) {
    val c = AppTheme.colors
    AppCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BankBadge(a.source, a.isCard)
            Column(Modifier.weight(1f)) {
                Text("${a.source} ••${a.last4 ?: "?"}", style = MaterialTheme.typography.titleSmall, color = c.muted)
                Text(
                    stringResource(
                        when (a.status) {
                            Account.NOT_MINE -> R.string.not_yours_other_side
                            Account.SOMEONE_ELSES -> R.string.not_yours_someone
                            else -> R.string.not_yours_not_listed
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            TextButton(onClick = onMine, enabled = enabled) { Text(stringResource(R.string.its_mine)) }
        }
    }
}

@Composable
private fun AccountCard(acct: Account?, s: AccountSummary?, source: String, cards: List<Account>, onClick: () -> Unit) {
    val c = AppTheme.colors
    val credit = acct?.type == Account.CREDIT_CARD || (acct?.type == null && acct?.creditCard == true)
    val debitCardLabel = stringResource(R.string.acct_debit_card)
    AppCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BankBadge(source, acct?.isCard == true)
            Column(Modifier.weight(1f)) {
                Text(accountTitle(acct, source), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        accountSubtitle(acct, source),
                        cards.takeIf { it.isNotEmpty() }?.joinToString { "$debitCardLabel ••${it.last4}" },
                        stringResource(R.string.dormant).takeIf { acct?.status == Account.DORMANT },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            if (acct != null && !acct.confirmed) {
                Text(stringResource(R.string.set_up), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
                Icon(Glyphs.Chevron, null, Modifier.size(18.dp), tint = c.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (credit) {
                Stat(Modifier.weight(1f), stringResource(R.string.bill_paid), money(s?.billPaid ?: 0.0), c.inTile, c.moneyIn)
                Stat(Modifier.weight(1f), stringResource(R.string.spent), "−" + money(s?.moneyOut ?: 0.0), c.outTile, c.moneyOut)
            } else {
                Stat(Modifier.weight(1f), stringResource(R.string.incoming), "+" + money(s?.moneyIn ?: 0.0), c.inTile, c.moneyIn)
                Stat(Modifier.weight(1f), stringResource(R.string.outgoing), "−" + money(s?.moneyOut ?: 0.0), c.outTile, c.moneyOut)
            }
        }
        acct?.lastBalance?.let {
            Text(
                stringResource(if (credit) R.string.available_limit_n else R.string.balance_n, money(it)),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = bg) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
            Text(value, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1)
        }
    }
}

/**
 * "New account found" / edit an account: what it is, an optional name, and (for a debit card) which
 * account it takes money from. Also: dormant, or not the user's account at all.
 */
@Composable
fun AccountSheet(
    account: Account,
    accounts: List<Account>,
    position: Int?,
    total: Int?,
    onSave: (type: String?, name: String?, parentKey: String?) -> Unit,
    onLater: () -> Unit,
    onStatus: (String?) -> Unit,
    onOwnNumbers: (() -> Unit)? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val suggested = account.type ?: when {
        account.isCard && account.creditCard == true -> Account.CREDIT_CARD
        account.isCard -> Account.DEBIT_CARD
        else -> null
    }
    var type by remember(account.key) { mutableStateOf(suggested) }
    var name by remember(account.key) { mutableStateOf(account.name ?: "") }
    var askNotMine by remember(account.key) { mutableStateOf(false) }
    val parents = accounts.filter { it.source == account.source && !it.isCard && it.key != account.key && it.status == null }
    var parent by remember(account.key) { mutableStateOf(account.parentKey ?: parents.singleOrNull()?.key) }
    val choices = if (account.isCard) listOf(Account.CREDIT_CARD, Account.DEBIT_CARD, Account.OTHER)
    else Account.TYPES.filter { it != Account.CREDIT_CARD && it != Account.DEBIT_CARD && it != Account.WALLET }
    val numbered = account.type != Account.WALLET && account.last4 != null

    ModalBottomSheet(onDismissRequest = onLater, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BankBadge(account.source, account.isCard)
                Column {
                    if (!account.confirmed) {
                        Text(stringResource(R.string.new_account_found), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.ok)
                    }
                    Text("${account.source} ••${account.last4 ?: "?"}", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(stringResource(R.string.what_is_it), color = AppTheme.colors.muted)
            Text(stringResource(R.string.type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { t -> Pill(accountTypeName(t) ?: t, type == t) { type = t } }
            }
            if (suggested != null && account.type == null) {
                Text(
                    stringResource(if (suggested == Account.CREDIT_CARD) R.string.suggest_credit else R.string.suggest_debit),
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted,
                )
            }
            if (type == Account.LOAN) {
                Text(stringResource(R.string.loan_note), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
            OutlinedTextField(
                name, { name = it.take(40) },
                label = { Text(stringResource(R.string.account_name_optional)) },
                placeholder = { Text(stringResource(R.string.account_name_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (type == Account.DEBIT_CARD && parents.isNotEmpty()) {
                Text(stringResource(R.string.takes_money_from), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parents.forEach { p -> Pill("${p.name ?: accountTypeName(p.type) ?: p.source} ••${p.last4}", parent == p.key) { parent = p.key } }
                }
            }
            if (account.confirmed && account.type != Account.WALLET) {
                Text(stringResource(R.string.status_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.status_in_use), account.status == null) { if (account.status != null) onStatus(null) }
                    Pill(stringResource(R.string.status_dormant), account.status == Account.DORMANT) { if (account.status != Account.DORMANT) onStatus(Account.DORMANT) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(if (account.confirmed) R.string.cancel else R.string.ask_me_later))
                }
                Button(onClick = { onSave(type, name, parent) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.save)) }
            }
            if (numbered) {
                TextButton(onClick = { askNotMine = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.not_my_account), color = AppTheme.colors.moneyOut, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onOwnNumbers != null && total != null && total > 2) {
                TextButton(onClick = onOwnNumbers, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.many_not_yours))
                }
            }
            if (position != null && total != null && total > 1) {
                Text(
                    stringResource(R.string.n_of_m_accounts, position.coerceAtMost(total), total),
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    if (askNotMine) {
        NotMineDialog(
            "${account.source} ••${account.last4}",
            onDismiss = { askNotMine = false },
            onChoose = { askNotMine = false; onStatus(it) },
        )
    }
}

/** "Not your account?": the other side of the user's transfers, or an account that isn't theirs at all. */
@Composable
private fun NotMineDialog(label: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.not_mine_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.not_mine_text, label))
                ChoiceCard(stringResource(R.string.not_mine_other_side), stringResource(R.string.not_mine_other_side_sub)) { onChoose(Account.NOT_MINE) }
                ChoiceCard(stringResource(R.string.someone_elses), stringResource(R.string.someone_elses_sub)) { onChoose(Account.SOMEONE_ELSES) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceCard(title: String, sub: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppTheme.colors.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
        }
    }
}

/**
 * "My account numbers": the user lists the last digits of their own accounts and cards. When the list
 * isn't empty, only those count as theirs; other numbers in SMS are treated as the other side.
 */
@Composable
fun OwnNumbersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val saved by vm.ownNumbers.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val reassigning by vm.reassigning.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    var typed by remember { mutableStateOf("") }
    val c = AppTheme.colors
    val found = accounts.filter { it.type != Account.WALLET && it.last4 != null && it.status != Account.SOMEONE_ELSES }
        .sortedWith(compareBy({ it.source }, { it.isCard }, { it.last4 }))
    fun add(n: String) { AccountChooser.clean(n)?.let { d -> if (draft.none { AccountChooser.matches(d, it) }) draft = draft + d } }
    fun remove(n: String) { draft = draft.filterNot { AccountChooser.matches(n, it) } }

    Scaffold(
        containerColor = c.ground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_numbers)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.ground),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.my_numbers_intro), color = c.muted) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        typed, { typed = it.filter(Char::isDigit).take(20) }, singleLine = true,
                        label = { Text(stringResource(R.string.last_digits)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = AccountChooser.clean(typed) != null,
                        onClick = { add(typed); typed = "" },
                        modifier = Modifier.height(56.dp),
                    ) { Text(stringResource(R.string.add)) }
                }
            }
            item {
                AppCard(padding = 12.dp) {
                    if (draft.isEmpty()) {
                        Text(stringResource(R.string.my_numbers_empty), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            draft.forEach { n -> Pill("••$n  ✕", true) { remove(n) } }
                        }
                    }
                }
            }
            if (found.isNotEmpty()) {
                item { Text(stringResource(R.string.my_numbers_found), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
                items(found, key = { it.key }) { a ->
                    val ticked = draft.any { AccountChooser.matches(a.last4!!, it) }
                    Row(
                        Modifier.fillMaxWidth().clickable { if (ticked) remove(a.last4!!) else add(a.last4!!) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(checked = ticked, onCheckedChange = { if (ticked) remove(a.last4!!) else add(a.last4!!) })
                        BankBadge(a.source, a.isCard)
                        Column(Modifier.weight(1f)) {
                            Text("${a.source} ••${a.last4}", fontWeight = FontWeight.SemiBold)
                            Text(
                                a.name ?: accountTypeName(a.type) ?: stringResource(if (a.isCard) R.string.acct_card_unknown else R.string.acct_unknown),
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                        }
                    }
                }
            }
            if (reassigning) item { Sorting() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { draft = emptyList() }, enabled = draft.isNotEmpty() && !reassigning,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(stringResource(R.string.clear_list)) }
                    Button(
                        onClick = { vm.setOwnNumbers(draft) }, enabled = draft != saved && !reassigning,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
            item { Text(stringResource(R.string.my_numbers_note), style = MaterialTheme.typography.bodySmall, color = c.muted) }
        }
    }
}
