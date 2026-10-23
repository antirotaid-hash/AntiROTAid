package com.mindlift.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.R
import com.mindlift.ui.CourseViewModel
import com.mindlift.ui.ExamDateResult
import com.mindlift.ui.ExamDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    courseId: String,
    title: String,
    vm: CourseViewModel,
    onStudy: () -> Unit,
    onPlan: () -> Unit,
    onReport: () -> Unit,
    onAutopilot: () -> Unit,
    onSyllabus: () -> Unit,
    onOral: () -> Unit,
    onQuality: () -> Unit,
    onIntelligence: () -> Unit,
    onRecovery: () -> Unit,
    onImportWorkbench: () -> Unit,
    onCompliance: () -> Unit,
    onInsights: () -> Unit,
    onProgress: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var material by remember { mutableStateOf("") }
    var examDateInput by remember { mutableStateOf("") }
    var examTimeInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val name = fileNameFromUri(context, uri) ?: "import.txt"
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    name to Base64.encodeToString(bytes, Base64.NO_WRAP)
                }.getOrNull()
            }
            if (payload != null) vm.importFile(payload.first, payload.second)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let {
                SectionCard {
                    Text(it)
                    OutlinedButton(onClick = vm::clearMessage) { Text("Am inteles") }
                }
            }

            HeroPanel(
                eyebrow = com.mindlift.Features.brandName,
                title = title,
                subtitle = if (com.mindlift.Features.playSafe)
                    "Adauga materialul de curs, apoi AntiROTAid construieste carduri si pasi de studiu direct pe telefon. Fara cont, fara server, fara internet."
                else
                    "Adauga materialul de curs, pregateste carduri si foloseste modulele avansate ale buildului privat."
            ) {
                StatRow(
                    "Carduri" to state.deckSize.toString(),
                    "Motor" to "Offline",
                    "Stare" to (if (state.deckSize > 0) "gata de studiu" else "adauga material")
                )
            }

            // v10 boost pack: habit + weak-spot + interop, all in one compact card.
            if (state.deckSize > 0) {
                SectionCard(title = "Recomandare de azi") {
                    state.nudge?.optString("nudge")?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    state.streak?.let { s ->
                        val streak = s.optInt("current_streak")
                        val goal = s.optInt("daily_goal")
                        val done = s.optInt("today_count")
                        Text(
                            (if (streak >= 2) "$streak zile la rand" else "Incepe-ti seria azi")
                                + "  ·  azi $done/$goal carduri",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    state.digest?.optString("narrative")?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.leech?.let { l ->
                        val n = l.optInt("leech_count")
                        if (n > 0) Text("$n carduri de reparat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.predictedGrade?.let { g ->
                            Text("Nivel estimat: ${g.optDouble("predicted_grade")}/10",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        state.achievements?.let { a ->
                            Text("Repere: ${a.optInt("earned_count")}/${a.optInt("total")}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.weakDrill?.let { d ->
                        val concepts = d.optJSONArray("weak_concepts")
                        if (concepts != null && concepts.length() > 0) {
                            val names = (0 until concepts.length()).joinToString(", ") { concepts.optString(it) }
                            Text("Puncte slabe: $names",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.loadWeakDrill() }, enabled = !state.busy) {
                            Text("Puncte slabe")
                        }
                        OutlinedButton(onClick = { vm.exportAnki() }, enabled = !state.busy) {
                            Text("Export Anki")
                        }
                    }
                    OutlinedButton(onClick = { vm.enrichDeck() }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Adauga variante de recapitulare")
                    }
                }
            }

            SectionCard(title = "Data examenului") {
                OutlinedTextField(
                    value = examDateInput,
                    onValueChange = { examDateInput = it },
                    label = { Text("Data (ex: 15 iunie 2026)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("15.06.2026 sau 15 iunie 2026") },
                )
                OutlinedTextField(
                    value = examTimeInput,
                    onValueChange = { examTimeInput = it },
                    label = { Text("Ora, optional (ex: 14:30)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("HH:MM") },
                )
                val examResult = ExamDateTime.parse(examDateInput, examTimeInput)
                (examResult as? ExamDateResult.Invalid)?.let {
                    Text(it.reason, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                PrimaryAction(
                    text = "Salveaza data",
                    enabled = examDateInput.isNotBlank() &&
                        examResult !is ExamDateResult.Invalid && !state.busy,
                    onClick = { (examResult as? ExamDateResult.Ok)?.let { vm.setExamDate(it.iso) } },
                )
            }

            SectionCard(title = stringResource(R.string.import_title)) {
                OutlinedTextField(
                    value = material, onValueChange = { material = it },
                    label = { Text(stringResource(R.string.import_hint)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 4,
                )
                PrimaryAction(
                    text = stringResource(
                        if (com.mindlift.Features.playSafe) R.string.action_build_deck_local
                        else R.string.action_build_deck
                    ),
                    enabled = material.isNotBlank() && !state.busy,
                    onClick = { vm.importMaterial(material) },
                )
                state.importWarning?.let { w ->
                    Text(
                        w.warning ?: "Textul pare administrativ/logistic. Adauga continut de curs inainte de a genera carduri.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmImport() }, enabled = !state.busy) {
                            Text("Genereaza oricum")
                        }
                        OutlinedButton(onClick = { vm.dismissImportWarning() }) {
                            Text("Renunta")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { pickFile.launch(arrayOf("*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Importa fisier local (PDF / DOCX / PPTX / TXT)") }
            }

            state.dna?.let { dna ->
                SectionCard(title = "Ce a inteles AntiROTAid") {
                    Text("${dna.conceptCount} concepte gasite, ${dna.topicCount} teme")
                    if (dna.bridgeConcepts.isNotEmpty()) {
                        Text("Concepte importante: ${dna.bridgeConcepts.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("$${state.deckSize} carduri pregatite".removePrefix("$"))
                }
            }

            state.progress?.let { p ->
                SectionCard(title = "Progres") {
                    Text("Studiate: ${(p.studiedRatio * 100).toInt()}%  •  de revazut: ${p.dueCount}")
                    Text(p.bandCounts.entries.joinToString("  ") { "${bandLabel(it.key)}:${it.value}" },
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.dashboard?.let { dash ->
                SectionCard(title = "Rezumat") {
                    Text("${dash.sourceCount} surse • ${dash.passageCount} pasaje • ${dash.cardCount} carduri")
                    Text("Pas recomandat: ${dash.nextActionTitle}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.pulse?.let { pulse ->
                SectionCard(title = "Ritm de studiu") {
                    Text("Mai ai azi: ${pulse.remainingMinutes.toInt()} min • bloc: ${pulse.recommendedBlockMinutes.toInt()} min")
                    Text("Ritm: ${pulse.mode} • verificare in ${pulse.pollAfterMinutes} min")
                    if (pulse.weakConcepts.isNotEmpty()) Text("De consolidat: ${pulse.weakConcepts.take(4).joinToString(", ")}")
                }
            }

            SectionCard(title = "Ce faci acum") {
                PrimaryAction("Studiaza cardurile", enabled = state.deckSize > 0, onClick = onStudy)
                PrimaryAction("Vezi progresul", enabled = state.deckSize > 0, onClick = onProgress)
                SecondaryAction("Planul de azi", onPlan)
                SecondaryAction("Harta cursului", onSyllabus)
                SecondaryAction("Verifica deck-ul", onQuality)
                SecondaryAction("Raportul zilei", onReport)
                if (!com.mindlift.Features.playSafe) {
                    SecondaryAction("Mod oral", onOral)
                    SecondaryAction("Analiza avansata", onIntelligence)
                    SecondaryAction("Insights avansate", onInsights)
                    SecondaryAction("Recapitulare ghidata", onRecovery)
                    SecondaryAction("Import workbench", onImportWorkbench)
                    SecondaryAction("Manual / release compliance", onCompliance)
                    SecondaryAction("Autopilot privat", onAutopilot)
                }
            }

            // Attention monitor needs PACKAGE_USAGE_STATS + SYSTEM_ALERT_WINDOW,
            // both stripped from the public build. Hide its controls there.
            if (!com.mindlift.Features.playSafe) {
                MonitorControls(courseId = courseId)
            }
        }
    }
}

/** Resolve the human file name (with extension) so the engine can pick the right
 *  adapter (.pptx/.pdf/.docx/...). Falls back to null if the provider hides it. */
private fun fileNameFromUri(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
    }
    return null
}
