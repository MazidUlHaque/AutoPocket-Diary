@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mazidulhaque.autopocket.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Account

@Composable
fun AccountsScreen(vm: MainViewModel) {
    val summary by vm.summary.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val period by vm.period.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    val c = AppTheme.colors

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
        val active = summary.accounts.associateBy { it.key }
        // Every known account (not debit cards, which sit inside theirs), plus Cash / unnumbered sources with entries.
        val keys = (accounts.filter { it.parentKey == null }.map { it.key } + summary.accounts.map { it.key }).distinct()
        val groups = keys.groupBy { k -> accounts.firstOrNull { it.key == k }?.source ?: active[k]?.source ?: k }
        groups.forEach { (bank, bankKeys) ->
            item { Text(bank.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
            items(bankKeys, key = { it }) { key ->
                val acct = accounts.firstOrNull { it.key == key }
                val s = active[key]
                AccountCard(acct, s, bank, cards = accounts.filter { it.parentKey == key }) { if (acct != null) editing = acct }
            }
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
        )
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
 * account it takes money from.
 */
@Composable
fun AccountSheet(
    account: Account,
    accounts: List<Account>,
    position: Int?,
    total: Int?,
    onSave: (type: String?, name: String?, parentKey: String?) -> Unit,
    onLater: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val suggested = account.type ?: when {
        account.isCard && account.creditCard == true -> Account.CREDIT_CARD
        account.isCard -> Account.DEBIT_CARD
        else -> null
    }
    var type by remember(account.key) { mutableStateOf(suggested) }
    var name by remember(account.key) { mutableStateOf(account.name ?: "") }
    val parents = accounts.filter { it.source == account.source && !it.isCard && it.key != account.key }
    var parent by remember(account.key) { mutableStateOf(account.parentKey ?: parents.singleOrNull()?.key) }
    val choices = if (account.isCard) listOf(Account.CREDIT_CARD, Account.DEBIT_CARD, Account.OTHER)
    else Account.TYPES.filter { it != Account.CREDIT_CARD && it != Account.DEBIT_CARD && it != Account.WALLET }

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.ask_me_later)) }
                Button(onClick = { onSave(type, name, parent) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.save)) }
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
}
