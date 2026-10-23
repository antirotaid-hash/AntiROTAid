package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel

/**
 * The study loop: show a prompt, reveal the answer, then self-grade. Grading is
 * agency-preserving — every option is a valid choice, none is a "wrong" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.due.isEmpty()) vm.refreshDue() }

    val card = state.due.getOrNull(state.currentIndex)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Studiu") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (card == null) {
                SectionCard {
                    Text("Nu ai carduri de revazut acum. Revino mai tarziu sau adauga material nou.",
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Inapoi")
                    }
                }
                return@Column
            }

            Text("Card ${state.currentIndex + 1} din ${state.due.size}",
                style = MaterialTheme.typography.bodyMedium)

            // "Why this card now?" — turn the SRS from a black box into a reason.
            state.whyNow[card.cardId]?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            SectionCard(title = "Intrebare") {
                Text(card.prompt, style = MaterialTheme.typography.bodyLarge)
            }

            if (!state.revealed) {
                Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) {
                    Text("Arata raspunsul")
                }
            } else {
                SectionCard(title = "Raspuns") {
                    Text(card.answer.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge)
                }
                Text("Cum a mers?", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeButton("Nu stiu inca", "again", vm, Modifier.weight(1f))
                    GradeButton("A fost greu", "hard", vm, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeButton("Bine", "good", vm, Modifier.weight(1f))
                    GradeButton("Usor", "easy", vm, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, grade: String, vm: CourseViewModel, modifier: Modifier) {
    Button(onClick = { vm.grade(grade) }, modifier = modifier) { Text(label) }
}
