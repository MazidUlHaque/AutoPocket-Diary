@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mazidulhaque.autopocket.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import androidx.core.os.LocaleListCompat
import com.mazidulhaque.autopocket.Settings
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.widget.MoneyWidget
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
    )
}

@Composable
fun SettingsScreen(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var widgetAmounts by remember { mutableStateOf(settings.widgetShowsAmounts) }

    Scaffold(topBar = { BackBar(stringResource(R.string.settings), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to stringResource(R.string.same_as_phone), ThemeMode.LIGHT to stringResource(R.string.light), ThemeMode.DARK to stringResource(R.string.dark)).forEach { (mode, label) ->
                    FilterChip(selected = themeMode == mode, onClick = { onThemeMode(mode) }, label = { Text(label) })
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Language names are written in their own language, so anyone can find theirs.
                listOf("" to stringResource(R.string.same_as_phone), "en" to "English", "bn" to "বাংলা").forEach { (tag, label) ->
                    FilterChip(
                        selected = current == tag || (tag.isNotEmpty() && current.startsWith(tag)),
                        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.homescreen_widget), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.shows_this_months_money_in_out))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_amounts_on_the_widget))
                    Text(
                        stringResource(R.string.anyone_who_sees_your_home_screen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(widgetAmounts, {
                    widgetAmounts = it
                    settings.widgetShowsAmounts = it
                    scope.launch { MoneyWidget.refresh(context.applicationContext) }
                })
            }
        }
    }
}

private val plainMoney = DecimalFormat("#,##0")

@Composable
fun BudgetsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val budgets by vm.budgets.collectAsState()
    val status by vm.budgetStatus.collectAsState()
    val spent = status.associate { it.category to it.spent }
    val edits = remember { mutableStateMapOf<String, String>() }
    val categories = (Categories.expense + budgets.map { it.category }).distinct()

    Scaffold(topBar = { BackBar(stringResource(R.string.monthly_budgets), onBack) }) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.set_how_much_you_want_to),
                )
            }
            items(categories, key = { it }) { c ->
                val saved = budgets.firstOrNull { it.category == c }?.amount
                val text = edits[c] ?: saved?.let { plainMoney.format(it).replace(",", "") } ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c, fontWeight = FontWeight.Medium)
                        spent[c]?.takeIf { saved != null }?.let {
                            Text(
                                stringResource(R.string.spent_this_month_n, plainMoney.format(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        text, { v -> edits[c] = v.filter { it.isDigit() || it == '.' } },
                        label = { Text(stringResource(R.string.per_month)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = edits.isNotEmpty(),
                    onClick = {
                        edits.forEach { (c, v) -> vm.setBudget(c, v.toDoubleOrNull()) }
                        edits.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save_budgets)) }
            }
        }
    }
}
