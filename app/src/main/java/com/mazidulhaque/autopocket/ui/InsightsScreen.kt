package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.logic.PeriodType

@Composable
fun InsightsScreen(vm: MainViewModel, onOpenCategory: (String) -> Unit, onOpenBudgets: () -> Unit) {
    val period by vm.period.collectAsState()
    val s by vm.summary.collectAsState()
    val budgetStatus by vm.budgetStatus.collectAsState()
    var pickingRange by remember { mutableStateOf(false) }
    val c = AppTheme.colors

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.insights), style = MaterialTheme.typography.headlineMedium) }
        item {
            PeriodPicker(
                period,
                onType = { if (it == PeriodType.CUSTOM) pickingRange = true else vm.setPeriodType(it) },
                onPrevious = vm::previousPeriod, onNext = vm::nextPeriod, onPickRange = { pickingRange = true },
            )
        }
        item {
            AppCard {
                Row {
                    Text(stringResource(R.string.incoming), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(money(s.incoming), style = MaterialTheme.typography.titleMedium, color = c.moneyIn)
                }
                val fixed = listOf(Categories.SALARY, Categories.BONUS, Categories.INTEREST)
                val byCat = s.incomingByCategory.associateBy { it.category }
                val rows = fixed.map { it to (byCat[it]?.amount ?: 0.0) } +
                    (Categories.OTHER_INCOMING to s.incomingByCategory.filter { it.category !in fixed }.sumOf { it.amount })
                val dots = listOf(0xFF0F5132, 0xFF3E8E66, 0xFF7FBF9C, 0xFFB9D4C6)
                rows.forEachIndexed { i, (cat, amount) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenCategory(cat) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(dots[i])))
                        Text(categoryLabel(cat), Modifier.weight(1f).padding(start = 10.dp))
                        Text(money(amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            val spending = s.spending.filter { it.category !in Categories.savings }
            val max = spending.maxOfOrNull { it.amount } ?: 0.0
            val budgets = if (period.type == PeriodType.MONTH) budgetStatus.associate { it.category to it.budget } else emptyMap()
            AppCard {
                Row {
                    Text(stringResource(R.string.where_money_went), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(money(s.outgoing - s.savings), style = MaterialTheme.typography.titleMedium)
                }
                if (spending.isEmpty()) Text(stringResource(R.string.nothing_spent), color = c.muted)
                spending.forEach { cat -> SpendingBar(cat, s.outgoing, max, budgets[cat.category]) { onOpenCategory(cat.category) } }
                TextButton(onClick = onOpenBudgets) { Text(stringResource(R.string.set_budgets)) }
            }
        }
        if (s.savings > 0) {
            item {
                AppCard(color = c.warnBackground, border = c.warnBorder) {
                    Row {
                        Text(stringResource(R.string.savings_and_investments), style = MaterialTheme.typography.titleMedium, color = c.warnText, modifier = Modifier.weight(1f))
                        Text(money(s.savings), style = MaterialTheme.typography.titleMedium, color = c.warnText)
                    }
                    s.spending.filter { it.category in Categories.savings }.forEach { cat ->
                        Row(Modifier.clickable { onOpenCategory(cat.category) }) {
                            Text(categoryLabel(cat.category), color = c.warnText, modifier = Modifier.weight(1f))
                            Text(money(cat.amount), color = c.warnText, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(stringResource(R.string.savings_note), style = MaterialTheme.typography.bodySmall, color = c.warnText)
                }
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

/** One category as a horizontal bar: length shows the amount, scaled to the largest category. */
@Composable
private fun SpendingBar(c: CategorySummary, total: Double, max: Double, budget: Double?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val used = budget?.takeIf { it > 0 }?.let { c.amount / it }
    val barColor = when {
        used == null -> MaterialTheme.colorScheme.primary
        used >= 1.0 -> colors.moneyOut
        used >= 0.8 -> colors.warnText
        else -> MaterialTheme.colorScheme.primary
    }
    val length = if (max > 0) (c.amount / max).toFloat().coerceIn(0.02f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(categoryLabel(c.category), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(money(c.amount), fontWeight = FontWeight.Bold)
            Text(
                "  ${if (total > 0) Math.round(c.amount / total * 100) else 0}%",
                style = MaterialTheme.typography.bodySmall, color = colors.muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.border)) {
            Box(Modifier.fillMaxWidth(length).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
        }
        if (used != null) {
            Text(
                stringResource(R.string.budget_used, money(budget!!), Math.round(used * 100).toInt()) + when {
                    used >= 1.0 -> stringResource(R.string.over_budget_2)
                    used >= 0.8 -> stringResource(R.string.almost_used_up_2)
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (used >= 0.8) barColor else colors.muted,
            )
        }
    }
}
