@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.BuildConfig
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Account
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.logic.PeriodType

@Composable
fun HomeScreen(
    vm: MainViewModel,
    hasSmsPermission: Boolean,
    unofficialCopy: Boolean,
    onFixPermission: () -> Unit,
    onOpenAddSms: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onEdit: (Transaction) -> Unit,
) {
    val period by vm.period.collectAsState()
    val summary by vm.summary.collectAsState()
    val importing by vm.importing.collectAsState()
    val review by vm.needsReview.collectAsState()
    val budgetStatus by vm.budgetStatus.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val newAccounts by vm.newAccounts.collectAsState()
    var periodSheet by remember { mutableStateOf(false) }
    var pickingRange by remember { mutableStateOf(false) }
    var askedLater by rememberSaveable { mutableStateOf(false) }
    val byKey = accounts.associateBy { it.key }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted)
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { periodSheet = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(periodLabel(period), style = MaterialTheme.typography.headlineSmall)
                        Icon(Glyphs.Down2, stringResource(R.string.choose_period), Modifier.size(22.dp))
                    }
                }
                when {
                    importing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    BuildConfig.AUTO_SMS && hasSmsPermission -> SquareButton(Glyphs.Repeat, stringResource(R.string.scan_sms_again)) { vm.importSms() }
                    else -> SquareButton(Glyphs.Sms, stringResource(R.string.add_from_sms), onOpenAddSms)
                }
            }
        }
        if (unofficialCopy) item { IntegrityWarning() }
        if (BuildConfig.AUTO_SMS && !hasSmsPermission) {
            item {
                NoticeCard(Glyphs.Key, stringResource(R.string.sms_off_title), stringResource(R.string.sms_off_text), warn = true, onClick = onFixPermission)
            }
        }
        if (!BuildConfig.AUTO_SMS && summary.transactions.isEmpty()) {
            item {
                AppCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.add_sms_card_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.add_sms_card_text))
                    Button(onClick = onOpenAddSms) { Text(stringResource(R.string.add_from_sms)) }
                }
            }
        }
        if (review.isNotEmpty()) {
            item {
                NoticeCard(
                    Glyphs.Alert,
                    pluralText(review.size, R.string.review_one, R.string.review_many),
                    stringResource(R.string.review_sub),
                    warn = true, onClick = onOpenReview,
                )
            }
        }
        budgetStatus.filter { it.warning }.takeIf { it.isNotEmpty() }?.let { alerts ->
            item { BudgetAlertsCard(alerts, onOpenBudgets) }
        }
        item { HeroCard(summary, stringResource(R.string.net_this_period)) }

        val shownAccounts = summary.accounts.filter { it.moneyIn + it.moneyOut + it.billPaid > 0 }
        if (shownAccounts.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.your_accounts), stringResource(R.string.see_all), onOpenAccounts) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shownAccounts, key = { it.key }) { a -> AccountTile(a, onOpenAccounts) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.recent), stringResource(R.string.see_all), onOpenTransactions) }
        if (summary.transactions.isEmpty()) {
            item {
                Text(stringResource(R.string.nothing_in_this_period_yet_tap), color = AppTheme.colors.muted, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            item {
                Column {
                    DayGroupedList(summary.transactions.take(8), { t -> accountShortLabel(t, byKey) }, onEdit)
                }
            }
        }
    }

    if (periodSheet) {
        ModalBottomSheet(onDismissRequest = { periodSheet = false }) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                PeriodPicker(
                    period,
                    onType = { if (it == PeriodType.CUSTOM) pickingRange = true else vm.setPeriodType(it) },
                    onPrevious = vm::previousPeriod,
                    onNext = vm::nextPeriod,
                    onPickRange = { pickingRange = true },
                )
            }
        }
    }
    if (pickingRange) {
        RangePickerDialog(period, onDismiss = { pickingRange = false }) { from, to ->
            vm.setCustomPeriod(from, to)
            pickingRange = false
            periodSheet = false
        }
    }
    if (!askedLater) {
        newAccounts.firstOrNull()?.let { acct ->
            AccountSheet(
                account = acct,
                accounts = accounts,
                position = accounts.count { it.confirmed && it.type != Account.WALLET } + 1,
                total = accounts.count { it.type != Account.WALLET },
                onSave = { type, name, parent -> vm.describeAccount(acct, type, name, parent) },
                onLater = { askedLater = true },
            )
        }
    }
}

