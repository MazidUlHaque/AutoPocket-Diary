@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.data.isIncoming

/** Only what the app can't be sure about: possible own transfers, and foreign-currency amounts. */
@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit) {
    val review by vm.needsReview.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val byKey = accounts.associateBy { it.key }
    // A transfer pair appears once, led by its outgoing half.
    val transferPairs = review.filter { it.review == Transaction.REVIEW_TRANSFER && !it.isIncoming }
        .mapNotNull { out -> review.firstOrNull { it.id == out.pairId }?.let { out to it } }
    val amounts = review.filter { it.review == Transaction.REVIEW_AMOUNT }

    Scaffold(
        containerColor = AppTheme.colors.ground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.needs_a_quick_look)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.ground),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.review_intro), color = AppTheme.colors.muted) }
            if (transferPairs.isEmpty() && amounts.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Glyphs.Check, null, Modifier.size(20.dp), tint = AppTheme.colors.ok)
                        Text(stringResource(R.string.all_sorted), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(transferPairs, key = { "t" + it.first.id }) { (out, inc) ->
                AppCard {
                    Text(stringResource(R.string.possible_own_transfer).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3F6BA8))
                    TransferSide(out, accountShortLabel(out, byKey))
                    TransferSide(inc, accountShortLabel(inc, byKey))
                    Text(stringResource(R.string.possible_own_transfer_q), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.answerTransfer(out, true) }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.yes_own_transfer)) }
                        OutlinedButton(onClick = { vm.answerTransfer(out, false) }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.no_separate)) }
                    }
                }
            }
            items(amounts, key = { "a" + it.id }) { t -> ForeignAmountCard(t, accountShortLabel(t, byKey)) { vm.setTakaAmount(t, it) } }
        }
    }
}

@Composable
private fun TransferSide(t: Transaction, account: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphBadge(if (t.isIncoming) Glyphs.Up else Glyphs.Down, if (t.isIncoming) AppTheme.colors.moneyIn else AppTheme.colors.moneyOut, 34.dp)
        Column(Modifier.weight(1f)) {
            Text(account ?: accountName(t.source), fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(displayTitle(t), t.timestamp.toLocal().format(dayFormat.withLocale(locale()))).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted,
            )
        }
        Text(signed(t.amount, t.isIncoming), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ForeignAmountCard(t: Transaction, account: String?, onSave: (Double) -> Unit) {
    var text by remember(t.id) { mutableStateOf("") }
    val value = text.replace(",", "").toDoubleOrNull()
    AppCard {
        Text(stringResource(R.string.foreign_currency).uppercase(), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.warnText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphBadge(Glyphs.Cash, AppTheme.colors.warnText, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(t.counterparty ?: displayTitle(t), fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(account, t.timestamp.toLocal().format(dayFormat.withLocale(locale()))).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted,
                )
            }
            Text("${t.currency} ${t.foreignAmount}", fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, singleLine = true,
                label = { Text(stringResource(R.string.how_much_taka)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(enabled = value != null && value > 0, onClick = { onSave(value!!) }, modifier = Modifier.height(56.dp)) { Text(stringResource(R.string.save)) }
        }
        Text(stringResource(R.string.foreign_tip), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
    }
}
