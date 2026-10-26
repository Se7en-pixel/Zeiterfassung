@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.se7enpixel.zeiterfassung

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle

private val longDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
private val dayLabel = DateTimeFormatter.ofPattern("EE dd.MM.", GERMAN)

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    val titles = listOf("Heute", "Wochen", "Arbeitszeiten")

    AskForNotificationPermission()

    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[tab]) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text(titles[0]) },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) }, label = { Text(titles[1]) },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text(titles[2]) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(onEdit = { editDate = it })
                1 -> WeeksScreen(onEdit = { editDate = it })
                else -> PlanScreen()
            }
        }
    }

    editDate?.let { date ->
        key(date) { EntryDialog(date, onDismiss = { editDate = null }) }
    }
}

@Composable
private fun AskForNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun diffColor(min: Int): Color = when {
    min > 0 -> MaterialTheme.colorScheme.primary
    min < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun pickTime(ctx: Context, initial: LocalTime?, onPicked: (LocalTime) -> Unit) {
    val t = initial ?: LocalTime.of(9, 0)
    TimePickerDialog(ctx, { _, h, m -> onPicked(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
}

fun pickDate(ctx: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        ctx,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}

// ---------- Heute ----------

@Composable
fun TodayScreen(onEdit: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val data = Store.data
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(20_000)
            value = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val plan = data.planFor(today)
    val open = data.openEntry
    val todayEntry = data.entries[today]

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(today.format(longDate), style = MaterialTheme.typography.titleLarge)
            Text(
                if (plan.sollMin > 0) "Geplant: ${plan.start.fmt()} – ${plan.end.fmt()} Uhr, ${plan.pauseMin} Min Pause"
                else "Heute ist kein Arbeitstag eingeplant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (open != null && open.start != null) {
            val started = open.date.atTime(open.start)
            val minutes = Duration.between(started, now).toMinutes().toInt().coerceAtLeast(0)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (open.date == today) "Eingestempelt seit ${open.start.fmt()} Uhr"
                        else "Eingestempelt seit ${open.date.fmtShort()} ${open.start.fmt()} Uhr",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("${fmtDur(minutes)} h", style = MaterialTheme.typography.displayMedium)
                    Button(onClick = { Store.clockOut(ctx) }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Gehen", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = { pickTime(ctx, open.start) { t -> Store.save(ctx, open.copy(start = t)) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Beginn ändern") }
                }
            }
        } else if (todayEntry != null) {
            DaySummaryCard(data, todayEntry, onEdit = { onEdit(today) })
        } else {
            Button(onClick = { Store.clockIn(ctx) }, Modifier.fillMaxWidth().height(72.dp)) {
                Text("Kommen", style = MaterialTheme.typography.titleLarge)
            }
        }

        val week = data.week(mondayOf(today), today)
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Diese Woche (KW ${week.week})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ist ${fmtDur(week.istMin)} h · Soll ${fmtDur(week.sollMin)} h",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "${fmtDiff(week.diffMin)} h",
                    style = MaterialTheme.typography.titleLarge,
                    color = diffColor(week.diffMin),
                )
            }
        }

        OutlinedButton(onClick = { pickDate(ctx, today) { onEdit(it) } }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Tag nachtragen")
        }

        if (data.plan.values.none { it.enabled }) {
            Text(
                "Tipp: Trag unter „Arbeitszeiten“ ein, wann du normalerweise arbeitest. " +
                    "Dann rechnet die App Überstunden aus und erinnert dich ans Ein- und Ausstempeln.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DaySummaryCard(data: AppData, e: Entry, onEdit: () -> Unit) {
    val ist = data.istMin(e)
    val soll = data.sollMin(e.date)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.type == DayType.ARBEIT) {
                Text("${e.start.fmt()} – ${e.end.fmt()} Uhr", style = MaterialTheme.typography.titleLarge)
                Text("Pause: ${e.pauseMin} Min")
            } else {
                Text(e.type.label, style = MaterialTheme.typography.titleLarge)
            }
            Text("Arbeitszeit: ${fmtDur(ist)} h · Soll: ${fmtDur(soll)} h")
            Text(
                "Differenz: ${fmtDiff(ist - soll)} h",
                color = diffColor(ist - soll),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = onEdit, Modifier.fillMaxWidth()) { Text("Bearbeiten") }
        }
    }
}

