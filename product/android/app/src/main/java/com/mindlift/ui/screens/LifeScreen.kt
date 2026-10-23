package com.mindlift.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import com.mindlift.life.CalendarReader
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.TimeZone

/**
 * Life modes control room (full flavor): pick who you are (student / angajat /
 * adult / razboi total), set the sleep window, register bills, and pull the
 * next-48h meetings from the device calendar. Everything is engine state —
 * this screen only reads `life_status` and issues the `life_*` mutations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeScreen(repo: EngineRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var status by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        when (val r = repo.lifeStatus()) {
            is EngineResult.Ok -> status = r.value
            is EngineResult.Err -> message = r.message
        }
    }

    fun syncCalendar() = scope.launch {
        val meetings = CalendarReader.upcomingMeetings(context)
        if (meetings == null) {
            message = "Permisiunea de calendar lipseste."
        } else {
            repo.lifeSyncMeetings(meetings)
            CalendarReader.upcomingAllDayEvents(context)?.let { repo.lifeSyncEvents(it) }
            refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) syncCalendar() else message = "Fara calendar, modul angajat nu vede meetingurile." }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Moduri de viata") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            message?.let {
                SectionCard {
                    Text(it)
                    OutlinedButton(onClick = { message = null }) { Text("Am inteles") }
                }
            }

            val st = status

            SectionCard(title = "Raportul de razboi") {
                var report by remember { mutableStateOf<JSONObject?>(null) }
                LaunchedEffect(st) {
                    val r = repo.lifeWarReport()
                    if (r is EngineResult.Ok) report = r.value
                }
                report?.let { rep ->
                    Text(rep.optString("headline"),
                        style = MaterialTheme.typography.titleMedium,
                        color = when (rep.optString("grade")) {
                            "A" -> MaterialTheme.colorScheme.primary
                            "D" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                    val lines = rep.optJSONArray("lines")
                    if (lines != null) for (i in 0 until lines.length()) {
                        Text("• ${lines.optString(i)}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Doar pentru ochii tai (sau un screenshot pentru un prieten). Nu se trimite nicaieri.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Cine esti acum") {
                Text(
                    "Modul decide ce are voie sa te intrerupa din scroll: examene, meetinguri, facturi, somn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = st?.optString("mode") ?: "student"
                val modes = listOf(
                    "student" to "Student", "employee" to "Angajat",
                    "adult" to "Adult", "total_war" to "Razboi total",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { (key, label) ->
                        FilterChip(
                            selected = current == key,
                            onClick = { scope.launch { repo.lifeSetMode(key); refresh() } },
                            label = { Text(label) },
                        )
                    }
                }
                st?.optJSONObject("dominant")?.let { dom ->
                    Text(
                        "Acum apasa: ${dom.optString("kind")} — ${dom.optString("title")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                st?.optJSONObject("scroll_profile")?.let { prof ->
                    val streak = prof.optInt("clean_day_streak")
                    val base = prof.optDouble("baseline_minutes", 0.0).toInt()
                    Text(
                        if (prof.optBoolean("is_binge"))
                            "AZI E BINGE: ${prof.optDouble("today_minutes").toInt()} min, peste normalul tau de $base."
                        else
                            "Serie curata: $streak zile fara binge. Normalul tau: $base min/zi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (prof.optBoolean("is_binge"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            SectionCard(title = "Portofelul de scroll") {
                var walletInfo by remember { mutableStateOf<JSONObject?>(null) }
                LaunchedEffect(st) {
                    val r = repo.lifeWallet()
                    if (r is EngineResult.Ok) walletInfo = r.value
                }
                val w = walletInfo?.optJSONObject("wallet")
                val loan = walletInfo?.optJSONObject("loan")
                Text(
                    "Sold: ${w?.optDouble("balance_min", 0.0)?.toInt() ?: 0} min de scroll fara overlay. " +
                        "Castigi 2 min pe card stiut la studiu (max 30/zi). Somnul, ora sfanta si panica NU se cumpara.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (loan?.optBoolean("open") == true) {
                    Text(
                        "IMPRUMUT DESCHIS: mai datorezi ${loan.optInt("remaining_cards")} carduri " +
                            "pana la ${loan.optString("due_at").take(16).replace('T', ' ')}." +
                            if (loan.optBoolean("rolled")) " (RESTANT — dublat)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30).forEach { min ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        when (val r = repo.lifeTakeLoan(min)) {
                                            is EngineResult.Ok -> refresh()
                                            is EngineResult.Err -> message = r.message
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("+$min min") }
                        }
                    }
                    Text(
                        "Imprumut: 3 carduri stiute la fiecare 5 min, scadenta 21:00. Neplatit = escaladare + datoria se dubleaza.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Mod panica (examen maine)") {
                val panic = st?.optJSONObject("panic")
                if (panic?.optBoolean("active") == true) {
                    Text(
                        "PANICA ACTIVA pana la ${panic.optString("until").take(16).replace('T', ' ')}. " +
                            "Carduri la 20 de secunde, taxa maxima, zero negociere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = { scope.launch { repo.lifeSetPanic(null); refresh() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stai jos, soldat (opreste panica)") }
                } else {
                    Text(
                        "Examen maine? Apasa si totul merge la maxim pana maine seara: carduri continue, fara amanare.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            // until tomorrow 23:59, device-local day
                            val cal = java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 23)
                                set(java.util.Calendar.MINUTE, 59)
                            }
                            val iso = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                .atOffset(java.time.ZoneOffset.UTC)
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            scope.launch { repo.lifeSetPanic(iso); refresh() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("MOBILIZARE TOTALA") }
                }
            }

            SectionCard(title = "Vacanta (armistitiu declarat)") {
                val vac = st?.optJSONObject("vacation")
                if (vac?.optBoolean("active") == true) {
                    Text(
                        "Vacanta activa pana la ${vac.optString("until").take(10)}. Totul tace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = { scope.launch { repo.lifeSetVacation(null); refresh() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Opreste vacanta acum") }
                } else {
                    Text(
                        "Pauza oficiala, cu data de sfarsit. Mai cinstit decat sa dezinstalezi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    var vacUntil by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vacUntil, onValueChange = { vacUntil = it },
                            label = { Text("Pana la") }, placeholder = { Text("20.06.2026") },
                            modifier = Modifier.weight(1f), singleLine = true,
                        )
                        Button(onClick = {
                            val iso = parseDayToIso(vacUntil.trim())
                            if (iso == null) message = "Pune data de sfarsit (zz.ll.aaaa)."
                            else scope.launch {
                                when (val r = repo.lifeSetVacation(iso)) {
                                    is EngineResult.Ok -> refresh()
                                    is EngineResult.Err -> message = r.message
                                }
                            }
                        }) { Text("Activeaza") }
                    }
                }
            }

            SectionCard(title = "Contractul de seara") {
                Text(
                    "Tu decizi cat scroll iti dai voie azi. Diseara, overlay-ul te citeaza pe tine, nu o regula impusa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var contractMin by remember { mutableStateOf("") }
                var contractInfo by remember { mutableStateOf<String?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contractMin, onValueChange = { contractMin = it },
                        label = { Text("Minute de scroll azi") },
                        placeholder = { Text("30") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    Button(onClick = {
                        val m = contractMin.trim().toIntOrNull()
                        if (m == null || m <= 0) {
                            message = "Pune un numar de minute."
                        } else scope.launch {
                            when (val r = repo.lifeSetContract(m)) {
                                is EngineResult.Ok ->
                                    contractInfo = "Semnat: $m min pentru azi. Te tinem de cuvant."
                                is EngineResult.Err -> message = r.message
                            }
                        }
                    }) { Text("Semneaza") }
                }
                contractInfo?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionCard(title = "Ora sfanta de studiu") {
                val focus = st?.optJSONObject("focus")
                if (focus?.optBoolean("enabled") == true) {
                    Text(
                        "Activa: ${focus.optString("start")}–${focus.optString("end")} zilnic. " +
                            "Scroll in fereastra asta = promisiune rupta, presiune maxima.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = { scope.launch { repo.lifeSetFocusWindow(null, null, 0); refresh() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Dezactiveaza") }
                } else {
                    Text(
                        "Alege o ora zilnica doar pentru invatat. In ea, scrollul e tratat ca promisiune rupta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    var focusStart by remember { mutableStateOf("18:00") }
                    var focusEnd by remember { mutableStateOf("19:00") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = focusStart, onValueChange = { focusStart = it },
                            label = { Text("De la") }, modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = focusEnd, onValueChange = { focusEnd = it },
                            label = { Text("Pana la") }, modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Button(
                        onClick = {
                            val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
                            scope.launch {
                                when (val r = repo.lifeSetFocusWindow(
                                        focusStart.trim(), focusEnd.trim(), tz)) {
                                    is EngineResult.Ok -> refresh()
                                    is EngineResult.Err -> message = r.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Declara ora sfanta") }
                }
            }

            SectionCard(title = "Somn") {
                val win = st?.optJSONObject("sleep_window")
                var bed by remember(win) { mutableStateOf(win?.optString("bedtime") ?: "23:30") }
                var wake by remember(win) { mutableStateOf(win?.optString("wake") ?: "07:00") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bed, onValueChange = { bed = it },
                        label = { Text("Culcare") }, modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = wake, onValueChange = { wake = it },
                        label = { Text("Trezire") }, modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
                        scope.launch {
                            when (val r = repo.lifeSetSleepWindow(bed.trim(), wake.trim(), tz)) {
                                is EngineResult.Ok -> refresh()
                                is EngineResult.Err -> message = r.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salveaza fereastra de somn") }
                Text(
                    "Dupa ora de culcare, cardurile devin tot mai dese pana lasi telefonul.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var debtLine by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(st) {
                    val r = repo.lifeSleepDebt()
                    if (r is EngineResult.Ok) {
                        val d = r.value.optDouble("debt_minutes", 0.0)
                        debtLine = if (d <= 0.0) "Datorie de somn: 0 min. Nopti curate."
                        else "Datorie de somn: ${d.toInt()} min — azi presiunea porneste mai sus."
                    }
                }
                debtLine?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = if (it.startsWith("Datorie de somn: 0"))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
            }

            SectionCard(title = "Facturi") {
                var billTitle by remember { mutableStateOf("") }
                var billDue by remember { mutableStateOf("") }
                var billAmount by remember { mutableStateOf("") }
                var billMonthly by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = billTitle, onValueChange = { billTitle = it },
                    label = { Text("Ce platesti (ex: chirie)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billDue, onValueChange = { billDue = it },
                        label = { Text("Scadenta") }, placeholder = { Text("20.06.2026") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        value = billAmount, onValueChange = { billAmount = it },
                        label = { Text("Suma (optional)") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = billMonthly,
                        onClick = { billMonthly = !billMonthly },
                        label = { Text("Lunara (chirie, abonament)") },
                    )
                }
                Button(
                    onClick = {
                        val iso = parseDayToIso(billDue.trim())
                        if (billTitle.isBlank() || iso == null) {
                            message = "Completeaza ce platesti si scadenta (zz.ll.aaaa)."
                        } else scope.launch {
                            when (val r = repo.lifeAddBill(billTitle.trim(), iso,
                                    billAmount.trim(),
                                    if (billMonthly) "monthly" else "")) {
                                is EngineResult.Ok -> {
                                    billTitle = ""; billDue = ""; billAmount = ""
                                    billMonthly = false
                                    refresh()
                                }
                                is EngineResult.Err -> message = r.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adauga factura") }

                val bills = st?.optJSONArray("bills")
                if (bills != null && bills.length() > 0) {
                    for (i in 0 until bills.length()) {
                        val b = bills.optJSONObject(i) ?: continue
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    b.optString("title") +
                                        b.optString("amount").takeIf { it.isNotBlank() }
                                            ?.let { " ($it)" }.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    if (b.optBoolean("paid")) "platita"
                                    else "scadenta " + b.optString("due_at").take(10),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!b.optBoolean("paid")) {
                                TextButton(onClick = {
                                    scope.launch { repo.lifePayBill(b.optString("id")); refresh() }
                                }) { Text("Platita") }
                            }
                            TextButton(onClick = {
                                scope.launch { repo.lifeRemoveBill(b.optString("id")); refresh() }
                            }) { Text("Sterge") }
                        }
                    }
                } else {
                    Text(
                        "Nicio factura inregistrata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Meetinguri (calendar)") {
                Text(
                    "Citim local urmatoarele 48h din calendarul telefonului. Nimic nu pleaca de pe telefon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (CalendarReader.hasPermission(context)) syncCalendar()
                        else permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Citeste calendarul acum") }
                val meetings = st?.optJSONArray("meetings")
                if (meetings != null && meetings.length() > 0) {
                    for (i in 0 until meetings.length()) {
                        val m = meetings.optJSONObject(i) ?: continue
                        Text(
                            "• ${m.optString("title")} — ${m.optString("due_at").take(16).replace('T', ' ')}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text(
                        "Niciun meeting in fereastra de 48h (sau calendarul nu e citit inca).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "20.06.2026" or "2026-06-20" -> ISO datetime at noon UTC, or null. */
private fun parseDayToIso(raw: String): String? {
    val dot = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""").find(raw)
    if (dot != null) {
        val (d, m, y) = dot.destructured
        return "%s-%02d-%02dT12:00:00+00:00".format(y, m.toInt(), d.toInt())
    }
    val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(raw)
    if (iso != null) return "${raw}T12:00:00+00:00"
    return null
}
