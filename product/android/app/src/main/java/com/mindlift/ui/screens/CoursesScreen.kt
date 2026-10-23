package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindlift.R
import com.mindlift.bridge.Course
import com.mindlift.ui.ExamDateResult
import com.mindlift.ui.ExamDateTime
import com.mindlift.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    state: MainViewModel.State,
    onCreate: (String, String?) -> Unit,
    onExamDateChanged: (String) -> Unit,
    onOpen: (Course) -> Unit,
    onDelete: (String) -> Unit,
    onPrivacy: () -> Unit,
    onHowToUse: () -> Unit,
    onProductTour: () -> Unit,
    onLaunchChecklist: () -> Unit,
    onDismissMessage: () -> Unit,
    onLife: (() -> Unit)? = null,   // full flavor only: life-modes control room
) {
    var title by remember { mutableStateOf("") }
    var exam by remember { mutableStateOf("") }
    var examTime by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Course?>(null) }

    deleteTarget?.let { course ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Sterge cursul?") },
            text = { Text("\"${course.title}\" va fi arhivat. Datele nu se pot recupera.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(course.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                ) { Text("Sterge") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Anuleaza") }
            },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                onLife?.let { TextButton(onClick = it) { Text("Viata") } }
                IconButton(onClick = onPrivacy) {
                    Icon(Icons.Default.Lock, contentDescription = "Confidentialitate")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.message?.let {
                SectionCard {
                    Text(it)
                    OutlinedButton(onClick = onDismissMessage) { Text("Am inteles") }
                }
            }

            if (state.courses.isNotEmpty()) {
                state.agendaHeadline?.let {
                    SectionCard(title = "Azi") {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SectionCard(title = "Curs nou") {
                Text("Creeaza un curs local. Nu ai nevoie de cont si nu se trimite nimic pe internet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Titlul cursului") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = exam,
                    onValueChange = { exam = it; onExamDateChanged(it) },
                    label = { Text("Data examenului (optional)") },
                    placeholder = { Text(state.datePreview?.placeholder ?: "15.06.2026 sau 15 iunie 2026") },
                    isError = state.datePreview?.error != null,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )

                // Localized shortcut chips (Maine / Saptamana viitoare / In 14 zile / In 30 zile).
                val shortcuts = state.datePreview?.shortcuts ?: emptyList()
                if (shortcuts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        shortcuts.forEach { sc ->
                            AssistChip(
                                onClick = { exam = sc.value; onExamDateChanged(sc.value) },
                                label = { Text(sc.label) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = examTime, onValueChange = { examTime = it },
                    label = { Text("Ora examenului (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )

                // Date validation comes from the engine (accepts human-typed dates);
                // the time portion is still validated UI-side and combined below.
                val preview = state.datePreview
                val examEmpty = exam.isBlank()
                val isoDate = preview?.normalizedValue
                val timeResult = if (isoDate != null) ExamDateTime.parse(isoDate, examTime) else ExamDateResult.Empty

                val errorText = when {
                    examEmpty -> null
                    preview?.error != null -> preview.error
                    timeResult is ExamDateResult.Invalid -> timeResult.reason
                    else -> null
                }
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (!examEmpty && errorText == null && preview?.relativeLabel?.isNotBlank() == true) {
                    Text("${preview.displayValue} · ${preview.relativeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val canSaveDate = examEmpty || (preview?.canSave == true && timeResult !is ExamDateResult.Invalid)
                PrimaryAction(
                    text = "Creeaza curs",
                    enabled = title.isNotBlank() && canSaveDate && !state.busy,
                    onClick = {
                        val iso = (timeResult as? ExamDateResult.Ok)?.iso ?: isoDate
                        onCreate(title.trim(), iso)
                        title = ""; exam = ""; examTime = ""
                        onExamDateChanged("")
                    },
                )
            }

            if (state.courses.isEmpty()) {
                SectionCard {
                    Text("Nu ai inca un curs. Creeaza unul, adauga materialul tau si AntiROTAid va construi carduri offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                state.courses.forEach { course ->
                    SectionCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    course.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                course.examDate?.let {
                                    Text(
                                        "Examen: ${it.replace('T', ' ')}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { deleteTarget = course }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sterge",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Button(
                            onClick = { onOpen(course) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Deschide") }
                    }
                }
            }
        }
    }
}
