package com.financeos.hub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.financeos.hub.MainActivity
import com.financeos.hub.R
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.TransactionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class BalanceWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun accountDao(): AccountDao
        fun transactionDao(): TransactionDao
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        // goAsync keeps the broadcast alive while we read the DB off the main thread.
        val pending = goAsync()
        val handler = CoroutineExceptionHandler { _, t ->
            android.util.Log.e("BalanceWidget", "widget update failed", t)
            // pending.finish() is NOT called here — the finally block always runs it,
            // so calling it again here would be a double-finish.
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler).launch {
            try {
                val balanceKopecks = ep.accountDao().sumAllBalances()
                val todayExpenses  = ep.transactionDao().getTodayExpenses(todayStartMillis())
                ids.forEach { id -> updateWidget(context, manager, id, balanceKopecks, todayExpenses) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun updateWidget(
        context : Context,
        manager : AppWidgetManager,
        id      : Int,
        balance : Long,
        expenses: Long,
    ) {
        val fmt = NumberFormat.getNumberInstance(Locale("ru")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val views = RemoteViews(context.packageName, R.layout.widget_balance)
        views.setTextViewText(R.id.widget_balance, "${fmt.format(balance / 100.0)} ₽")
        views.setTextViewText(R.id.widget_expenses, "−${fmt.format(abs(expenses) / 100.0)} ₽")
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        manager.updateAppWidget(id, views)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