@Composable
internal fun SquareButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border), modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, label, Modifier.padding(13.dp))
    }
}

@Composable
internal fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, warn: Boolean, onClick: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(
        onClick = onClick, padding = 14.dp,
        color = if (warn) c.warnBackground else c.card, border = if (warn) c.warnBorder else c.border,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (warn) c.warnText else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (warn) c.warnText else MaterialTheme.colorScheme.onSurface)
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (warn) c.warnText else c.muted)
            }
            Icon(Glyphs.Chevron, null, Modifier.size(18.dp), tint = if (warn) c.warnText else c.muted)
        }
    }
}

@Composable
private fun AccountTile(a: AccountSummary, onClick: () -> Unit) {
    val c = AppTheme.colors
    val credit = a.account?.type == Account.CREDIT_CARD || (a.account?.type == null && a.account?.creditCard == true)
    AppCard(modifier = Modifier.widthIn(min = 160.dp, max = 200.dp), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BankBadge(a.source, a.account?.isCard == true)
            Column {
                Text(accountTitle(a.account, a.source), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(accountSubtitle(a.account, a.source), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
        }
        if (credit) {
            Text(stringResource(R.string.spent_n, money(a.moneyOut)), color = c.moneyOut, style = MaterialTheme.typography.labelLarge)
            if (a.billPaid > 0) Text(stringResource(R.string.bill_paid_n, money(a.billPaid)), color = c.moneyIn, style = MaterialTheme.typography.labelLarge)
        } else {
            Text("+" + money(a.moneyIn), color = c.moneyIn, style = MaterialTheme.typography.labelLarge)
            Text("−" + money(a.moneyOut), color = c.moneyOut, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BudgetAlertsCard(alerts: List<BudgetStatus>, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val anyOver = alerts.any { it.over }
    AppCard(onClick = onOpen, color = if (anyOver) c.dangerBackground else c.warnBackground, border = if (anyOver) c.dangerBackground else c.warnBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Alert, null, Modifier.size(20.dp), tint = if (anyOver) c.dangerText else c.warnText)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.budget_alerts_this_month), style = MaterialTheme.typography.titleMedium)
        }
        alerts.sortedByDescending { it.ratio }.forEach { a ->
            Text(
                stringResource(R.string.budget_alert_line, categoryLabel(a.category), money(a.spent), money(a.budget), Math.round(a.ratio * 100).toInt()) +
                    if (a.over) stringResource(R.string.over_budget) else stringResource(R.string.almost_used_up),
                color = if (a.over) c.dangerText else c.warnText,
            )
        }
    }
}

/** Short account name for an entry, e.g. "DBBL Visa ••1111". */
@Composable
internal fun accountShortLabel(t: Transaction, byKey: Map<String, Account>): String? {
    val a = t.accountKey?.let { byKey[it] } ?: return null
    return listOfNotNull(a.name ?: accountTypeName(a.type) ?: shortBank(a.source), a.last4?.let { "••$it" }).joinToString(" ")
}

@Composable
internal fun BankBadge(source: String, card: Boolean) {
    val tint = Color(bankColor(source))
    Surface(shape = RoundedCornerShape(9.dp), color = tint, modifier = Modifier.size(width = if (card) 40.dp else 32.dp, height = 30.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(shortBank(source).take(5), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

internal fun shortBank(source: String): String = when (source) {
    "Dutch-Bangla Bank" -> "DBBL"
    "IFIC Bank" -> "IFIC"
    "BRAC Bank" -> "BRAC"
    "City Bank" -> "City"
    "Eastern Bank" -> "EBL"
    "Islami Bank" -> "IBBL"
    "Standard Chartered" -> "SCB"
    "Mutual Trust Bank" -> "MTB"
    "Southeast Bank" -> "SEBL"
    "Cash" -> "৳"
    else -> source.split(' ').firstOrNull()?.take(5) ?: source
}

internal fun bankColor(source: String): Long = when (source) {
    "Dutch-Bangla Bank", "Rocket" -> 0xFF1B6B45
    "IFIC Bank" -> 0xFF9A3412
    "bKash" -> 0xFFC2185B
    "Nagad" -> 0xFFD84315
    "BRAC Bank" -> 0xFF1E40AF
    "City Bank" -> 0xFFB91C1C
    "Cash" -> 0xFF3F4F47
    else -> 0xFF37474F
}
