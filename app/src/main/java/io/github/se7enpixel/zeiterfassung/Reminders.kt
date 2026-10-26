package io.github.se7enpixel.zeiterfassung

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Plant die Erinnerungen zum Arbeitsbeginn und zum Feierabend. */
object Reminders {
    const val KIND_START = "start"
    const val KIND_END = "end"
    const val NOTIFICATION_ID = 1
    private const val CHANNEL = "erinnerungen"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Erinnerungen zum Ein- und Ausstempeln"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(ctx: Context, kind: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            if (kind == KIND_START) 1 else 2,
            Intent(ctx, AlarmReceiver::class.java).putExtra("kind", kind),
            FLAGS,
        )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val data = Store.data
        for (kind in listOf(KIND_START, KIND_END)) {
            val pi = alarmIntent(ctx, kind)
            am.cancel(pi)
            val enabled = if (kind == KIND_START) data.remindStart else data.remindEnd
            if (!enabled) continue
            val next = nextTime(data, kind) ?: continue
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
    }

    /** Nächster geplanter Arbeitsbeginn bzw. Feierabend nach [now]. */
    fun nextTime(data: AppData, kind: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        for (i in 0..7) {
            val date = now.toLocalDate().plusDays(i.toLong())
            val p = data.planFor(date)
            if (!p.enabled || p.start == null || p.end == null) continue
            var at = date.atTime(if (kind == KIND_START) p.start else p.end)
            if (kind == KIND_END && p.end < p.start) at = at.plusDays(1)
            if (at.isAfter(now)) return at
        }
        return null
    }

    fun notify(ctx: Context, kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val openApp = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS,
        )
        val isStart = kind == KIND_START
        val action = PendingIntent.getBroadcast(
            ctx,
            if (isStart) 11 else 12,
            Intent(ctx, ActionReceiver::class.java).setAction(if (isStart) ActionReceiver.CLOCK_IN else ActionReceiver.CLOCK_OUT),
            FLAGS,
        )
        val notification = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isStart) "Arbeitsbeginn" else "Feierabend")
            .setContentText(if (isStart) "Möchtest du mit der Arbeit beginnen?" else "Möchtest du ausstempeln?")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null as Icon?, if (isStart) "Einstempeln" else "Ausstempeln", action).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val data = Store.data
        val todayEntry = data.entries[LocalDate.now()]
        when (intent.getStringExtra("kind")) {
            // Nur fragen, wenn heute noch nichts eingetragen ist (also auch nicht bei Urlaub usw.).
            Reminders.KIND_START -> if (data.openEntry == null && todayEntry == null) {
                Reminders.notify(context, Reminders.KIND_START)
            }
            Reminders.KIND_END -> if (data.openEntry != null) {
                Reminders.notify(context, Reminders.KIND_END)
            }
        }
        Reminders.schedule(context)
    }
}

/** Knöpfe „Einstempeln“ und „Ausstempeln“ direkt in der Benachrichtigung. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val CLOCK_IN = "io.github.se7enpixel.zeiterfassung.CLOCK_IN"
        const val CLOCK_OUT = "io.github.se7enpixel.zeiterfassung.CLOCK_OUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        when (intent.action) {
            CLOCK_IN -> Store.clockIn(context)
            CLOCK_OUT -> Store.clockOut(context)
        }
        context.getSystemService(NotificationManager::class.java).cancel(Reminders.NOTIFICATION_ID)
    }
}

/** Nach einem Neustart oder Update sind geplante Erinnerungen weg und werden neu gesetzt. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Reminders.schedule(context)
    }
}