// ---------- Wochen ----------

@Composable
fun WeeksScreen(onEdit: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val data = Store.data
    val today = LocalDate.now()
    val weeks = remember(data) { data.weeks(today) }
    var expanded by remember { mutableStateOf(setOf(mondayOf(today))) }
    val total = weeks.sumOf { it.diffMin }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Überstunden insgesamt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${fmtDiff(total)} h",
                        style = MaterialTheme.typography.headlineMedium,
                        color = diffColor(total),
                    )
                    OutlinedButton(onClick = { exportCsv(ctx, data) }, enabled = weeks.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Als CSV teilen")
                    }
                }
            }
        }
        if (weeks.isEmpty()) {
            item { Text("Noch keine Einträge.", Modifier.padding(8.dp)) }
        }
        items(weeks, key = { it.monday.toString() }) { w ->
            val isOpen = w.monday in expanded
            Card(
                Modifier.fillMaxWidth().clickable {
                    expanded = if (isOpen) expanded - w.monday else expanded + w.monday
                },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("KW ${w.week} / ${w.year}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${w.monday.fmtShort()} – ${w.monday.plusDays(6).fmtShort()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${fmtDiff(w.diffMin)} h",
                                style = MaterialTheme.typography.titleMedium,
                                color = diffColor(w.diffMin),
                            )
                            Text(
                                "Ist ${fmtDur(w.istMin)} / Soll ${fmtDur(w.sollMin)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        w.days.forEach { r -> DayLine(r, onClick = { onEdit(r.date) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayLine(r: DayRow, onClick: () -> Unit) {
    val e = r.entry
    val text = when {
        e == null -> if (r.missing) "fehlt – antippen zum Nachtragen" else ""
        e.type != DayType.ARBEIT -> e.type.label
        e.isOpen -> "${e.start.fmt()} – läuft"
        else -> "${e.start.fmt()} – ${e.end.fmt()}, ${e.pauseMin} Min Pause"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.date.format(dayLabel), Modifier.width(90.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (r.missing) MaterialTheme.colorScheme.error else LocalContentColor.current,
        )
        if (e != null && r.counted) {
            Text("${fmtDur(r.istMin)} h", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun exportCsv(ctx: Context, data: AppData) {
    fun dec(min: Int) = String.format(GERMAN, "%.2f", min / 60.0)
    val sb = StringBuilder("﻿")
    sb.append("Datum;Wochentag;KW;Art;Beginn;Ende;Pause (Min);Arbeitszeit (h);Soll (h);Differenz (h)\n")
    data.weeks().reversed().forEach { w ->
        w.days.forEach { r ->
            val e = r.entry
            if (e != null || r.missing) {
                val soll = if (r.counted) r.sollMin else 0
                val ist = if (r.counted) r.istMin else 0
                sb.append(
                    listOf(
                        r.date.fmtDate(),
                        r.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, GERMAN),
                        w.week.toString(),
                        e?.type?.label ?: "fehlt",
                        e?.start?.fmt() ?: "",
                        e?.end?.fmt() ?: "",
                        (e?.pauseMin ?: 0).toString(),
                        dec(ist),
                        dec(soll),
                        dec(ist - soll),
                    ).joinToString(";"),
                ).append('\n')
            }
        }
    }
    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
    val file = File(dir, "zeiterfassung.csv")
    file.writeText(sb.toString())
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Zeiterfassung")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "CSV teilen"))
}

// ---------- Arbeitszeiten ----------

@Composable
fun PlanScreen() {
    val ctx = LocalContext.current
    val data = Store.data
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Trag ein, wann du normalerweise arbeitest. Daraus berechnet die App deine Sollzeit und die Überstunden.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DayOfWeek.values().forEach { day ->
            val p = data.planFor(day)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.getDisplayName(DateTextStyle.FULL, GERMAN),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (p.enabled) {
                            Text("Soll ${fmtDur(p.sollMin)} h", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(12.dp))
                        }
                        Switch(checked = p.enabled, onCheckedChange = { on -> Store.setPlan(ctx, day) { it.copy(enabled = on) } })
                    }
                    if (p.enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { pickTime(ctx, p.start) { t -> Store.setPlan(ctx, day) { it.copy(start = t) } } },
                                modifier = Modifier.weight(1f),
                            ) { Text("Von ${p.start.fmt()}") }
                            OutlinedButton(
                                onClick = { pickTime(ctx, p.end ?: LocalTime.of(17, 0)) { t -> Store.setPlan(ctx, day) { it.copy(end = t) } } },
                                modifier = Modifier.weight(1f),
                            ) { Text("Bis ${p.end.fmt()}") }
                        }
                        PauseField(p.pauseMin, label = "Geplante Pause (Minuten)") { m -> Store.setPlan(ctx, day) { it.copy(pauseMin = m) } }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Erinnerungen", style = MaterialTheme.typography.titleLarge)
        SwitchRow("Zum Arbeitsbeginn fragen, ob ich einstempeln möchte", data.remindStart) { on ->
            Store.update(ctx) { it.copy(remindStart = on) }
        }
        SwitchRow("Zum Feierabend fragen, ob ich ausstempeln möchte", data.remindEnd) { on ->
            Store.update(ctx) { it.copy(remindEnd = on) }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Text(
                "Benachrichtigungen sind für diese App ausgeschaltet. Erlaube sie in den Android-Einstellungen, " +
                    "sonst kommen keine Erinnerungen.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PauseField(value: Int, label: String = "Pause (Minuten)", onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val digits = s.filter { it.isDigit() }.take(3)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Tag bearbeiten / nachtragen ----------

@Composable
fun EntryDialog(date: LocalDate, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val data = Store.data
    val existing = data.entries[date]
    val plan = data.planFor(date)
    // Neue Tage werden mit den regulären Arbeitszeiten vorausgefüllt.
    var type by remember { mutableStateOf(existing?.type ?: DayType.ARBEIT) }
    var start by remember { mutableStateOf(if (existing != null) existing.start else plan.start.takeIf { plan.enabled }) }
    var end by remember { mutableStateOf(if (existing != null) existing.end else plan.end.takeIf { plan.enabled }) }
    var pause by remember { mutableIntStateOf(existing?.pauseMin ?: if (plan.enabled) plan.pauseMin else 0) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(longDate)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayType.values().forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { type = t },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t.label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (type == DayType.ARBEIT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime(ctx, start) { start = it } }, Modifier.weight(1f)) {
                            Text("Beginn ${start.fmt()}")
                        }
                        OutlinedButton(onClick = { pickTime(ctx, end ?: start) { end = it } }, Modifier.weight(1f)) {
                            Text("Ende ${end.fmt()}")
                        }
                    }
                    PauseField(pause) { pause = it }
                    val s = start
                    val e = end
                    when {
                        s != null && e != null -> Text("Arbeitszeit: ${fmtDur(maxOf(0, spanMinutes(s, e) - pause))} h")
                        s != null -> Text("Noch kein Ende eingetragen, der Tag läuft noch.")
                        else -> Text("Bitte einen Beginn eintragen.", color = MaterialTheme.colorScheme.error)
                    }
                } else if (type.credited) {
                    Text("Zählt wie ein normaler Arbeitstag (${fmtDur(plan.sollMin)} h).")
                } else {
                    Text("Die Sollzeit von ${fmtDur(plan.sollMin)} h wird von den Überstunden abgezogen.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = type != DayType.ARBEIT || start != null,
                onClick = {
                    val work = type == DayType.ARBEIT
                    Store.save(
                        ctx,
                        Entry(
                            date = date,
                            type = type,
                            start = if (work) start else null,
                            end = if (work) end else null,
                            pauseMin = if (work) pause else 0,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = {
                        if (confirmDelete) {
                            Store.delete(ctx, date)
                            onDismiss()
                        } else {
                            confirmDelete = true
                        }
                    }) {
                        Text(if (confirmDelete) "Wirklich löschen?" else "Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
