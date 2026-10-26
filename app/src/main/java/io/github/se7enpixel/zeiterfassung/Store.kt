package io.github.se7enpixel.zeiterfassung

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Hält alle Daten und speichert sie als JSON-Datei auf dem Handy. */
object Store {
    var data by mutableStateOf(AppData())
        private set
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "daten.json")

    fun init(ctx: Context) {
        if (loaded) return
        val f = file(ctx)
        if (f.exists()) {
            try {
                data = appDataFromJson(JSONObject(f.readText()))
            } catch (e: Exception) {
                // Defekte Datei aufheben statt sie beim nächsten Speichern zu überschreiben.
                f.renameTo(File(ctx.filesDir, "daten-defekt-${System.currentTimeMillis()}.json"))
            }
        }
        loaded = true
    }

    fun update(ctx: Context, change: (AppData) -> AppData) {
        init(ctx)
        data = change(data)
        val f = file(ctx)
        val tmp = File(ctx.filesDir, "daten.json.tmp")
        tmp.writeText(data.toJson().toString(2))
        tmp.renameTo(f)
        Reminders.schedule(ctx)
        ZeitWidget.updateAll(ctx)
    }

    private fun nowMinute(): LocalTime = LocalTime.now().withSecond(0).withNano(0)

    fun clockIn(ctx: Context) = update(ctx) { d ->
        val today = LocalDate.now()
        if (d.openEntry != null || d.entries[today]?.start != null) {
            d
        } else {
            d.copy(entries = d.entries + (today to Entry(today, DayType.ARBEIT, nowMinute())))
        }
    }

    /** Beendet den laufenden Tag. Ist noch keine Pause eingetragen, wird die geplante Pause vorgeschlagen. */
    fun clockOut(ctx: Context) = update(ctx) { d ->
        val open = d.openEntry ?: return@update d
        val pause = if (open.pauseMin > 0) open.pauseMin else d.planFor(open.date).pauseMin
        d.copy(entries = d.entries + (open.date to open.copy(end = nowMinute(), pauseMin = pause)))
    }

    fun save(ctx: Context, entry: Entry) = update(ctx) { it.copy(entries = it.entries + (entry.date to entry)) }

    fun delete(ctx: Context, date: LocalDate) = update(ctx) { it.copy(entries = it.entries - date) }

    fun setPlan(ctx: Context, day: DayOfWeek, change: (DayPlan) -> DayPlan) =
        update(ctx) { it.copy(plan = it.plan + (day to change(it.planFor(day)))) }
}
