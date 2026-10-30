@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mazidulhaque.autopocket.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.data.isIncoming
import com.mazidulhaque.autopocket.data.isManual
import com.mazidulhaque.autopocket.logic.Categories
import com.mazidulhaque.autopocket.logic.Period
import com.mazidulhaque.autopocket.logic.PeriodType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ----- formatting -----

private val amountFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private val inputFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
internal val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

/** "৳1,23,456.5" — South Asian grouping (lakh, crore), as on bank SMS. */
internal fun money(v: Double): String {
    val whole = kotlin.math.floor(kotlin.math.abs(v)).toLong()
    val fraction = amountFormat.format(kotlin.math.abs(v)).substringAfter('.', "")
    val s = whole.toString()
    val grouped = if (s.length <= 3) s else {
        val head = s.dropLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
    }
    return "৳" + grouped + (if (fraction.isNotEmpty() && fraction != s) ".$fraction" else "")
}

internal fun signed(v: Double, incoming: Boolean) = (if (incoming) "+" else "−") + money(v)

internal fun Long.toLocal() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

@Composable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

// ----- icons (thin strokes, drawn here so the app needs no icon library) -----

private fun strokeIcon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(path), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
).build()

internal object Glyphs {
    val Food = strokeIcon("food", "M7 3v8M5 3v4a2 2 0 0 0 4 0V3M7 11v10M16 3c-2 1-3 4-3 7h3v11")
    val Cart = strokeIcon("cart", "M3 4h2l2 11h11l2-8H6M9 20h.01M17 20h.01")
    val Bag = strokeIcon("bag", "M5 8h14l-1 12H6zM9 8V6a3 3 0 0 1 6 0v2")
    val Bolt = strokeIcon("bolt", "M13 2L4 14h7l-1 8 9-12h-7z")
    val Flame = strokeIcon("flame", "M12 3c3 4 6 6 6 11a6 6 0 0 1-12 0c0-3 2-5 3-7 1 2 2 3 3 3 0-3-1-5 0-7z")
    val Drop = strokeIcon("drop", "M12 3c4 5 6 8 6 11a6 6 0 0 1-12 0c0-3 2-6 6-11z")
    val Wifi = strokeIcon("wifi", "M2 9a15 15 0 0 1 20 0M5 13a10 10 0 0 1 14 0M8.5 16.5a5 5 0 0 1 7 0M12 20h.01")
    val Phone = strokeIcon("phone", "M8 3h8v18H8zM11 18h2")
    val Car = strokeIcon("car", "M5 16l1-6h12l1 6M4 16h16v3H4zM7 19v2M17 19v2")
    val Health = strokeIcon("health", "M12 5v14M5 12h14")
    val Cap = strokeIcon("cap", "M3 9l9-5 9 5-9 5zM7 11v5c3 2 7 2 10 0v-5")
    val Play = strokeIcon("play", "M8 5l11 7-11 7z")
    val Repeat = strokeIcon("repeat", "M4 12a8 8 0 0 1 14-5l2 2M20 12a8 8 0 0 1-14 5l-2-2M20 5v4h-4M4 19v-4h4")
    val Home = strokeIcon("home", "M4 11l8-7 8 7v9H4z")
    val Bill = strokeIcon("bill", "M6 3h9l3 3v15H6zM9 9h6M9 13h6M9 17h4")
    val Cash = strokeIcon("cash", "M3 7h18v10H3zM9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0")
    val Send = strokeIcon("send", "M4 12l16-8-6 16-3-7z")
    val Percent = strokeIcon("percent", "M19 5L5 19M7 7h.01M17 17h.01")
    val Shield = strokeIcon("shield", "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z")
    val Bars = strokeIcon("bars", "M4 20h16M6 16v-6M10 16V8M14 16v-5M18 16V6")
    val Trend = strokeIcon("trend", "M3 17l6-6 4 4 8-8M15 7h6v6")
    val Briefcase = strokeIcon("briefcase", "M4 8h16v11H4zM9 8V5h6v3")
    val Gift = strokeIcon("gift", "M4 10h16v10H4zM3 7h18v3H3zM12 7v13M12 7c-2-4-6-3-5 0M12 7c2-4 6-3 5 0")
    val Up = strokeIcon("up", "M12 19V5M5 12l7-7 7 7")
    val Down = strokeIcon("down", "M12 5v14M5 12l7 7 7-7")
    val Swap = strokeIcon("swap", "M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7")
    val Dot = strokeIcon("dot", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    val Card = strokeIcon("card", "M3 6h18v13H3zM3 10h18")
    val Chart = strokeIcon("chart", "M5 20V10M12 20V4M19 20v-7")
    val More = strokeIcon("more", "M5 12h.01M12 12h.01M19 12h.01")
    val Sms = strokeIcon("sms", "M4 5h16v11H8l-4 4zM8 9h8M8 12h5")
    val Alert = strokeIcon("alert", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 7v6M12 16.5v.5")
    val Chevron = strokeIcon("chevron", "M9 6l6 6-6 6")
    val Down2 = strokeIcon("chevron-down", "M6 9l6 6 6-6")
    val Check = strokeIcon("check", "M5 12l5 5L20 7")
    val Gear = strokeIcon("gear", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Heart = strokeIcon("heart", "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z")
    val Info = strokeIcon("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 11v6M12 7.5v.5")
    val Folder = strokeIcon("folder", "M3 6h6l2 2h10v11H3z")
    val Lock = strokeIcon("lock", "M6 11h12v10H6zM8 11V8a4 4 0 0 1 8 0v3")
    val Key = strokeIcon("key", "M8 14a4 4 0 1 1 3-6.5L21 7v3h-2v2h-2v2h-3.5A4 4 0 0 1 8 14z")
}

/** Icon and tint for a category (falls back to a neutral dot). */
internal fun categoryGlyph(category: String?, transfer: Boolean): Pair<ImageVector, Long> = when {
    transfer -> Glyphs.Swap to 0xFF3F6BA8
    else -> when (category) {
        "Food & Dining" -> Glyphs.Food to 0xFFB4561D
        "Groceries" -> Glyphs.Cart to 0xFF2E7D32
        "Clothing & Shoes", "Shopping" -> Glyphs.Bag to 0xFF7A3E93
        "Electricity" -> Glyphs.Bolt to 0xFFB8860B
        "Gas" -> Glyphs.Flame to 0xFFC2410C
        "Water" -> Glyphs.Drop to 0xFF1D6FA5
        "Internet" -> Glyphs.Wifi to 0xFF1D6FA5
        "Mobile Recharge" -> Glyphs.Phone to 0xFF2A5DA8
        "Transport" -> Glyphs.Car to 0xFF00796B
        "Health" -> Glyphs.Health to 0xFFC62828
        "Education" -> Glyphs.Cap to 0xFF3949AB
        "Entertainment" -> Glyphs.Play to 0xFFAD1457
        "Subscriptions" -> Glyphs.Repeat to 0xFF5E35B1
        "Rent" -> Glyphs.Home to 0xFF6D4C41
        "Bills" -> Glyphs.Bill to 0xFF546E7A
        "Cash Withdrawal" -> Glyphs.Cash to 0xFF2E7D32
        Categories.SEND_MONEY -> Glyphs.Send to 0xFF3F4F47
        Categories.CHARGES -> Glyphs.Percent to 0xFF6D4C41
        Categories.INSURANCE -> Glyphs.Shield to 0xFF8A5A12
        Categories.DPS -> Glyphs.Bars to 0xFF8A5A12
        Categories.INVESTMENTS -> Glyphs.Trend to 0xFF8A5A12
        Categories.SALARY -> Glyphs.Briefcase to 0xFF1E7A4E
        Categories.BONUS -> Glyphs.Gift to 0xFF1E7A4E
        Categories.INTEREST -> Glyphs.Percent to 0xFF1E7A4E
        "Received Money", "Cash In", "Refund & Cashback", Categories.OTHER_INCOMING -> Glyphs.Up to 0xFF1E7A4E
        else -> Glyphs.Dot to 0xFF5B6B63
    }
}

@Composable
internal fun GlyphBadge(icon: ImageVector, tint: Color, size: Dp = 38.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.48f), tint = tint) }
}

