package com.mazidulhaque.autopocket.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mazidulhaque.autopocket.MainActivity
import com.mazidulhaque.autopocket.R
import com.mazidulhaque.autopocket.Settings
import com.mazidulhaque.autopocket.data.AppDatabase
import com.mazidulhaque.autopocket.data.counted
import com.mazidulhaque.autopocket.data.isIncoming
import com.mazidulhaque.autopocket.data.total
import com.mazidulhaque.autopocket.logic.Period
import com.mazidulhaque.autopocket.logic.PeriodType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen widget: this month's money in, out and net. Hidden zone entries are never included. */
class MoneyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                update(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val format = DecimalFormat("#,##0")

        /** Redraws every AutoPocket widget on the home screen. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoneyWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val month = Period.containing(PeriodType.MONTH, LocalDate.now())
            val zone = ZoneId.systemDefault()
            val items = AppDatabase.get(context).transactions().inRangeOnce(
                month.start.atStartOfDay(zone).toInstant().toEpochMilli(),
                month.end.atStartOfDay(zone).toInstant().toEpochMilli(),
            ).filter { !it.hidden && it.counted }
            val moneyIn = items.filter { it.isIncoming }.sumOf { it.amount }
            val moneyOut = items.filter { !it.isIncoming }.sumOf { it.total }
            val net = moneyIn - moneyOut
            val show = Settings(context).widgetShowsAmounts
            fun money(v: Double) = if (show) "৳" + format.format(v) else "৳ •••"

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_money).apply {
                setTextViewText(R.id.widget_period, month.label(context.resources.configuration.locales[0]))
                setTextViewText(R.id.widget_in, money(moneyIn))
                setTextViewText(R.id.widget_out, money(moneyOut))
                setTextViewText(R.id.widget_net, (if (net >= 0) "+" else "−") + money(kotlin.math.abs(net)))
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
