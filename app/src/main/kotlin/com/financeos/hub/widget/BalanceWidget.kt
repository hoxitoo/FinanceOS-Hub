package com.financeos.hub.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.financeos.hub.R
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        CoroutineScope(Dispatchers.IO).launch {
            val balanceKopecks = ep.accountDao().sumAllBalances()
            val todayExpenses  = ep.transactionDao().getTodayExpenses(todayStartMillis())
            ids.forEach { id -> updateWidget(context, manager, id, balanceKopecks, todayExpenses) }
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
        manager.updateAppWidget(id, views)
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