// ----- surfaces -----

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = AppTheme.colors.card,
    border: Color = AppTheme.colors.border,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(shape).clickable(onClick = onClick) else it },
        shape = shape, color = color, border = BorderStroke(1.dp, border),
    ) { Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The big summary card: net, incoming (with salary), outgoing (with savings), own transfers. */
@Composable
internal fun HeroCard(s: PeriodSummary, title: String) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = c.hero, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = c.heroMuted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    (if (s.net >= 0) "+" else "−") + money(kotlin.math.abs(s.net)),
                    color = Color.White, style = MaterialTheme.typography.displaySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroTile(
                    Modifier.weight(1f), Glyphs.Up, Color(0xFF7FE0B0), stringResource(R.string.incoming), money(s.incoming),
                    if (s.salary > 0) stringResource(R.string.salary_n, money(s.salary)) else null, c.heroMuted,
                )
                HeroTile(
                    Modifier.weight(1f), Glyphs.Down, Color(0xFFFFB4A6), stringResource(R.string.outgoing), money(s.outgoing),
                    if (s.savings > 0) stringResource(R.string.savings_included_n, money(s.savings)) else null, c.savings,
                )
            }
            if (s.transfers > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.heroTile))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.own_transfers_not_counted), color = c.heroMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(money(s.transfers), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HeroTile(modifier: Modifier, icon: ImageVector, iconTint: Color, label: String, value: String, sub: String?, subColor: Color) {
    val c = AppTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.heroTile).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = iconTint)
            Text(label, color = c.heroMuted, style = MaterialTheme.typography.labelMedium)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        if (sub != null) Text(sub, color = subColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ----- entries -----

@Composable
internal fun TransactionRow(t: Transaction, accountLabel: String? = null, onClick: () -> Unit) {
    val (icon, tint) = categoryGlyph(t.category, t.transfer)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphBadge(icon, Color(tint))
        Column(Modifier.weight(1f)) {
            Text(displayTitle(t), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (t.transfer) stringResource(R.string.own_transfer) else t.category?.let { categoryLabel(it) },
                    accountLabel ?: accountName(t.source),
                    t.timestamp.toLocal().format(timeFormat.withLocale(locale())),
                    if (t.ignored) stringResource(R.string.not_counted) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (t.currency != null && t.amount == 0.0) "${t.currency} ${amountFormat.format(t.foreignAmount ?: 0.0)}"
                else signed(t.amount, t.isIncoming),
                color = when {
                    t.ignored || t.transfer -> AppTheme.colors.muted
                    t.isIncoming -> AppTheme.colors.moneyIn
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Bold,
            )
            if (t.review != null) {
                Text(stringResource(R.string.check_this), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warnText)
            }
        }
    }
}

/** Entries grouped under day headings ("Today", "Yesterday", "Mon, 28 Oct"). */
@Composable
internal fun DayGroupedList(items: List<Transaction>, accountLabel: @Composable (Transaction) -> String?, onClick: (Transaction) -> Unit) {
    val today = LocalDate.now()
    items.groupBy { it.timestamp.toLocal().toLocalDate() }.forEach { (day, list) ->
        Text(
            when (day) {
                today -> stringResource(R.string.today)
                today.minusDays(1) -> stringResource(R.string.yesterday)
                else -> day.format(dayFormat.withLocale(locale()))
            },
            style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        AppCard(padding = 10.dp) {
            Column {
                list.forEach { t -> TransactionRow(t, accountLabel(t)) { onClick(t) } }
            }
        }
    }
}

// ----- period -----

@Composable
internal fun PeriodPicker(
    period: Period,
    onType: (PeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickRange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PeriodType.entries.forEach { t ->
                FilterChip(selected = period.type == t, onClick = { onType(t) }, label = { Text(periodTypeLabel(t)) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_period)) }
            Text(
                periodLabel(period),
                Modifier.weight(1f).clickable(enabled = period.type == PeriodType.CUSTOM, onClick = onPickRange),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (period.type == PeriodType.CUSTOM) {
                IconButton(onClick = onPickRange) { Icon(Icons.Default.DateRange, stringResource(R.string.choose_dates)) }
            }
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_period)) }
        }
    }
}

