package io.github.se7enpixel.zeiterfassung

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Art eines Tages. Bei [credited] zählt der Tag so, als hätte man die Sollzeit gearbeitet. */
enum class DayType(val label: String, val credited: Boolean) {
    ARBEIT("Arbeit", false),
    URLAUB("Urlaub", true),
    KRANK("Krank", true),
    FEIERTAG("Feiertag", true),
    ZEITAUSGLEICH("Überstundenabbau", false),
}

/** Ein Tag mit Beginn, Ende und Pause. Pro Datum gibt es höchstens einen Eintrag. */
data class Entry(
    val date: LocalDate,
    val type: DayType = DayType.ARBEIT,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val pauseMin: Int = 0,
) {
    val isOpen: Boolean get() = type == DayType.ARBEIT && start != null && end == null
}

/** Reguläre Arbeitszeit für einen Wochentag. */
data class DayPlan(
    val enabled: Boolean = false,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val pauseMin: Int = 0,
) {
    val sollMin: Int
        get() = if (enabled && start != null && end != null) maxOf(0, spanMinutes(start, end) - pauseMin) else 0
}

data class AppData(
    val entries: Map<LocalDate, Entry> = emptyMap(),
    val plan: Map<DayOfWeek, DayPlan> = emptyMap(),
    val remindStart: Boolean = true,
    val remindEnd: Boolean = true,
) {
    fun planFor(day: DayOfWeek): DayPlan = plan[day] ?: DayPlan()
    fun planFor(date: LocalDate): DayPlan = planFor(date.dayOfWeek)

    /** Der Tag, an dem gerade eingestempelt ist (falls vorhanden). */
    val openEntry: Entry? get() = entries.values.filter { it.isOpen }.maxByOrNull { it.date }
}

/** Minuten von [start] bis [end]; geht das Ende über Mitternacht, zählt es zum nächsten Tag. */
fun spanMinutes(start: LocalTime, end: LocalTime): Int {
    var m = Duration.between(start, end).toMinutes().toInt()
    if (m < 0) m += 24 * 60
    return m
}

private fun JSONObject.timeOrNull(key: String): LocalTime? =
    if (isNull(key)) null else LocalTime.parse(getString(key))

private fun LocalTime?.toJsonValue(): Any = this?.toString() ?: JSONObject.NULL

fun AppData.toJson(): JSONObject {
    val list = JSONArray()
    entries.values.sortedBy { it.date }.forEach { e ->
        list.put(JSONObject().apply {
            put("date", e.date.toString())
            put("type", e.type.name)
            put("start", e.start.toJsonValue())
            put("end", e.end.toJsonValue())
            put("pause", e.pauseMin)
        })
    }
    val planJson = JSONObject()
    plan.forEach { (day, p) ->
        planJson.put(day.name, JSONObject().apply {
            put("on", p.enabled)
            put("start", p.start.toJsonValue())
            put("end", p.end.toJsonValue())
            put("pause", p.pauseMin)
        })
    }
    return JSONObject().apply {
        put("version", 1)
        put("entries", list)
        put("plan", planJson)
        put("remindStart", remindStart)
        put("remindEnd", remindEnd)
    }
}

fun appDataFromJson(o: JSONObject): AppData {
    val entries = mutableMapOf<LocalDate, Entry>()
    val list = o.optJSONArray("entries") ?: JSONArray()
    for (i in 0 until list.length()) {
        val e = list.getJSONObject(i)
        val date = LocalDate.parse(e.getString("date"))
        entries[date] = Entry(
            date = date,
            type = DayType.valueOf(e.optString("type", DayType.ARBEIT.name)),
            start = e.timeOrNull("start"),
            end = e.timeOrNull("end"),
            pauseMin = e.optInt("pause", 0),
        )
    }
    val plan = mutableMapOf<DayOfWeek, DayPlan>()
    val planJson = o.optJSONObject("plan")
    if (planJson != null) {
        for (day in DayOfWeek.values()) {
            val p = planJson.optJSONObject(day.name) ?: continue
            plan[day] = DayPlan(p.optBoolean("on"), p.timeOrNull("start"), p.timeOrNull("end"), p.optInt("pause", 0))
        }
    }
    return AppData(entries, plan, o.optBoolean("remindStart", true), o.optBoolean("remindEnd", true))
}
