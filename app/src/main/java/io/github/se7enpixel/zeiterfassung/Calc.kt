package io.github.se7enpixel.zeiterfassung

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

val GERMAN: Locale = Locale.GERMANY
private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
private val shortDateFormat = DateTimeFormatter.ofPattern("dd.MM.")
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalTime?.fmt(): String = this?.format(timeFormat) ?: "--:--"
fun LocalDate.fmtShort(): String = format(shortDateFormat)
fun LocalDate.fmtDate(): String = format(dateFormat)

/** 125 -> "2:05", -30 -> "−0:30" */
fun fmtDur(min: Int): String {
    val a = abs(min)
    val s = "%d:%02d".format(a / 60, a % 60)
    return if (min < 0) "−$s" else s
}

/** Wie [fmtDur], aber immer mit Vorzeichen. */
fun fmtDiff(min: Int): String = when {
    min > 0 -> "+" + fmtDur(min)
    min < 0 -> fmtDur(min)
    else -> "±0:00"
}

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun AppData.sollMin(date: LocalDate): Int = planFor(date).sollMin

/** Gearbeitete Minuten eines Eintrags. Urlaub, Krank und Feiertag zählen mit der Sollzeit. */
fun AppData.istMin(e: Entry): Int = when {
    e.type.credited -> sollMin(e.date)
    e.type == DayType.ARBEIT && e.start != null && e.end != null -> maxOf(0, spanMinutes(e.start, e.end) - e.pauseMin)
    else -> 0
}

data class DayRow(
    val date: LocalDate,
    val entry: Entry?,
    val sollMin: Int,
    val istMin: Int,
    /** Zählt der Tag schon für die Überstunden? */
    val counted: Boolean,
    /** Ein Arbeitstag in der Vergangenheit ohne Eintrag. */
    val missing: Boolean,
)

data class WeekSummary(val year: Int, val week: Int, val monday: LocalDate, val days: List<DayRow>) {
    val sollMin: Int get() = days.filter { it.counted }.sumOf { it.sollMin }
    val istMin: Int get() = days.filter { it.counted }.sumOf { it.istMin }
    val diffMin: Int get() = istMin - sollMin
}

/**
 * Eine Kalenderwoche. Gezählt werden die Tage ab dem ersten Eintrag bis gestern,
 * und heute erst, wenn der Tag abgeschlossen ist.
 */
fun AppData.week(monday: LocalDate, today: LocalDate = LocalDate.now()): WeekSummary {
    val first = entries.keys.minOrNull()
    val days = (0L..6L).map { i ->
        val d = monday.plusDays(i)
        val entry = entries[d]
        val inRange = first != null && !d.isBefore(first)
        val counted = when {
            d.isAfter(today) -> false
            d == today -> entry != null && !entry.isOpen
            else -> inRange
        }
        val soll = sollMin(d)
        DayRow(
            date = d,
            entry = entry,
            sollMin = soll,
            istMin = if (entry != null) istMin(entry) else 0,
            counted = counted,
            missing = counted && entry == null && soll > 0,
        )
    }
    val wf = WeekFields.ISO
    return WeekSummary(monday.get(wf.weekBasedYear()), monday.get(wf.weekOfWeekBasedYear()), monday, days)
}

/** Alle Wochen vom ersten Eintrag bis heute, die neueste zuerst. */
fun AppData.weeks(today: LocalDate = LocalDate.now()): List<WeekSummary> {
    val first = entries.keys.minOrNull() ?: return emptyList()
    val last = maxOf(today, entries.keys.max())
    val firstMonday = mondayOf(first)
    val result = mutableListOf<WeekSummary>()
    var monday = mondayOf(last)
    while (!monday.isBefore(firstMonday)) {
        result += week(monday, today)
        monday = monday.minusWeeks(1)
    }
    return result
}