@Composable
internal fun RangePickerDialog(period: Period, onDismiss: () -> Unit, onPicked: (LocalDate, LocalDate) -> Unit) {
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = period.start.utcMillis(),
        initialSelectedEndDateMillis = period.end.minusDays(1).utcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis!!.utcDate()
                    onPicked(start, state.selectedEndDateMillis?.utcDate() ?: start)
                },
            ) { Text(stringResource(R.string.show)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text(stringResource(R.string.choose_dates), Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}

// ----- editing -----

@Composable
internal fun TransactionDialog(
    initial: Transaction?,
    knownSources: List<String>,
    onDismiss: () -> Unit,
    onSave: (Transaction, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    hideLabel: String? = null,
    onToggleHidden: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.let { inputFormat.format(it) } ?: "") }
    var incoming by remember { mutableStateOf(initial?.isIncoming ?: false) }
    var source by remember { mutableStateOf(initial?.source ?: "Cash") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var ignored by remember { mutableStateOf(initial?.ignored ?: false) }
    var transfer by remember { mutableStateOf(initial?.transfer ?: false) }
    var timestamp by remember { mutableLongStateOf(initial?.timestamp ?: System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val merchant = initial?.counterparty?.takeIf { Categories.merchantKey(it) != null }
    var rememberCategory by remember { mutableStateOf(true) }
    val amountValue = amount.replace(",", "").toDoubleOrNull()
    val isManual = initial == null || initial.isManual
    val categoryChoices = if (incoming) Categories.incoming else Categories.expense

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.add_transaction) else stringResource(R.string.edit_transaction)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.title_eg_shoes)) },
                    placeholder = { initial?.let { Text(displayTitle(it)) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial?.currency != null) {
                    Text(
                        stringResource(R.string.foreign_amount_n, "${initial.currency} ${amountFormat.format(initial.foreignAmount ?: 0.0)}"),
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.warnText,
                    )
                }
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    singleLine = true,
                    isError = amount.isNotEmpty() && amountValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !incoming, onClick = { incoming = false }, label = { Text(stringResource(R.string.outgoing)) })
                    FilterChip(selected = incoming, onClick = { incoming = true }, label = { Text(stringResource(R.string.incoming)) })
                }

                Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryChoices.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryLabel(c)) })
                    }
                }
                OutlinedTextField(
                    category, { category = it },
                    label = { Text(stringResource(R.string.or_type_your_own_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (merchant != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberCategory, { rememberCategory = it })
                        Text(stringResource(R.string.use_category_for, merchant), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (isManual) {
                    OutlinedTextField(
                        source, { source = it },
                        label = { Text(stringResource(R.string.account)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("Cash") + knownSources).distinct().forEach { s ->
                            FilterChip(selected = source == s, onClick = { source = s }, label = { Text(accountName(s)) })
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val d = timestamp.toLocal().toLocalDate()
                    DatePickerDialog(context, { _, y, m, day ->
                        val time = timestamp.toLocal().toLocalTime()
                        timestamp = LocalDate.of(y, m + 1, day).atTime(time)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }) { Text(stringResource(R.string.date_n, timestamp.toLocal().format(dateFormat.withLocale(locale())))) }
                OutlinedTextField(
                    note, { note = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(transfer, { transfer = it })
                    Text(stringResource(R.string.transfer_between_my_own_accounts_not), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ignored, { ignored = it })
                    Text(stringResource(R.string.dont_count_in_totals), style = MaterialTheme.typography.bodySmall)
                }
                initial?.rawMessage?.let {
                    Text(stringResource(R.string.original_sms), style = MaterialTheme.typography.labelMedium)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                }
                if (onToggleHidden != null && hideLabel != null) {
                    OutlinedButton(onClick = onToggleHidden) {
                        Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(hideLabel)
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_entry), color = AppTheme.colors.moneyOut) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountValue != null && amountValue > 0 && source.isNotBlank(),
                onClick = {
                    val base = initial ?: Transaction(
                        source = "", sourceType = Transaction.TYPE_CASH, direction = Transaction.OUT,
                        amount = 0.0, kind = "Manual", timestamp = timestamp,
                    )
                    val fallback = if (incoming) Categories.OTHER_INCOMING else Categories.OTHER
                    onSave(
                        base.copy(
                            title = title.trim().ifEmpty { null },
                            amount = amountValue ?: 0.0,
                            direction = if (incoming) Transaction.IN else Transaction.OUT,
                            source = if (isManual) source.trim() else base.source,
                            sourceType = if (isManual) Transaction.TYPE_CASH else base.sourceType,
                            category = category.trim().ifEmpty { fallback },
                            note = note.trim().ifEmpty { null },
                            ignored = ignored,
                            transfer = transfer,
                            timestamp = timestamp,
                            kind = if (isManual) (if (incoming) "Income" else "Expense") else base.kind,
                        ),
                        merchant != null && rememberCategory,
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_this_entry)) },
            text = {
                Text(
                    if (isManual) stringResource(R.string.it_will_be_removed_from_the)
                    else stringResource(R.string.it_will_be_removed_from_the_2),
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = AppTheme.colors.moneyOut) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else c.card)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else c.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
