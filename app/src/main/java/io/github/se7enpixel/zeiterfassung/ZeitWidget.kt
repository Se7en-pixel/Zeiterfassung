package io.github.se7enpixel.zeiterfassung

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget für den Startbildschirm: Kommen/Gehen-Knopf und die laufende Arbeitszeit.
 * Schmal (1–2 Felder breit) nur Zeit und Knopf, breiter zusätzlich Status und Wochensaldo.
 */
class ZeitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Store.init(context)
        render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            Store.init(context)
            if (Store.data.openEntry != null) Store.clockOut(context) else Store.clockIn(context)
            context.getSystemService(NotificationManager::class.java).cancel(Reminders.NOTIFICATION_ID)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "io.github.se7enpixel.zeiterfassung.WIDGET_TOGGLE"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, ZeitWidget::class.java)).forEach { render(ctx, manager, it) }
        }

        private fun render(ctx: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 60)
            val wide = width >= 150
            val views = RemoteViews(ctx.packageName, if (wide) R.layout.widget_wide else R.layout.widget_small)

            val data = Store.data
            val today = LocalDate.now()
            val open = data.openEntry
            val todayEntry = data.entries[today]

            val openApp = PendingIntent.getActivity(
                ctx, 30,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                FLAGS,
            )
            val toggle = PendingIntent.getBroadcast(
                ctx, 31,
                Intent(ctx, ZeitWidget::class.java).setAction(ACTION_TOGGLE),
                FLAGS,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            if (open != null && open.start != null) {
                // Die Stoppuhr läuft im Widget selbst weiter, ohne dass die App aktualisieren muss.
                val startMillis = open.date.atTime(open.start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startMillis)
                views.setChronometer(R.id.widget_timer, base, null, true)
                views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                views.setViewVisibility(R.id.widget_today, View.GONE)
                views.setTextViewText(R.id.widget_status, "Eingestempelt seit ${open.start.fmt()} Uhr")
                views.setTextViewText(R.id.widget_button, "Gehen")
                views.setOnClickPendingIntent(R.id.widget_button, toggle)
            } else {
                views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_timer, View.GONE)
                views.setViewVisibility(R.id.widget_today, View.VISIBLE)
                if (todayEntry != null) {
                    // Heute schon abgeschlossen: Änderungen gehen in der App.
                    views.setTextViewText(R.id.widget_today, "${fmtDur(data.istMin(todayEntry))} h")
                    views.setTextViewText(
                        R.id.widget_status,
                        if (todayEntry.type == DayType.ARBEIT) "Heute gearbeitet" else todayEntry.type.label,
                    )
                    views.setTextViewText(R.id.widget_button, "Öffnen")
                    views.setOnClickPendingIntent(R.id.widget_button, openApp)
                } else {
                    views.setTextViewText(R.id.widget_today, "0:00 h")
                    views.setTextViewText(R.id.widget_status, "Nicht eingestempelt")
                    views.setTextViewText(R.id.widget_button, "Kommen")
                    views.setOnClickPendingIntent(R.id.widget_button, toggle)
                }
            }

            if (wide) {
                val week = data.week(mondayOf(today), today)
                views.setTextViewText(R.id.widget_week, "KW ${week.week}: ${fmtDiff(week.diffMin)} h")
                views.setViewVisibility(R.id.widget_week, if (height >= 90) View.VISIBLE else View.GONE)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
