@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.logic.PeriodType

/** Every entry in the chosen period, optionally only one category. */
@Composable
fun TransactionsScreen(vm: MainViewModel, category: String?, onClearCategory: () -> Unit, onBack: () -> Unit, onEdit: (Transaction) -> Unit) {
    val period by vm.period.collectAsState()
    val s by vm.summary.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var pickingRange by remember { mutableStateOf(false) }
    val byKey = accounts.associateBy { it.key }
    val shown = s.transactions.filter { t ->
        category == null || when (category) {
            Categories.OTHER_INCOMING -> t.isIncomingOther()
            else -> t.category == category
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.ground,
        topBar = {
            TopAppBar(
                title = { Text(category?.let { categoryLabel(it) } ?: stringResource(R.string.transactions_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { if (category != null) TextButton(onClick = onClearCategory) { Text(stringResource(R.string.show_all)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.ground),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                PeriodPicker(
                    period,
                    onType = { if (it == PeriodType.CUSTOM) pickingRange = true else vm.setPeriodType(it) },
                    onPrevious = vm::previousPeriod, onNext = vm::nextPeriod, onPickRange = { pickingRange = true },
                )
            }
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.nothing_in_this_period_yet_tap), color = AppTheme.colors.muted, modifier = Modifier.padding(vertical = 16.dp)) }
            } else {
                item { Column { DayGroupedList(shown, { t -> accountShortLabel(t, byKey) }, onEdit) } }
            }
        }
    }
    if (pickingRange) {
        RangePickerDialog(period, onDismiss = { pickingRange = false }) { from, to ->
            vm.setCustomPeriod(from, to)
            pickingRange = false
        }
    }
}

private val fixedIncoming = setOf(Categories.SALARY, Categories.BONUS, Categories.INTEREST)

private fun Transaction.isIncomingOther() =
    direction == Transaction.IN && !transfer && !ignored && category !in fixedIncoming
